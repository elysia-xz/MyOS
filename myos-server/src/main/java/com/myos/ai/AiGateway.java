package com.myos.ai;

import java.util.List;

/**
 * AI 网关 —— 对应 PRD §7.3。
 *
 * 设计约定：
 *  - V1 只对接一个模型，避免过早抽象（未来可加实现类做多模型路由）
 *  - API Key 只存后端，绝不下发前端
 *  - 所有调用结果先落库，支持用户编辑后保存
 *
 * 两种调用形态：
 *  ① complete() —— 场景化单轮：后端拼好一整段提示词，模型回一段文字。
 *                    用于「生成日报 / 复盘 / 月报」这类一次性场景。
 *  ② chat()     —— 多轮对话：带上历史消息，可附带工具声明。
 *                    用于「问答」与「办事」（工具声明见 AiTools）。
 *                    注意它返回的 toolCalls 只是模型的**意图**，
 *                    调用方不得据此自动写库，必须经用户确认后另行执行。
 */
public interface AiGateway {

    /** 网关是否已配置（未配置时前端隐藏或提示） */
    boolean available();

    /**
     * 按场景生成文本（单轮）。
     *
     * @param scene  场景标识：daily_summary / daily_report / daily_review / monthly_summary / annual_report / chat
     * @param prompt 已由后端拼装好的完整提示词
     * @return 模型返回的文本
     */
    String complete(String scene, String prompt);

    /**
     * 多轮对话，可带工具声明。
     *
     * @param scene     场景标识，用于日志与统计
     * @param messages  完整历史，按时间顺序，首条可以是 system
     * @param toolsJson 工具声明的 JSON（OpenAI tools 格式）；不需要工具时传 null
     * @return 正文与工具调用意图；两者各自可能为空
     */
    ChatReply chat(String scene, List<ChatMessage> messages, String toolsJson);

    /**
     * 一条对话消息。
     *
     * 四个字段分别服务三种消息形态：
     *   user      (user, 正文, null, null)
     *   assistant (assistant, 正文或 null, null, toolCallsJson) ← 只想调用工具时正文为 null，
     *               但 toolCallsJson 必须**原样带上**，因为协议要求 assistant 消息里出现过的
     *               tool_calls，后面才能用 role=tool 的结果消息回应它
     *   tool      (tool, 执行结果, toolCallId, null)
     *
     * 历史是从库里读出来的，所以这里的 toolCallsJson 就是当初存库的那份原始 JSON，
     * 不重新拼装 —— 拼出来少个 type 字段，严格的服务端会整包拒绝。
     */
    record ChatMessage(String role, String content, String toolCallId, String toolCallsJson) {}

    /** 模型想调用的一个工具。arguments 是原始 JSON 字符串，交由工具层解析校验 */
    record ToolCall(String id, String name, String arguments) {}

    /** 多轮对话的返回 */
    record ChatReply(String content, List<ToolCall> toolCalls) {}
}
