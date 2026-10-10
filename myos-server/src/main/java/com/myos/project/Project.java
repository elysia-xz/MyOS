package com.myos.project;

import java.time.LocalDate;

/**
 * 项目 —— 含 Portfolio 作品集字段（对应 PRD §6.4）。
 */
public record Project(
        Long id,
        String name,
        String description,
        String status,
        Integer progress,
        LocalDate startDate,
        LocalDate targetDate,
        String githubUrl,
        String demoUrl,
        String problemSolved,
        String techStack,
        String coverImage,
        String difficulty,
        String solution,
        LocalDate finishedAt
) {
    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public Project withId(Long id) {
        return new Project(id, name, description, status, progress, startDate, targetDate,
                githubUrl, demoUrl, problemSolved, techStack, coverImage,
                difficulty, solution, finishedAt);
    }
}
