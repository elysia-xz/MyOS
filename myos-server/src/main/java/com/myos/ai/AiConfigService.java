package com.myos.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 网关配置的「唯一真相」（对应 PRD §7.3）。
 *
 * 为什么要有这个类：Key 原本是构造期 {@code @Value} 固化的，改一次就得重启后端。
 * 现在把配置的读写集中到这里，网关每次调用时向这里要，界面上一保存就即时生效。
 *
 * 取值优先级：数据库 app_config（界面里填的） &gt; 环境变量（部署时注入的） &gt; 内置默认值。
 * 这样两种用法都成立：
 *   - 本机开发：什么都不配，直接 export MYOS_AI_API_KEY
 *   - 多端部署：在设置页里填一次，所有设备共享，不用重启后端
 *
 * 安全约束：**明文 Key 只在本类内部流转**。对外只暴露 {@link #mask(String)} 之后的形态，
 * 完整的 Key 既不进日志、也不出现在任何接口响应里。
 *
 * 存储方式：明文入库。对本项目（单用户、本机 PostgreSQL、Key 由用户自己填）是可接受的
 * 取舍——若改为加密，主密钥仍得有地方放，反而增加复杂度。要部署到公网时应重新评估。
 */
@Service
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    static final String K_BASE_URL = "ai.base-url";
    static final String K_MODEL = "ai.model";
    static final String K_API_KEY = "ai.api-key";

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final JdbcTemplate jdbc;
    private final String envBaseUrl;
    private final String envModel;
    private final String envApiKey;

    public AiConfigService(JdbcTemplate jdbc,
                           @Value("${myos.ai.base-url:" + DEFAULT_BASE_URL + "}") String envBaseUrl,
                           @Value("${myos.ai.model:" + DEFAULT_MODEL + "}") String envModel,
                           @Value("${myos.ai.api-key:}") String envApiKey) {
        this.jdbc = jdbc;
        this.envBaseUrl = envBaseUrl;
        this.envModel = envModel;
        this.envApiKey = envApiKey;
    }

    /** 生效配置。含明文 Key，**仅限后端内部使用**，绝不可直接返回给前端。 */
    public record Effective(String baseUrl, String model, String apiKey, String source) {}

    /** 表里的一行：值 + 最后更新时间 */
    private record Row(String value, String updatedAt) {}

    public Effective effective() {
        return effective(readAll());
    }

    private Effective effective(Map<String, Row> db) {
        Row keyRow = db.get(K_API_KEY);
        String dbKey = keyRow == null ? null : keyRow.value();

        String source;
        if (!isBlank(dbKey)) {
            source = "DB";
        } else if (!isBlank(envApiKey)) {
            source = "ENV";
        } else {
            source = "NONE";
        }

        return new Effective(
                pick(value(db.get(K_BASE_URL)), envBaseUrl, DEFAULT_BASE_URL),
                pick(value(db.get(K_MODEL)), envModel, DEFAULT_MODEL),
                pick(dbKey, envApiKey, ""),
                source);
    }

    /**
     * 面向接口的状态视图。**只回脱敏值**，永远不含完整 Key。
     */
    public Map<String, Object> status() {
        Map<String, Row> db = readAll();
        Effective e = effective(db);

        Map<String, Object> out = new LinkedHashMap<>();
        boolean configured = !isBlank(e.apiKey());
        out.put("configured", configured);
        out.put("available", configured);           // 兼容 /ai/status 的既有字段名
        out.put("source", e.source());              // DB / ENV / NONE
        out.put("baseUrl", e.baseUrl());
        out.put("model", e.model());
        out.put("masked", configured ? mask(e.apiKey()) : "");
        // 库里到底有没有存过东西。前端据此决定要不要显示「清空配置」——
        // 只看 masked 是不够的：只存了模型名、没存 Key 时也需要能清空。
        out.put("stored", db.containsKey(K_API_KEY) || db.containsKey(K_BASE_URL) || db.containsKey(K_MODEL));
        out.put("updatedAt", latestUpdatedAt(db));
        return out;
    }

    /**
     * 写入配置。三个字段分别独立判断，留空即「不改动」——
     * 这样界面上只改模型名时，不会把已保存的 Key 抹掉。
     *
     * @param apiKey 留空表示保留原有 Key
     * @param reset  为 true 时清空全部三项，让配置退回环境变量 / 内置默认值
     *
     * 为什么 reset 要连 base-url 和 model 一起删：库的优先级高于环境变量，只要库里
     * 还留着 base-url，部署时注入的 MYOS_AI_BASE_URL 就会被静默盖掉，而界面上又
     * 没法把字段改回空（空 = 不改动）——那就永远回不到环境变量那条路了。
     * 所以「清空」必须是整体清空，不能只清 Key。
     */
    public void save(String baseUrl, String model, String apiKey, boolean reset) {
        if (reset) {
            delete(K_API_KEY);
            delete(K_BASE_URL);
            delete(K_MODEL);
            return;
        }
        if (!isBlank(baseUrl)) put(K_BASE_URL, baseUrl.trim());
        if (!isBlank(model)) put(K_MODEL, model.trim());
        if (!isBlank(apiKey)) put(K_API_KEY, apiKey.trim());
    }

    // ---------------- 存储层 ----------------

    private Map<String, Row> readAll() {
        try {
            Map<String, Row> out = new LinkedHashMap<>();
            jdbc.queryForList("select config_key, config_value, updated_at from app_config")
                    .forEach(r -> out.put(
                            str(r.get("config_key")),
                            new Row(str(r.get("config_value")), str(r.get("updated_at")))));
            return out;
        } catch (DataAccessException ex) {
            // 旧库还没跑 schema-v1.4，或数据库暂时不可达。
            // 退回环境变量即可，不影响已有部署 —— 这不是「吞异常」，是设计好的兜底路径。
            log.warn("读取 app_config 失败，AI 配置回退到环境变量：{}", ex.getMessage());
            return Map.of();
        }
    }

    private void put(String key, String value) {
        jdbc.update("""
                insert into app_config(config_key, config_value, updated_at)
                values (?, ?, now())
                on conflict (config_key) do update
                    set config_value = excluded.config_value,
                        updated_at   = now()
                """, key, value);
    }

    private void delete(String key) {
        jdbc.update("delete from app_config where config_key = ?", key);
    }

    private String latestUpdatedAt(Map<String, Row> db) {
        String latest = null;
        for (String k : new String[]{K_API_KEY, K_BASE_URL, K_MODEL}) {
            Row row = db.get(k);
            if (row != null && row.updatedAt() != null && (latest == null || row.updatedAt().compareTo(latest) > 0)) {
                latest = row.updatedAt();
            }
        }
        return latest;
    }

    // ---------------- 小工具 ----------------

    /** sk-1234567890abcdef → sk-123****cdef。太短就整体打码，宁可看不出也不泄露。 */
    static String mask(String key) {
        if (isBlank(key)) return "";
        String k = key.trim();
        if (k.length() <= 10) return "****";
        return k.substring(0, 6) + "****" + k.substring(k.length() - 4);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String value(Row row) {
        return row == null ? null : row.value();
    }

    private static String pick(String first, String second, String fallback) {
        if (!isBlank(first)) return first.trim();
        if (!isBlank(second)) return second.trim();
        return fallback;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
