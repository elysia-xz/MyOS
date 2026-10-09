package com.myos.task;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskMapper mapper;

    public TaskController(TaskMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) LocalDate date) {
        LocalDate queryDate = date == null ? LocalDate.now() : date;
        return ApiResponse.ok(mapper.findByUserId(CurrentUser.id(), queryDate));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateTaskRequest request) {
        Task task = new Task(null, request.goalId(), request.projectId(), request.title(), request.description(),
                request.priority() == null ? "MEDIUM" : request.priority(), "TODO", request.dueDate(), null, null, null);
        mapper.insert(CurrentUser.id(), task);
        return ApiResponse.ok(task);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<?> updateStatus(@PathVariable Long id, @RequestParam String status) {
        int affected = mapper.updateStatus(CurrentUser.id(), id, status);
        return affected == 1 ? ApiResponse.ok(true) : ApiResponse.fail("任务不存在");
    }


    public record CreateTaskRequest(Long goalId, Long projectId, @NotBlank String title, String description,
                                    String priority, LocalDate dueDate) {}
}
