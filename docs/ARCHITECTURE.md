# MyOS V1.0 架构

```text
Vue 3 + TS + Vite
  │  REST / JSON（Session 认证）
  ▼
Spring Boot 4.1.1
  ├── auth      登录/登出/me（bcrypt）
  ├── dashboard 首页成长数据聚合（7 系统水位 + 能力 Lv + 本月进度 + 沉淀）
  ├── habit     7 系统每日打点
  ├── task      今日任务
  ├── goal      目标层级（年 → 月 → 周）
  ├── learning  学习记录（五段式）
  ├── content   内容输出 + 打卡日历
  ├── project   项目 + Portfolio + 开发日志
  ├── finance   记账 / 储蓄率 / 资产快照
  ├── review    日 / 周 / 月 / 年复盘
  └── ai        AI 网关（OpenAI 兼容，5 个场景）
  │
  ├── MyBatis（XML Mapper）
  ▼
PostgreSQL 18
```

## 领域模型

```text
User
 ├── Goal（YEAR → MONTH → WEEK 自关联）
 ├── HabitLog        （按天 × 7 系统的唯一打点）
 ├── Task → Project
 ├── Project → ProjectLog
 ├── LearningRecord  （五段式：source/content/understanding/practice/conclusion）
 ├── ContentRecord   （DRAFT → PUBLISHED）
 ├── FinanceRecord   （INCOME / EXPENSE）
 ├── AssetSnapshot   （按月唯一）
 └── Review          （DAY / WEEK / MONTH / YEAR）
```

## 关键设计约定

### 1. 双维度
- **7 大系统**（AI/TECH/CONTENT/ENGLISH/BODY/FINANCE/SOCIAL）→ 首页水位
- **6 大模块**（今日/学习/输出/项目/财务/复盘）→ 侧边栏导航
- 系统编码集中在 `SystemCatalog`，等级阈值集中在 `LevelRule`

### 2. AI 是贯穿层
- `AiGateway` 接口 + `OpenAiCompatGateway` 实现
- V1 只对接一个模型，不做多模型路由（避免过早抽象）
- API Key 只存后端环境变量（`MYOS_AI_API_KEY`），绝不下发前端
- 前端不做独立聊天页，只做场景化按钮

### 3. 后端包结构
```text
com.myos.<domain>
  ├── XxxController.java     REST 入口
  ├── XxxMapper.java         MyBatis 接口
  ├── Xxx.java               领域模型（record）
  └── resources/mapper/XxxMapper.xml    SQL
```

### 4. 数据库脚本执行顺序
```text
schema.sql     基础表（V0.1 已存在）
schema-v1.sql  V1 增量（幂等，可重复执行）
seed-v1.sql    演示数据（幂等）
```

## 安全

- 密码 bcrypt 哈希落库（`spring-security-crypto`，未引入完整 Security 过滤器链）
- Session 认证，`AuthInterceptor` 拦截 `/api/**`
- 统一异常处理：`GlobalExceptionHandler`
- 所有查询按 `user_id` 隔离

## V1 取舍

先打通「打开首页 → 看到 7 系统水位 → 打点 → 数据回流 → AI 生成日报」的核心闭环。
RAG、MCP/Agent、GitHub 自动同步、多用户暂不引入。
