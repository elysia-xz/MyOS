package com.myos.common;

import com.myos.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器 —— 多端改造后由 Session 改为 Bearer 令牌。
 *
 * 变更要点：
 *  - 从 Authorization: Bearer &lt;token&gt; 解析登录态，不再依赖 Cookie/Session，
 *    使小程序、桌面端、原生 App 与 Web 共用同一套鉴权。
 *  - 校验通过后把 userId 写入请求属性，业务代码通过 {@link CurrentUser#id()} 读取。
 *  - 放行 OPTIONS 预检请求，交给 CORS 处理，否则跨域调用会在预检阶段就被 401 拦死。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 无需登录即可访问的路径前缀 */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/public/health"
    };

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 跨域预检请求不带业务令牌，必须放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        for (String pub : PUBLIC_PATHS) {
            if (uri.startsWith(pub)) {
                return true;
            }
        }

        Long userId = tokenService.resolve(extractBearer(request));
        if (userId == null) {
            unauthorized(response);
            return false;
        }
        request.setAttribute(CurrentUser.ATTR_USER_ID, userId);
        return true;
    }

    /** 从 Authorization 头解析 Bearer 令牌 */
    private static String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (header.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return header.substring(prefix.length()).trim();
        }
        return null;
    }

    private static void unauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"未登录或登录已过期\"}");
    }
}
