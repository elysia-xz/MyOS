-- ============================================================
-- MyOS V1.5 增量脚本 —— AI 对话记录（幂等，可重复执行）
--
-- 背景：AI 板块原先只有 4 个固定按钮（和 AI 讨论 / 生成日报 /
-- 复盘今天 / 本月总结），点一次生成一段文字就没了。用户要求
-- 「能问答，并且能根据我在工作台提的需求办事」—— conversational
-- 是这个需求的前提，而对话一旦要多轮，就必须落库：
--   · 不落库 → 一刷新页面历史全丢，更谈不上多设备同步
--   · 落库 → 换设备打开能看到同一份对话，与多端改造的目标一致
--
-- 表关系：一个会话（ai_conversation）有多条消息（ai_message），
-- 消息分三种角色：
--   user      用户说的话
--   assistant 模型的回复；若它想操作工作台，tool_calls 里带调用意图
--   tool      某个 tool_call 执行后的结果回填（确认或拒绝都记一笔）
--
-- ⚠️ 关键约定：assistant 消息里的 tool_calls 只是「意图」，
--    后端绝不依据它自动写库。必须由用户在界面上确认，
--    走 /ai/tool/execute 执行后才允许落库。
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_conversation (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES my_user(id) ON DELETE CASCADE,
  title      TEXT,                                  -- 由首条用户消息截取，便于历史列表展示
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user ON ai_conversation(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_message (
  id              BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT       NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
  role            VARCHAR(16)  NOT NULL,            -- user / assistant / tool
  content         TEXT,                             -- 正文；tool 消息此处为执行结果简述
  tool_calls      JSONB,                            -- 仅 assistant 有值：模型想调用的工具及参数
  tool_call_id    VARCHAR(64),                      -- 仅 tool 有值：对应哪一次调用
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_ai_message_role CHECK (role IN ('user', 'assistant', 'tool'))
);

CREATE INDEX IF NOT EXISTS idx_ai_message_conv ON ai_message(conversation_id, id);

COMMENT ON TABLE  ai_conversation            IS 'AI 助手会话（每用户多条）';
COMMENT ON TABLE  ai_message                 IS 'AI 会话消息；assistant.tool_calls 仅为意图，须用户确认后才执行';
COMMENT ON COLUMN ai_message.tool_calls      IS '模型返回的工具调用意图（JSONB），未经确认不得执行';
