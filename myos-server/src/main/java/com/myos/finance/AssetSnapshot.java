package com.myos.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 资产配置快照 —— 对应 asset_snapshot 表 */
public record AssetSnapshot(
        Long id,
        LocalDate snapshotMonth,
        BigDecimal cash,
        BigDecimal cnyAssets,
        BigDecimal usdAssets,
        BigDecimal otherAssets,
        String note
) {
    public BigDecimal total() {
        return nz(cash).add(nz(cnyAssets)).add(nz(usdAssets)).add(nz(otherAssets));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
