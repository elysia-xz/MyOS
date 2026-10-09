package com.myos.project;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {

    List<Project> findByUserId(@Param("userId") Long userId);

    Project findById(@Param("userId") Long userId, @Param("id") Long id);

    int insert(@Param("userId") Long userId, @Param("project") Project project);

    int update(@Param("userId") Long userId, @Param("project") Project project);

    int updateProgress(@Param("userId") Long userId, @Param("id") Long id, @Param("progress") int progress);

    int delete(@Param("userId") Long userId, @Param("id") Long id);

    // ---- 开发日志 ----

    List<ProjectLog> findLogs(@Param("userId") Long userId, @Param("projectId") Long projectId);

    int insertLog(@Param("userId") Long userId, @Param("log") ProjectLog log);
}
