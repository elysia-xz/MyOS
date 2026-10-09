package com.myos.auth;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
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

    private final JdbcTemplate jdbc;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbc, TokenService tokenService) {
        this.jdbc = jdbc;
        this.tokenService = tokenService;
        ensureDefaultUser();
    }

    /** 若默认账号不存在则创建（密码 admin123，bcrypt 存储） */
    private void ensureDefaultUser() {
        try {
            Integer cnt = jdbc.queryForObject(
                    "select count(*) from my_user where username = 'admin'", Integer.class);
            if (cnt != null && cnt == 0) {
                jdbc.update("""
                        insert into my_user(username, email, password_hash, nickname)
                        values ('admin', 'admin@myos.local', ?, 'Troy')
                        """, encoder.encode("admin123"));
            }
        } catch (Exception ignored) {
            // 表尚未初始化时不阻塞应用启动
        }
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
