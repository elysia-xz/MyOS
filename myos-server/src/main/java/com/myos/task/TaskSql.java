package com.myos.task;

/**
 * 「今日任务」的 SQL 口径，单一来源。
 *
 * 为什么要有这个类：这段 WHERE 条件原来在
 * TaskMapper.xml、DashboardController、AiController 三处各抄了一份。
 * 改成「今天到期 + 逾期未完成」时如果只动其中一处，另外两处会静默失配 ——
 * 「今日」页变了、首页完成率没变、AI 还在按旧口径汇报「任务完成 0/0」。
 * 这类"同一条规则抄三遍"的写法，漏改一处在编译期毫无提示。
 *
 * 口径：今天到期 + 逾期未完成；已完成的历史任务不出现，
 * 否则所有做过的任务会天天重复刷屏。
 */
public final class TaskSql {

    private TaskSql() {}

    /** JdbcTemplate 版（DashboardController / AiController 用） */
    public static final String SCOPE_TODAY =
            "(due_date = ? OR due_date IS NULL OR (due_date < ? AND status <> 'DONE'))";

    /** MyBatis 版，占位符不同但条件必须与上面完全一致（TaskMapper.xml 用） */
    public static final String SCOPE_TODAY_MYBATIS =
            "(due_date = #{date} OR due_date IS NULL OR (due_date < #{date} AND status <> 'DONE'))";

    /** 排序：逾期项排最前 —— 逾期的事比今天新安排的事更紧急 */
    public static final String ORDER =
            "CASE WHEN due_date < ? AND status <> 'DONE' THEN 0 ELSE 1 END";
}
