package com.myos.review;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReviewMapper {

    List<Review> findByType(@Param("userId") Long userId,
                            @Param("type") String type,
                            @Param("limit") int limit);

    Review findByDate(@Param("userId") Long userId,
                      @Param("type") String type,
                      @Param("date") LocalDate date);

    /** upsert 并返回该行主键（SQL 用 RETURNING id；命中冲突时返回已存在那行的 id） */
    long upsert(@Param("userId") Long userId, @Param("r") Review review);

    int updateAiSummary(@Param("userId") Long userId,
                        @Param("id") Long id,
                        @Param("summary") String summary);

    long countByType(@Param("userId") Long userId, @Param("type") String type);
}
