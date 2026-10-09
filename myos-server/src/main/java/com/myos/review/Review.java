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
) {}
