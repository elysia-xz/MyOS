package com.myos.goal;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 目标层级 —— 年 → 月 → 周（对应 PRD §2 主线）。
 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalMapper mapper;

    public GoalController(GoalMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(mapper.findByUserId(CurrentUser.id()));
    }

    /** 树形结构：年 → 月 → 周，供前端直接渲染 */
    @GetMapping("/tree")
    public ApiResponse<?> tree() {
        List<Goal> all = mapper.findByUserId(CurrentUser.id());
        Map<Long, List<Goal>> byParent = new LinkedHashMap<>();
        List<Goal> roots = new ArrayList<>();
        for (Goal g : all) {
            if (g.parentId() == null) roots.add(g);
            else byParent.computeIfAbsent(g.parentId(), k -> new ArrayList<>()).add(g);
        }
        List<Map<String, Object>> tree = roots.stream().map(r -> toNode(r, byParent)).toList();
        return ApiResponse.ok(tree);
    }

    private Map<String, Object> toNode(Goal g, Map<Long, List<Goal>> byParent) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", g.id());
        node.put("title", g.title());
        node.put("level", g.level());
        node.put("systemCode", g.systemCode());
        node.put("status", g.status());
        node.put("progress", g.progress());
        node.put("startDate", g.startDate());
        node.put("endDate", g.endDate());
        List<Goal> children = byParent.getOrDefault(g.id(), List.of());
        node.put("children", children.stream().map(c -> toNode(c, byParent)).toList());
        return node;
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest req) {
        Goal g = new Goal(null, req.parentId(), req.title(), req.description(), req.category(),
                req.level(), req.systemCode(), "ACTIVE", 0, req.targetValue(),
                req.startDate(), req.endDate());
        mapper.insert(CurrentUser.id(), g);
        return ApiResponse.ok(g);
    }

    @PutMapping("/{id}/progress")
    public ApiResponse<?> progress(@PathVariable Long id,
                                   @RequestParam int value) {
        mapper.updateProgress(CurrentUser.id(), id, value);
        return ApiResponse.ok(true);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<?> status(@PathVariable Long id,
                                 @RequestParam String status) {
        mapper.updateStatus(CurrentUser.id(), id, status);
        return ApiResponse.ok(true);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        mapper.delete(CurrentUser.id(), id);
        return ApiResponse.ok(true);
    }


    public record CreateRequest(Long parentId, @NotBlank String title, String description,
                                String category, String level, String systemCode,
                                Integer targetValue, LocalDate startDate, LocalDate endDate) {}
}
