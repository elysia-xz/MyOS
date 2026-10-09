package com.myos.content;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContentMapper {

    List<ContentRecord> findByUserId(@Param("userId") Long userId);

    int insert(@Param("userId") Long userId, @Param("record") ContentRecord record);

    int update(@Param("userId") Long userId, @Param("id") Long id,
               @Param("title") String title, @Param("platform") String platform,
               @Param("content") String content, @Param("quote") String quote);

    int updateStatus(@Param("userId") Long userId, @Param("id") Long id,
                     @Param("status") String status);

    int delete(@Param("userId") Long userId, @Param("id") Long id);

    /** 按天统计发布数量（供打卡日历） */
    List<Map<String, Object>> countByDay(@Param("userId") Long userId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}
