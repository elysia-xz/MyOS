-- ============================================================
-- MyOS V1.1 增量脚本 —— 多端就绪改造
-- 说明：可重复执行（所有语句均幂等）
-- 前置：schema.sql → schema-v1.sql 已执行
-- ============================================================

-- ------------------------------------------------------------
-- user_token —— 多端登录令牌
--
-- 设计要点：
--  1) 只存令牌的 SHA-256 摘要（token_hash），不存明文——
--     即使数据库被导出，也无法直接冒用登录态。
--  2) 同一用户可有多条记录：Web / 小程序 / 桌面 / App 各持一份，
--     在任一端登出只撤销该端的令牌，不影响其他端。
--  3) 用不透明令牌而非 JWT：零新增依赖，且登出可真正失效。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_token (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT       NOT NULL REFERENCES my_user(id) ON DELETE CASCADE,
  token_hash   VARCHAR(128) NOT NULL,
  device       VARCHAR(64)  NOT NULL DEFAULT 'unknown',
  expires_at   TIMESTAMP    NOT NULL,
  last_used_at TIMESTAMP,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_user_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_user_token_user    ON user_token(user_id);
CREATE INDEX IF NOT EXISTS idx_user_token_expires ON user_token(expires_at);
