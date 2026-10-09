package com.myos.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置 —— 多端就绪改造后新增 CORS。
 *
 * 为什么需要：Web 同源部署时不需要 CORS，但小程序、桌面端、原生 App
 * 以及独立域名的前端都会跨域调用，没有这段配置会在浏览器/客户端侧被直接拦掉。
 *
 * 关于凭据：改用 Bearer 令牌后不再依赖 Cookie，因此 allowCredentials 保持 false，
 * 这样可以安全地放开来源（令牌由客户端显式携带，不依赖浏览器自动附带）。
 * 来源可用 myos.cors.allowed-origins 覆盖，默认 * 便于本地多端联调。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Value("${myos.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
