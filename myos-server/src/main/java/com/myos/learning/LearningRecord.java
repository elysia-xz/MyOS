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
) {
    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public LearningRecord withId(Long id) {
        return new LearningRecord(id, title, category, systemCode, source, content,
                understanding, practice, conclusion, durationMinutes, learnedAt);
    }
}
