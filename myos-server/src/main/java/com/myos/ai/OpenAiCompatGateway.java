package com.myos.ai;

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
 * V1 默认对接 DeepSeek（OpenAI 兼容），可切换到任意兼容服务。
 * 配置从 {@link AiConfigService} 每次实时读取，因此界面上改完即时生效，无需重启后端
 * （之前这里是构造期 {@code @Value} 固化，改了环境变量必须重启）。
 */
@Component
public class OpenAiCompatGateway implements AiGateway {

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
        AiConfigService.Effective cfg = config.effective();
        if (AiConfigService.isBlank(cfg.apiKey())) {
            throw new AiUnavailableException("AI 网关未配置，请先在首页「配置 AI 网关」里填入 API Key");
        }

        String body = buildRequestBody(cfg.model(), prompt);
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
            return extractContent(resp.body());
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
            case 401, 403 -> "AI 调用失败 HTTP " + status + "：API Key 无效或已过期，请重新填写";
            case 402 -> "AI 调用失败 HTTP 402：账户余额不足";
            case 404 -> "AI 调用失败 HTTP 404：Base URL 或模型名不对，请检查这两项";
            case 429 -> "AI 调用失败 HTTP 429：调用过于频繁或超出配额";
            default -> status >= 500
                    ? "AI 调用失败 HTTP " + status + "：对方服务异常，稍后再试"
                    : "AI 调用失败 HTTP " + status;
        };
    }

    private String buildRequestBody(String model, String prompt) {
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
