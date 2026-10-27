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

    /**
     * 强制把默认账号口令重置为文档值（一次性救急开关）。
     *
     * 存在理由：库里出现「格式合法但内容错误」的 bcrypt 时，登录必然失败，
     * 而下面的自愈逻辑按设计**不会**覆盖它（怕推翻用户自己改的密码）——
     * 于是没有任何自救路径，只能手写 SQL 生成哈希。
     * 用 MYOS_AUTH_RESET_DEFAULT=true 起一次后端即可复位，用完去掉即可。
     */
    private final boolean forceResetDefaultPassword;

    public AuthController(JdbcTemplate jdbc, TokenService tokenService,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${myos.auth.reset-default:false}") boolean forceResetDefaultPassword) {
        this.jdbc = jdbc;
        this.tokenService = tokenService;
        this.forceResetDefaultPassword = forceResetDefaultPassword;
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
            if (!isBcryptHash(hash) || forceResetDefaultPassword) {
                String before = hash == null ? "(空)" : hash.length() > 24 ? hash.substring(0, 24) + "…" : hash;
                jdbc.update("update my_user set password_hash = ? where username = ?",
                        encoder.encode(DEFAULT_PASSWORD), DEFAULT_USERNAME);
                log.warn("默认账号 {} 的口令哈希{}（原值 {}），已重置为文档值。"
                                + "若这是你自己改过的密码被误判，请检查 isBcryptHash 的判定",
                        DEFAULT_USERNAME,
                        forceResetDefaultPassword ? "被 MYOS_AUTH_RESET_DEFAULT 强制重置" : "不是合法 bcrypt",
                        before);
            } else if (!encoder.matches(DEFAULT_PASSWORD, hash)) {
                // ⚠️ 这个分支是真实踩过的坑：哈希格式合法、但谁都不匹配。
                // 旧代码只判格式，于是这种值被静默尊重 → 登录页永远「用户名或密码错误」，
                // 日志里一个字都没有，排查全靠猜（本次就是这么卡住的）。
                // 不自动覆盖是对的（可能是用户自己改的密码），但必须留下线索 + 自救路径。
                log.warn("默认账号 {} 的口令不是文档值 {}（哈希格式合法但校验不通过）。"
                                + "若这是你自己改过的密码请忽略；若登录不上，"
                                + "用环境变量 MYOS_AUTH_RESET_DEFAULT=true 重启后端一次即可复位。",
                        DEFAULT_USERNAME, DEFAULT_PASSWORD);
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
                select id, username, password_hash, nickname, status, role from my_user where username = ?
                """, request.username());
        if (rows.isEmpty()) {
            return ApiResponse.fail("用户名或密码错误");
        }
        Map<String, Object> user = rows.get(0);

        // ⚠️ status 字段早在建表时就有，但登录从来没检查过 ——
        // 于是「停用账号」此前等于摆设：停用后照样能登录。
        // 多用户场景下这是必须的（测试结束要能收回某人的访问权）。
        Object status = user.get("status");
        if (status != null && !"ACTIVE".equals(status.toString())) {
            return ApiResponse.fail("该账号已停用，请联系管理员");
        }

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
        var rows = jdbc.queryForList(
                "select id, username, nickname, role from my_user where id = ?", uid);
        if (rows.isEmpty()) {
            return ApiResponse.fail("用户不存在");
        }
        return ApiResponse.ok(userOf(rows.get(0)));
    }

    /**
     * 修改自己的密码。
     *
     * 为什么必须验旧密码：令牌是长期有效的（30 天），
     * 如果只看令牌就能改密码，那么令牌一旦泄露（共用电脑、被拷走 localStorage），
     * 攻击者可以直接改密码把本人锁在门外。验旧密码能把危害限制在「已登录态可读」。
     *
     * 多用户场景下这是刚需：几个人各自持有初始密码，必须能自己换成自己的。
     */
    @PostMapping("/password")
    public ApiResponse<?> changePassword(@RequestBody ChangePasswordRequest req) {
        Long uid = CurrentUser.id();

        if (req.newPassword() == null || req.newPassword().length() < 8) {
            return ApiResponse.fail("新密码至少 8 位");
        }
        if (req.newPassword().equals(req.oldPassword())) {
            return ApiResponse.fail("新密码不能和原密码相同");
        }

        var rows = jdbc.queryForList("select password_hash from my_user where id = ?", uid);
        if (rows.isEmpty()) {
            return ApiResponse.fail("用户不存在");
        }
        String hash = (String) rows.get(0).get("password_hash");
        if (hash == null || !encoder.matches(req.oldPassword(), hash)) {
            return ApiResponse.fail("原密码不正确");
        }

        jdbc.update("update my_user set password_hash = ?, updated_at = current_timestamp where id = ?",
                encoder.encode(req.newPassword()), uid);

        // 改完密码撤销其它设备的令牌：这是密码可能泄露时的补救手段。
        // 当前这台设备的令牌保留，否则用户改完立刻被踢出去，体验很怪。
        int revoked = tokenService.revokeOthers(uid, bearerFromContext());
        log.info("用户 {} 修改了密码，已撤销其它设备 {} 个令牌", uid, revoked);

        return ApiResponse.ok(java.util.Map.of("revokedTokens", revoked));
    }

    /** 从当前请求取令牌（改密码后要用它排除「本机」） */
    private static String bearerFromContext() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            return bearer(sra.getRequest());
        }
        return null;
    }

    private static Map<String, Object> userOf(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("username", row.get("username"));
        m.put("nickname", row.get("nickname") == null ? "" : row.get("nickname"));
        // 角色只用于前端决定是否显示「用户管理」入口，真正的权限校验在后端 AdminGuard。
        // 前端隐藏入口不是安全边界，只是不让普通用户看到一个点了就报错的按钮。
        Object role = row.get("role");
        m.put("role", role == null ? "USER" : role.toString());
        m.put("admin", "ADMIN".equals(role == null ? null : role.toString()));
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

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
}
