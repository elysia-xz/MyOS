package com.myos.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 记账流水 —— 对应 finance_record 表 */
public record FinanceRecord(
        Long id,
        LocalDate recordDate,
        String recordType,
        String category,
        BigDecimal amount,
        String currency,
        String note
) {
    /** 插入后回填自增主键（record 不可变，只能新建一条） */
    public FinanceRecord withId(Long id) {
        return new FinanceRecord(id, recordDate, recordType, category, amount, currency, note);
    }
}
