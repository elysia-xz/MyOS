package com.myos.habit;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import com.myos.common.SystemCatalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 今日习惯打点 —— 7 系统水位（对应 PRD §6.1）
 */
@RestController
@RequestMapping("/api/v1/habit")
public class HabitController {

    private final HabitMapper mapper;

    public HabitController(HabitMapper mapper) {
        this.mapper = mapper;
    }

    /** 7 系统定义（前端渲染表头用） */
    @GetMapping("/systems")
    public ApiResponse<?> systems() {
        return ApiResponse.ok(SystemCatalog.ALL);
    }

    /**
     * 某天的打点情况。始终返回全部 7 个系统（未打点的补空行），
     * 便于前端一屏渲染。
     */
    @GetMapping
    public ApiResponse<?> byDate(@RequestParam(required = false) LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        Map<String, HabitLog> existing = mapper.findByDate(CurrentUser.id(), d).stream()
                .collect(Collectors.toMap(HabitLog::systemCode, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SystemCatalog.Def def : SystemCatalog.ALL) {
            HabitLog log = existing.get(def.code());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("systemCode", def.code());
            row.put("systemName", def.name());
            row.put("unit", def.unit());
            row.put("logDate", d);
            row.put("metricValue", log == null ? 0 : log.metricValue());
            row.put("targetValue", log == null ? def.dailyTarget() : log.targetValue());
            row.put("done", log != null && Boolean.TRUE.equals(log.done()));
            row.put("note", log == null ? null : log.note());
            row.put("recorded", log != null);
            rows.add(row);
        }
        return ApiResponse.ok(rows);
    }

    /** 批量保存今日打点 */
    @PutMapping
    public ApiResponse<?> save(@Valid @RequestBody SaveHabitRequest request) {
        LocalDate d = request.logDate() == null ? LocalDate.now() : request.logDate();
        for (Entry e : request.items()) {
            HabitLog log = new HabitLog(null, d, e.systemCode(),
                    e.metricValue(), e.targetValue(), e.done(), e.note());
            mapper.upsert(CurrentUser.id(), log);
        }
        return ApiResponse.ok(true);
    }

    /** 区间打点记录（用于打卡日历） */
    @GetMapping("/range")
    public ApiResponse<?> range(@RequestParam LocalDate from,
                                @RequestParam LocalDate to) {
        return ApiResponse.ok(mapper.findBetween(CurrentUser.id(), from, to));
    }


    public record Entry(@NotNull String systemCode, Integer metricValue, Integer targetValue,
                        Boolean done, String note) {}

    public record SaveHabitRequest(LocalDate logDate, @NotNull List<Entry> items) {}
}
