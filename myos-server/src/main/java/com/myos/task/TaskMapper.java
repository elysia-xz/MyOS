package com.myos.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TaskMapper {
    List<Task> findByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);
    int insert(@Param("userId") Long userId, @Param("task") Task task);
    int updateStatus(@Param("userId") Long userId, @Param("id") Long id, @Param("status") String status);
}
