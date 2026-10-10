package com.myos.goal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoalMapper {

    List<Goal> findByUserId(@Param("userId") Long userId);

    List<Goal> findByLevel(@Param("userId") Long userId, @Param("level") String level);

    /** 插入并返回自增主键（SQL 用 RETURNING id） */
    long insert(@Param("userId") Long userId, @Param("g") Goal goal);

    int updateProgress(@Param("userId") Long userId,
                       @Param("id") Long id,
                       @Param("progress") int progress);

    int updateStatus(@Param("userId") Long userId,
                     @Param("id") Long id,
                     @Param("status") String status);

    int delete(@Param("userId") Long userId, @Param("id") Long id);
}
