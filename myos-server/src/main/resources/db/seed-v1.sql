-- ============================================================
-- MyOS V1.0 演示种子数据
-- 依赖：schema.sql + schema-v1.sql 已执行
-- 可重复执行
-- ============================================================

-- ------------------------------------------------------------
-- 用户：确保密码哈希存在（bcrypt of "admin123"）
-- 若 V0.1 已插入 id=1 的 admin，这里更新其密码为 bcrypt 值
-- ------------------------------------------------------------
INSERT INTO my_user(id, username, email, password_hash, nickname)
VALUES (1, 'admin', 'admin@myos.local',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Troy')
ON CONFLICT (id) DO UPDATE
  SET password_hash = EXCLUDED.password_hash
  WHERE my_user.password_hash = 'MVP-DEMO-password';

SELECT setval(pg_get_serial_sequence('my_user','id'), COALESCE((SELECT MAX(id) FROM my_user), 1), true);

-- ------------------------------------------------------------
-- 目标层级：年 -> 月 -> 周
-- ------------------------------------------------------------
INSERT INTO goal(user_id, title, description, category, level, system_code, status, progress, target_value, start_date, end_date)
SELECT 1, '掌握 AI 编程并完成 12 个项目', '年度主线目标', 'AI', 'YEAR', 'TECH', 'ACTIVE', 35, 12,
       DATE_TRUNC('year', CURRENT_DATE)::date, (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date
WHERE NOT EXISTS (SELECT 1 FROM goal WHERE user_id = 1 AND title = '掌握 AI 编程并完成 12 个项目');

INSERT INTO goal(user_id, parent_id, title, description, category, level, system_code, status, progress, target_value, start_date, end_date)
SELECT 1, g.id, '完成私人工作台 V1.0', '本月项目', '编程', 'MONTH', 'TECH', 'ACTIVE', 70, 1,
       DATE_TRUNC('month', CURRENT_DATE)::date, (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::date
FROM goal g
WHERE g.user_id = 1 AND g.title = '掌握 AI 编程并完成 12 个项目'
AND NOT EXISTS (SELECT 1 FROM goal WHERE user_id = 1 AND title = '完成私人工作台 V1.0');

-- ------------------------------------------------------------
-- 项目
-- ------------------------------------------------------------
INSERT INTO project(user_id, name, description, status, progress, start_date, target_date,
                    problem_solved, tech_stack, github_url, difficulty, solution)
SELECT 1, 'MyOS 私人工作台', '个人成长操作系统 V1.0', 'IN_PROGRESS', 70, CURRENT_DATE, CURRENT_DATE + 30,
       '把每天的行动自动沉淀成作品集与成长报告，解决「学了很多却没有产出」的问题',
       'Vue3 / Spring Boot / PostgreSQL',
       'https://github.com/example/myos', '中等',
       '用一次行动触发多路产出：任务→项目进度→作品集→日报→月复盘'
WHERE NOT EXISTS (SELECT 1 FROM project WHERE user_id = 1 AND name = 'MyOS 私人工作台');

INSERT INTO project(user_id, name, description, status, progress, start_date, target_date,
                    problem_solved, tech_stack, github_url, finished_at)
SELECT 1, 'AI Prompt 管理工具', '统一管理常用 Prompt 模板', 'DONE', 100, CURRENT_DATE - 40, CURRENT_DATE - 10,
       'Prompt 散落各处，复用成本高', 'Vue3 / IndexedDB', 'https://github.com/example/prompt-manager', CURRENT_DATE - 10
WHERE NOT EXISTS (SELECT 1 FROM project WHERE user_id = 1 AND name = 'AI Prompt 管理工具');

-- ------------------------------------------------------------
-- 今日任务
-- ------------------------------------------------------------
INSERT INTO task(user_id, project_id, title, description, priority, status, due_date)
SELECT 1, p.id, '完成 MyOS V1.0 数据库与后端骨架', '新增 habit/finance/review/ai 模块', 'HIGH', 'DONE', CURRENT_DATE
FROM project p WHERE p.user_id = 1 AND p.name = 'MyOS 私人工作台'
AND NOT EXISTS (SELECT 1 FROM task WHERE user_id = 1 AND title = '完成 MyOS V1.0 数据库与后端骨架');

INSERT INTO task(user_id, project_id, title, priority, status, due_date)
SELECT 1, p.id, '每天英语学习 60 分钟', 'MEDIUM', 'TODO', CURRENT_DATE
FROM project p WHERE p.user_id = 1 AND p.name = 'MyOS 私人工作台'
AND NOT EXISTS (SELECT 1 FROM task WHERE user_id = 1 AND title = '每天英语学习 60 分钟');

INSERT INTO task(user_id, project_id, title, priority, status, due_date)
SELECT 1, p.id, '输出一篇开发日志', 'MEDIUM', 'TODO', CURRENT_DATE
FROM project p WHERE p.user_id = 1 AND p.name = 'MyOS 私人工作台'
AND NOT EXISTS (SELECT 1 FROM task WHERE user_id = 1 AND title = '输出一篇开发日志');

-- ------------------------------------------------------------
-- 今日习惯打点（7 系统，最近 3 天）
-- ------------------------------------------------------------
INSERT INTO habit_log(user_id, log_date, system_code, metric_value, target_value, done)
SELECT 1, CURRENT_DATE, v.code, v.metric, v.target, v.done
FROM (VALUES
  ('AI',      45, 60,  FALSE),
  ('TECH',    90, 60,  TRUE),
  ('CONTENT',  1,  1,  TRUE),
  ('ENGLISH', 60, 60,  TRUE),
  ('BODY',    30, 30,  TRUE),
  ('FINANCE',  1,  1,  TRUE),
  ('SOCIAL',   0,  1,  FALSE)
) AS v(code, metric, target, done)
ON CONFLICT (user_id, log_date, system_code) DO NOTHING;

-- ------------------------------------------------------------
-- 学习记录（五段式）
-- ------------------------------------------------------------
INSERT INTO learning_record(user_id, title, category, system_code, source, content, understanding, practice, conclusion, duration_minutes, learned_at)
SELECT 1, 'Claude 代码分析能力实测', 'AI', 'AI', 'https://docs.anthropic.com',
       'Claude 在长上下文代码理解上的表现',
       '相比其他模型，它在跨文件依赖分析上更稳定',
       '拿 MyOS 后端做了一次重构建议测试',
       '适合做代码审查搭档，但不适合直接生成大段业务逻辑',
       90, CURRENT_TIMESTAMP - INTERVAL '1 day'
WHERE NOT EXISTS (SELECT 1 FROM learning_record WHERE user_id = 1 AND title = 'Claude 代码分析能力实测');

INSERT INTO learning_record(user_id, title, category, system_code, source, content, understanding, practice, conclusion, duration_minutes, learned_at)
SELECT 1, 'GitHub 开源项目目录结构分析', 'GitHub', 'TECH', 'https://github.com',
       '研究了一个 Spring Boot 项目的目录划分',
       '按领域分包比按技术分层更好维护',
       '在 MyOS 里按 com.myos.<domain> 重构',
       '领域分包让新增模块成本明显下降',
       60, CURRENT_TIMESTAMP - INTERVAL '2 day'
WHERE NOT EXISTS (SELECT 1 FROM learning_record WHERE user_id = 1 AND title = 'GitHub 开源项目目录结构分析');

-- ------------------------------------------------------------
-- 内容输出
-- ------------------------------------------------------------
INSERT INTO content_record(user_id, title, platform, content, quote, status, published_at)
SELECT 1, '我是如何用工作台管理每天行动的', 'X',
       '把「我要学很多东西」换成「今天 7 个系统分别有没有向前走一点」。',
       '不要用忙碌感代替进展。',
       'PUBLISHED', CURRENT_TIMESTAMP - INTERVAL '3 day'
WHERE NOT EXISTS (SELECT 1 FROM content_record WHERE user_id = 1 AND title = '我是如何用工作台管理每天行动的');

INSERT INTO content_record(user_id, title, platform, content, quote, status, published_at)
SELECT 1, '为什么我放弃了待办清单', '国内自媒体',
       '清单只记录「要做什么」，不记录「做成了什么」。',
       '把注意力放在能积累的事情上，而不是能立刻被看见的事情上。',
       'DRAFT', NULL
WHERE NOT EXISTS (SELECT 1 FROM content_record WHERE user_id = 1 AND title = '为什么我放弃了待办清单');

-- ------------------------------------------------------------
-- 财务流水（本月）
-- ------------------------------------------------------------
INSERT INTO finance_record(user_id, record_date, record_type, category, amount, currency, note)
SELECT 1, DATE_TRUNC('month', CURRENT_DATE)::date, 'INCOME', '工资', 20000.00, 'CNY', '本月收入'
WHERE NOT EXISTS (SELECT 1 FROM finance_record WHERE user_id = 1 AND note = '本月收入');

INSERT INTO finance_record(user_id, record_date, record_type, category, amount, currency, note)
SELECT 1, CURRENT_DATE, 'EXPENSE', '餐饮', 68.50, 'CNY', '午饭'
WHERE NOT EXISTS (SELECT 1 FROM finance_record WHERE user_id = 1 AND note = '午饭');

INSERT INTO finance_record(user_id, record_date, record_type, category, amount, currency, note)
SELECT 1, CURRENT_DATE - 1, 'EXPENSE', '学习', 199.00, 'CNY', '订阅课程'
WHERE NOT EXISTS (SELECT 1 FROM finance_record WHERE user_id = 1 AND note = '订阅课程');

-- ------------------------------------------------------------
-- 资产快照（本月）
-- ------------------------------------------------------------
INSERT INTO asset_snapshot(user_id, snapshot_month, cash, cny_assets, usd_assets, other_assets, note)
SELECT 1, DATE_TRUNC('month', CURRENT_DATE)::date, 50000, 120000, 30000, 10000, '本月资产快照'
WHERE NOT EXISTS (SELECT 1 FROM asset_snapshot WHERE user_id = 1 AND snapshot_month = DATE_TRUNC('month', CURRENT_DATE)::date);
