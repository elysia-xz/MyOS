package com.myos.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TaskMapper {
    List<Task> findByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);
    /** 某个项目下的全部任务 —— 项目详情页据此展示进度构成 */
    List<Task> findByProject(@Param("userId") Long userId, @Param("projectId") Long projectId);
    /** 插入并返回自增主键（SQL 用 RETURNING id） */
    long insert(@Param("userId") Long userId, @Param("task") Task task);
    int updateStatus(@Param("userId") Long userId, @Param("id") Long id, @Param("status") String status);
    /** 改归属项目；projectId 传 null 表示脱离项目 */
    int updateProject(@Param("userId") Long userId, @Param("id") Long id, @Param("projectId") Long projectId);
    int deleteById(@Param("userId") Long userId, @Param("id") Long id);
}
