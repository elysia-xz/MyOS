package com.myos.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI 兼容协议的 AI 网关实现。
 *
 * V1 默认对接 DeepSeek（OpenAI 兼容），可通过环境变量切换任意兼容服务：
 *   MYOS_AI_BASE_URL  默认 https://api.deepseek.com
 *   MYOS_AI_MODEL     默认 deepseek-chat
 *   MYOS_AI_API_KEY   必填，缺失时网关标记为不可用
 */
@Component
public class OpenAiCompatGateway implements AiGateway {

    private final String baseUrl;
    private final String model;
    private final String apiKey;

    public OpenAiCompatGateway(
            @Value("${myos.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${myos.ai.model:deepseek-chat}") String model,
            @Value("${myos.ai.api-key:}") String apiKey) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String scene, String prompt) {
        if (!available()) {
            throw new AiUnavailableException("AI 网关未配置（缺少 MYOS_AI_API_KEY）");
        }
        String body = buildRequestBody(prompt);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15)).build()) {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new AiUnavailableException("AI 调用失败 HTTP " + resp.statusCode());
            }
            return extractContent(resp.body());
        } catch (IOException e) {
            throw new AiUnavailableException("AI 调用网络异常：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 调用被中断");
        }
    }

    private String buildRequestBody(String prompt) {
        // 极简 JSON 拼装，避免引入额外序列化依赖；prompt 经过转义
        return "{\"model\":\"" + escape(model) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escape(prompt) + "\"}],"
                + "\"temperature\":0.7,\"stream\":false}";
    }

    /** 从 OpenAI 兼容响应中抽取 content 字段 */
    private String extractContent(String json) {
        int idx = json.indexOf("\"content\"");
        if (idx < 0) return json;
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return json;
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(n);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
