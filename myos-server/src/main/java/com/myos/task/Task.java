package com.myos.task;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Task(Long id, Long goalId, Long projectId, String title, String description,
                   String priority, String status, LocalDate dueDate,
                   LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
