package com.myos.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容协议的 AI 网关实现。
 *
 * V1 默认对接 DeepSeek（OpenAI 兼容），可切换到任意兼容服务。
 * 配置从 {@link AiConfigService} 每次实时读取，因此界面上改完即时生效，无需重启后端
 * （之前这里是构造期 {@code @Value} 固化，改了环境变量必须重启）。
 *
 * JSON 一律交给 Jackson 处理（发出与解析都是）。
 * 原先是手写字符串拼接 + 手写扫描器（indexOf("\"content\"")），有两个
 * 已经踩到边缘的坑：
 *   ① 请求体里 prompt 的转义靠自己维护，漏一个控制字符就是畸形 JSON；
 *   ② 响应里 content 为 null 时（工具调用场景的典型形状
 *      {"role":"assistant","content":null,"tool_calls":[...]}），
 *      扫描器找不到引号会一路往后找到 "tool_calls"，把字段名当正文返回。
 *      引入多轮对话和工具调用后这个形状变成常态，所以必须换掉。
 */
@Component
public class OpenAiCompatGateway implements AiGateway {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiConfigService config;

    public OpenAiCompatGateway(AiConfigService config) {
        this.config = config;
    }

    @Override
    public boolean available() {
        return !AiConfigService.isBlank(config.effective().apiKey());
    }

    @Override
    public String complete(String scene, String prompt) {
        ChatReply reply = chat(scene, List.of(new ChatMessage("user", prompt, null, null)), null);
        return reply.content() == null ? "" : reply.content();
    }

    @Override
    public ChatReply chat(String scene, List<ChatMessage> messages, String toolsJson) {
        AiConfigService.Effective cfg = config.effective();
        if (AiConfigService.isBlank(cfg.apiKey())) {
            throw new AiUnavailableException("AI 网关未配置，请先在首页「配置 AI 网关」里填入 API Key");
        }

        String body = buildRequestBody(cfg.model(), messages, toolsJson);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.baseUrl() + "/v1/chat/completions"))
                // 实测推理型模型生成一份日报要 50s+，原来 60s 太贴边，
                // 一次网络抖动就会误报失败，所以放宽到 120s
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15)).build()) {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new AiUnavailableException(explain(resp.statusCode()));
            }
            return parseReply(resp.body());
        } catch (IOException e) {
            throw new AiUnavailableException("AI 调用网络异常：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 调用被中断");
        }
    }

    /**
     * 把 HTTP 状态码翻成人话。
     * 原先是「AI 调用失败 HTTP 401」，用户看不出该改什么，等于没说。
     */
    private static String explain(int status) {
        return switch (status) {
            case 401, 403 -> "AI 调用失败 HTTP " + status + "：API Key 无效或过期，请重新填写";
            case 402 -> "AI 调用失败 HTTP 402：账户余额不足";
            case 404 -> "AI 调用失败 HTTP 404：Base URL 或模型名不对，请检查这两项";
            case 429 -> "AI 调用失败 HTTP 429：调用过于频繁或超出配额";
            default -> status >= 500
                    ? "AI 调用失败 HTTP " + status + "：对方服务异常，稍后再试"
                    : "AI 调用失败 HTTP " + status;
        };
    }

    private String buildRequestBody(String model, List<ChatMessage> messages, String toolsJson) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", model);
        ArrayNode arr = root.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode node = arr.addObject();
            node.put("role", m.role());
            // content 允许为 null：assistant 的 tool_calls 消息有时不带正文，
            // 塞空字符串进去反而可能让模型误解为「已作答」。
            node.put("content", m.content());
            // 协议硬性要求：tool 结果消息必须带 tool_call_id，指回它回应的那次调用
            if (m.toolCallId() != null) {
                node.put("tool_call_id", m.toolCallId());
            }
            // 回放历史里的 assistant 调用意图。少了这一段，后面的 tool 消息
            // 就变成「回应一次不存在的调用」，DeepSeek 直接 400。
            if (m.toolCallsJson() != null && !m.toolCallsJson().isBlank()) {
                node.set("tool_calls", JSON.readTree(m.toolCallsJson()));
            }
        }
        if (toolsJson != null && !toolsJson.isBlank()) {
            root.set("tools", JSON.readTree(toolsJson));
        }
        root.put("temperature", 0.7);
        root.put("stream", false);
        return root.toString();
    }

    /** 解析 chat/completions 响应，取出正文与工具调用意图 */
    private ChatReply parseReply(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (Exception e) {
            throw new AiUnavailableException("AI 返回的不是合法 JSON，无法解析：" + firstChars(json));
        }

        // 有的兼容服务会把错误包在 200 里，先看一层
        JsonNode errNode = root.path("error");
        if (!errNode.isMissingNode() && !errNode.isNull()) {
            String msg = errNode.path("message").asText("未知错误");
            throw new AiUnavailableException("AI 服务返回错误：" + msg);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiUnavailableException("AI 响应里没有 choices，无法解析：" + firstChars(json));
        }
        JsonNode message = choices.get(0).path("message");

        String content = null;
        JsonNode c = message.path("content");
        if (!c.isMissingNode() && !c.isNull()) {
            content = c.asText();
        }

        List<ToolCall> calls = new ArrayList<>();
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            for (JsonNode tc : toolCalls) {
                String id = tc.path("id").asText("");
                JsonNode fn = tc.path("function");
                String name = fn.path("name").asText("");
                String args = fn.path("arguments").asText("{}");
                if (!name.isBlank()) {
                    calls.add(new ToolCall(id, name, args));
                }
            }
        }
        return new ChatReply(content, calls);
    }

    private static String firstChars(String s) {
        if (s == null) return "(空)";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
