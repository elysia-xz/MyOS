-- ============================================================
-- MyOS V1.7 增量脚本 —— 用户角色与状态（幂等，可重复执行）
--
-- 背景：要给几个人长期用，但「加人」「停用某人」目前只能敲命令。
-- 要在界面上做用户管理，第一件事是区分「谁是管理员」——
-- 否则任何登录用户都能调用建号接口，等于把系统对外开放。
--
-- 两个字段的职责要分清：
--   role   —— 能不能管用户（ADMIN / USER）
--   status —— 能不能登录（ACTIVE / DISABLED）
--
-- ⚠️ status 字段早在 schema.sql 就有，但**登录时从来没检查过**，
--    所以「停用账号」这件事此前是没有效果的。
--    本次一并修掉（见 AuthController.login），否则停用等于摆设。
-- ============================================================

ALTER TABLE my_user
  ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER';

-- 已有账号里的 admin 提升为管理员。用 username 判定而不是 id，
-- 因为不同环境里 admin 的 id 未必是 1（虽然种子脚本固定成 1）。
UPDATE my_user SET role = 'ADMIN' WHERE username = 'admin' AND role <> 'ADMIN';

COMMENT ON COLUMN my_user.role   IS 'ADMIN 可管理用户；USER 为普通用户';
COMMENT ON COLUMN my_user.status IS 'ACTIVE 可登录；DISABLED 禁止登录（登录时校验）';

-- 管理员查用户列表走这个索引（几个人量级其实用不上，但成本为零，
-- 且将来用户变多时不必再补迁移）
CREATE INDEX IF NOT EXISTS idx_my_user_role ON my_user(role);
