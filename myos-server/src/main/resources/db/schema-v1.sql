-- ============================================================
-- MyOS V1.0 增量脚本
-- 从 V0.1 升级到 V1.0
-- 说明：可重复执行（所有语句均为 IF NOT EXISTS 幂等）
-- 对应 PRD: docs/PRD-V1.0.md §5
-- ============================================================

-- ------------------------------------------------------------
-- 1. project 表补充字段（Portfolio 作品集所需）
-- ------------------------------------------------------------
ALTER TABLE project ADD COLUMN IF NOT EXISTS problem_solved TEXT;
ALTER TABLE project ADD COLUMN IF NOT EXISTS tech_stack     VARCHAR(300);
ALTER TABLE project ADD COLUMN IF NOT EXISTS cover_image    VARCHAR(500);
ALTER TABLE project ADD COLUMN IF NOT EXISTS difficulty     TEXT;
ALTER TABLE project ADD COLUMN IF NOT EXISTS solution       TEXT;
ALTER TABLE project ADD COLUMN IF NOT EXISTS finished_at    DATE;

-- ------------------------------------------------------------
-- 2. goal 表补充字段（年 -> 月 -> 周 -> 今日 层级）
-- ------------------------------------------------------------
ALTER TABLE goal ADD COLUMN IF NOT EXISTS parent_id  BIGINT REFERENCES goal(id);
ALTER TABLE goal ADD COLUMN IF NOT EXISTS level      VARCHAR(16);  -- YEAR / MONTH / WEEK
ALTER TABLE goal ADD COLUMN IF NOT EXISTS system_code VARCHAR(32); -- 关联 7 大系统
ALTER TABLE goal ADD COLUMN IF NOT EXISTS target_value INT;

CREATE INDEX IF NOT EXISTS idx_goal_user_level ON goal(user_id, level, status);

-- ------------------------------------------------------------
-- 3. habit_log —— 每日习惯打点（7 系统水位的数据源）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS habit_log (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT      NOT NULL REFERENCES my_user(id),
  log_date      DATE        NOT NULL,
  system_code   VARCHAR(32) NOT NULL,   -- AI/TECH/CONTENT/ENGLISH/BODY/FINANCE/SOCIAL
  metric_value  INT         NOT NULL DEFAULT 0,
  target_value  INT,
  done          BOOLEAN     NOT NULL DEFAULT FALSE,
  note          VARCHAR(500),
  created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, log_date, system_code)
);
CREATE INDEX IF NOT EXISTS idx_habit_user_date ON habit_log(user_id, log_date);
CREATE INDEX IF NOT EXISTS idx_habit_user_system ON habit_log(user_id, system_code, log_date);

-- ------------------------------------------------------------
-- 4. project_log —— 项目开发日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS project_log (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES my_user(id),
  project_id BIGINT      NOT NULL REFERENCES project(id),
  log_date   DATE        NOT NULL,
  content    TEXT        NOT NULL,
  commit_ref VARCHAR(120),
  created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_project_log_project ON project_log(project_id, log_date DESC);

-- ------------------------------------------------------------
-- 5. finance_record —— 记账流水
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS finance_record (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT        NOT NULL REFERENCES my_user(id),
  record_date DATE          NOT NULL,
  record_type VARCHAR(16)   NOT NULL,   -- INCOME / EXPENSE
  category    VARCHAR(64),
  amount      NUMERIC(14,2) NOT NULL,
  currency    VARCHAR(8)    NOT NULL DEFAULT 'CNY',
  note        VARCHAR(500),
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_finance_user_date ON finance_record(user_id, record_date DESC);
CREATE INDEX IF NOT EXISTS idx_finance_user_type ON finance_record(user_id, record_type, record_date);

-- ------------------------------------------------------------
-- 6. asset_snapshot —— 资产配置快照（按月）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asset_snapshot (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT        NOT NULL REFERENCES my_user(id),
  snapshot_month DATE          NOT NULL,   -- 每月 1 日
  cash           NUMERIC(14,2) NOT NULL DEFAULT 0,
  cny_assets     NUMERIC(14,2) NOT NULL DEFAULT 0,
  usd_assets     NUMERIC(14,2) NOT NULL DEFAULT 0,
  other_assets   NUMERIC(14,2) NOT NULL DEFAULT 0,
  note           VARCHAR(500),
  created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, snapshot_month)
);
CREATE INDEX IF NOT EXISTS idx_asset_user_month ON asset_snapshot(user_id, snapshot_month DESC);

-- ------------------------------------------------------------
-- 7. learning_record 补充：关联系统编码，便于水位统计
-- ------------------------------------------------------------
ALTER TABLE learning_record ADD COLUMN IF NOT EXISTS system_code VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_learning_user_system ON learning_record(user_id, system_code, learned_at);

-- ------------------------------------------------------------
-- 8. review 表补充：AI 总结与关联周期
-- ------------------------------------------------------------
ALTER TABLE review ADD COLUMN IF NOT EXISTS period_start DATE;
ALTER TABLE review ADD COLUMN IF NOT EXISTS period_end   DATE;
ALTER TABLE review ADD COLUMN IF NOT EXISTS metrics_json TEXT;

CREATE INDEX IF NOT EXISTS idx_review_user_type_date ON review(user_id, review_type, review_date DESC);

-- 唯一约束：同一用户、同一类型、同一周期只保留一条复盘（便于 upsert）
CREATE UNIQUE INDEX IF NOT EXISTS uq_review_user_type_date
  ON review(user_id, review_type, review_date);
