package com.myos.common;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前登录用户上下文。
 *
 * 多端改造后不再使用 HttpSession：由 {@link AuthInterceptor} 在请求进入时
 * 解析 Authorization: Bearer 令牌，把 userId 写入请求属性，
 * 业务代码统一通过 {@link #id()} 读取。
 *
 * 这样 Web / 小程序 / 桌面 / 原生 App 共用同一套鉴权逻辑。
 */
public final class CurrentUser {

    /** 请求属性名，AuthInterceptor 写入、业务代码读取，两边共用此常量 */
    public static final String ATTR_USER_ID = "myos.userId";

    private CurrentUser() {}

    /**
     * 取当前登录用户 ID。
     * 未登录时抛异常——正常流程下请求已被 AuthInterceptor 拦下，
     * 走到这里说明拦截器路径配置有遗漏，应当尽早暴露而不是静默返回 null。
     */
    public static Long id() {
        Long id = idOrNull();
        if (id == null) {
            throw new IllegalStateException("当前请求未携带有效登录态");
        }
        return id;
    }

    /** 取当前登录用户 ID，未登录返回 null */
    public static Long idOrNull() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        Object v = sra.getRequest().getAttribute(ATTR_USER_ID);
        return v instanceof Long l ? l : null;
    }
}
