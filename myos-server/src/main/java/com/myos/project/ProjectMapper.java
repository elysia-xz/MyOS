package com.myos.project;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {

    List<Project> findByUserId(@Param("userId") Long userId);

    Project findById(@Param("userId") Long userId, @Param("id") Long id);

    /** 插入并返回自增主键（SQL 用 RETURNING id） */
    long insert(@Param("userId") Long userId, @Param("project") Project project);

    int update(@Param("userId") Long userId, @Param("project") Project project);

    int updateProgress(@Param("userId") Long userId, @Param("id") Long id, @Param("progress") int progress);

    int delete(@Param("userId") Long userId, @Param("id") Long id);

    /** 归属校验：写接口必须先确认这个 project 是当前用户的，不能靠前端给的 id */
    boolean existsForUser(@Param("userId") Long userId, @Param("id") Long id);

    // ---- 开发日志 ----

    List<ProjectLog> findLogs(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /** 插入并返回自增主键（SQL 用 RETURNING id） */
    long insertLog(@Param("userId") Long userId, @Param("log") ProjectLog log);
}
