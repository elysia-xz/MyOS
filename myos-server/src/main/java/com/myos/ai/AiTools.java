package com.myos.ai;

import com.myos.common.SystemCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 助手可调用的工作台工具。
 *
 * ⚠️ 安全模型（本类的核心约束）：
 *   模型返回 tool_calls 只代表**意图**。AiTools 只负责两件事 ——
 *   ① DECLARATION 告诉模型有哪些工具、要传什么参数；
 *   ② execute() 在**用户已经确认过**之后真正写库。
 *   execute() 不由模型触发，只能由 /ai/tool/execute 控制器调用。
 *   读工具（list_tasks）例外：它不改数据，agent 需要它才知道任务 id。
 *
 * 参数校验为什么必须做：模型给的参数是自由文本生成的，可能缺字段、
 * 给错枚举值、甚至给个奇怪日期。写库前不校验，脏数据就直接进库了。
 */
@Component
public class AiTools {

    /** 工具风险等级。WRITE 一律要求用户确认后才能执行。 */
    public enum Risk { READ, WRITE }

    public record ToolDef(String name, Risk risk, String label) {}

    /** 全部工具的定义，顺序即声明顺序 */
    public static final List<ToolDef> DEFS = List.of(
            new ToolDef("list_tasks", Risk.READ, "查看我的任务"),
            new ToolDef("create_task", Risk.WRITE, "创建任务"),
            new ToolDef("complete_task", Risk.WRITE, "完成任务"),
            new ToolDef("log_habit", Risk.WRITE, "习惯打点"),
            new ToolDef("add_finance", Risk.WRITE, "记一笔账"),
            new ToolDef("write_review", Risk.WRITE, "写复盘")
    );

    /**
     * 工具声明（OpenAI tools 格式）。
     *
     * 写这段的两个注意点：
     *  ① description 要写清「什么时候该用」，模型靠它判断是否调用；
     *  ② required 尽量短。必填项越多，模型越容易因为某个可选项没填而整体失败。
     */
    public static final String DECLARATION = """
            [
              {"type":"function","function":{
                "name":"list_tasks",
                "description":"查看当前用户今天到期以及逾期未完成的任务列表，返回任务 id、标题、状态。需要操作某个具体任务（如标记完成）时先用它查到 id。",
                "parameters":{"type":"object","properties":{},"required":[]}
              }},
              {"type":"function","function":{
                "name":"create_task",
                "description":"在用户的工作台创建一条新任务。只要用户明确要求新增或建任务，就必须调用本工具提出意图，不要只用文字描述要做什么。",
                "parameters":{"type":"object","properties":{
                  "title":{"type":"string","description":"任务标题，简洁明确"},
                  "priority":{"type":"string","enum":["HIGH","MEDIUM","LOW"],"description":"优先级，默认 MEDIUM"},
                  "dueDate":{"type":"string","description":"截止日期，格式 YYYY-MM-DD，不填则为今天"}
                },"required":["title"]}
              }},
              {"type":"function","function":{
                "name":"complete_task",
                "description":"把某个任务标记为已完成。taskId 必须来自 list_tasks 的返回，不要自己编造。",
                "parameters":{"type":"object","properties":{
                  "taskId":{"type":"integer","description":"任务 id"}
                },"required":["taskId"]}
              }},
              {"type":"function","function":{
                "name":"log_habit",
                "description":"为今天的某个学习系统记录打点（如今天英语学了 45 分钟）。",
                "parameters":{"type":"object","properties":{
                  "systemCode":{"type":"string","enum":["AI","TECH","CONTENT","ENGLISH","BODY","FINANCE","SOCIAL"],"description":"系统编码，必须取自枚举"},
                  "metricValue":{"type":"integer","description":"今日累计数值，单位见系统定义（多为分钟）"},
                  "note":{"type":"string","description":"可选备注"}
                },"required":["systemCode","metricValue"]}
              }},
              {"type":"function","function":{
                "name":"add_finance",
                "description":"记录一笔收入或支出。",
                "parameters":{"type":"object","properties":{
                  "recordType":{"type":"string","enum":["INCOME","EXPENSE"],"description":"收入或支出"},
                  "category":{"type":"string","description":"分类，如 餐饮/交通/学习/工资"},
                  "amount":{"type":"number","description":"金额，正数"},
                  "note":{"type":"string","description":"可选备注"}
                },"required":["recordType","category","amount"]}
              }},
              {"type":"function","function":{
                "name":"write_review",
                "description":"把一段复盘内容写入用户的复盘记录。",
                "parameters":{"type":"object","properties":{
                  "reviewType":{"type":"string","enum":["DAY","WEEK","MONTH"],"description":"复盘周期，默认 DAY"},
                  "goodThings":{"type":"string","description":"做得好的"},
                  "biggestGain":{"type":"string","description":"最大收获"},
                  "nextFocus":{"type":"string","description":"下一个焦点"}
                },"required":[]}
              }}
            ]
            """;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public AiTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Risk riskOf(String name) {
        return DEFS.stream().filter(d -> d.name().equals(name))
                .map(ToolDef::risk).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知工具：" + name));
    }

    public boolean exists(String name) {
        return DEFS.stream().anyMatch(d -> d.name().equals(name));
    }

    /**
     * 是否写工具，**不抛异常**。
     * 模型偶尔会凭空编一个不存在的工具名，这里必须先判存在再判等级，
     * 否则 riskOf 抛的 IllegalArgumentException 会把整个对话打断。
     * 未知工具按「不是写」处理：它会被当成调用失败回填给模型自己纠正。
     */
    public boolean isWrite(String name) {
        return exists(name) && riskOf(name) == Risk.WRITE;
    }

    /** 工具的中文名，给确认卡片显示用 */
    public String labelOf(String name) {
        return DEFS.stream().filter(d -> d.name().equals(name))
                .map(ToolDef::label).findFirst().orElse(name);
    }

    /**
     * 执行一个**已确认**的工具调用。
     *
     * @param uid  当前用户 id（由后端从令牌取，绝不信任请求体里的用户标识）
     * @param call 模型的调用意图
     * @return 给用户看的结果简述，会作为 tool 消息回填进对话
     * @throws IllegalArgumentException 参数不合法（调用方应转成错误提示，不落库）
     */
    public String execute(Long uid, ToolCallAlias call) {
        JsonNode args = parseArgs(call.arguments());
        return switch (call.name()) {
            case "list_tasks" -> listTasks(uid);
            case "create_task" -> createTask(uid, args);
            case "complete_task" -> completeTask(uid, args);
            case "log_habit" -> logHabit(uid, args);
            case "add_finance" -> addFinance(uid, args);
            case "write_review" -> writeReview(uid, args);
            default -> throw new IllegalArgumentException("未知工具：" + call.name());
        };
    }

    /** 与 AiGateway.ToolCall 同形，独立出来是为了让 execute 不被接口实现牵着走 */
    public record ToolCallAlias(String id, String name, String arguments) {}

    private JsonNode parseArgs(String raw) {
        if (raw == null || raw.isBlank()) return JSON.createObjectNode();
        try {
            JsonNode n = JSON.readTree(raw);
            return n.isObject() ? n : JSON.createObjectNode();
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数不是合法 JSON：" + raw);
        }
    }

    // ---------------- 各工具实现 ----------------

    private String listTasks(Long uid) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, title, status, due_date from task
                where user_id=? and (due_date = ? or due_date is null
                                     or (due_date < ? and status <> 'DONE'))
                order by case when due_date < ? and status <> 'DONE' then 0 else 1 end, id desc
                """, uid, today, today, today);
        if (rows.isEmpty()) return "当前没有今天到期或逾期未完成的任务。";
        StringBuilder sb = new StringBuilder("任务列表：\n");
        for (Map<String, Object> r : rows) {
            sb.append(String.format("- id=%s | %s | %s | 截止 %s%n",
                    r.get("id"), r.get("status"), r.get("title"),
                    r.get("due_date") == null ? "无" : r.get("due_date")));
        }
        return sb.toString().strip();
    }

    private String createTask(Long uid, JsonNode a) {
        String title = a.path("title").asText("").strip();
        if (title.isEmpty()) throw new IllegalArgumentException("任务标题不能为空");
        if (title.length() > 300) title = title.substring(0, 300);

        String priority = enumOr(a, "priority", "MEDIUM", List.of("HIGH", "MEDIUM", "LOW"));
        LocalDate due = dateOr(a, "dueDate", LocalDate.now());

        // 用 Long.class 而不是 Number.class：Spring 走 getColumnValue 时
        // Number 分支会按 double 取值，%s 格式化出来就是「#29.0」这种尾巴。
        // 实测踩过，返回给模型的文案里带着 .0，挺难看。
        Long id = jdbc.queryForObject("""
                insert into task(user_id, title, priority, status, due_date)
                values (?, ?, ?, 'TODO', ?) returning id
                """, Long.class, uid, title, priority, due);
        return String.format("已创建任务 #%s「%s」（优先级 %s，截止 %s）",
                id, title, priority, due);
    }

    private String completeTask(Long uid, JsonNode a) {
        long taskId = a.path("taskId").asLong(0);
        if (taskId <= 0) throw new IllegalArgumentException("taskId 不合法，请先用 list_tasks 查询");
        // 必须带 user_id：否则拿着别人的 id 也能改，越权
        int n = jdbc.update("""
                update task set status='DONE', completed_at=now()
                where id=? and user_id=? and status <> 'DONE'
                """, taskId, uid);
        if (n == 0) {
            // 区分「不存在/不属于你」和「本来就已完成」，提示才对得上
            Integer owned = jdbc.queryForObject(
                    "select count(*) from task where id=? and user_id=?", Integer.class, taskId, uid);
            if (owned == null || owned == 0) throw new IllegalArgumentException("任务 #" + taskId + " 不存在或不属于当前用户");
            return "任务 #" + taskId + " 之前已完成，无需重复操作";
        }
        return "已把任务 #" + taskId + " 标记为完成";
    }

    private String logHabit(Long uid, JsonNode a) {
        String code = a.path("systemCode").asText("").strip().toUpperCase(Locale.ROOT);
        if (!SystemCatalog.exists(code)) {
            throw new IllegalArgumentException("系统编码无效：" + code + "（可选 AI/TECH/CONTENT/ENGLISH/BODY/FINANCE/SOCIAL）");
        }
        int value;
        try {
            value = a.path("metricValue").asInt(-1);
        } catch (Exception e) {
            throw new IllegalArgumentException("metricValue 必须是整数");
        }
        if (value < 0) throw new IllegalArgumentException("metricValue 不能为负");
        if (value > 1440 * 4) throw new IllegalArgumentException("metricValue 超出合理范围（一天最多 5760 分钟）");

        LocalDate today = LocalDate.now();
        // 沿用习惯打点的 upsert 语义：同一天同一系统只留一条
        jdbc.update("""
                insert into habit_log(user_id, log_date, system_code, metric_value, target_value, done, note)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (user_id, log_date, system_code) do update set
                  metric_value = excluded.metric_value,
                  target_value = excluded.target_value,
                  done = excluded.done,
                  note = excluded.note,
                  updated_at = now()
                """, uid, today, code, value,
                SystemCatalog.of(code).dailyTarget(),
                value >= SystemCatalog.of(code).dailyTarget(),
                textOrNull(a, "note"));
        return String.format("已记录 %s 今日 %d 分钟%s",
                SystemCatalog.of(code).name(), value,
                value >= SystemCatalog.of(code).dailyTarget() ? "，已达标" : "");
    }

    private String addFinance(Long uid, JsonNode a) {
        String type = enumOr(a, "recordType", "EXPENSE", List.of("INCOME", "EXPENSE"));
        String category = a.path("category").asText("").strip();
        if (category.isEmpty()) throw new IllegalArgumentException("分类不能为空");
        if (category.length() > 64) category = category.substring(0, 64);

        BigDecimal amount;
        try {
            amount = a.path("amount").decimalValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("金额必须是数字");
        }
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("金额必须是正数");
        if (amount.compareTo(BigDecimal.valueOf(100_000_000)) > 0) {
            throw new IllegalArgumentException("金额超出合理范围");
        }

        Long id = jdbc.queryForObject("""
                insert into finance_record(user_id, record_date, record_type, category, amount, note)
                values (?, ?, ?, ?, ?, ?) returning id
                """, Long.class, uid, LocalDate.now(), type, category, amount, textOrNull(a, "note"));
        return String.format("已记一笔%s：%s %s 元（%s）#%s",
                "INCOME".equals(type) ? "收入" : "支出", category, amount.toPlainString(),
                LocalDate.now(), id);
    }

    private String writeReview(Long uid, JsonNode a) {
        String type = enumOr(a, "reviewType", "DAY", List.of("DAY", "WEEK", "MONTH"));
        LocalDate date = LocalDate.now();
        jdbc.update("""
                insert into review(user_id, review_type, review_date, completed,
                                   good_things, biggest_gain, next_focus)
                values (?, ?, ?, true, ?, ?, ?)
                on conflict (user_id, review_type, review_date) do update set
                  good_things = coalesce(excluded.good_things, review.good_things),
                  biggest_gain = coalesce(excluded.biggest_gain, review.biggest_gain),
                  next_focus = coalesce(excluded.next_focus, review.next_focus),
                  completed = true
                """, uid, type, date,
                textOrNull(a, "goodThings"), textOrNull(a, "biggestGain"), textOrNull(a, "nextFocus"));
        return "已写入" + type + "复盘（" + date + "）";
    }

    // ---------------- 参数取值小工具 ----------------

    private static String enumOr(JsonNode a, String field, String def, List<String> allowed) {
        String v = a.path(field).asText(def).strip().toUpperCase(Locale.ROOT);
        return allowed.contains(v) ? v : def;
    }

    private static LocalDate dateOr(JsonNode a, String field, LocalDate def) {
        String v = a.path(field).asText("").strip();
        if (v.isEmpty()) return def;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD，收到：" + v);
        }
    }

    private static String textOrNull(JsonNode a, String field) {
        String v = a.path(field).asText("").strip();
        return v.isEmpty() ? null : v;
    }
}
