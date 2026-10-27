package com.myos.admin;

import com.myos.common.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 管理员判定。
 *
 * 单独抽出来而不是在每个接口里写 SQL：
 *  · 判定口径只有一处，不会出现「这个接口漏了校验」的越权口子
 *  · 加新管理接口时忘带检查，会在 code review 时一眼看出
 *
 * ⚠️ 每次请求都查库而不是把角色塞进令牌：
 *    令牌有效期 30 天，如果把角色编码进令牌，撤销管理员身份要等到令牌过期才生效。
 *    几个人量级的查询成本可以忽略，正确性优先。
 */
@Component
public class AdminGuard {

    private final JdbcTemplate jdbc;

    public AdminGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前请求者是否管理员 */
    public boolean isAdmin() {
        Long uid = CurrentUser.idOrNull();
        if (uid == null) return false;
        Integer n = jdbc.queryForObject(
                "select count(*) from my_user where id = ? and role = 'ADMIN' and status = 'ACTIVE'",
                Integer.class, uid);
        return n != null && n > 0;
    }

    /** 指定用户是否管理员（用于「不能停用/降级最后一个管理员」这类保护） */
    public boolean isAdmin(Long userId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from my_user where id = ? and role = 'ADMIN'", Integer.class, userId);
        return n != null && n > 0;
    }

    /** 有效管理员总数。用来阻止把最后一个管理员停掉，导致再无人能管用户 */
    public int activeAdminCount() {
        Integer n = jdbc.queryForObject(
                "select count(*) from my_user where role = 'ADMIN' and status = 'ACTIVE'", Integer.class);
        return n == null ? 0 : n;
    }
}
