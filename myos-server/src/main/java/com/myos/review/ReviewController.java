package com.myos.review;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复盘 —— 对应 PRD §6.6。支持 日 / 周 / 月 / 年。
 */
@RestController
@RequestMapping("/api/v1/review")
public class ReviewController {

    private static final List<String> TYPES = List.of("DAY", "WEEK", "MONTH", "YEAR");

    private final ReviewMapper mapper;

    public ReviewController(ReviewMapper mapper) {
        this.mapper = mapper;
    }

    /** 某种类型的最近记录 */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue = "DAY") String type,
                               @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(mapper.findByType(CurrentUser.id(), type.toUpperCase(), limit));
    }

    /** 当前周期的复盘（含周期区间提示），用于页面初始化 */
    @GetMapping("/current")
    public ApiResponse<?> current(@RequestParam(defaultValue = "DAY") String type) {
        String t = type.toUpperCase();
        LocalDate anchor = anchorDate(t);
        Review existing = mapper.findByDate(CurrentUser.id(), t, anchor);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reviewType", t);
        out.put("reviewDate", anchor);
        out.put("periodStart", periodStart(t, anchor));
        out.put("periodEnd", periodEnd(t, anchor));
        out.put("existing", existing);
        return ApiResponse.ok(out);
    }

    /** 保存复盘 */
    @PutMapping
    public ApiResponse<?> save(@Valid @RequestBody SaveRequest req) {
        String t = req.reviewType().toUpperCase();
        LocalDate anchor = req.reviewDate() == null ? anchorDate(t) : req.reviewDate();
        Review r = new Review(null, t, anchor,
                req.completed() == null ? Boolean.TRUE : req.completed(),
                req.goodThings(), req.badThings(), req.wastedTime(), req.unfinished(),
                req.biggestGain(), req.nextFocus(), req.aiSummary(), req.metricsJson(),
                periodStart(t, anchor), periodEnd(t, anchor));
        long id = mapper.upsert(CurrentUser.id(), r);
        return ApiResponse.ok(r.withId(id));
    }

    /**
     * 删除某个周期的复盘。
     * 原先没有这个接口，于是「写错了想删掉」只能去改数据库，
     * 冒烟测试也只能把测试内容留在用户的复盘历史里。
     */
    @DeleteMapping
    public ApiResponse<?> remove(@RequestParam(defaultValue = "DAY") String type,
                                 @RequestParam(required = false) LocalDate date) {
        String t = type.toUpperCase();
        LocalDate anchor = date == null ? anchorDate(t) : date;
        int affected = mapper.deleteByDate(CurrentUser.id(), t, anchor);
        return affected >= 1 ? ApiResponse.ok(true) : ApiResponse.fail("该周期还没有复盘");
    }

    /** 各周期的锚点日期（统一用周期起始日，保证唯一性） */
    private LocalDate anchorDate(String type) {
        LocalDate today = LocalDate.now();
        return periodStart(type, today);
    }

    private LocalDate periodStart(String type, LocalDate d) {
        return switch (type) {
            case "WEEK"  -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "MONTH" -> d.withDayOfMonth(1);
            case "YEAR"  -> d.withDayOfYear(1);
            default      -> d;
        };
    }

    private LocalDate periodEnd(String type, LocalDate d) {
        return switch (type) {
            case "WEEK"  -> periodStart(type, d).plusDays(6);
            case "MONTH" -> YearMonth.from(d).atEndOfMonth();
            case "YEAR"  -> d.with(TemporalAdjusters.lastDayOfYear());
            default      -> d;
        };
    }


    public record SaveRequest(@NotBlank String reviewType, LocalDate reviewDate, Boolean completed,
                              String goodThings, String badThings, String wastedTime, String unfinished,
                              String biggestGain, String nextFocus, String aiSummary, String metricsJson) {}
}
