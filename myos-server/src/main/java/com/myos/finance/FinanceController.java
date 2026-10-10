package com.myos.finance;

import com.myos.common.ApiResponse;
import com.myos.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务模块 —— 对应 PRD §6.5。
 *
 * 三个层次严格分开：
 *   ① 现金流  收入 - 支出 = 结余
 *   ② 储蓄率  结余 / 收入
 *   ③ 资产配置 现金 / 人民币 / 美元 / 其他
 *
 * 红线：只做记录、统计、提醒、复盘，不做任何投资决策。
 */
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private final FinanceMapper mapper;

    public FinanceController(FinanceMapper mapper) {
        this.mapper = mapper;
    }

    // ---------------- ① 现金流 ----------------

    @GetMapping("/records")
    public ApiResponse<?> records(@RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) {
        LocalDate f = from == null ? YearMonth.now().atDay(1) : from;
        LocalDate t = to == null ? YearMonth.now().atEndOfMonth() : to;
        return ApiResponse.ok(mapper.findRecords(CurrentUser.id(), f, t));
    }

    @PostMapping("/records")
    public ApiResponse<?> addRecord(@Valid @RequestBody RecordRequest req) {
        FinanceRecord r = new FinanceRecord(null,
                req.recordDate() == null ? LocalDate.now() : req.recordDate(),
                req.recordType(), req.category(), req.amount(),
                req.currency() == null ? "CNY" : req.currency(), req.note());
        long id = mapper.insertRecord(CurrentUser.id(), r);
        return ApiResponse.ok(r.withId(id));
    }

    @DeleteMapping("/records/{id}")
    public ApiResponse<?> deleteRecord(@PathVariable Long id) {
        return mapper.deleteRecord(CurrentUser.id(), id) == 1
                ? ApiResponse.ok(true) : ApiResponse.fail("记录不存在");
    }

    // ---------------- ② 储蓄率 ----------------

    /**
     * 月度概览：现金流 + 储蓄率 + 支出构成。
     */
    @GetMapping("/overview")
    public ApiResponse<?> overview(@RequestParam(required = false) String month) {
        Long uid = CurrentUser.id();
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);
        LocalDate f = ym.atDay(1);
        LocalDate t = ym.atEndOfMonth();

        BigDecimal income = nz(mapper.sumByTypeValue(uid, "INCOME", f, t));
        BigDecimal expense = nz(mapper.sumByTypeValue(uid, "EXPENSE", f, t));
        BigDecimal balance = income.subtract(expense);
        BigDecimal rate = income.compareTo(BigDecimal.ZERO) > 0
                ? balance.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", ym.toString());
        out.put("income", income);
        out.put("expense", expense);
        out.put("balance", balance);
        out.put("savingsRate", rate.setScale(1, RoundingMode.HALF_UP));
        out.put("targetRate", 30);
        out.put("expenseByCategory", mapper.sumByCategory(uid, "EXPENSE", f, t));
        out.put("trend", mapper.monthlyTrend(uid, 6));
        return ApiResponse.ok(out);
    }

    // ---------------- ③ 资产配置 ----------------

    @GetMapping("/assets")
    public ApiResponse<?> assets() {
        Long uid = CurrentUser.id();
        List<AssetSnapshot> list = mapper.findSnapshots(uid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("snapshots", list);
        out.put("latest", list.isEmpty() ? null : list.get(0));
        return ApiResponse.ok(out);
    }

    @PutMapping("/assets")
    public ApiResponse<?> saveAssets(@Valid @RequestBody AssetRequest req) {
        LocalDate month = req.snapshotMonth() == null
                ? YearMonth.now().atDay(1) : req.snapshotMonth();
        AssetSnapshot s = new AssetSnapshot(null, month,
                nz(req.cash()), nz(req.cnyAssets()), nz(req.usdAssets()), nz(req.otherAssets()), req.note());
        long id = mapper.upsertSnapshot(CurrentUser.id(), s);
        return ApiResponse.ok(s.withId(id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }


    public record RecordRequest(LocalDate recordDate, @NotNull String recordType, String category,
                                @NotNull BigDecimal amount, String currency, String note) {}

    public record AssetRequest(LocalDate snapshotMonth, BigDecimal cash, BigDecimal cnyAssets,
                               BigDecimal usdAssets, BigDecimal otherAssets, String note) {}
}
