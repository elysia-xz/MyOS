# MyOS V1.0 产品需求文档（PRD）

> 个人成长操作系统 · 私人工作台
> 版本：V1.0 草案
> 编写日期：2026-10-08
> 技术栈（不变）：Vue 3 + TypeScript + Vite / Spring Boot 4 + MyBatis / PostgreSQL

---

## 0. 一句话定义

MyOS 不是待办清单，也不是笔记软件，而是**一个人的长期成长数据资产系统**：
让「年目标 → 月项目 → 周任务 → 今日行动」这条链路持续运转，并自动把每一份行动沉淀成内容、作品与年度成长报告。

**核心命题**：一次行动，产生多个结果。

```text
Git Commit
  ↓
项目进度  →  作品集  →  日报  →  月度复盘  →  年度成长档案
```

---

## 1. 设计原则（先立规矩，再谈功能）

这一版最重要的事情不是"加功能"，而是**把边界立死**，否则会变成一个什么都想装、最后什么都不用的系统。

| 编号 | 原则 | 说明 |
|---|---|---|
| P1 | **不再增加目标** | 用户已有足够方向，V1 只做「让已存在的方向持续发生」的系统，不新增学习目标 |
| P2 | **记录优先于规划** | 先让系统如实记录真实行动，再谈 AI 分析和规划。没有数据的智能都是空谈 |
| P3 | **减法优先** | 每个功能都必须回答："不做它，核心闭环会断吗？" 答案是否，就不做 |
| P4 | **AI 是贯穿层，不是独立页面** | AI 能力嵌入今日/学习/输出/项目/复盘各处，不做孤立的"聊天页面" |
| P5 | **只记录，不替用户决策** | 财务模块只做记录、统计、提醒、复盘；投资决策绝不自动执行 |
| P6 | **写下来 > 做完美** | 内容输出不追求爆款，只追求留下痕迹。系统设计要降低"写"的门槛 |
| P7 | **数据属于用户** | 所有数据本地 PostgreSQL，可随时导出，不锁定 |

---

## 2. 双维度切分（关键概念澄清）

用户资料中出现了两套维度，必须分工明确，否则会互相打架：

### 2.1 两套维度

| 维度 | 内容 | 用途 |
|---|---|---|
| **7 大系统**（内容视角） | AI 能力 / 技术能力 / 内容能力 / 英语与信息 / 身体 / 财务 / 人际 | 首页**水位条**的数据源；用于回答"今天 7 个系统分别有没有向前走一点" |
| **6 大模块**（功能视角） | 今日 / 学习 / 输出 / 项目 / 财务 / 复盘 | **侧边栏导航结构**；用于回答"我要去哪里操作" |

### 2.2 分工约定（V1 强制）

> **6 模块做导航，7 系统做首页水位。**

- 侧边栏只放 6 个入口，**不为 7 个系统各开一个页面**（太重，用户会迷路）。
- 首页「今日水位」区域的 7 条进度条，数据来自 7 系统的每日打点（见 §5.2 habit_log）。
- 用户日常操作路径是"6 模块"，看全局状态时看"7 系统水位"。

---

## 3. 信息架构（IA）

```text
MyOS
├── 登录
└── 主应用（侧边栏 6 模块）
    ├── ① 今日 Today        ← 每天必来
    │   ├── 今日任务（已完成）
    │   ├── 今日习惯打点（新增）
    │   └── 今日 AI 助手（新增）
    ├── ② 学习 Learning     ← 输入
    │   ├── 学习记录五段式（已完成）
    │   └── 知识库 / 沉淀（V1.1）
    ├── ③ 输出 Content      ← 输出
    │   ├── 内容记录（已完成）
    │   └── 输出日历 / 打卡（新增）
    ├── ④ 项目 Projects     ← 成果
    │   ├── 项目列表（已完成）
    │   ├── 项目详情 / 开发日志（新增）
    │   └── Portfolio 作品集（新增）
    ├── ⑤ 财务 Finance      ← 基础（全新）
    │   ├── 记账流水
    │   ├── 月度储蓄率
    │   └── 资产配置快照
    └── ⑥ 复盘 Review       ← 闭环（全新）
        ├── 日复盘
        ├── 周复盘
        ├── 月复盘
        └── 年度成长档案（AI 生成）
```

---

## 4. 首页 = 我的成长 Dashboard（V1 核心，必做）

首页从"4 个数字"升级为完整成长仪表盘。这是整个产品**最有感知、最值得先做**的部分。

### 4.1 页面结构

```text
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                2026.10.08  周四
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【今日水位】7 大系统
英语          ███████░░░  60 min
AI 学习       █████░░░░░  45 min
内容输出      ✓  已打卡
运动          ✓  已打卡
阅读          ██████░░░░  30 min
人际          —  今日无记录
财务          ✓  已记账

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【能力等级】长期积累（由累计数据自动计算）
AI 能力       Lv.03
编程          Lv.04
英语          Lv.02
内容创作      Lv.02
身体          Lv.01
财务          Lv.02

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【本月项目】
「MyOS 私人工作台」
███████░░░  70%

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【本月输出】目标 30 天
内容   18 / 30    ██████░░░░
英语   21 / 30    ███████░░░
运动   16 / 30    █████░░░░░
学习   24 / 30    ████████░░

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【最近沉淀】
- Claude 使用笔记
- GPT 工作流
- GitHub 项目分析
- 行业报告
- AI 编程实践

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【今日 AI 助手】
[ 和 AI 讨论 ]  [ 总结今天 ]
[ 生成日报 ]    [ 复盘今天 ]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 4.2 数据来源对照

| 首页区块 | 数据来源表 | 计算逻辑 |
|---|---|---|
| 今日水位 | `habit_log` + `task` | 今日各系统打点值 / 目标值 → 百分比 |
| 能力等级 | `learning_record` / `project` / `habit_log` 累计 | 见 §4.3 |
| 本月项目 | `project` | `status = IN_PROGRESS`，取 `progress` |
| 本月输出 | `content_record` / `habit_log` | 本月计数 / 目标天数 |
| 最近沉淀 | `learning_record` + `knowledge_item` | 按时间倒序取 5 条 |
| 今日 AI 助手 | AI Gateway | 见 §7 |

### 4.3 能力等级（Lv）计算规则

V1 先用**简单累计值分档**，不做花哨算法：

| 系统 | 依据 | 分档（示例） |
|---|---|---|
| AI 能力 | AI 类学习累计分钟数 | 每 600 分钟 +1 Lv |
| 编程 | 编程类学习 + 已完成项目数 | 每 600 分钟 +1 Lv，每完成项目 +1 Lv |
| 英语 | 英语类学习累计分钟 | 每 600 分钟 +1 Lv |
| 内容创作 | 已发布内容数 | 每 10 篇 +1 Lv |
| 身体 | 运动打卡次数 | 每 20 次 +1 Lv |
| 财务 | 连续记账月数 | 每 3 个月 +1 Lv |

> 分档阈值集中在后端一个 `LevelRule` 配置类里，方便后续调参。

---

## 5. 数据模型设计（V1 增量）

现有表：`my_user` / `goal` / `project` / `task` / `daily_log` / `learning_record` / `content_record` / `knowledge_item` / `review`

### 5.1 复用与调整

| 表 | 处理 |
|---|---|
| `task` / `project` / `learning_record` / `content_record` | **直接复用**，补少量字段 |
| `goal` | **启用**（当前未接前后端），补齐 API 支撑「年→月→周」层级 |
| `review` | **启用**（当前无 Controller），字段已够用，补齐 API + 页面 |
| `knowledge_item` | V1.1 再做，V1 先只读展示 |
| `daily_log` | V1 用 `habit_log` 替代，`daily_log` 保留待迁移 |

### 5.2 新增表

#### habit_log（每日习惯打点 —— 7 系统水位的数据源）

```sql
CREATE TABLE IF NOT EXISTS habit_log (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  log_date DATE NOT NULL,
  system_code VARCHAR(32) NOT NULL,   -- AI / TECH / CONTENT / ENGLISH / BODY / FINANCE / SOCIAL
  metric_value INT NOT NULL DEFAULT 0, -- 分钟数或次数
  target_value INT,                    -- 当时的目标值，便于回溯
  done BOOLEAN NOT NULL DEFAULT FALSE, -- 是否达标（布尔型系统直接用）
  note VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, log_date, system_code)
);
CREATE INDEX IF NOT EXISTS idx_habit_user_date ON habit_log(user_id, log_date);
```

#### project_log（项目开发日志 —— "一次行动产生多个结果"的落点）

```sql
CREATE TABLE IF NOT EXISTS project_log (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  project_id BIGINT NOT NULL REFERENCES project(id),
  log_date DATE NOT NULL,
  content TEXT NOT NULL,
  commit_ref VARCHAR(120),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### finance_record（记账流水）

```sql
CREATE TABLE IF NOT EXISTS finance_record (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  record_date DATE NOT NULL,
  record_type VARCHAR(16) NOT NULL,    -- INCOME / EXPENSE
  category VARCHAR(64),
  amount NUMERIC(14,2) NOT NULL,
  currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
  note VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### asset_snapshot（资产配置快照，按月）

```sql
CREATE TABLE IF NOT EXISTS asset_snapshot (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES my_user(id),
  snapshot_month DATE NOT NULL,        -- 每月 1 日
  cash NUMERIC(14,2) NOT NULL DEFAULT 0,
  cny_assets NUMERIC(14,2) NOT NULL DEFAULT 0,
  usd_assets NUMERIC(14,2) NOT NULL DEFAULT 0,
  other_assets NUMERIC(14,2) NOT NULL DEFAULT 0,
  note VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, snapshot_month)
);
```

### 5.3 project 表补充字段

```sql
ALTER TABLE project ADD COLUMN IF NOT EXISTS problem_solved TEXT;
ALTER TABLE project ADD COLUMN IF NOT EXISTS tech_stack VARCHAR(300);
ALTER TABLE project ADD COLUMN IF NOT EXISTS cover_image VARCHAR(500);
ALTER TABLE project ADD COLUMN IF NOT EXISTS difficulty TEXT;
ALTER TABLE project ADD COLUMN IF NOT EXISTS solution TEXT;
```

---

## 6. 模块详细需求

### 6.1 ① 今日 Today

| 功能 | 优先级 | 说明 |
|---|---|---|
| 今日任务列表 | P0（已有） | 已实现，保留 |
| 今日习惯打点 | **P0（新增）** | 7 系统各一行，支持填分钟数 / 打勾，一键保存 |
| 今日 AI 助手四入口 | **P0（新增）** | 和 AI 讨论 / 总结今天 / 生成日报 / 复盘今天 |

**习惯打点交互**：一屏 7 行，每行「系统名 + 数值输入 + 达标勾选」，底部「保存今日打点」。已打过点的当天直接回显。

### 6.2 ② 学习 Learning

| 功能 | 优先级 | 说明 |
|---|---|---|
| 五段式学习记录 | P0（已有） | 原始资料→理解→实践→结论，保留 |
| 学习记录列表 + 筛选 | P1 | 按系统/时间筛选 |
| 知识库沉淀 | P1（V1.1） | `knowledge_item` 接入 |

### 6.3 ③ 输出 Content

| 功能 | 优先级 | 说明 |
|---|---|---|
| 内容记录（草稿→发布） | P0（已有） | 保留 |
| 输出打卡日历 | **P1（新增）** | 月度格子图，一眼看到"哪天没写" |
| 发布状态流转 | P1 | DRAFT → PUBLISHED，自动回填 `published_at` |

### 6.4 ④ 项目 Projects

| 功能 | 优先级 | 说明 |
|---|---|---|
| 项目列表（进度条） | P0（已有） | 保留 |
| 项目详情页 | **P0（新增）** | 简介/解决的问题/技术栈/GitHub/Demo/截图/开发过程/问题/方案/成果 |
| 开发日志录入 | **P1（新增）** | `project_log`，与日报打通 |
| Portfolio 作品集视图 | **P1（新增）** | 按年月归档已完成项目，可对外展示 |

**Portfolio 字段清单**（用户明确要求）：

```text
项目名称 / 项目简介 / 解决的问题 / 技术栈
GitHub / Demo / 截图
开发过程 / 遇到的问题 / 解决方案 / 最终成果
```

### 6.5 ⑤ 财务 Finance（全新）

三个层次**分开做，不要混在一个页面**：

```text
① 现金流    收入 - 支出 = 结余
② 储蓄率    结余 / 收入
③ 资产配置  现金 / 人民币资产 / 美元资产 / 其他
```

| 功能 | 优先级 | 说明 |
|---|---|---|
| 记账（收入/支出） | P0 | 流水列表 + 快速录入 |
| 月度储蓄率 | P0 | 自动计算，可设目标（如 30%） |
| 资产快照 | P1 | 按月填一次，展示配置占比 |

> **红线（对应原则 P5）**：财务模块只做记录、统计、提醒、复盘。**绝不接入自动交易、绝不替用户做投资决策、绝不给具体买卖建议。**

### 6.6 ⑥ 复盘 Review（全新，但表已存在）

| 功能 | 优先级 | 说明 |
|---|---|---|
| 日复盘 | P0 | 今天做了什么 / 收获 / 浪费的时间 / 明天重点 |
| 周复盘 | P0 | 本周四件事：报告/整理/推进/复盘 |
| 月复盘 | P0 | 项目完成情况 + 内容/习惯完成率 + AI 总结 |
| 年度成长档案 | **P1（特色）** | 全年数据汇总 + AI 生成《年度成长报告》 |

**年度成长档案字段**（用户明确要求）：

```text
365 天
内容输出 XX 篇 / 英语学习 XX 小时 / 运动 XX 次
行业报告 XX 份 / 完成项目 XX 个
AI 使用 X 种模型 / 作品集 XX 个
储蓄率 XX% / 认识新朋友 XX 人

然后 AI 自动生成：
这一年做了什么 → 获得什么能力 → 完成什么项目
→ 哪些目标没完成 → 哪些习惯坚持了
→ 哪些事浪费时间 → 下一年调整什么
```

---

## 7. AI 能力设计（贯穿层）

### 7.1 定位

AI 不是第 7 个模块，而是**贯穿 6 模块的底层能力**（对应原则 P4）。

### 7.2 V1 AI 功能清单

| 功能 | 位置 | 输入 | 输出 |
|---|---|---|---|
| 总结今天 | 今日 | 今日任务 + 打点 + 学习记录 | 一段总结文字 |
| 生成日报 | 今日 | 同上 | 结构化日报（可复制到内容模块） |
| 复盘今天 | 今日 | 今日数据 | 引导式复盘问答 |
| 月度总结 | 复盘 | 本月全量数据 | 月复盘初稿 |
| 年度成长报告 | 复盘 | 全年数据 | 长文报告 |

### 7.3 技术形态

- 后端新增 `ai` 包，提供 `AiGateway` 接口，V1 先对接**一个**模型（避免过早抽象）。
- 前端不做独立聊天页，只做**场景化入口按钮**。
- API Key 存后端环境变量，**绝不下发到前端**。
- 所有 AI 调用**先落库**（`ai_summary` 类字段已存在于 `review` 表），支持用户编辑后保存。

### 7.4 使用哲学（写入产品文案）

> 追求的不是「用更多 Token」，而是**让 AI 成为思考伙伴**。
> 正确姿势：提出想法 → AI 质疑 → 你修改 → AI 再质疑 → 形成方案 → 执行。

---

## 8. 版本边界（明确砍掉什么）

### 8.1 V1.0 必做（Definition of Done）

1. 首页成长 Dashboard 完整实现（§4 全部区块）
2. 今日习惯打点（7 系统）
3. 项目详情页 + Portfolio 视图
4. 财务：记账 + 储蓄率
5. 复盘：日 / 周 / 月
6. AI：至少「生成日报」一个场景打通
7. `goal` 表启用，接通「年→月→周→今日」

### 8.2 V1.0 坚决不做（明确砍掉）

| 不做 | 原因 |
|---|---|
| 知识库 RAG / 向量检索 | 数据量还太小，做了没用（P2） |
| MCP / Agent 自动执行 | 过早引入复杂度（P3） |
| GitHub 自动同步 | 手动记录已够用，自动同步是 V2 |
| 多用户 / 社交 / 分享 | 个人工具，不需要（P3） |
| 移动 App | Web 响应式已够（P3） |
| 自动交易 / 投资建议 | 触碰原则红线（P5） |
| 独立 AI 聊天页 | 违反原则 P4 |
| 花哨的等级/成就动画 | 先有数据，再谈激励 |
| 第三方平台数据抓取（X/公众号） | 合规与维护成本高 |

### 8.3 后续版本规划

| 版本 | 主题 |
|---|---|
| V1.1 | 知识库 `knowledge_item` 接入 + 输出打卡日历 + 项目开发日志 |
| V1.2 | 年度成长档案 + AI 月度/年度报告 |
| V2.0 | GitHub 集成 + MCP/Agent + 数据导出 |
| V2.x | 知识库 RAG + 多模型路由 |

---

## 9. 技术架构

### 9.1 分层

```text
Vue 3 + TS + Vite
  │  REST / JSON（Session 认证）
  ▼
Spring Boot 4.1.1
  ├── auth      登录/登出/me
  ├── dashboard 首页聚合（成长 Dashboard）
  ├── task      今日任务
  ├── habit     习惯打点        ← 新增
  ├── goal      目标层级        ← 启用
  ├── learning  学习记录
  ├── content   内容输出
  ├── project   项目 + 开发日志  ← 扩展
  ├── finance   记账/储蓄/资产    ← 新增
  ├── review    日/周/月/年复盘   ← 启用
  └── ai        AI 网关          ← 新增
  │
  ├── MyBatis（XML Mapper）
  ▼
PostgreSQL 18
```

### 9.2 后端包结构约定

```text
com.myos.<domain>
  ├── XxxController.java     REST 入口，只做参数校验与转发
  ├── XxxMapper.java         MyBatis 接口
  ├── Xxx.java               领域模型（record 或 POJO）
  └── resources/mapper/XxxMapper.xml    SQL
```

### 9.3 前端约定

```text
src/
  ├── api/index.ts    所有接口集中在此，按域分组
  ├── views/          每个模块一个 View
  ├── components/     复用组件（水位条、Lv 卡、进度条等）  ← 新增
  ├── stores/         Pinia
  └── router/index.ts 路由表（6 模块）
```

### 9.4 安全待办（V1 必须修）

> 当前 `AuthController` 是**硬编码演示实现**（`admin/admin123` 写死在代码里，无密码哈希）。V1 必须替换：

- [ ] 密码 bcrypt 哈希，落库校验
- [ ] Session 有效期与过期处理
- [ ] CORS 白名单化
- [ ] 统一异常处理与错误码

---

## 10. 关键交互稿（成长 Dashboard 组件拆分）

```text
DashboardView
├── TrendHeader          日期 + 一句话
├── SystemWaterBars      7 系统水位（今日）      ← habit_log
├── LevelCards           能力等级 （长期）        ← 累计计算
├── CurrentProjectCard   本月项目进度            ← project
├── MonthlyTargets       本月输出 x/30           ← 计数
├── RecentKnowledge      最近沉淀 5 条           ← learning/knowledge
└── AiActionPanel        今日 AI 四入口          ← ai
```

---

## 11. 验收标准（V1.0）

| 编号 | 验收项 |
|---|---|
| A1 | 打开首页，7 系统水位、能力等级、本月项目、本月输出、最近沉淀、AI 入口全部可见且数据正确 |
| A2 | 在今日页完成一次习惯打点，刷新首页后水位条同步变化 |
| A3 | 创建一个项目，填写 Portfolio 全部字段，作品集视图可见 |
| A4 | 录入一笔支出，月度储蓄率自动更新 |
| A5 | 完成一次日复盘并保存 |
| A6 | 点击「生成日报」，得到一段基于真实数据的 AI 日报 |
| A7 | 数据库可通过 `schema.sql` + 增量脚本一键初始化 |

---

## 12. 待用户确认的开放问题

1. **7 系统与 6 模块是否接受 §2 的分工约定**（6 做导航，7 做水位）？
2. **能力等级 Lv 的分档阈值**是否采用 §4.3 的示例值，还是先用更简单的"有记录就 +1"？
3. **AI 模型选型**：V1 先接哪一个（DeepSeek / OpenAI / Claude / 国内模型）？
4. **财务模块货币**：是否 V1 就需要支持美元记账，还是先只记 CNY、美元只在资产快照里体现？
5. **Portfolio 是否需要对外的公开页面**（可分享链接），还是仅本地查看？

---

## 附录 A：与 V0.1 的差距清单

| 模块 | V0.1 现状 | V1.0 目标 | 工作量 |
|---|---|---|---|
| 首页 | 4 个数字 + 硬编码 3 行假数据 | 完整成长 Dashboard | 大 |
| 今日 | 任务 CRUD | + 习惯打点 + AI 入口 | 中 |
| 学习 | 五段式记录 | 基本不动，+ 筛选 | 小 |
| 输出 | 内容记录 | + 打卡日历 + 状态流转 | 中 |
| 项目 | 列表 + 进度条 | + 详情页 + 开发日志 + Portfolio | 大 |
| 财务 | 无 | 记账 + 储蓄率 + 资产快照 | 大 |
| 复盘 | 有表，无 API 无页面 | 日/周/月 + 年度档案 | 大 |
| 目标 | 有表，无 API | 启用年→月→周层级 | 中 |
| AI | 无 | 网关 + 5 个场景 | 大 |
| 认证 | 硬编码 admin/admin123 | bcrypt + 正式 Session | 中 |

## 附录 B：一次行动产生多个结果（核心闭环）

```text
用户完成一个动作
  ↓
① 写入事实数据（task / habit_log / project_log / learning_record）
  ↓
② 更新聚合视图（首页水位 / 能力 Lv / 本月进度）
  ↓
③ 生成可复用产出（日报 / 周报 / Portfolio 条目 / 内容草稿）
  ↓
④ 进入复盘（月复盘 / 年度成长档案）
```

> 这条链路是 MyOS 与普通待办软件的根本区别：**普通软件只做 ①，MyOS 要做 ①→④。**
