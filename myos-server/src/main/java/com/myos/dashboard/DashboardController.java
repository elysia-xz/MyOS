package com.myos.dashboard;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import com.myos.common.LevelRule;
import com.myos.common.SystemCatalog;
import com.myos.habit.HabitLog;
import com.myos.habit.HabitMapper;
import com.myos.task.TaskSql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页成长 Dashboard —— 对应 PRD §4。
 *
 * 一次性返回首页全部区块所需数据，避免前端多次请求：
 *   ① 今日水位（7 系统）
 *   ② 能力等级（长期）
 *   ③ 本月项目
 *   ④ 本月输出目标
 *   ⑤ 最近沉淀
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final JdbcTemplate jdbc;
    private final HabitMapper habitMapper;
    private final LevelRule levelRule;

    public DashboardController(JdbcTemplate jdbc, HabitMapper habitMapper, LevelRule levelRule) {
        this.jdbc = jdbc;
        this.habitMapper = habitMapper;
        this.levelRule = levelRule;
    }

    @GetMapping
    public ApiResponse<?> dashboard() {
        Long uid = CurrentUser.id();
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.now();
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        int daysInMonth = month.lengthOfMonth();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", today);
        out.put("month", month.toString());
        out.put("waterBars", waterBars(uid, today));
        out.put("levels", levels(uid, today));
        out.put("currentProjects", currentProjects(uid));
        out.put("monthlyTargets", monthlyTargets(uid, monthStart, monthEnd, daysInMonth));
        out.put("recentKnowledge", recentKnowledge(uid, 5));
        out.put("aiEnabled", true);
        out.put("todayTasks", todayTasks(uid, today));
        return ApiResponse.ok(out);
    }

    // ---------------- ① 今日水位（7 系统） ----------------

    private List<Map<String, Object>> waterBars(Long uid, LocalDate today) {
        Map<String, HabitLog> existing = habitMapper.findByDate(uid, today).stream()
                .collect(Collectors.toMap(HabitLog::systemCode, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SystemCatalog.Def def : SystemCatalog.ALL) {
            HabitLog log = existing.get(def.code());
            int value = log == null ? 0 : log.metricValue();
            int target = log == null || log.targetValue() == null ? def.dailyTarget() : log.targetValue();
            boolean done = log != null && Boolean.TRUE.equals(log.done());
            int percent = target > 0 ? Math.min(100, (int) Math.round(value * 100.0 / target)) : (done ? 100 : 0);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("systemCode", def.code());
            m.put("systemName", def.name());
            m.put("unit", def.unit());
            m.put("value", value);
            m.put("target", target);
            m.put("done", done);
            m.put("percent", percent);
            m.put("recorded", log != null);
            rows.add(m);
        }
        return rows;
    }

    // ---------------- ② 能力等级 ----------------

    private List<Map<String, Object>> levels(Long uid, LocalDate today) {
        LocalDate from = today.minusYears(5);
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (SystemCatalog.Def def : SystemCatalog.ALL) {
            long amount = switch (def.code()) {
                case "CONTENT" -> countContents(uid);
                case "BODY", "FINANCE", "SOCIAL" ->
                        habitMapper.countDoneBySystem(uid, def.code(), from, today);
                case "ENGLISH" -> habitMapper.sumBySystem(uid, "ENGLISH", from, today)
                        + learningMinutes(uid, "ENGLISH");
                default -> habitMapper.sumBySystem(uid, def.code(), from, today)
                        + learningMinutes(uid, def.code());
            };
            amounts.put(def.code(), amount);
        }
        long finishedProjects = jdbc.queryForObject(
                "select count(*) from project where user_id=? and status='DONE'",
                Long.class, uid);
        return levelRule.levels(amounts, finishedProjects);
    }

    private long learningMinutes(Long uid, String systemCode) {
        Long v = jdbc.queryForObject("""
                select coalesce(sum(duration_minutes),0) from learning_record
                where user_id = ? and (system_code = ? or category = ?)
                """, Long.class, uid, systemCode, systemCode);
        return v == null ? 0 : v;
    }

    private long countContents(Long uid) {
        Long v = jdbc.queryForObject(
                "select count(*) from content_record where user_id=? and status='PUBLISHED'",
                Long.class, uid);
        return v == null ? 0 : v;
    }

    // ---------------- ③ 本月项目 ----------------

    private List<Map<String, Object>> currentProjects(Long uid) {
        return jdbc.queryForList("""
                select id, name, description, status, progress, tech_stack
                from project
                where user_id = ? and status = 'IN_PROGRESS'
                order by progress desc, id desc
                limit 5
                """, uid);
    }

    // ---------------- ④ 本月输出目标 ----------------

    private List<Map<String, Object>> monthlyTargets(Long uid, LocalDate from, LocalDate to, int daysInMonth) {
        Map<String, Map<String, Object>> stats = habitMapper.monthlyStats(uid, from, to).stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.get("systemCode")), Function.identity()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SystemCatalog.Def def : SystemCatalog.ALL) {
            Map<String, Object> s = stats.get(def.code());
            long doneDays = s == null ? 0 : asLong(s.get("doneDays"));
            int target = "CONTENT".equals(def.code())
                    ? daysInMonth
                    : Math.max(1, (int) Math.round(daysInMonth * 0.8));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("systemCode", def.code());
            m.put("systemName", def.name());
            m.put("doneDays", doneDays);
            m.put("targetDays", target);
            m.put("percent", target > 0 ? Math.min(100, (int) Math.round(doneDays * 100.0 / target)) : 0);
            rows.add(m);
        }
        return rows;
    }

    private static long asLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    // ---------------- ⑤ 最近沉淀 ----------------

    private List<Map<String, Object>> recentKnowledge(Long uid, int limit) {
        return jdbc.queryForList("""
                select title, category, conclusion, learned_at
                from learning_record
                where user_id = ?
                order by learned_at desc
                limit ?
                """, uid, limit);
    }

    // ---------------- 今日任务（首页一并返回） ----------------

    /**
     * 口径取自 {@link TaskSql#SCOPE_TODAY}（今天到期 + 逾期未完成）。
     * 不要在这里另写一份条件 —— 三处各抄一遍正是上次静默失配的原因。
     */
    private Map<String, Object> todayTasks(Long uid, LocalDate today) {
        String base = "select count(*) from task where user_id=? and " + TaskSql.SCOPE_TODAY;
        Integer total = jdbc.queryForObject(base, Integer.class, uid, today, today);
        Integer done = jdbc.queryForObject(
                base + " and status='DONE'", Integer.class, uid, today, today);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total == null ? 0 : total);
        m.put("done", done == null ? 0 : done);
        return m;
    }
}
