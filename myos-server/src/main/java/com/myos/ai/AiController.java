package com.myos.ai;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 助手 —— 场景化入口（对应 PRD §7.2）。
 *
 * 前端不做独立聊天页，只做场景按钮。所有场景在这里把「真实数据」
 * 拼装成提示词，再交给 AiGateway。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiGateway gateway;
    private final JdbcTemplate jdbc;
    private final AiConfigService config;

    public AiController(AiGateway gateway, JdbcTemplate jdbc, AiConfigService config) {
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.config = config;
    }

    /** 网关状态：前端据此决定是否显示 AI 入口 */
    @GetMapping("/status")
    public ApiResponse<?> status() {
        return ApiResponse.ok(Map.of("available", gateway.available()));
    }

    // ---------------- 网关配置：配置界面在前端，Key 存在后端 ----------------
    //
    // 设计取舍：Key 不放在前端。它不是「设置项」而是「凭据」——前端要记住它只能塞进
    // localStorage，本机任何人都能读出来，浏览器网络面板里也会明文出现，泄露即不可逆
    // （直接挂账单）。所以拆成两半：填写界面在前端，存储在后端，读回永远只有 masked。

    /** 配置状态。**只回脱敏值**，完整的 Key 既不进日志也不出现在响应里。 */
    @GetMapping("/config")
    public ApiResponse<?> configStatus() {
        return ApiResponse.ok(config.status());
    }

    /**
     * 写入配置。三个字段留空均表示「不改动」，
     * 这样单独改模型名不会把已保存的 Key 抹掉；要整体清空请用 reset。
     */
    @PostMapping("/config")
    public ApiResponse<?> saveConfig(@RequestBody ConfigRequest req) {
        String baseUrl = trim(req.baseUrl());
        if (!baseUrl.isEmpty() && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return ApiResponse.fail("Base URL 需要以 http:// 或 https:// 开头");
        }
        // 防呆：粘贴时没覆盖原有内容会拼成两段地址（前端实测踩过，
        // 得到 https://api.deepseek.comhttps://… ）。这种地址必然调不通，
        // 与其让用户对着「网络异常」猜半天，不如在这里直接拦下并说清原因。
        if (countOf(baseUrl.toLowerCase(), "http") > 1) {
            return ApiResponse.fail("Base URL 里出现了两段地址，像是粘贴时没有覆盖原有内容，请重新填写");
        }
        config.save(baseUrl, trim(req.model()), trim(req.apiKey()), Boolean.TRUE.equals(req.reset()));
        return ApiResponse.ok(config.status());
    }

    /**
     * 连通性测试：真发一次最小请求。
     * 光看「已配置」是不够的——Key 填错、模型名写错、Base URL 不对，都要当场暴露出来。
     */
    @PostMapping("/config/test")
    public ApiResponse<?> testConfig() {
        try {
            String reply = gateway.complete("config_test", "只回复两个字：可用");
            String brief = reply == null ? "" : reply.strip();
            if (brief.length() > 60) brief = brief.substring(0, 60) + "…";
            return ApiResponse.ok(Map.of("reachable", true, "reply", brief));
        } catch (AiUnavailableException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 配置请求体。注意 apiKey 只进不出，响应里换成 configStatus() 的 masked。 */
    public record ConfigRequest(String baseUrl, String model, String apiKey, Boolean reset) {}

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static int countOf(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    /**
     * 「和 AI 讨论」。
     *
     * 注意：这里原先直接把数据快照当提示词发出去，**没有任何指令** ——
     * 实测模型会自行发挥（生成了一整段 SQL 脚本），完全不是「讨论」。
     * 所以补上明确的角色与输出要求。
     */
    @PostMapping("/daily/summary")
    public ApiResponse<?> summaryToday() {
        String prompt = """
                你是我的个人成长助理。以下是我今天的真实数据，请像朋友一样跟我聊两句。

                要求：
                1. 先用一句话概括今天的状态，再点出 2 个值得注意的地方（好的和不足的都要说）
                2. 最后问我一个能让我想下去的问题
                3. 直接说话，不要写标题、不要分点编号、不要给 SQL 或代码
                4. 只用数据里出现的事实，不要编造
                5. 200 字以内

                【今日数据】
                """ + snapshotToday(CurrentUser.id());
        return run("daily_summary", prompt);
    }

    @PostMapping("/daily/report")
    public ApiResponse<?> dailyReport() {
        Long uid = CurrentUser.id();
        String data = snapshotToday(uid);
        String prompt = """
                你是我的个人成长助理。以下是我今天的真实数据，请帮我生成一份「今日日报」。

                要求：
                1. 语言简洁、具体，不要空话套话
                2. 结构：今天做了什么 / 关键收获 / 待改进 / 明天重点
                3. 只用数据里出现的事实，不要编造
                4. 200 字以内

                【今日数据】
                """ + data;
        return run("daily_report", prompt);
    }

    @PostMapping("/daily/review")
    public ApiResponse<?> reviewToday() {
        Long uid = CurrentUser.id();
        String prompt = """
                你是我的复盘教练。基于我今天的真实数据，用提问的方式引导我复盘。
                提出 4 个具体问题（不要泛泛而问），每个问题后留空行给我回答。

                【今日数据】
                """ + snapshotToday(uid);
        return run("daily_review", prompt);
    }

    @PostMapping("/monthly/summary")
    public ApiResponse<?> monthlySummary() {
        Long uid = CurrentUser.id();
        String prompt = """
                你是我的个人成长助理。以下是我本月的真实数据，请生成一份「月度复盘初稿」。

                要求：结构为 目标完成情况 / 数据回顾 / 做得好 / 没做到 / 下月调整建议，
                语言具体、诚实，敢于指出问题。300 字以内。

                【本月数据】
                """ + snapshotMonth(uid);
        return run("monthly_summary", prompt);
    }

    @PostMapping("/annual/report")
    public ApiResponse<?> annualReport() {
        Long uid = CurrentUser.id();
        String prompt = """
                你是我的个人成长档案撰写者。以下是我这一年的真实数据，请生成《年度成长报告》。

                结构：
                1. 这一年做了什么
                2. 获得了什么能力
                3. 完成了什么项目
                4. 哪些目标没有完成
                5. 哪些习惯坚持下来了
                6. 哪些事情浪费了时间
                7. 下一年应该调整什么

                要求：诚实、具体，有数据支撑，不要鸡汤。600 字以内。

                【年度数据】
                """ + snapshotYear(uid);
        return run("annual_report", prompt);
    }

    // ---------------- 数据快照拼装 ----------------

    private String snapshotToday(Long uid) {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("日期：").append(today).append('\n');

        Integer doneTasks = jdbc.queryForObject(
                "select count(*) from task where user_id=? and status='DONE' and (due_date=? or due_date is null)",
                Integer.class, uid, today);
        Integer totalTasks = jdbc.queryForObject(
                "select count(*) from task where user_id=? and (due_date=? or due_date is null)",
                Integer.class, uid, today);
        sb.append("任务：完成 ").append(doneTasks).append('/').append(totalTasks).append('\n');

        sb.append("习惯打点：\n");
        jdbc.queryForList("""
                select system_code, metric_value, target_value, done
                from habit_log where user_id=? and log_date=?
                """, uid, today)
                .forEach(r -> sb.append("  - ").append(r.get("system_code"))
                        .append("：").append(r.get("metric_value"))
                        .append('/').append(r.get("target_value"))
                        .append(Boolean.TRUE.equals(r.get("done")) ? " 已达标" : " 未达标")
                        .append('\n'));

        jdbc.queryForList("""
                select title, conclusion from learning_record
                where user_id=? and learned_at::date=?
                """, uid, today)
                .forEach(r -> sb.append("学习：").append(r.get("title")).append(" → ")
                        .append(r.get("conclusion")).append('\n'));

        jdbc.queryForList("""
                select title, platform from content_record
                where user_id=? and created_at::date=?
                """, uid, today)
                .forEach(r -> sb.append("输出：").append(r.get("title"))
                        .append("（").append(r.get("platform")).append("）\n"));

        return sb.toString();
    }

    private String snapshotMonth(Long uid) {
        YearMonth ym = YearMonth.now();
        StringBuilder sb = new StringBuilder();
        sb.append("月份：").append(ym).append('\n');

        sb.append("习惯达标天数：\n");
        jdbc.queryForList("""
                select system_code, count(*) filter (where done) as done_days
                from habit_log where user_id=? and log_date between ? and ?
                group by system_code
                """, uid, ym.atDay(1), ym.atEndOfMonth())
                .forEach(r -> sb.append("  - ").append(r.get("system_code"))
                        .append("：").append(r.get("done_days")).append(" 天\n"));

        Integer contents = jdbc.queryForObject("""
                select count(*) from content_record
                where user_id=? and created_at>=? and created_at<?
                """, Integer.class, uid, ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay());
        sb.append("本月输出：").append(contents).append(" 篇\n");

        Integer learnMin = jdbc.queryForObject("""
                select coalesce(sum(duration_minutes),0) from learning_record
                where user_id=? and learned_at>=? and learned_at<?
                """, Integer.class, uid, ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay());
        sb.append("本月学习：").append(learnMin).append(" 分钟\n");

        sb.append("项目：\n");
        jdbc.queryForList("select name, status, progress from project where user_id=?", uid)
                .forEach(r -> sb.append("  - ").append(r.get("name"))
                        .append("：").append(r.get("status"))
                        .append(' ').append(r.get("progress")).append("%\n"));

        return sb.toString();
    }

    private String snapshotYear(Long uid) {
        StringBuilder sb = new StringBuilder();
        sb.append("全年统计：\n");

        jdbc.queryForList("""
                select to_char(learned_at,'YYYY-MM') as m,
                       count(*) as cnt, coalesce(sum(duration_minutes),0) as mins
                from learning_record where user_id=?
                  and learned_at >= date_trunc('year', current_date)
                group by 1 order by 1
                """, uid)
                .forEach(r -> sb.append("  ").append(r.get("m"))
                        .append("：学习 ").append(r.get("cnt")).append(" 次，")
                        .append(r.get("mins")).append(" 分钟\n"));

        Integer contents = jdbc.queryForObject("""
                select count(*) from content_record
                where user_id=? and created_at >= date_trunc('year', current_date)
                """, Integer.class, uid);
        sb.append("全年输出：").append(contents).append(" 篇\n");

        Integer doneProjects = jdbc.queryForObject(
                "select count(*) from project where user_id=? and status='DONE'", Integer.class, uid);
        sb.append("完成项目：").append(doneProjects).append(" 个\n");

        jdbc.queryForList("select name, progress, status from project where user_id=?", uid)
                .forEach(r -> sb.append("  - ").append(r.get("name"))
                        .append("（").append(r.get("status")).append(" ")
                        .append(r.get("progress")).append("%）\n"));

        jdbc.queryForList("""
                select system_code, count(*) filter (where done) as done_days
                from habit_log where user_id=? and log_date >= date_trunc('year', current_date)
                group by system_code
                """, uid)
                .forEach(r -> sb.append("习惯 ").append(r.get("system_code"))
                        .append("：").append(r.get("done_days")).append(" 天\n"));

        return sb.toString();
    }

    private ApiResponse<?> run(String scene, String prompt) {
        try {
            String text = gateway.complete(scene, prompt);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("scene", scene);
            out.put("content", text);
            return ApiResponse.ok(out);
        } catch (AiUnavailableException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

}
