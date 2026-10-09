# MyOS V1.0

个人成长操作系统 / 私人工作台。

**核心命题：一次行动，产生多个结果。**

```
任务 → 项目进度 → 作品集 → 日报 → 月复盘 → 年度成长档案
```

## 技术栈

- Frontend: Vue 3 + TypeScript + Vite + Vue Router + Pinia + Axios
- Backend: Spring Boot 4.1.1 + Java 25 + MyBatis Spring Boot Starter 4.1.0
- Database: PostgreSQL 18
- Deploy: Docker Compose

## 双维度设计（重要）

| 维度 | 内容 | 用途 |
|---|---|---|
| **7 大系统** | AI / 技术 / 内容 / 英语与信息 / 身体 / 财务 / 人际 | 首页**水位条** |
| **6 大模块** | 今日 / 学习 / 输出 / 项目 / 财务 / 复盘 | **侧边栏导航** |

> 约定：6 模块做导航，7 系统做首页水位。

## 快速启动

### 方式 A：本地一键启动（推荐，无需 Docker）

```bash
cd myos-v0.1
bash deploy/dev-up.sh
```

脚本会自动完成四步：启动内置 PostgreSQL → 初始化数据库 → 起后端 → 起前端。
访问 <http://localhost:5173> ，默认账号 `admin` / `admin123`。

前置条件：JDK 25 + Maven 3.9+（后端）、Node 18+（前端），
以及 PostgreSQL 免安装包（默认放在
`C:\Users\Lenovo\.workbuddy-ai\binaries\postgres`，可用 `MYOS_PG_ROOT` 环境变量覆盖）。

需要分步执行或排查问题时：

```bash
bash deploy/local-pg.sh start    # 启动数据库（首次自动 initdb + 建角色建库）
bash deploy/init-db.sh           # 灌数据库结构（幂等，可重复执行）
bash deploy/local-pg.sh stop     # 停止数据库
```

### 方式 B：Docker Compose

```powershell
cd myos-v0.1
docker compose -f deploy/docker-compose.yml up --build
```

前端 <http://localhost:5173> ，后端 <http://localhost:8080>
默认账号 `admin` / `admin123`

> **注意**：数据库初始化脚本只在数据卷为空时自动执行一次。
> 若你之前用旧版 compose 启动过（卷里已有 V0.1 的表），V1 增量脚本不会自动补跑，
> 请手动执行一次：`bash deploy/init-db.sh`（脚本幂等，可放心重复运行）。

### 方式 C：完全手动

适合已有自己的 PostgreSQL 实例、或需要逐步排查的情况。

1. 准备数据库（**必须按此顺序**，脚本幂等可重复执行）：

```bash
psql -U myos -d myos -f myos-server/src/main/resources/db/schema.sql        # 基础表
psql -U myos -d myos -f myos-server/src/main/resources/db/schema-v1.sql     # V1 增量
psql -U myos -d myos -f myos-server/src/main/resources/db/schema-v1.1.sql   # 多端令牌表
psql -U myos -d myos -f myos-server/src/main/resources/db/seed.sql          # 基础演示数据
psql -U myos -d myos -f myos-server/src/main/resources/db/seed-v1.sql       # V1 演示数据
```

2. 后端（需要 **JDK 25 + Maven 3.9+**）：

```bash
cd myos-server
source mvn-env.sh      # 本机便捷脚本，设好 JAVA_HOME 与 PATH；已配好环境可跳过
mvn spring-boot:run
```

3. 前端：

```bash
cd myos-web
npm install
npm run dev
```

## 接口约定（多端就绪）

### 版本前缀

所有接口统一挂在 `/api/v1` 下，为后续接口演进留出空间：

```
/api/v1/auth/login     /api/v1/dashboard     /api/v1/habit
/api/v1/tasks          /api/v1/learning      /api/v1/contents
/api/v1/projects       /api/v1/goals         /api/v1/finance
/api/v1/review         /api/v1/ai            /api/v1/public/health
```

### 认证方式：Bearer 令牌

不使用 Cookie/Session —— Cookie 在微信小程序、桌面端、原生 App 上不可靠。
客户端登录后拿到令牌，之后每次请求在请求头携带：

```
Authorization: Bearer <token>
```

```bash
# 登录拿令牌
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","device":"web"}'

# 带令牌访问
curl http://localhost:8080/api/v1/dashboard -H "Authorization: Bearer <token>"
```

设计要点：

- 令牌**不透明**（随机 32 字节），数据库只存 SHA-256 摘要，不存明文
- 同一账号可签发多份令牌，Web / 小程序 / 桌面 / App 各持一份，
  在任一端登出只撤销该端令牌，不影响其他端
- 默认有效期 30 天
- 跨域已开启（`myos.cors.allowed-origins` 可配，默认 `*`）

### AI 网关配置（可选）

AI 功能默认关闭。配置下面任一环境变量后启用：

```powershell
$env:MYOS_AI_API_KEY="sk-xxxx"           # 必填
$env:MYOS_AI_BASE_URL="https://api.deepseek.com"
$env:MYOS_AI_MODEL="deepseek-chat"
```

未配置时，首页 AI 面板会提示，其余功能不受影响。

## 功能模块

| 模块 | 状态 | 说明 |
|---|---|---|
| 首页 | V1 新增 | 成长 Dashboard：7 系统水位 / 能力 Lv / 本月项目 / 本月完成度 / 最近沉淀 / AI 四入口 |
| 今日 | 升级 | 任务 CRUD + **7 系统习惯打点** |
| 学习 | 升级 | 五段式记录（来源→理解→实践→结论）+ 系统筛选 |
| 输出 | 升级 | 内容记录 + **打卡日历** + 草稿/发布流转 |
| 项目 | 升级 | 列表 + **Portfolio 作品集** + 详情页 + **开发日志** |
| 财务 | V1 新增 | 现金流 / 储蓄率 / 支出构成 / **资产快照** |
| 复盘 | V1 新增 | 日 / 周 / 月复盘 + AI 月度总结 |

## API 一览

| 前缀 | 说明 |
|---|---|
| `/api/auth` | 登录 / 登出 / 当前用户（bcrypt） |
| `/api/dashboard` | 首页成长数据聚合 |
| `/api/habit` | 7 系统打点 |
| `/api/tasks` | 今日任务 |
| `/api/learning` | 学习记录 |
| `/api/contents` | 内容输出 + 打卡日历 |
| `/api/projects` | 项目 + Portfolio + 开发日志 |
| `/api/goals` | 目标层级（年→月→周） |
| `/api/finance` | 记账 / 储蓄率 / 资产快照 |
| `/api/review` | 日 / 周 / 月 / 年复盘 |
| `/api/ai` | AI 场景入口（需要配置 Key） |

## 文档

- [PRD V1.0](docs/PRD-V1.0.md) — 产品需求文档
- [PRD V1.0 (HTML)](docs/PRD-V1.0.html) — 可阅读版
- [设计预览](docs/preview-dashboard.html) — 首页 Dashboard 静态预览
- [架构说明](docs/ARCHITECTURE.md)

## 设计原则

1. **不再增加目标** —— 只做「让已存在的方向持续发生」的系统
2. **记录优先于规划** —— 先有真实数据，再谈 AI 分析
3. **减法优先** —— 不做它核心闭环会断吗？不会就不做
4. **AI 是贯穿层，不是独立页面**
5. **只记录，不替用户决策** —— 财务不做任何投资建议
6. **写下来 > 做完美** —— 不追求爆款，只追求留下痕迹
7. **数据属于用户** —— 本地 PostgreSQL，可随时导出

## 版本边界

V1.0 **不做**：知识库 RAG、MCP/Agent 自动执行、GitHub 自动同步、多用户、移动 App、自动交易、独立 AI 聊天页。
