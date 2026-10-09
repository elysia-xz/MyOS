package com.myos.ai;

/**
 * AI 网关 —— 对应 PRD §7.3。
 *
 * 设计约定：
 *  - V1 只对接一个模型，避免过早抽象（未来可加实现类做多模型路由）
 *  - API Key 只存后端环境变量，绝不下发前端
 *  - 所有调用结果先落库，支持用户编辑后保存
 */
public interface AiGateway {

    /** 网关是否已配置（未配置时前端隐藏或提示） */
    boolean available();

    /**
     * 按场景生成文本。
     *
     * @param scene  场景标识：daily_summary / daily_report / daily_review / monthly_summary / annual_report
     * @param prompt 已由后端拼装好的完整提示词
     * @return 模型返回的文本
     */
    String complete(String scene, String prompt);
}
