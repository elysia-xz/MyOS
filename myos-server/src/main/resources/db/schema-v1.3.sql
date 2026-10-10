-- ============================================================
-- MyOS schema v1.3 —— 外键删除规则修正（幂等，可重复执行）
--
-- 背景：V1.0 建表时外键都没写删除规则，默认是 NO ACTION。
-- 后果（连真实库做端到端测试才暴露）：
--   删除一个已有开发日志的项目 →
--   ERROR: update or delete on table "project" violates foreign key
--          constraint "project_log_project_id_fkey" on table "project_log"
--   也就是「项目删不掉」。同类隐患还在 task→goal、task→project、goal→parent。
--
-- 语义选择（判断依据：什么该跟着消失，什么该留下）
--   project_log.project_id  → CASCADE   日志属于项目，项目没了日志没有意义
--   task.project_id         → SET NULL  任务不该因为项目被删而消失
--   task.goal_id            → SET NULL  同上
--   goal.parent_id          → SET NULL  子目标降级为顶层目标，而不是被连带删除
--
-- 写法：先 DROP CONSTRAINT IF EXISTS 再 ADD，保证可重复执行且结果一致，
--       也不依赖约束当前的定义（无论旧定义是什么都会被换成正确的）。
-- ============================================================

-- 1. project_log.project_id —— 随项目一起删除
ALTER TABLE project_log DROP CONSTRAINT IF EXISTS project_log_project_id_fkey;
ALTER TABLE project_log
  ADD CONSTRAINT project_log_project_id_fkey
  FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

-- 2. task.project_id —— 项目删除后置空，任务保留
ALTER TABLE task DROP CONSTRAINT IF EXISTS task_project_id_fkey;
ALTER TABLE task
  ADD CONSTRAINT task_project_id_fkey
  FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE SET NULL;

-- 3. task.goal_id —— 目标删除后置空，任务保留
ALTER TABLE task DROP CONSTRAINT IF EXISTS task_goal_id_fkey;
ALTER TABLE task
  ADD CONSTRAINT task_goal_id_fkey
  FOREIGN KEY (goal_id) REFERENCES goal(id) ON DELETE SET NULL;

-- 4. goal.parent_id —— 父目标删除后置空，子目标保留为顶层
ALTER TABLE goal DROP CONSTRAINT IF EXISTS goal_parent_id_fkey;
ALTER TABLE goal
  ADD CONSTRAINT goal_parent_id_fkey
  FOREIGN KEY (parent_id) REFERENCES goal(id) ON DELETE SET NULL;
