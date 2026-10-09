package com.myos.habit;

import java.time.LocalDate;

/** 每日习惯打点 —— 对应 habit_log 表 */
public record HabitLog(
        Long id,
        LocalDate logDate,
        String systemCode,
        Integer metricValue,
        Integer targetValue,
        Boolean done,
        String note
) {}
