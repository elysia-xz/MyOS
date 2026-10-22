-- ============================================================
-- MyOS V1.6 增量脚本 —— 输出内容归属项目（幂等，可重复执行）
--
-- 背景：产品的核心命题是「一次行动产生多个结果」——
--   做任务 → 推进项目 → 沉淀成内容 → 汇成作品集 → 进复盘。
-- 但盘点时发现这条链在数据层是断的：
--   task 表有 goal_id / project_id，而 content_record 一个关联字段都没有。
-- 结果是「写一篇」写完就成了一条孤立记录，系统答不出
-- 「这个项目产出了什么」，作品集只能靠回忆手工重编。
--
-- 约定：
--   · project_id 可空 —— 不是所有输出都属于某个项目（随手记录也要能存）
--   · ON DELETE SET NULL —— 删项目不该连带删掉你写过的内容，
--     内容是第一等资产，只把归属置空
-- ============================================================

ALTER TABLE content_record
  ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES project(id) ON DELETE SET NULL;

-- 项目详情要按 project_id 反查「本项目产出了哪些内容」，走这个索引
CREATE INDEX IF NOT EXISTS idx_content_user_project ON content_record(user_id, project_id);

COMMENT ON COLUMN content_record.project_id IS '归属项目；可空（不属于具体项目的输出）。项目删除时置空，不连带删内容';
