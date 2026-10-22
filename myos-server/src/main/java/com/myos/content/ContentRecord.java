package com.myos.content;

import java.time.LocalDateTime;

/**
 * 一条内容输出。
 *
 * quote：本篇的一句话提炼（语录），可选。
 * 它属于这篇文章，不是独立的语录库 —— 写的时候顺手记一句，
 * 看列表时以引用样式回显。
 *
 * projectId / projectName：本篇输出归属哪个项目（可空）。
 * 这是「一次行动产生多个结果」那条链的关键一环 ——
 * 没有它，输出就是一串孤立记录，项目详情答不出「产出了什么」。
 * projectName 是连表带出来的展示字段，不落库。
 */
public record ContentRecord(Long id, String title, String platform, String content,
                            // 本篇输出的一句话提炼（语录），可选
                            String quote,
                            String status,
                            LocalDateTime publishedAt, Integer views, Integer likes,
                            // 归属项目；可为空（随手记录不必挂项目）
                            Long projectId,
                            // 仅供展示：来自 join project，不写回库
                            String projectName) {

    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public ContentRecord withId(Long id) {
        return new ContentRecord(id, title, platform, content, quote, status, publishedAt,
                views, likes, projectId, projectName);
    }
}
