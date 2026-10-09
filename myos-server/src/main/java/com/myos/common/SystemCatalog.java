package com.myos.common;

import java.util.List;
import java.util.Map;

/**
 * 7 大系统定义（对应 PRD §2.1）。
 * 6 模块做导航，7 系统做首页水位。
 */
public final class SystemCatalog {

    private SystemCatalog() {}

    public record Def(String code, String name, String unit, int dailyTarget, String icon) {}

    /** 顺序即首页水位条展示顺序 */
    public static final List<Def> ALL = List.of(
            new Def("AI",      "AI 能力",  "min", 60, "AI"),
            new Def("TECH",    "技术能力", "min", 60, "TECH"),
            new Def("CONTENT", "内容能力", "篇",  1,  "CONTENT"),
            new Def("ENGLISH", "英语与信息", "min", 60, "EN"),
            new Def("BODY",    "身体",     "min", 30, "BODY"),
            new Def("FINANCE", "财务",     "次",  1,  "FIN"),
            new Def("SOCIAL",  "人际",     "次",  1,  "SOC")
    );

    private static final Map<String, Def> BY_CODE =
            ALL.stream().collect(java.util.stream.Collectors.toMap(Def::code, d -> d));

    public static Def of(String code) {
        return BY_CODE.getOrDefault(code, new Def(code, code, "", 1, code));
    }

    public static boolean exists(String code) {
        return BY_CODE.containsKey(code);
    }
}
