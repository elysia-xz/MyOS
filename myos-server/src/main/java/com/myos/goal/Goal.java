package com.myos.goal;

import java.time.LocalDate;

/** 目标 —— 支持 年 / 月 / 周 层级（对应 PRD §2 的「年→月→周→今日」主线） */
public record Goal(
        Long id,
        Long parentId,
        String title,
        String description,
        String category,
        String level,
        String systemCode,
        String status,
        Integer progress,
        Integer targetValue,
        LocalDate startDate,
        LocalDate endDate
) {
    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public Goal withId(Long id) {
        return new Goal(id, parentId, title, description, category, level, systemCode,
                status, progress, targetValue, startDate, endDate);
    }
}
