package com.myos.learning;

import java.time.LocalDateTime;

/** 学习记录 —— 五段式：原始资料 → 理解 → 实践 → 结论 */
public record LearningRecord(
        Long id,
        String title,
        String category,
        String systemCode,
        String source,
        String content,
        String understanding,
        String practice,
        String conclusion,
        Integer durationMinutes,
        LocalDateTime learnedAt
) {}
