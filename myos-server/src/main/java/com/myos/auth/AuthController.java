package com.myos.auth;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口 —— 多端就绪改造版。
 *
 * 与旧版的区别：
 *  - 登录不再写 Session，而是签发 Bearer 令牌，由前端/客户端自行保存并随请求携带。
 *    这是小程序、桌面端、原生 App 能接入的前提。
 *  - 登出撤销当前设备的令牌，其他设备不受影响。
 *  - 密码仍为 bcrypt 校验，不落明文。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JdbcTemplate jdbc;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbc, TokenService tokenService) {
        this.jdbc = jdbc;
        this.tokenService = tokenService;
        ensureDefaultUser();
    }

    /** 默认演示账号的口令。文档、README、种子数据都以它为准。 */
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    /**
     * 确保默认演示账号存在，且口令哈希是**合法的 bcrypt**。
     *
     * 为什么不在 SQL 里写死 bcrypt 哈希：
     *   bcrypt 哈希由「算法版本 + 随机盐」决定，写进种子脚本后一旦对不上，
     *   现象是登录页永远提示「用户名或密码错误」——没有任何报错线索，
     *   排查成本极高（这个坑已经真实踩过一次：种子里的哈希是占位符/示例值）。
     *   所以把「默认账号口令 = admin123」这件事交给后端在启动时保证，
     *   发现对不上就纠正并打 WARN，让它自愈而不是静默失效。
     *
     * ⚠️ 纠正的条件是「哈希非法」，不是「哈希不等于 admin123」。
     *    曾经写成 matches(DEFAULT_PASSWORD, hash) 不成立就重置 —— 那样用户
     *    一旦把密码改成强密码，下次重启就会被悄悄改回 admin123，
     *    既推翻用户的改动又毫无提示。合法的 bcrypt 哈希一律尊重，
     *    只有非 bcrypt 格式（种子里的占位符、明文、空值）才覆盖。
     */
    private void ensureDefaultUser() {
        try {
            var rows = jdbc.queryForList(
                    "select id, password_hash from my_user where username = ?", DEFAULT_USERNAME);

            if (rows.isEmpty()) {
                jdbc.update("""
                        insert into my_user(username, email, password_hash, nickname)
                        values (?, 'admin@myos.local', ?, 'Troy')
                        """, DEFAULT_USERNAME, encoder.encode(DEFAULT_PASSWORD));
                log.info("已创建默认账号 {}（口令见 README）", DEFAULT_USERNAME);
                return;
            }

            Object stored = rows.get(0).get("password_hash");
            String hash = stored == null ? null : stored.toString();
            if (!isBcryptHash(hash)) {
                String before = hash == null ? "(空)" : hash.length() > 24 ? hash.substring(0, 24) + "…" : hash;
                jdbc.update("update my_user set password_hash = ? where username = ?",
                        encoder.encode(DEFAULT_PASSWORD), DEFAULT_USERNAME);
                log.warn("默认账号 {} 的口令哈希不是合法 bcrypt（原值 {}），已重置为文档值。"
                        + "若这是你自己改过的密码被误判，请检查 isBcryptHash 的判定",
                        DEFAULT_USERNAME, before);
            }
        } catch (Exception e) {
            // 表尚未初始化时不阻塞应用启动
            log.debug("跳过默认账号检查：{}", e.getMessage());
        }
    }

    /** 合法 bcrypt 哈希的形态：$2a$ / $2b$ / $2y$ 开头，后跟 cost 与 53 位 salt+digest。 */
    private static boolean isBcryptHash(String hash) {
        return hash != null && hash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    /**
     * 登录并签发令牌。
     *
     * @param request 账号密码，device 可选（web / miniprogram / desktop / app）
     */
    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        var rows = jdbc.queryForList("""
                select id, username, password_hash, nickname from my_user where username = ?
                """, request.username());
        if (rows.isEmpty()) {
            return ApiResponse.fail("用户名或密码错误");
        }
        Map<String, Object> user = rows.get(0);
        String hash = (String) user.get("password_hash");
        if (hash == null || !encoder.matches(request.password(), hash)) {
            return ApiResponse.fail("用户名或密码错误");
        }

        Long id = ((Number) user.get("id")).longValue();
        String token = tokenService.issue(id, request.device());
        LocalDateTime expiresAt = tokenService.expiresAt();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", token);
        out.put("expiresAt", expiresAt.toString());
        out.put("user", userOf(user));
        return ApiResponse.ok(out);
    }

    /** 登出：撤销当前请求携带的令牌 */
    @PostMapping("/logout")
    public ApiResponse<?> logout(HttpServletRequest request) {
        tokenService.revoke(bearer(request));
        return ApiResponse.ok(null);
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public ApiResponse<?> me() {
        Long uid = CurrentUser.idOrNull();
        if (uid == null) {
            return ApiResponse.fail("未登录");
        }
        var rows = jdbc.queryForList("select id, username, nickname from my_user where id = ?", uid);
        if (rows.isEmpty()) {
            return ApiResponse.fail("用户不存在");
        }
        return ApiResponse.ok(userOf(rows.get(0)));
    }

    private static Map<String, Object> userOf(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("username", row.get("username"));
        m.put("nickname", row.get("nickname") == null ? "" : row.get("nickname"));
        return m;
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    public record LoginRequest(String username, String password, String device) {}
}
