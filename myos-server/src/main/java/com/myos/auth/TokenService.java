package com.myos.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 登录令牌服务 —— 多端鉴权的核心。
 *
 * 设计取舍（对应 PRD 多端就绪改造）：
 *  - 采用**不透明令牌**而非 JWT：零新增依赖，且登出可以真正失效（删库即可），
 *    JWT 要做到同样效果反而需要额外维护黑名单。
 *  - 数据库只存令牌的 SHA-256 摘要，**不存明文**：即使库被导出也无法直接冒用登录态。
 *  - 同一账号可签发多个令牌（多端各自一份），互不影响，登出只影响当前设备。
 */
@Service
public class TokenService {

    /** 令牌有效期：30 天。个人工具场景下避免频繁重登 */
    private static final Duration TTL = Duration.ofDays(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public TokenService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 签发新令牌。
     *
     * @param userId 用户 ID
     * @param device 设备标识（web / miniprogram / desktop / app），仅用于管理端展示与区分
     * @return 明文令牌——**只在这一次返回**，之后无法再取回
     */
    public String issue(Long userId, String device) {
        String raw = randomToken();
        jdbc.update("""
                insert into user_token(user_id, token_hash, device, expires_at)
                values (?, ?, ?, ?)
                """,
                userId,
                sha256(raw),
                device == null || device.isBlank() ? "unknown" : device,
                LocalDateTime.now().plus(TTL));
        return raw;
    }

    /**
     * 校验令牌并返回对应用户 ID。
     *
     * @return 有效则返回 userId；无效、过期或不存在返回 null
     */
    public Long resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String hash = sha256(rawToken);
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("""
                select user_id from user_token
                where token_hash = ? and expires_at > CURRENT_TIMESTAMP
                """, hash);
        if (rows.isEmpty()) {
            return null;
        }
        Long userId = ((Number) rows.get(0).get("user_id")).longValue();

        // 记录最近使用时间。失败不影响鉴权主流程，忽略异常。
        try {
            jdbc.update("update user_token set last_used_at = CURRENT_TIMESTAMP where token_hash = ?", hash);
        } catch (Exception ignored) {
            // 统计性字段，写失败不阻断请求
        }
        return userId;
    }

    /** 撤销单个令牌（登出当前设备） */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        jdbc.update("delete from user_token where token_hash = ?", sha256(rawToken));
    }

    /** 撤销某用户全部令牌（改密码等场景） */
    public void revokeAll(Long userId) {
        jdbc.update("delete from user_token where user_id = ?", userId);
    }

    /**
     * 撤销某用户**除当前令牌外**的全部令牌，返回撤销数量。
     *
     * 用在改密码之后：密码可能泄露时，这是「把别人踢下线」的补救手段；
     * 但当前这台设备要保留，否则用户改完密码立刻被踢回登录页，
     * 会以为改密码失败。
     *
     * @param rawToken 当前请求携带的明文令牌；为空时退化为撤销全部
     */
    public int revokeOthers(Long userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            // 拿不到当前令牌就保守处理：全部撤销，让用户重新登录一次
            Integer n = jdbc.queryForObject(
                    "select count(*) from user_token where user_id = ?", Integer.class, userId);
            revokeAll(userId);
            return n == null ? 0 : n;
        }
        return jdbc.update("delete from user_token where user_id = ? and token_hash <> ?",
                userId, sha256(rawToken));
    }

    /** 清理已过期令牌，供启动或定时任务调用 */
    public int purgeExpired() {
        return jdbc.update("delete from user_token where expires_at <= CURRENT_TIMESTAMP");
    }

    /** 令牌到期时间（返回给前端展示） */
    public LocalDateTime expiresAt() {
        return LocalDateTime.now().plus(TTL);
    }

    // ---------------- 内部工具 ----------------

    /** 生成 32 字节随机令牌，Base64 URL 安全编码，无填充 */
    private static String randomToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** 计算 SHA-256 十六进制摘要 */
    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走到这里说明运行环境异常
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
