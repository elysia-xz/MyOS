package com.myos.task;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import com.myos.project.ProjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskMapper mapper;
    private final ProjectMapper projectMapper;

    public TaskController(TaskMapper mapper, ProjectMapper projectMapper) {
        this.mapper = mapper;
        this.projectMapper = projectMapper;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) LocalDate date) {
        LocalDate queryDate = date == null ? LocalDate.now() : date;
        return ApiResponse.ok(mapper.findByUserId(CurrentUser.id(), queryDate));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateTaskRequest request) {
        Long uid = CurrentUser.id();
        // 归属必须校验：否则要么把任务挂到别人的项目上，要么撞外键直接 500
        String bad = checkProject(uid, request.projectId());
        if (bad != null) return ApiResponse.fail(bad);

        Task task = new Task(null, request.goalId(), request.projectId(), request.title(), request.description(),
                request.priority() == null ? "MEDIUM" : request.priority(), "TODO", request.dueDate(), null, null, null);
        long id = mapper.insert(uid, task);
        return ApiResponse.ok(task.withId(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<?> updateStatus(@PathVariable Long id, @RequestParam String status) {
        int affected = mapper.updateStatus(CurrentUser.id(), id, status);
        return affected == 1 ? ApiResponse.ok(true) : ApiResponse.fail("任务不存在");
    }

    /**
     * 改归属项目 —— 「一次行动产生多个结果」最短的那一步：
     * 任务挂上项目后，项目进度就会自己往前走，不用再手动拖进度条。
     *
     * @param projectId 传 null / 省略 表示脱离项目（不是「不改」）
     */
    @PutMapping("/{id}/project")
    public ApiResponse<?> updateProject(@PathVariable Long id,
                                        @RequestParam(required = false) Long projectId) {
        Long uid = CurrentUser.id();
        String bad = checkProject(uid, projectId);
        if (bad != null) return ApiResponse.fail(bad);

        int affected = mapper.updateProject(uid, id, projectId);
        return affected == 1 ? ApiResponse.ok(true) : ApiResponse.fail("任务不存在");
    }

    /** 删除任务。原先没有这个接口 —— 建错了的任务在界面上根本删不掉。 */
    @DeleteMapping("/{id}")
    public ApiResponse<?> remove(@PathVariable Long id) {
        int affected = mapper.deleteById(CurrentUser.id(), id);
        return affected == 1 ? ApiResponse.ok(true) : ApiResponse.fail("任务不存在");
    }

    /** 归属校验统一出口：返回 null 表示通过，否则返回可读的失败原因 */
    private String checkProject(Long uid, Long projectId) {
        if (projectId == null) return null;
        return projectMapper.existsForUser(uid, projectId) ? null : "项目不存在或不属于你";
    }

    public record CreateTaskRequest(Long goalId, Long projectId, @NotBlank String title, String description,
                                    String priority, LocalDate dueDate) {}
}
