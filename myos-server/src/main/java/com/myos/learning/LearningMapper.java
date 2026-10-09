package com.myos.learning;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningMapper {

    List<LearningRecord> findByUserId(@Param("userId") Long userId);

    List<LearningRecord> findBySystem(@Param("userId") Long userId,
                                      @Param("systemCode") String systemCode);

    int insert(@Param("userId") Long userId, @Param("record") LearningRecord record);

    int delete(@Param("userId") Long userId, @Param("id") Long id);
}
