package com.myos.content;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContentMapper {

    List<ContentRecord> findByUserId(@Param("userId") Long userId);

    /**
     * 某个项目产出的全部内容（项目详情页「本项目产出了什么」）。
     * 放在这里而不是 ProjectMapper：涉及 content_record 的查询集中在一处，
     * 免得同一条取数口径在两个 mapper 里各写一遍（逾期任务就吃过这个亏）。
     */
    List<ContentRecord> findByProject(@Param("userId") Long userId,
                                      @Param("projectId") Long projectId);

    /** 插入并返回自增主键（SQL 用 RETURNING id，见 ContentMapper.xml 注释） */
    long insert(@Param("userId") Long userId, @Param("record") ContentRecord record);

    int update(@Param("userId") Long userId, @Param("id") Long id,
               @Param("title") String title, @Param("platform") String platform,
               @Param("content") String content, @Param("quote") String quote,
               @Param("projectId") Long projectId);

    int updateStatus(@Param("userId") Long userId, @Param("id") Long id,
                     @Param("status") String status);

    int delete(@Param("userId") Long userId, @Param("id") Long id);

    /** 按天统计发布数量（供打卡日历） */
    List<Map<String, Object>> countByDay(@Param("userId") Long userId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /**
     * 校验项目确实属于当前用户。
     * 不加这层的话：projectId 传别人的项目 id 会给别人的项目挂内容，
     * 传不存在的 id 会直接撞外键约束、接口 500（用户看到的是「服务器内部错误」）。
     */
    int countOwnedProject(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
