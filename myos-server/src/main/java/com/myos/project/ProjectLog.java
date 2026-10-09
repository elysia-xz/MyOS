package com.myos.project;

import java.time.LocalDate;

/** 项目开发日志 —— 对应 project_log 表 */
public record ProjectLog(
        Long id,
        Long projectId,
        LocalDate logDate,
        String content,
        String commitRef
) {}
