package com.myos.common;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力等级（Lv）计算规则 —— 对应 PRD §4.3。
 * V1 用简单累计值分档，阈值集中在此，便于后续调参。
 */
@Component
public class LevelRule {

    private static final Logger log = LoggerFactory.getLogger(LevelRule.class);

    /**
     * 每多少单位 +1 Lv。
     * 注意：这里必须覆盖 SystemCatalog.ALL 的全部 7 个系统编码，
     * 漏掉的系统 level() 会因查不到规则而恒返回 0（首页该卡片永远显示 Lv.00）。
     */
    private static final Map<String, Integer> PER_LEVEL = Map.of(
            "AI",      600,   // 学习分钟
            "TECH",    600,   // 学习分钟（另加完成项目）
            "ENGLISH", 600,   // 学习分钟
            "CONTENT", 10,    // 已发布内容篇数
            "BODY",    20,    // 运动打卡次数
            "FINANCE", 3,     // 记账打卡次数
            "SOCIAL",  10     // 人际打卡次数
    );

    /**
     * 启动自检：确认 7 大系统都有 Lv 规则。
     * 历史上 SOCIAL 曾漏配，导致首页「人际」卡片恒为 Lv.00 且无人察觉，
     * 因此改为启动时主动告警，避免同类问题再次静默发生。
     */
    @PostConstruct
    void validateCoverage() {
        List<String> missing = SystemCatalog.ALL.stream()
                .map(SystemCatalog.Def::code)
                .filter(code -> {
                    Integer per = PER_LEVEL.get(code);
                    return per == null || per <= 0;
                })
                .toList();
        if (!missing.isEmpty()) {
            log.warn("LevelRule 缺少以下系统的 Lv 阈值，这些系统的等级将恒为 0：{}", missing);
        }
    }

    /**
     * @param code   系统编码
     * @param amount 累计值
     * @return 等级（最小为 0）
     */
    public int level(String code, long amount) {
        Integer per = PER_LEVEL.get(code);
        if (per == null || per <= 0) return 0;
        return (int) Math.max(0, amount / per);
    }

    /**
     * 编程系统：学习分钟 + 完成项目数各按 600 分钟 / 1 项目 折算。
     */
    public int techLevel(long minutes, long finishedProjects) {
        return level("TECH", minutes) + (int) finishedProjects;
    }

    /** 生成 7 系统的等级列表（含系统名称，便于前端直接渲染） */
    public List<Map<String, Object>> levels(Map<String, Long> amounts, long finishedProjects) {
        Map<String, Object> out = new LinkedHashMap<>();
        return SystemCatalog.ALL.stream().map(def -> {
            long amount = amounts.getOrDefault(def.code(), 0L);
            int lv = "TECH".equals(def.code())
                    ? techLevel(amount, finishedProjects)
                    : level(def.code(), amount);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("systemCode", def.code());
            m.put("systemName", def.name());
            m.put("level", lv);
            m.put("amount", amount);
            m.put("nextLevelAt", nextLevelAt(def.code(), amount));
            return m;
        }).toList();
    }

    private long nextLevelAt(String code, long amount) {
        Integer per = PER_LEVEL.get(code);
        if (per == null || per <= 0) return 0;
        return (amount / per + 1) * (long) per;
    }
}
