package com.myos.habit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface HabitMapper {

    List<HabitLog> findByDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    List<HabitLog> findBetween(@Param("userId") Long userId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /** upsert：同一用户同一天同一系统唯一 */
    int upsert(@Param("userId") Long userId, @Param("log") HabitLog log);

    /** 统计某系统在区间内的累计值（用于 Lv 计算） */
    long sumBySystem(@Param("userId") Long userId,
                     @Param("systemCode") String systemCode,
                     @Param("from") LocalDate from,
                     @Param("to") LocalDate to);

    /** 统计某系统的达标天数 */
    long countDoneBySystem(@Param("userId") Long userId,
                           @Param("systemCode") String systemCode,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    /** 按月统计各系统达标天数（用于本月输出进度） */
    List<java.util.Map<String, Object>> monthlyStats(@Param("userId") Long userId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);
}
