package com.myos.finance;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface FinanceMapper {

    List<FinanceRecord> findRecords(@Param("userId") Long userId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /** 插入并返回自增主键（SQL 用 RETURNING id） */
    long insertRecord(@Param("userId") Long userId, @Param("r") FinanceRecord record);

    int deleteRecord(@Param("userId") Long userId, @Param("id") Long id);

    /** 区间合计：收入/支出 */
    List<Map<String, Object>> sumByType(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    BigDecimal sumByTypeValue(@Param("userId") Long userId,
                             @Param("type") String type,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to);

    /** 按分类聚合（支出构成） */
    List<Map<String, Object>> sumByCategory(@Param("userId") Long userId,
                                             @Param("type") String type,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /** 近 N 个月储蓄率趋势 */
    List<Map<String, Object>> monthlyTrend(@Param("userId") Long userId,
                                            @Param("months") int months);

    List<AssetSnapshot> findSnapshots(@Param("userId") Long userId);

    AssetSnapshot findSnapshotByMonth(@Param("userId") Long userId,
                                      @Param("month") LocalDate month);

    /** upsert 并返回该行主键（SQL 用 RETURNING id） */
    long upsertSnapshot(@Param("userId") Long userId, @Param("s") AssetSnapshot snapshot);
}
