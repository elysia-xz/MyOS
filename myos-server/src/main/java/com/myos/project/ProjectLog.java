package com.myos.project;

import java.time.LocalDate;

/** 项目开发日志 —— 对应 project_log 表 */
public record ProjectLog(
        Long id,
        Long projectId,
        LocalDate logDate,
        String content,
        String commitRef
) {
    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public ProjectLog withId(Long id) {
        return new ProjectLog(id, projectId, logDate, content, commitRef);
    }
}
