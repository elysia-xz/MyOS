INSERT INTO my_user(id, username, password_hash, nickname)
VALUES (1, 'admin', 'MVP-DEMO-password', 'Troy')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('my_user','id'), COALESCE((SELECT MAX(id) FROM my_user), 1), true);

INSERT INTO project(user_id, name, description, status, progress, start_date, target_date)
SELECT 1, 'MyOS', '个人成长操作系统 V0.1', 'IN_PROGRESS', 70, CURRENT_DATE, CURRENT_DATE + 30
WHERE NOT EXISTS (SELECT 1 FROM project WHERE user_id = 1 AND name = 'MyOS');

INSERT INTO task(user_id, project_id, title, description, priority, status, due_date)
SELECT 1, p.id, '完成 MyOS V0.1 数据库与后端骨架', '把数据库、Spring Boot、MyBatis、任务 CRUD 打通', 'HIGH', 'TODO', CURRENT_DATE
FROM project p
WHERE p.user_id = 1 AND p.name = 'MyOS'
AND NOT EXISTS (SELECT 1 FROM task WHERE user_id = 1 AND title = '完成 MyOS V0.1 数据库与后端骨架');

INSERT INTO task(user_id, project_id, title, priority, status, due_date)
SELECT 1, p.id, '每天英语学习 60 分钟', 'MEDIUM', 'TODO', CURRENT_DATE
FROM project p
WHERE p.user_id = 1 AND p.name = 'MyOS'
AND NOT EXISTS (SELECT 1 FROM task WHERE user_id = 1 AND title = '每天英语学习 60 分钟');
