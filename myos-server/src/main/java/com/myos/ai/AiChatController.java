package com.myos.ai;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 助手对话入口（对应 PRD §7.2 / 二期「办事」）。
 *
 * 五个接口：
 *   POST /ai/chat          发一句话，返回模型的回复或一组待确认的工具调用
 *   POST /ai/tool/execute  用户确认之后真正执行
 *   POST /ai/tool/cancel   用户取消，把悬空的调用补成取消记录
 *   GET  /ai/conversations 历史会话列表
 *   GET  /ai/conversations/{id} 某次会话的全部消息
 *
 * ── 安全模型（整个二期的地基，改动前先读这里）──────────────────
 * 模型返回的 tool_calls 只是**意图**。本类把它原样存进 ai_message.tool_calls，
 * 然后分两种情况：
 *   · 读工具（list_tasks）→ 立刻执行，不回填给用户确认（它不改数据，
 *     而且 agent 得先知道任务 id 才能谈下一步）
 *   · 写工具（create_task / complete_task / log_habit / add_finance / write_review）
 *     → **一律停下**，把调用清单交给前端渲染成确认卡片。
 *       只有用户点了确认、走到 /ai/tool/execute，才可能真正写库。
 *
 * 还有一道容易漏的关口：/ai/tool/execute 不信任请求体里的工具名和参数。
 * 客户端只被允许说「确认哪几个 id」，name 与 arguments 一律回读最近一条
 * assistant 消息里存的意图。否则手拼一个 POST 就能让后端执行任意工具、任意参数，
 * 确认卡片就成了摆设。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 场景标识，进日志和统计 */
    private static final String SCENE = "chat";

    /** 单个用户请求内最多跑几轮工具查询。防止模型反复要数据把请求拖死 */
    private static final int MAX_TURNS = 4;

    /** 每次请求带多少条历史。裁掉的部分不能再切断「assistant 调用 → tool 结果」这一对 */
    private static final int HISTORY_LIMIT = 30;

    private static final String EMPTY_REPLY = "（模型这次没有返回内容，请再问一次）";

    private static final String TOO_MANY_TURNS =
            "（这次需要的查询步骤太多，已经先停下来。请把问题拆小一点再试）";

    /** 用户没确认（或只确认了一部分）时，给悬空的调用补上的回填文案 */
    private static final String CANCELLED_TEXT = "用户未确认这个操作，已取消，没有改动任何数据。";

    private final AiGateway gateway;
    private final AiTools tools;
    private final JdbcTemplate jdbc;

    public AiChatController(AiGateway gateway, AiTools tools, JdbcTemplate jdbc) {
        this.gateway = gateway;
        this.tools = tools;
        this.jdbc = jdbc;
    }

    // ---------------- 对外接口 ----------------

    /** 发一句话。返回 status=DONE（可以显示）或 PENDING（等用户确认工具调用） */
    @PostMapping("/chat")
    public ApiResponse<?> chat(@RequestBody ChatRequest req) {
        Long uid = CurrentUser.id();
        String message = req.message() == null ? "" : req.message().strip();
        if (message.isEmpty()) {
            return ApiResponse.fail("消息不能为空");
        }
        if (message.length() > 2000) {
            message = message.substring(0, 2000);
        }

        Long convId = req.conversationId();
        if (convId != null) {
            if (!ownedBy(convId, uid)) {
                return ApiResponse.fail("会话不存在或不属于当前用户");
            }
        } else {
            convId = jdbc.queryForObject("""
                    insert into ai_conversation(user_id, title) values (?, ?) returning id
                    """, Long.class, uid, titleOf(message));
        }

        // 上一轮可能停在「待确认」而用户压根没确认 —— 先把悬空的调用补成取消，
        // 否则模型带着一条没有 tool 结果的 assistant(tool_calls) 继续跑，必炸
        closeDanglingToolCalls(convId, Set.of());

        insertMessage(convId, "user", message, null, null);
        touch(convId);

        try {
            return ApiResponse.ok(turnOut(convId, runLoop(uid, convId)));
        } catch (AiUnavailableException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 用户点了「取消」。
     *
     * 不重新调模型：没有任何执行结果需要总结，把取消记录补进历史就够了。
     */
    @PostMapping("/tool/cancel")
    public ApiResponse<?> cancel(@RequestBody ExecuteRequest req) {
        Long uid = CurrentUser.id();
        Long convId = req.conversationId();
        if (convId == null || !ownedBy(convId, uid)) {
            return ApiResponse.fail("会话不存在或不属于当前用户");
        }
        closeDanglingToolCalls(convId, Set.of());
        return ApiResponse.ok(turnOut(convId, new Turn(true, "已取消，没有改动任何数据", List.of())));
    }

    /**
     * 执行用户确认过的工具调用。
     *
     * 请求体只带 id 列表 —— 工具名和参数一律以库里存的意图为准（见类注释）。
     */
    @PostMapping("/tool/execute")
    public ApiResponse<?> execute(@RequestBody ExecuteRequest req) {
        Long uid = CurrentUser.id();
        Long convId = req.conversationId();
        if (convId == null) {
            return ApiResponse.fail("缺少 conversationId");
        }
        if (!ownedBy(convId, uid)) {
            return ApiResponse.fail("会话不存在或不属于当前用户");
        }
        if (req.calls() == null || req.calls().isEmpty()) {
            return ApiResponse.fail("没有要执行的调用");
        }

        Map<String, AiGateway.ToolCall> offered = lastOfferedCalls(convId);
        if (offered.isEmpty()) {
            return ApiResponse.fail("当前没有待确认的工具调用");
        }

        List<AiGateway.ToolCall> toRun = new ArrayList<>();
        Set<String> runningIds = new LinkedHashSet<>();
        for (IdOnly c : req.calls()) {
            AiGateway.ToolCall src = offered.get(c.id());
            if (src == null) {
                return ApiResponse.fail("调用 " + c.id() + " 不在本次待确认列表里，已拒绝执行");
            }
            toRun.add(src);
            runningIds.add(src.id());
        }

        // 只确认了一部分时，剩下那些也要补成「已取消」。
        // 交给 closeDanglingToolCalls 统一收尾，前端就不用记得「漏确认的会坏掉」。
        closeDanglingToolCalls(convId, runningIds);

        for (AiGateway.ToolCall c : toRun) {
            log.info("AI 工具调用已确认并执行：conv={} tool={} id={}", convId, c.name(), c.id());
            insertMessage(convId, "tool", runTool(uid, c), null, c.id());
        }
        touch(convId);

        try {
            return ApiResponse.ok(turnOut(convId, runLoop(uid, convId)));
        } catch (AiUnavailableException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 历史会话列表，最近更新的在前 */
    @GetMapping("/conversations")
    public ApiResponse<?> conversations() {
        Long uid = CurrentUser.id();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.id, c.title, c.updated_at,
                       (select count(*) from ai_message m where m.conversation_id = c.id) as message_count
                from ai_conversation c
                where c.user_id = ?
                order by c.updated_at desc limit 50
                """, uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("title", r.get("title"));
            m.put("updatedAt", r.get("updated_at"));
            m.put("messageCount", r.get("message_count"));
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** 某次会话的完整消息。toolCalls 原样带回，刷新后确认卡片还能重放 */
    @GetMapping("/conversations/{id}")
    public ApiResponse<?> messages(@PathVariable Long id) {
        Long uid = CurrentUser.id();
        if (!ownedBy(id, uid)) {
            return ApiResponse.fail("会话不存在或不属于当前用户");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, role, content, tool_calls, tool_call_id, created_at
                from ai_message where conversation_id = ? order by id
                """, id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("role", r.get("role"));
            m.put("content", r.get("content"));
            m.put("toolCalls", parseToolCalls(r.get("tool_calls")));
            m.put("toolCallId", r.get("tool_call_id"));
            m.put("createdAt", r.get("created_at"));
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    // ---------------- 对话主循环 ----------------

    /**
     * 一轮完整的「问 → 可能查几次 → 答」。
     *
     * 每次迭代都从库里重建上下文，而不是在内存里攒 —— 内存里攒的那份
     * 和库里存的那份一旦不一致（比如某次插入失败后没回滚），后续所有请求
     * 都会带着一份和现实不符的历史，而且极难排查。宁可多查几次库。
     *
     * @param done=true 的 content 可以直接显示；false 表示在等用户确认
     */
    private Turn runLoop(Long uid, Long convId) {
        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            AiGateway.ChatReply reply =
                    gateway.chat(SCENE, buildContext(convId), AiTools.DECLARATION);
            List<AiGateway.ToolCall> calls =
                    reply.toolCalls() == null ? List.of() : reply.toolCalls();

            if (calls.isEmpty()) {
                String text = reply.content() == null ? "" : reply.content().strip();
                if (text.isEmpty()) {
                    // 既没正文也没调用意图：不往库里塞一条空消息（历史会很难看），
                    // 但也不能让前端对着空白发呆
                    return new Turn(true, EMPTY_REPLY, List.of());
                }
                insertMessage(convId, "assistant", text, null, null);
                return new Turn(true, text, List.of());
            }

            // 有调用意图，先把意图存住。这一步很关键：/ai/tool/execute 就是靠
            // 回读这条消息来校验「用户确认的到底是不是模型要做的那些事」
            insertMessage(convId, "assistant", blankToNull(reply.content()),
                    toWireJson(calls), null);

            boolean hasWrite = calls.stream().anyMatch(c -> tools.isWrite(c.name()));
            if (hasWrite) {
                log.info("AI 工具调用待确认：conv={} calls={}", convId,
                        calls.stream().map(AiGateway.ToolCall::name).toList());
                return new Turn(false, blankToNull(reply.content()), toPending(calls));
            }

            // 全是读操作，当场执行并把结果回填，然后接着问
            for (AiGateway.ToolCall c : calls) {
                insertMessage(convId, "tool", runTool(uid, c), null, c.id());
            }
        }
        return new Turn(true, TOO_MANY_TURNS, List.of());
    }

    /** 执行一次工具调用。参数不合法不算崩溃，而是作为「失败的结果」回填给模型 */
    private String runTool(Long uid, AiGateway.ToolCall c) {
        try {
            return tools.execute(uid, new AiTools.ToolCallAlias(c.id(), c.name(), c.arguments()));
        } catch (IllegalArgumentException e) {
            // 模型给的参数缺字段/类型不对时，让它看到失败原因自己换一组参数，
            // 比直接把请求打回给用户体验好得多
            return "调用失败：" + e.getMessage();
        } catch (Exception e) {
            log.warn("AI 工具执行异常：tool={}", c.name(), e);
            return "调用失败：系统内部错误（" + e.getClass().getSimpleName() + "）";
        }
    }

    // ---------------- 上下文拼装 ----------------

    /**
     * 人设 + 最近若干条历史。
     *
     * 日期每次现算，不用静态常量固化 —— 后端可能是几周前起的，
     * 固化的日期会让模型在回答「今天」时一直说错。
     */
    private List<AiGateway.ChatMessage> buildContext(Long convId) {
        List<AiGateway.ChatMessage> ctx = new ArrayList<>();
        ctx.add(new AiGateway.ChatMessage("system", systemPrompt(), null, null));

        List<Map<String, Object>> rows = new ArrayList<>(jdbc.queryForList("""
                select role, content, tool_calls, tool_call_id from ai_message
                where conversation_id = ? order by id desc limit ?
                """, convId, HISTORY_LIMIT + 1));
        // 多取一条只为判断「是不是被裁过」；正序翻回来才是时间顺序
        boolean trimmed = rows.size() > HISTORY_LIMIT;
        if (trimmed) {
            rows = new ArrayList<>(rows.subList(0, HISTORY_LIMIT));
        }
        Collections.reverse(rows);

        if (trimmed) {
            // 裁掉的老消息里可能包含某次 assistant 调用，只剩下它的 tool 结果——
            // 那这条 tool 消息就在「回应一次不存在的调用」，服务端会整包拒绝。
            // 所以从前往后跳到第一条 user 为止，整段前史一起丢掉。
            while (!rows.isEmpty() && !"user".equals(rows.get(0).get("role"))) {
                rows.remove(0);
            }
        }

        for (Map<String, Object> r : rows) {
            ctx.add(new AiGateway.ChatMessage(
                    (String) r.get("role"),
                    (String) r.get("content"),
                    (String) r.get("tool_call_id"),
                    normalizeJson(r.get("tool_calls"))));
        }
        return ctx;
    }

    private String systemPrompt() {
        return """
                你是 MyOS 的私人成长助理，只服务当前登录的这一个用户。
                今天是 %s。

                你能做两件事：
                1. 回答问题：用工具查到的真实数据回答，查不到就直说查不到
                2. 帮用户办事：创建任务、完成任务、习惯打点、记一笔账、写复盘

                铁律（违反任何一条都是严重错误）：
                - 只回答和眼前这个用户的工作台有关的问题
                - 工具返回什么就说什么，禁止编造数据、id、金额
                - 用户明确要求改动数据时，必须调用工具提出意图，
                  不允许只用文字描述要做什么 —— 文字描述等于什么都没做
                - 不要说「已经创建好了」「已经记上了」「已提交」，
                  改成「我准备这样做，请确认」
                - 直接说人话，不写标题、不列编号、不给代码

                工具参数一律按 tools 声明填写；今天截止的日期可以省略不填。
                """.formatted(LocalDate.now());
    }

    // ---------------- 序列化小工具 ----------------

    /**
     * 把调用意图还原成 OpenAI 的线上形状，原样存库、原样回放。
     *
     * 为什么不存成 {id,name,arguments} 三段式：协议里 arguments 是
     * 「字符串里套 JSON」，而且必须套在 type=function 的 function 对象里。
     * 少这层包装，严格的服务端会整包 400 —— 回放历史时才会炸，
     * 现场复现还得靠多轮对话，排查成本很高。
     */
    private String toWireJson(List<AiGateway.ToolCall> calls) {
        ArrayNode arr = JSON.createArrayNode();
        for (AiGateway.ToolCall c : calls) {
            ObjectNode node = arr.addObject();
            node.put("id", c.id());
            node.put("type", "function");
            ObjectNode fn = node.putObject("function");
            fn.put("name", c.name());
            fn.put("arguments", c.arguments() == null ? "{}" : c.arguments());
        }
        return arr.toString();
    }

    /** 待确认清单，喂给前端渲染确认卡片 */
    private List<Map<String, Object>> toPending(List<AiGateway.ToolCall> calls) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiGateway.ToolCall c : calls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("name", c.name());
            m.put("label", tools.labelOf(c.name()));
            m.put("risk", tools.isWrite(c.name()) ? "WRITE" : "READ");
            m.put("arguments", c.arguments() == null ? "{}" : c.arguments());
            out.add(m);
        }
        return out;
    }

    /** 把库里读出来的 tool_calls 翻成前端好用的形状；没有或坏了就返回 null */
    private List<Map<String, Object>> parseToolCalls(Object raw) {
        String json = normalizeJson(raw);
        if (json == null) {
            return null;
        }
        try {
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode tc : JSON.readTree(json)) {
                JsonNode fn = tc.path("function");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", tc.path("id").asText(""));
                m.put("name", fn.path("name").asText(""));
                m.put("label", tools.labelOf(fn.path("name").asText("")));
                m.put("risk", tools.isWrite(fn.path("name").asText("")) ? "WRITE" : "READ");
                m.put("arguments", fn.path("arguments").asText("{}"));
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSONB 列经 pgjdbc 回来是 PGobject，toString 即原文。
     * 顺手做一次「能解析就规范化、解析不了就当没有」的兜底：万一库里那段
     * 被外部改动过，也不该让整个对话报错。
     */
    private String normalizeJson(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString();
        if (text.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(text).toString();
        } catch (Exception e) {
            log.warn("ai_message.tool_calls 不是合法 JSON，已按空处理：{}", firstChars(text));
            return null;
        }
    }

    private static String firstChars(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    // ---------------- 落库小工具 ----------------

    private void insertMessage(Long convId, String role, String content,
                               String toolCallsJson, String toolCallId) {
        jdbc.update("""
                insert into ai_message(conversation_id, role, content, tool_calls, tool_call_id)
                values (?, ?, ?, ?::jsonb, ?)
                """, convId, role, content, toolCallsJson, toolCallId);
    }

    private void touch(Long convId) {
        jdbc.update("update ai_conversation set updated_at = now() where id = ?", convId);
    }

    /**
     * 把「悬空的工具调用」补上取消记录。
     *
     * 为什么必须有这一步：模型给出待确认的调用后，用户完全可以不点确认，
     * 而是直接发下一句话，或者干脆关掉页面。此时历史里就留着一条
     * assistant(tool_calls)，后面没有任何 tool 消息回应它。
     * OpenAI 协议下这个形状会让整个请求被拒，而且报错发生在**下一次**请求，
     * 现场极难复现 —— 用户只会觉得「聊着聊着就不能用了」。
     *
     * 因此在两个可能续聊的入口（/ai/chat、/ai/tool/execute）都先调用它。
     *
     * @param keep 正要执行的调用 id，不补取消（它们马上会有执行结果）
     */
    private void closeDanglingToolCalls(Long convId, Set<String> keep) {
        List<Map<String, Object>> rows = new ArrayList<>(jdbc.queryForList("""
                select id, tool_calls from ai_message
                where conversation_id = ? and role = 'assistant' and tool_calls is not null
                order by id
                """, convId));
        if (rows.isEmpty()) {
            return;
        }

        Set<String> answered = new HashSet<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select tool_call_id from ai_message
                where conversation_id = ? and role = 'tool' and tool_call_id is not null
                """, convId)) {
            Object v = r.get("tool_call_id");
            if (v != null) {
                answered.add(v.toString());
            }
        }

        for (Map<String, Object> r : rows) {
            String json = normalizeJson(r.get("tool_calls"));
            if (json == null) {
                continue;
            }
            try {
                for (JsonNode tc : JSON.readTree(json)) {
                    String id = tc.path("id").asText("");
                    if (id.isBlank() || answered.contains(id) || keep.contains(id)) {
                        continue;
                    }
                    insertMessage(convId, "tool", CANCELLED_TEXT, null, id);
                    answered.add(id);
                }
            } catch (Exception e) {
                log.warn("悬空工具调用解析失败：{}", firstChars(json));
            }
        }
    }

    private boolean ownedBy(Long convId, Long uid) {
        Integer n = jdbc.queryForObject(
                "select count(*) from ai_conversation where id = ? and user_id = ?",
                Integer.class, convId, uid);
        return n != null && n > 0;
    }

    /**
     * 最近一条 assistant 消息里的调用意图，key = tool_call id。
     *
     * /ai/tool/execute 用它做唯一事实来源：客户端只被允许说「确认哪几个」，
     * 工具名和参数一律以这里读出来的为准。
     */
    private Map<String, AiGateway.ToolCall> lastOfferedCalls(Long convId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tool_calls from ai_message
                where conversation_id = ? and role = 'assistant' and tool_calls is not null
                order by id desc limit 1
                """, convId);
        Map<String, AiGateway.ToolCall> out = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            return out;
        }
        String json = normalizeJson(rows.get(0).get("tool_calls"));
        if (json == null) {
            return out;
        }
        try {
            for (JsonNode tc : JSON.readTree(json)) {
                JsonNode fn = tc.path("function");
                String id = tc.path("id").asText("");
                if (!id.isBlank()) {
                    out.put(id, new AiGateway.ToolCall(id,
                            fn.path("name").asText(""),
                            fn.path("arguments").asText("{}")));
                }
            }
        } catch (Exception e) {
            log.warn("待确认的工具调用解析失败：{}", firstChars(json));
        }
        return out;
    }

    private Map<String, Object> turnOut(Long convId, Turn t) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", convId);
        out.put("status", t.done() ? "DONE" : "PENDING");
        out.put("content", t.content());
        out.put("toolCalls", t.pending());
        return out;
    }

    private static String titleOf(String message) {
        String t = message.strip();
        return t.length() > 20 ? t.substring(0, 20) + "…" : t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    // ---------------- 请求体与内部类型 ----------------

    public record ChatRequest(Long conversationId, String message) {}

    public record ExecuteRequest(Long conversationId, List<IdOnly> calls) {}

    /** 只带 id。工具名与参数由后端回读，前端给了也不算 */
    public record IdOnly(String id) {}

    /** 一轮对话的结果 */
    private record Turn(boolean done, String content, List<Map<String, Object>> pending) {}
}
