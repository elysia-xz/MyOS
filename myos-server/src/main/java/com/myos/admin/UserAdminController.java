package com.myos.admin;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理（仅管理员）。
 *
 * 为什么需要它：加人只能敲命令，对长期用不现实 ——
 * 每次都要开终端、切目录、记参数，而且看不到「现在有几个人在用」。
 *
 * 权限模型刻意保持最简：
 *   · 只有一个角色 ADMIN，其余都是 USER
 *   · 不做「授予管理员」的界面 —— 你们只有几个人，多管理员是过度设计
 *   · 不做删除用户，只做停用：删用户会连带删掉他的全部数据，
 *     而误删无法撤销。停用能立即收回访问权，且随时可以恢复
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private static final Logger log = LoggerFactory.getLogger(UserAdminController.class);

    private final JdbcTemplate jdbc;
    private final AdminGuard guard;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserAdminController(JdbcTemplate jdbc, AdminGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    /** 用户列表（不含口令哈希，永远不下发） */
    @GetMapping
    public ApiResponse<?> list() {
        if (!guard.isAdmin()) return ApiResponse.fail("仅管理员可查看用户列表");

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select u.id, u.username, u.nickname, u.email, u.role, u.status, u.created_at,
                       (select count(*) from user_token t
                         where t.user_id = u.id and t.expires_at > current_timestamp) as active_tokens
                from my_user u
                order by u.id
                """);

        // 用 select 拼好再返回（不用 select *）：口令哈希绝不能出现在响应里，
        // 哪怕它已经加密了 —— 没有任何前端逻辑需要它
        List<Map<String, Object>> out = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("username", r.get("username"));
            m.put("nickname", r.get("nickname") == null ? "" : r.get("nickname"));
            m.put("role", r.get("role"));
            m.put("status", r.get("status"));
            m.put("createdAt", r.get("created_at"));
            m.put("activeTokens", r.get("active_tokens"));
            return m;
        }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("users", out);
        payload.put("total", out.size());
        return ApiResponse.ok(payload);
    }

    /**
     * 建号。
     *
     * 密码两种来源：
     *   · 管理员指定（AdminCreateRequest.password）
     *   · 不指定则生成 12 位强随机，响应里带一次 plainPassword 供转交
     * 无论哪种，库里只存 bcrypt。
     */
    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest req) {
        if (!guard.isAdmin()) return ApiResponse.fail("仅管理员可创建用户");

        String username = req.username() == null ? "" : req.username().trim();
        if (!username.matches("^[A-Za-z0-9_]{3,32}$")) {
            return ApiResponse.fail("用户名只能是 3-32 位字母、数字或下划线");
        }

        Integer exists = jdbc.queryForObject(
                "select count(*) from my_user where username = ?", Integer.class, username);
        if (exists != null && exists > 0) {
            return ApiResponse.fail("用户名已存在");
        }

        boolean generated = req.password() == null || req.password().isBlank();
        String plain = generated ? randomPassword() : req.password();
        if (plain.length() < 8) {
            return ApiResponse.fail("密码至少 8 位");
        }

        String nickname = req.nickname() == null || req.nickname().isBlank()
                ? username : req.nickname().trim();

        Long id = jdbc.queryForObject("""
                insert into my_user(username, email, password_hash, nickname, role, status)
                values (?, null, ?, ?, 'USER', 'ACTIVE')
                returning id
                """, Long.class, username, encoder.encode(plain), nickname);

        log.info("管理员 {} 创建了用户 {}（id={}）", CurrentUser.id(), username, id);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("username", username);
        out.put("nickname", nickname);
        // 只在「系统生成」时回传一次明文；管理员自己指定密码则原样已知
        out.put("plainPassword", generated ? plain : null);
        out.put("passwordGenerated", generated);
        return ApiResponse.ok(out);
    }

    /**
     * 启停账号。
     *
     * 停用后登录直接拒绝、已签发的令牌也一并撤销 ——
     * 只拦登录不撤令牌是常见疏漏：对方开着页面还能继续用到令牌过期。
     */
    @PutMapping("/{id}/status")
    public ApiResponse<?> setStatus(@PathVariable Long id, @RequestParam String status) {
        if (!guard.isAdmin()) return ApiResponse.fail("仅管理员可修改用户状态");

        String s = status == null ? "" : status.toUpperCase();
        if (!s.equals("ACTIVE") && !s.equals("DISABLED")) {
            return ApiResponse.fail("状态只能是 ACTIVE 或 DISABLED");
        }

        // 不能停用自己：会把自己锁在门外
        if (id.equals(CurrentUser.id()) && s.equals("DISABLED")) {
            return ApiResponse.fail("不能停用自己的账号");
        }

        Long target = findById(id);
        if (target == null) return ApiResponse.fail("用户不存在");

        // 不能停用最后一个有效管理员：否则再没人能管用户
        if (s.equals("DISABLED") && guard.isAdmin(id) && guard.activeAdminCount() <= 1) {
            return ApiResponse.fail("这是最后一个管理员，不能停用");
        }

        jdbc.update("update my_user set status = ?, updated_at = current_timestamp where id = ?", s, id);

        int revoked = 0;
        if (s.equals("DISABLED")) {
            revoked = jdbc.update("delete from user_token where user_id = ?", id);
        }

        log.info("管理员 {} 将用户 {} 状态改为 {}，撤销令牌 {} 个", CurrentUser.id(), id, s, revoked);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", s);
        out.put("revokedTokens", revoked);
        return ApiResponse.ok(out);
    }

    /**
     * 重置密码（对方忘记密码时管理员操作）。
     * 总是生成随机密码，不让管理员自己指定：
     * 管理员能设任意密码意味着他能登进别人账号，隐私边界就没了。
     */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<?> resetPassword(@PathVariable Long id) {
        if (!guard.isAdmin()) return ApiResponse.fail("仅管理员可重置密码");

        Long target = findById(id);
        if (target == null) return ApiResponse.fail("用户不存在");

        String plain = randomPassword();
        jdbc.update("update my_user set password_hash = ?, updated_at = current_timestamp where id = ?",
                encoder.encode(plain), id);

        // 重置后撤销该用户全部令牌：密码可能已泄露，旧会话不该继续有效
        int revoked = jdbc.update("delete from user_token where user_id = ?", id);

        log.info("管理员 {} 重置了用户 {} 的密码，撤销令牌 {} 个", CurrentUser.id(), id, revoked);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plainPassword", plain);
        out.put("revokedTokens", revoked);
        return ApiResponse.ok(out);
    }

    private Long findById(Long id) {
        List<Long> ids = jdbc.queryForList("select id from my_user where id = ?", Long.class, id);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 删除用户 —— 仅当该用户**没有任何业务数据**时允许。
     *
     * 为什么限制这么死：库里绝大多数表对 my_user 的外键是 NO ACTION，
     * 有数据时删除会直接被外键拦住（变成 500）。与其让用户撞一脸「服务器内部错误」，
     * 不如提前查清楚并给出可操作的提示。
     *
     * 真实用途是两个：
     *  · 建错号了要清掉
     *  · 冒烟测试跑完清理测试账号
     * 正常「某人不用了」请用停用：数据留着，万一以后要看。
     *
     * 前端刻意不放这个按钮：删除是不可逆操作，不值得为省一次操作而暴露在界面上。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        if (!guard.isAdmin()) return ApiResponse.fail("仅管理员可删除用户");
        if (id.equals(CurrentUser.id())) return ApiResponse.fail("不能删除自己的账号");
        if (guard.isAdmin(id)) return ApiResponse.fail("不能删除管理员账号");

        Long target = findById(id);
        if (target == null) return ApiResponse.fail("用户不存在");

        Integer rows = jdbc.queryForObject("""
                select (select count(*) from task           where user_id = ?)
                     + (select count(*) from project        where user_id = ?)
                     + (select count(*) from goal           where user_id = ?)
                     + (select count(*) from content_record where user_id = ?)
                     + (select count(*) from learning_record where user_id = ?)
                     + (select count(*) from habit_log      where user_id = ?)
                     + (select count(*) from finance_record where user_id = ?)
                     + (select count(*) from asset_snapshot where user_id = ?)
                     + (select count(*) from review         where user_id = ?)
                     + (select count(*) from daily_log      where user_id = ?)
                     + (select count(*) from knowledge_item where user_id = ?)
                     + (select count(*) from project_log    where user_id = ?)
                """, Integer.class, id, id, id, id, id, id, id, id, id, id, id, id);

        int n = rows == null ? 0 : rows;
        if (n > 0) {
            return ApiResponse.fail("该用户已有 " + n + " 条数据，不能删除。请改用停用");
        }

        jdbc.update("delete from user_token where user_id = ?", id);
        jdbc.update("delete from my_user where id = ?", id);
        log.info("管理员 {} 删除了空账号 {}", CurrentUser.id(), id);
        return ApiResponse.ok(true);
    }

    /**
     * 生成 12 位随机密码。
     * 排除 0/O/1/l/I 这些手抄、口头转述时容易看错的字符 ——
     * 这类密码是要靠人传给人的，可读性直接影响对方能否一次登录成功。
     */
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RND = new SecureRandom();

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET[RND.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    public record CreateRequest(@NotBlank String username, String nickname, String password) {}
}
