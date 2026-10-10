package com.myos.task;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Task(Long id, Long goalId, Long projectId, String title, String description,
                   String priority, String status, LocalDate dueDate,
                   LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {

    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public Task withId(Long id) {
        return new Task(id, goalId, projectId, title, description, priority, status, dueDate,
                completedAt, createdAt, updatedAt);
    }
}
