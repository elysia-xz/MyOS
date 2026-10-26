package com.myos.export;

import com.myos.common.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导出 —— 把当前用户的全部数据导成一份 JSON。
 *
 * 为什么要有它：这是个只属于自己的自建系统，最怕的不是功能少，是数据没了。
 * 数据库备份（deploy/backup.sh 的 pg_dump）能整库恢复，但那是给机器看的；
 * 这里导出的是**给人和给未来程序看**的：换系统、迁移、或者只是想确认
 * 自己这几年到底积累了什么，都能直接读。
 *
 * 边界说明（故意为之）：
 *  · 不导出 my_user.password_hash —— 口令哈希离开数据库只会扩大泄露面，
 *    且它在别处也无法复用（换一个库 salt 就不同了）
 *  · 不导出 user_token —— 那是会话凭据，导出去等于把登录权一起送走
 *  · ai_message 通过 ai_conversation 反查归属，它自己没有 user_id
 */
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final JdbcTemplate jdbc;

    public ExportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 导出清单：key = 导出后的字段名，value = 取数 SQL。
     *
     * 用 select * 而不是逐列列举：导出的目的是「原样留底」，
     * 将来表结构加了列，导出不该因为忘改这里而漏字段。
     * 代价是文件里会带上 user_id（无害，反正只有本人的数据）。
     */
    private static final List<Table> TABLES = List.of(
            new Table("goals", "select * from goal where user_id = ? order by id"),
            new Table("projects", "select * from project where user_id = ? order by id"),
            new Table("projectLogs", "select * from project_log where user_id = ? order by id"),
            new Table("tasks", "select * from task where user_id = ? order by id"),
            new Table("learningRecords", "select * from learning_record where user_id = ? order by id"),
            new Table("contentRecords", "select * from content_record where user_id = ? order by id"),
            new Table("reviews", "select * from review where user_id = ? order by id"),
            new Table("habitLogs", "select * from habit_log where user_id = ? order by id"),
            new Table("financeRecords", "select * from finance_record where user_id = ? order by id"),
            new Table("assetSnapshots", "select * from asset_snapshot where user_id = ? order by id"),
            new Table("dailyLogs", "select * from daily_log where user_id = ? order by id"),
            new Table("knowledgeItems", "select * from knowledge_item where user_id = ? order by id"),
            new Table("aiConversations", "select * from ai_conversation where user_id = ? order by id"),
            // ai_message 没有 user_id，只能顺着会话反查
            new Table("aiMessages", """
                    select m.* from ai_message m
                    join ai_conversation c on c.id = m.conversation_id
                    where c.user_id = ? order by m.id""")
    );

    @GetMapping
    public ResponseEntity<Map<String, Object>> export() {
        Long uid = CurrentUser.id();

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        // 取不到的表明确列出来，不假装成功 ——
        // 导出最怕的就是「看起来成功、实际少了一半」
        List<String> failed = new ArrayList<>();

        for (Table t : TABLES) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(t.sql(), uid);
                List<Map<String, Object>> clean = new ArrayList<>(rows.size());
                for (Map<String, Object> row : rows) {
                    clean.add(sanitize(row));
                }
                data.put(t.key(), clean);
                counts.put(t.key(), clean.size());
            } catch (Exception e) {
                // 旧库可能还没有某张表（例如没跑过 schema-v1.6），
                // 不让整次导出失败，但要留下痕迹
                log.warn("导出跳过表 {}：{}", t.key(), e.getMessage());
                data.put(t.key(), List.of());
                counts.put(t.key(), 0);
                failed.add(t.key());
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("app", "MyOS");
        meta.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        meta.put("userId", uid);
        meta.put("counts", counts);
        if (!failed.isEmpty()) {
            meta.put("failedTables", failed);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("meta", meta);
        out.put("data", data);

        String filename = "myos-export-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(out);
    }

    /**
     * 把 JDBC 的类型换成 JSON 友好的类型。
     * java.sql.Timestamp / Date 直接交给 Jackson 会依赖具体配置（有的序列化成
     * 时间戳数字），导出的文件应该是一眼能读的 ISO 文本，所以这里统一转 java.time。
     */
    private static Map<String, Object> sanitize(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row.size());
        row.forEach((k, v) -> out.put(k, normalize(v)));
        return out;
    }

    private static Object normalize(Object v) {
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof java.sql.Time t) {
            return t.toLocalTime();
        }
        if (v instanceof OffsetDateTime odt) {
            return odt;
        }
        if (v instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        return v;
    }

    private record Table(String key, String sql) {}
}
