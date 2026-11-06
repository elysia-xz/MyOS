-- ============================================================
-- schema-v1.8.sql —— 把「年目标 → 月项目 → 周任务」这条主线接上
--
-- 背景：这一段的数据模型其实早就有了（task.goal_id / task.project_id），
-- 但界面上从来没有真正连起来 —— 任务挂不到项目，项目挂不到目标，
-- 项目进度是手填的数字。于是「一次行动产生多个结果」只能靠人自觉，
-- 系统自己不动。这次补三处，全部可空且带默认值，不改动任何既有数据。
--
-- 幂等：可重复执行。
-- ============================================================

-- ① 项目归属目标：补上「年目标 → 月项目」这一跳
--    ON DELETE SET NULL —— 删目标不该连带删掉项目（项目是第一等资产）
ALTER TABLE project
  ADD COLUMN IF NOT EXISTS goal_id BIGINT REFERENCES goal(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_project_goal ON project(goal_id);

-- ② 项目进度来源
--    AUTO   —— 由该项目下的任务完成度推导（默认）
--    MANUAL —— 用户手填
--    历史项目没有挂任务，AUTO 会回退到已存的 progress，所以视觉上不会有跳变。
ALTER TABLE project
  ADD COLUMN IF NOT EXISTS progress_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO';

-- ③ AI 场景产出落库
--    此前 AiController 生成完直接 return，结果只活在首页组件的内存里，
--    刷新即失，也进不了「月复盘 → 年度档案」这条链。PRD §7.3 要求先落库。
CREATE TABLE IF NOT EXISTS ai_output (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id) ON DELETE CASCADE,
  scene VARCHAR(32) NOT NULL,             -- daily_summary / daily_report / daily_review / monthly_summary / annual_report
  content TEXT NOT NULL,
  output_date DATE NOT NULL DEFAULT CURRENT_DATE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_output_user_created ON ai_output(user_id, created_at DESC);
