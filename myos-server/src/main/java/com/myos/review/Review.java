package com.myos.review;

import java.time.LocalDate;

/** 复盘 —— 对应 review 表，支持日/周/月/年 */
public record Review(
        Long id,
        String reviewType,
        LocalDate reviewDate,
        Boolean completed,
        String goodThings,
        String badThings,
        String wastedTime,
        String unfinished,
        String biggestGain,
        String nextFocus,
        String aiSummary,
        /** 结构化自评（情绪/专注/完成度/时间浪费来源/任务类型），存 JSON 字符串 */
        String metricsJson,
        LocalDate periodStart,
        LocalDate periodEnd
) {
    /** upsert 后回填自增主键（record 不可变，只能新建一条） */
    public Review withId(Long id) {
        return new Review(id, reviewType, reviewDate, completed, goodThings, badThings,
                wastedTime, unfinished, biggestGain, nextFocus, aiSummary, metricsJson,
                periodStart, periodEnd);
    }
}
