package com.myos.project;

import java.time.LocalDate;

/**
 * 项目 —— 含 Portfolio 作品集字段（对应 PRD §6.4）。
 *
 * <p>关于 {@code progress}：
 * 它不再是一个独立的手填数字，而是**读取时按 progressMode 推导出来的结果**。
 * <ul>
 *   <li>{@code AUTO}（默认）：由该项目下任务的完成度算出（见 ProjectMapper.xml 的 derived）；</li>
 *   <li>{@code MANUAL}：用库里存的值。</li>
 * </ul>
 * 这样一来，「做完任务 → 项目进度前进」是系统行为，不靠人记得去拖进度条。
 *
 * <p>{@code taskTotal} / {@code taskDone} 是**只读的派生字段**，只在查询时填充；
 * 插入和更新时传 null 即可（insert 语句里根本不读它们）。
 */
public record Project(
        Long id,
        String name,
        String description,
        String status,
        Integer progress,
        String progressMode,
        Long goalId,
        Integer taskTotal,
        Integer taskDone,
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
        return new Project(id, name, description, status, progress, progressMode, goalId,
                taskTotal, taskDone, startDate, targetDate, githubUrl, demoUrl, problemSolved,
                techStack, coverImage, difficulty, solution, finishedAt);
    }
}
