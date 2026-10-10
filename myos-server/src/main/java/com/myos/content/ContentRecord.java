package com.myos.content;

import java.time.LocalDateTime;

/**
 * 一条内容输出。
 *
 * quote：本篇的一句话提炼（语录），可选。
 * 它属于这篇文章，不是独立的语录库 —— 写的时候顺手记一句，
 * 看列表时以引用样式回显。
 */
public record ContentRecord(Long id, String title, String platform, String content,
                            // 本篇输出的一句话提炼（语录），可选
                            String quote,
                            String status,
                            LocalDateTime publishedAt, Integer views, Integer likes) {

    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public ContentRecord withId(Long id) {
        return new ContentRecord(id, title, platform, content, quote, status, publishedAt, views, likes);
    }
}
