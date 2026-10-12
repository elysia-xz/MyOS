-- ============================================================
-- MyOS schema v1.4 —— 部署级应用配置表（幂等，可重复执行）
--
-- 背景：AI 网关的 Key / Base URL / Model 原本只能靠环境变量注入，
-- 改一次就得重启后端。用户要求「后期能在多端运行」，这个流程不成立
-- ——总不能每换一台设备就去服务器上改环境变量。
--
-- 现在的做法（配置与存储分离）：
--   前端设置页 POST /api/v1/ai/config  →  写入这张表  →  后端即时生效
--   GET  /api/v1/ai/config 只回脱敏后的 sk-123****cdef，永不回显完整 Key
--   取值优先级：本表（界面里填的） > 环境变量（部署时注入的） > 内置默认值
--   所以两种用法都成立：本机开发照旧 export MYOS_AI_API_KEY，
--   多端部署则在界面上填一次、所有设备共享、无需重启。
--
-- 为什么用 KV 而不是专用表：这是「部署级」配置而非用户级配置，
-- 单表 KV 足够；将来要加别的可配置项也不必再建表。
--
-- ⚠️ 安全说明：config_value 目前是明文存储。对本项目（单用户、本机
--    PostgreSQL、且 Key 本身由用户自己填）是可接受的取舍；一旦要部署到
--    公网，应改为加密存储，或干脆改回纯环境变量注入。
-- ============================================================

CREATE TABLE IF NOT EXISTS app_config (
    config_key   TEXT PRIMARY KEY,
    config_value TEXT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_config IS '部署级应用配置（KV），当前用于 AI 网关的 base-url / model / api-key';
COMMENT ON COLUMN app_config.config_key IS '配置键，当前取值：ai.base-url / ai.model / ai.api-key';
