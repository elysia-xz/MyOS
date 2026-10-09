package com.myos.ai;

/** AI 网关不可用或调用失败 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }
}
