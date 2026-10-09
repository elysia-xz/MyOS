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
) {}
