package com.myos.content;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容输出 —— 对应 PRD §6.3。
 * 不追求爆款，只追求留下痕迹。
 */
@RestController
@RequestMapping("/api/v1/contents")
public class ContentController {

    private final ContentMapper mapper;

    public ContentController(ContentMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(mapper.findByUserId(CurrentUser.id()));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest req) {
        Long uid = CurrentUser.id();
        String bad = checkProject(uid, req.projectId());
        if (bad != null) return ApiResponse.fail(bad);

        ContentRecord record = new ContentRecord(null, req.title(), req.platform(), req.content(),
                trimToNull(req.quote()),
                req.status() == null ? "DRAFT" : req.status(), null, 0, 0,
                req.projectId(), null);
        long id = mapper.insert(uid, record);
        return ApiResponse.ok(record.withId(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id,
                                 @Valid @RequestBody UpdateRequest req) {
        Long uid = CurrentUser.id();
        String bad = checkProject(uid, req.projectId());
        if (bad != null) return ApiResponse.fail(bad);

        int n = mapper.update(uid, id, req.title(), req.platform(), req.content(),
                trimToNull(req.quote()), req.projectId());
        return n == 1 ? ApiResponse.ok(true) : ApiResponse.fail("内容不存在");
    }

    /** 状态流转 DRAFT -> PUBLISHED，自动回填 published_at */
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

    /**
     * 输出打卡日历（对应 PRD §6.3）：返回本月每天是否发布了内容。
     */
    @GetMapping("/calendar")
    public ApiResponse<?> calendar(@RequestParam(required = false) String month) {
        Long uid = CurrentUser.id();
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);

        Map<Integer, Long> byDay = mapper.countByDay(uid, ym.atDay(1), ym.atEndOfMonth()).stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("day")).intValue(),
                        r -> ((Number) r.get("cnt")).longValue(),
                        (a, b) -> a));

        List<Map<String, Object>> days = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            String date = ym.atDay(d).toString();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", d);
            m.put("date", date);
            m.put("count", byDay.getOrDefault(d, 0L));
            m.put("published", byDay.getOrDefault(d, 0L) > 0);
            days.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", ym.toString());
        out.put("days", days);
        out.put("publishedDays", byDay.size());
        out.put("targetDays", ym.lengthOfMonth());
        return ApiResponse.ok(out);
    }


    /** 空白语录统一存 NULL，避免库里出现一堆空字符串 */
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 项目归属校验：null 表示合法（不挂项目），否则返回错误文案。
     * 直接拒绝比让它撞外键更友好 —— 外键失败用户只会看到「服务器内部错误」。
     */
    private String checkProject(Long uid, Long projectId) {
        if (projectId == null) return null;
        return mapper.countOwnedProject(uid, projectId) > 0 ? null : "项目不存在或无权访问";
    }

    public record CreateRequest(@NotBlank String title, String platform, String content,
                                String quote, String status,
                                // 归属项目，可空
                                Long projectId) {}

    public record UpdateRequest(@NotBlank String title, String platform, String content,
                                String quote,
                                // 传 null 表示取消归属
                                Long projectId) {}
}
