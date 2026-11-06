package com.myos.project;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目 —— 含 Portfolio 作品集与开发日志（对应 PRD §6.4）。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectMapper mapper;
    private final com.myos.content.ContentMapper contentMapper;
    private final com.myos.task.TaskMapper taskMapper;
    private final com.myos.goal.GoalMapper goalMapper;

    public ProjectController(ProjectMapper mapper,
                             com.myos.content.ContentMapper contentMapper,
                             com.myos.task.TaskMapper taskMapper,
                             com.myos.goal.GoalMapper goalMapper) {
        this.mapper = mapper;
        this.contentMapper = contentMapper;
        this.taskMapper = taskMapper;
        this.goalMapper = goalMapper;
    }

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(mapper.findByUserId(CurrentUser.id()));
    }

    /**
     * Portfolio 作品集：按年月归档的已完成项目（对应 PRD §6.4）。
     * 返回结构：[{ year, items: [{ month, project }] }]
     */
    @GetMapping("/portfolio")
    public ApiResponse<?> portfolio() {
        List<Project> all = mapper.findByUserId(CurrentUser.id());
        Map<Integer, Map<Integer, List<Project>>> grouped = new java.util.TreeMap<>();
        for (Project p : all) {
            if (!"DONE".equals(p.status())) continue;
            LocalDate anchor = p.finishedAt() != null ? p.finishedAt()
                    : (p.targetDate() != null ? p.targetDate() : p.startDate());
            if (anchor == null) continue;
            grouped.computeIfAbsent(anchor.getYear(), y -> new java.util.TreeMap<>())
                    .computeIfAbsent(anchor.getMonthValue(), m -> new java.util.ArrayList<>())
                    .add(p);
        }
        List<Map<String, Object>> years = grouped.entrySet().stream().map(ye -> {
            Map<String, Object> y = new LinkedHashMap<>();
            y.put("year", ye.getKey());
            y.put("items", ye.getValue().entrySet().stream().map(me -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("month", me.getKey());
                m.put("projects", me.getValue());
                return m;
            }).toList());
            return y;
        }).toList();
        return ApiResponse.ok(years);
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable Long id) {
        Long uid = CurrentUser.id();
        Project p = mapper.findById(uid, id);
        if (p == null) return ApiResponse.fail("项目不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("project", p);
        out.put("logs", mapper.findLogs(uid, id));
        // 这个项目产出了什么内容 —— 「一次行动产生多个结果」链路的收口处，
        // 复盘和作品集都从这里取数
        out.put("contents", contentMapper.findByProject(uid, id));
        // 这个项目由哪些任务推动 —— 项目进度的分子分母就是它们
        out.put("tasks", taskMapper.findByProject(uid, id));
        return ApiResponse.ok(out);
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody SaveRequest req) {
        Long uid = CurrentUser.id();
        if (req.goalId() != null && !goalMapper.existsForUser(uid, req.goalId())) {
            return ApiResponse.fail("目标不存在或不属于你");
        }
        Project p = toProject(null, req);
        long id = mapper.insert(uid, p);
        return ApiResponse.ok(p.withId(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id,
                                 @Valid @RequestBody SaveRequest req) {
        Long uid = CurrentUser.id();
        if (req.goalId() != null && !goalMapper.existsForUser(uid, req.goalId())) {
            return ApiResponse.fail("目标不存在或不属于你");
        }
        Project p = toProject(id, req);
        int n = mapper.update(uid, p);
        return n == 1 ? ApiResponse.ok(p) : ApiResponse.fail("项目不存在");
    }

    /**
     * 手动推进进度 —— 会同时把进度来源切成 MANUAL。
     * 想交还给「按任务自动算」，走 PUT /{id} 把 progressMode 传回 AUTO。
     */
    @PutMapping("/{id}/progress")
    public ApiResponse<?> progress(@PathVariable Long id,
                                   @RequestParam int value) {
        mapper.updateProgress(CurrentUser.id(), id, value);
        return ApiResponse.ok(true);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        mapper.delete(CurrentUser.id(), id);
        return ApiResponse.ok(true);
    }

    // ---- 开发日志 ----

    @GetMapping("/{id}/logs")
    public ApiResponse<?> logs(@PathVariable Long id) {
        return ApiResponse.ok(mapper.findLogs(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/logs")
    public ApiResponse<?> addLog(@PathVariable Long id,
                                 @Valid @RequestBody LogRequest req) {
        ProjectLog log = new ProjectLog(null, id,
                req.logDate() == null ? LocalDate.now() : req.logDate(),
                req.content(), req.commitRef());
        long logId = mapper.insertLog(CurrentUser.id(), log);
        return ApiResponse.ok(log.withId(logId));
    }

    private Project toProject(Long id, SaveRequest r) {
        // taskTotal / taskDone 是查询时算出来的派生字段，写入路径一律不传
        return new Project(id, r.name(), r.description(),
                r.status() == null ? "PLANNED" : r.status(),
                r.progress() == null ? 0 : r.progress(),
                r.progressMode() == null ? "AUTO" : r.progressMode(),
                r.goalId(), null, null,
                r.startDate(), r.targetDate(), r.githubUrl(), r.demoUrl(),
                r.problemSolved(), r.techStack(), r.coverImage(),
                r.difficulty(), r.solution(), r.finishedAt());
    }


    /**
     * @param progressMode AUTO / MANUAL，缺省 AUTO。前端切到手动时才落 progress。
     * @param goalId       归属的年/月目标，可空 —— 不是每个项目都值得挂目标。
     */
    public record SaveRequest(@NotBlank String name, String description, String status, Integer progress,
                              String progressMode, Long goalId,
                              LocalDate startDate, LocalDate targetDate, String githubUrl, String demoUrl,
                              String problemSolved, String techStack, String coverImage,
                              String difficulty, String solution, LocalDate finishedAt) {}

    public record LogRequest(LocalDate logDate, @NotBlank String content, String commitRef) {}
}
