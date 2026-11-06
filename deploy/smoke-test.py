#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MyOS 后端端到端冒烟测试

目的：验证后端在「真实数据库」上确实跑得通 —— 这是编译打包证明不了的部分。
      每个模块的读写都过一遍，把 MyBatis 里那些 PostgreSQL 专有写法
      （ON CONFLICT / ::int / EXTRACT / pg_get_serial_sequence …）真正执行一次。

用法：
    python deploy/smoke-test.py
    MYOS_BASE=http://localhost:8080/api/v1 python deploy/smoke-test.py

前置：
    1) PostgreSQL 已启动            bash deploy/local-pg.sh start
    2) 库结构与演示数据已灌入        bash deploy/init-db.sh
    3) 后端已在 8080 运行

退出码：全部通过为 0，有失败为 1（可直接接进 CI）。
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date, timedelta

BASE = os.environ.get("MYOS_BASE", "http://localhost:8080/api/v1")
USERNAME = os.environ.get("MYOS_USER", "admin")
PASSWORD = os.environ.get("MYOS_PWD", "admin123")

TODAY = date.today().isoformat()
WEEK_AGO = (date.today() - timedelta(days=6)).isoformat()
MONTH = date.today().strftime("%Y-%m-01")
TAG = "[smoke-" + uuid.uuid4().hex[:6] + "]"

PASS, FAIL = [], []


# ----------------------------------------------------------------------
# 基础请求封装
# ----------------------------------------------------------------------
def call(method, path, body=None, token=None, raw_query=""):
    """发一个请求，返回 (http_status, envelope_dict_or_None, raw_text)"""
    url = BASE + path + raw_query
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            text = r.read().decode("utf-8", "replace")
            status = r.status
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        status = e.code
    except Exception as e:                                    # 连不上
        return None, None, str(e)

    try:
        return status, json.loads(text), text
    except Exception:
        return status, None, text


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append(name)
    print(("  \u2713 " if ok else "  \u2717 ") + name + (("  \u2014 " + detail) if detail else ""))
    return ok


def ok_envelope(env):
    return isinstance(env, dict) and env.get("success") is True


def section(title):
    print("\n" + title)


# ----------------------------------------------------------------------
# 0. 连通性 & 认证
# ----------------------------------------------------------------------
section("[0] 连通性与认证")

status, env, text = call("GET", "/public/health")
if not check("健康检查 /public/health 可达", status == 200,
             "" if status == 200 else "HTTP %s %s" % (status, text[:120])):
    print("\n后端不可达，后续检查无意义。请确认后端已在 8080 启动。")
    sys.exit(1)

status, env, text = call("GET", "/dashboard")
check("无令牌访问 /dashboard 返回 401", status == 401, "HTTP %s" % status)

status, env, text = call("POST", "/auth/login",
                         {"username": USERNAME, "password": PASSWORD, "device": "smoke"})
login_ok = status == 200 and ok_envelope(env)
check("登录 /auth/login（%s / %s）" % (USERNAME, PASSWORD), login_ok,
      "" if login_ok else "HTTP %s %s" % (status, text[:200]))
if not login_ok:
    print("\n登录失败，无法继续。若是「用户名或密码错误」，检查种子数据里的 bcrypt 哈希。")
    sys.exit(1)

TOKEN = env["data"]["token"]
check("登录返回令牌与过期时间",
      bool(TOKEN) and bool(env["data"].get("expiresAt")),
      "token 长度 %d" % len(TOKEN))

status, env, _ = call("GET", "/auth/me", token=TOKEN)
check("当前用户 /auth/me", status == 200 and ok_envelope(env),
      "nickname=%s" % (env["data"].get("nickname") if ok_envelope(env) else "?"))


# ----------------------------------------------------------------------
# 1. 首页 Dashboard（聚合查询，最容易暴露 SQL 问题）
# ----------------------------------------------------------------------
section("[1] 首页 Dashboard")
status, env, text = call("GET", "/dashboard", token=TOKEN)
if check("GET /dashboard", status == 200 and ok_envelope(env),
         "" if status == 200 else text[:160]):
    d = env["data"]
    check("返回 7 个系统水位条", len(d.get("waterBars") or []) == 7,
          "实际 %d" % len(d.get("waterBars") or []))
    check("返回 7 个能力等级", len(d.get("levels") or []) == 7,
          "实际 %d" % len(d.get("levels") or []))
    check("每个等级都有 Lv（无遗漏配置）",
          all((lv or {}).get("level") is not None for lv in (d.get("levels") or [])),
          "含 SOCIAL 漏配检测")


# ----------------------------------------------------------------------
# 2. 今日习惯打点
# ----------------------------------------------------------------------
section("[2] 今日习惯打点（7 系统）")
status, env, text = call("GET", "/habit/systems", token=TOKEN)
check("GET /habit/systems", status == 200 and ok_envelope(env),
      "%d 个系统" % len(env["data"]) if ok_envelope(env) else text[:120])

status, env, text = call("GET", "/habit", token=TOKEN, raw_query="?date=" + TODAY)
check("GET /habit?date=today", status == 200 and ok_envelope(env), text[:120])

status, env, text = call("PUT", "/habit", {
    "logDate": TODAY,
    "items": [
        {"systemCode": "AI", "metricValue": 30, "targetValue": 60, "done": False, "note": TAG},
        {"systemCode": "TECH", "metricValue": 60, "targetValue": 60, "done": True, "note": TAG},
    ],
}, token=TOKEN)
check("PUT /habit（写入打点，走 upsert）", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("GET", "/habit/range", token=TOKEN,
                         raw_query="?from=%s&to=%s" % (WEEK_AGO, TODAY))
check("GET /habit/range（区间查询）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 3. 今日任务
# ----------------------------------------------------------------------
section("[3] 今日任务")
status, env, text = call("GET", "/tasks", token=TOKEN, raw_query="?date=" + TODAY)
check("GET /tasks?date=today", status == 200 and ok_envelope(env), text[:120])

# ----------------------------------------------------------------------
# 回归：逾期未完成的任务必须仍出现在「今日」列表里。
#
# 曾经的口径是 due_date = 今天，导致逾期没做完的任务一到次日就彻底查不到 ——
# 周一没干完，周二从「今日」页消失；首页完成率也照旧只算今天；
# AI 读快照时同样漏掉，于是一直汇报「任务完成 0/0」。
# 这条 SQL 当时在三处各抄了一份，只改一处会静默失配，所以必须留回归。
# ----------------------------------------------------------------------
OVERDUE = (date.today() - timedelta(days=3)).isoformat()
status, env, text = call("POST", "/tasks", {
    "title": TAG + " 逾期任务", "description": "回归测试，跑完会删",
    "priority": "HIGH", "dueDate": OVERDUE,
}, token=TOKEN)
overdue_ok = status == 200 and ok_envelope(env)
check("POST /tasks（逾期任务，回归用）", overdue_ok, text[:160])
if overdue_ok:
    oid = env["data"]["id"]
    # 再建一条「今天到期」的作对照，否则列表里没有可比对象，排序检查等于空跑
    status2, env2, text2 = call("POST", "/tasks", {
        "title": TAG + " 今日到期任务", "description": "排序对照，跑完会删",
        "priority": "LOW", "dueDate": TODAY,
    }, token=TOKEN)
    cid = env2["data"]["id"] if status2 == 200 and ok_envelope(env2) else None

    status, env, text = call("GET", "/tasks", token=TOKEN, raw_query="?date=" + TODAY)
    rows = env.get("data") or []
    hit = [t for t in rows if t["id"] == oid]
    check("逾期未完成的任务仍出现在今日列表", len(hit) == 1,
          "建了 1 条 3 天前截止的任务，今日列表 %d 条" % len(rows))
    if hit and cid is not None:
        pos_overdue = rows.index(hit[0])
        pos_today = [i for i, t in enumerate(rows) if t["id"] == cid]
        check("逾期任务排在今天到期项之前",
              pos_today and pos_overdue < pos_today[0],
              "逾期项第 %d 位，今日到期项第 %s 位"
              % (pos_overdue, pos_today[0] if pos_today else "-"))
    if cid is not None:
        status, env, text = call("DELETE", "/tasks/%s" % cid, token=TOKEN)
        check("DELETE /tasks/{id}（对照任务清理）", status == 200 and ok_envelope(env), text[:160])

    status, env, text = call("DELETE", "/tasks/%s" % oid, token=TOKEN)
    check("DELETE /tasks/{id}（逾期任务清理）", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("POST", "/tasks", {
    "title": TAG + " 冒烟任务", "description": "端到端验证用，跑完会删",
    "priority": "LOW", "dueDate": TODAY,
}, token=TOKEN)
task_ok = status == 200 and ok_envelope(env)
check("POST /tasks（新建）", task_ok, text[:160])
if task_ok:
    tid = env["data"]["id"]
    status, env, text = call("PUT", "/tasks/%s/status" % tid, token=TOKEN,
                             raw_query="?status=DONE")
    check("PUT /tasks/{id}/status（流转）", status == 200 and ok_envelope(env), text[:160])
    # 建了就必须能删。原先没有 DELETE /tasks/{id}，于是每次冒烟都在库里
    # 留下一条 [smoke-xxx] 任务，还会被 AI 日报当成真实数据读进去。
    status, env, text = call("DELETE", "/tasks/%s" % tid, token=TOKEN)
    check("DELETE /tasks/{id}（清理）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 4. 学习记录（五段式）
# ----------------------------------------------------------------------
section("[4] 学习记录")
status, env, text = call("GET", "/learning", token=TOKEN)
check("GET /learning", status == 200 and ok_envelope(env), text[:120])

status, env, text = call("POST", "/learning", {
    "title": TAG + " 冒烟学习记录", "category": "AI", "systemCode": "AI",
    "source": "smoke-test", "content": "验证五段式写入",
    "understanding": "字段齐全", "practice": "写一条真数据",
    "conclusion": "SQL 可执行", "durationMinutes": 10,
}, token=TOKEN)
learn_ok = status == 200 and ok_envelope(env)
check("POST /learning（五段式写入）", learn_ok, text[:160])
if learn_ok:
    lid = env["data"]["id"]
    status, env, text = call("DELETE", "/learning/%s" % lid, token=TOKEN)
    check("DELETE /learning/{id}（清理）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 5. 内容输出（含本轮新增的 quote 字段）
# ----------------------------------------------------------------------
section("[5] 内容输出（含 quote 语录字段）")
status, env, text = call("GET", "/contents", token=TOKEN)
check("GET /contents", status == 200 and ok_envelope(env), text[:120])

status, env, text = call("POST", "/contents", {
    "title": TAG + " 冒烟输出", "platform": "X", "content": "验证内容写入",
    "quote": "先完成，再完美。", "status": "DRAFT",
}, token=TOKEN)
content_ok = status == 200 and ok_envelope(env)
check("POST /contents（带 quote）", content_ok, text[:200])
if content_ok:
    cid = env["data"]["id"]
    check("quote 字段正确回存", env["data"].get("quote") == "先完成，再完美。",
          "实际 %r" % env["data"].get("quote"))

    status, env, text = call("PUT", "/contents/%s" % cid, {
        "title": TAG + " 冒烟输出（已改）", "platform": "X",
        "content": "验证更新", "quote": "改过的语录。",
    }, token=TOKEN)
    check("PUT /contents/{id}（更新）", status == 200 and ok_envelope(env), text[:160])

    status, env, text = call("PUT", "/contents/%s/status" % cid, token=TOKEN,
                             raw_query="?status=PUBLISHED")
    check("PUT /contents/{id}/status（发布）", status == 200 and ok_envelope(env), text[:160])

    status, env, text = call("DELETE", "/contents/%s" % cid, token=TOKEN)
    check("DELETE /contents/{id}（清理）", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("GET", "/contents/calendar", token=TOKEN)
check("GET /contents/calendar（EXTRACT 分组统计）",
      status == 200 and ok_envelope(env), text[:200])


# ----------------------------------------------------------------------
# 6. 项目（含 Portfolio 与开发日志）
# ----------------------------------------------------------------------
section("[6] 项目与 Portfolio")
status, env, text = call("GET", "/projects", token=TOKEN)
check("GET /projects", status == 200 and ok_envelope(env), text[:120])

status, env, text = call("GET", "/projects/portfolio", token=TOKEN)
check("GET /projects/portfolio", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("POST", "/projects", {
    "name": TAG + " 冒烟项目", "description": "端到端验证用",
    "status": "PLANNED", "progress": 0,
    "startDate": TODAY, "targetDate": TODAY,
}, token=TOKEN)
proj_ok = status == 200 and ok_envelope(env)
check("POST /projects（新建）", proj_ok, text[:200])
if proj_ok:
    pid = env["data"]["id"]
    status, env, text = call("GET", "/projects/%s" % pid, token=TOKEN)
    check("GET /projects/{id}（详情）", status == 200 and ok_envelope(env), text[:160])

    status, env, text = call("PUT", "/projects/%s/progress" % pid, token=TOKEN,
                             raw_query="?value=50")
    check("PUT /projects/{id}/progress", status == 200 and ok_envelope(env), text[:160])

    status, env, text = call("POST", "/projects/%s/logs" % pid, {
        "logDate": TODAY, "content": TAG + " 开发日志", "commitRef": "smoke000",
    }, token=TOKEN)
    check("POST /projects/{id}/logs", status == 200 and ok_envelope(env), text[:160])

    status, env, text = call("GET", "/projects/%s/logs" % pid, token=TOKEN)
    check("GET /projects/{id}/logs", status == 200 and ok_envelope(env), text[:160])

    # 「一次行动产生多个结果」链路的收口：输出能挂到项目，项目能反查出产出。
    # 这两条一起才有意义 —— 只测写不测读，project_id 拼错也发现不了。
    status, env, text = call("POST", "/contents", {
        "title": TAG + " 挂项目的输出", "platform": "X", "content": "验证归属",
        "status": "DRAFT", "projectId": pid,
    }, token=TOKEN)
    bind_ok = status == 200 and ok_envelope(env)
    check("POST /contents（带 projectId 归属）", bind_ok, text[:200])
    if bind_ok:
        bind_cid = env["data"]["id"]
        check("projectId 正确回存", env["data"].get("projectId") == pid,
              "实际 %r" % env["data"].get("projectId"))

        status, env, text = call("GET", "/projects/%s" % pid, token=TOKEN)
        got = []
        if status == 200 and ok_envelope(env):
            got = (env.get("data") or {}).get("contents") or []
        check("项目详情能反查出产出的内容（LEFT JOIN）",
              any(c.get("id") == bind_cid for c in got),
              "产出 %d 条" % len(got))
        # projectName 来自 join，能证明关联真的在库里而不只是回了入参
        check("产出内容带出项目名",
              any(c.get("projectName") == TAG + " 冒烟项目" for c in got),
              str([c.get("projectName") for c in got]))

        status, env, text = call("DELETE", "/contents/%s" % bind_cid, token=TOKEN)
        check("DELETE /contents（清理归属内容）", status == 200 and ok_envelope(env), text[:160])

    # 不存在的项目 id 必须是「友好拒绝」而不是撞外键 500
    status, env, text = call("POST", "/contents", {
        "title": TAG + " 非法归属", "platform": "X", "content": "x", "projectId": 99999999,
    }, token=TOKEN)
    check("非法 projectId 被拒绝（不是 500）",
          status == 200 and not ok_envelope(env), text[:160])

    status, env, text = call("DELETE", "/projects/%s" % pid, token=TOKEN)
    check("DELETE /projects/{id}（清理）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 7. 目标 → 项目 → 任务链路（自动进度）
#
# 这一段测的是产品命题本身：一次行动产生多个结果。
# 链路上的任何一环断了（任务挂不上项目、进度不跟着任务走、
# 目标拉不出项目），首页那块「目标链路」就是装饰品而不是机制。
# ----------------------------------------------------------------------
section("[7] 目标 → 项目 → 任务链路")

status, env, text = call("POST", "/goals", {
    "title": TAG + " 年度目标", "level": "YEAR",
}, token=TOKEN)
goal_ok = status == 200 and ok_envelope(env)
check("POST /goals（年度目标）", goal_ok, text[:160])
gid = env["data"]["id"] if goal_ok else None

if goal_ok:
    status, env, text = call("POST", "/projects", {
        "name": TAG + " 链路项目", "description": "验证自动进度",
        "status": "IN_PROGRESS", "progress": 0, "progressMode": "AUTO",
        "goalId": gid,
    }, token=TOKEN)
    link_ok = status == 200 and ok_envelope(env)
    check("POST /projects（带 goalId）", link_ok, text[:200])

    if link_ok:
        lpid = env["data"]["id"]
        check("goalId 正确回存", env["data"].get("goalId") == gid,
              "实际 %r" % env["data"].get("goalId"))

        # 任务直接挂项目 —— 链路上最短的一步
        status, env, text = call("POST", "/tasks", {
            "title": TAG + " 链路任务1", "priority": "MEDIUM",
            "dueDate": TODAY, "projectId": lpid,
        }, token=TOKEN)
        t1_ok = status == 200 and ok_envelope(env)
        check("POST /tasks（带 projectId）", t1_ok, text[:160])
        lt1 = env["data"]["id"] if t1_ok else None

        status, env, text = call("POST", "/tasks", {
            "title": TAG + " 链路任务2", "priority": "MEDIUM",
            "dueDate": TODAY, "projectId": lpid,
        }, token=TOKEN)
        lt2 = env["data"]["id"] if status == 200 and ok_envelope(env) else None

        def refetch():
            _, e, _ = call("GET", "/projects/%s" % lpid, token=TOKEN)
            return (e.get("data") or {}).get("project") or {}

        proj = refetch()
        check("自动进度：0/2 完成 → 0%",
              proj.get("progress") == 0 and proj.get("taskTotal") == 2,
              "progress=%r taskTotal=%r" % (proj.get("progress"), proj.get("taskTotal")))

        call("PUT", "/tasks/%s/status" % lt1, token=TOKEN, raw_query="?status=DONE")
        proj = refetch()
        check("自动进度：1/2 完成 → 50%",
              proj.get("progress") == 50 and proj.get("taskDone") == 1,
              "progress=%r taskDone=%r" % (proj.get("progress"), proj.get("taskDone")))

        # 解绑必须真的把任务移出去：projectId 省略 = 脱离，而不是「不改」
        call("PUT", "/tasks/%s/project" % lt2, token=TOKEN)
        proj = refetch()
        check("解绑任务后自动进度重算（1/1 → 100%）",
              proj.get("progress") == 100 and proj.get("taskTotal") == 1,
              "progress=%r taskTotal=%r" % (proj.get("progress"), proj.get("taskTotal")))

        # 手动进度：会顺带把模式切成 MANUAL，否则任务一变数就被自动值盖掉
        call("PUT", "/projects/%s/progress" % lpid, token=TOKEN, raw_query="?value=30")
        proj = refetch()
        check("手动推进后模式变 MANUAL 且值为 30",
              proj.get("progress") == 30 and proj.get("progressMode") == "MANUAL",
              "progress=%r mode=%r" % (proj.get("progress"), proj.get("progressMode")))

        call("PUT", "/projects/%s" % lpid, {
            "name": TAG + " 链路项目", "description": "验证自动进度",
            "status": "IN_PROGRESS", "progressMode": "AUTO", "goalId": gid,
        }, token=TOKEN)
        proj = refetch()
        check("切回 AUTO 后进度重新由任务决定（100%）",
              proj.get("progress") == 100 and proj.get("progressMode") == "AUTO",
              "progress=%r mode=%r" % (proj.get("progress"), proj.get("progressMode")))

        # 首页把「年目标 → 项目」接出来，否则 goal 表等于在空转
        status, env, text = call("GET", "/dashboard", token=TOKEN)
        chain = ((env or {}).get("data") or {}).get("goalChain") or []
        node = next((g for g in chain if g.get("id") == gid), None)
        check("首页 goalChain 带出该年度目标", node is not None,
              "共 %d 个目标" % len(chain))
        check("goalChain 把项目挂在目标下",
              bool(node) and any(p.get("id") == lpid for p in node.get("projects") or []),
              str([p.get("id") for p in (node or {}).get("projects") or []]))

        # 不存在的项目 id 必须友好拒绝 —— 否则前端下拉一旦传错就是 500
        status, env, text = call("PUT", "/tasks/%s/project" % lt1, token=TOKEN,
                                 raw_query="?projectId=99999999")
        check("非法 projectId 被拒绝（不是 500）",
              status == 200 and not ok_envelope(env), text[:160])

        # 清理：任务 → 项目 → 目标（顺序不能反，外键挡着）
        for tid in (lt1, lt2):
            if tid:
                call("DELETE", "/tasks/%s" % tid, token=TOKEN)
        call("DELETE", "/projects/%s" % lpid, token=TOKEN)
    call("DELETE", "/goals/%s" % gid, token=TOKEN)


# ----------------------------------------------------------------------
# 8. 目标层级（年 → 月 → 周）
# ----------------------------------------------------------------------
section("[8] 目标层级")
status, env, text = call("GET", "/goals", token=TOKEN)
check("GET /goals", status == 200 and ok_envelope(env), text[:120])

status, env, text = call("GET", "/goals/tree", token=TOKEN)
check("GET /goals/tree（递归树）", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("POST", "/goals", {
    "title": TAG + " 冒烟目标", "level": "MONTH", "systemCode": "TECH",
    "category": "TECH", "targetValue": 1, "startDate": TODAY, "endDate": TODAY,
}, token=TOKEN)
goal_ok = status == 200 and ok_envelope(env)
check("POST /goals（新建）", goal_ok, text[:200])
if goal_ok:
    gid = env["data"]["id"]
    status, env, text = call("PUT", "/goals/%s/progress" % gid, token=TOKEN,
                             raw_query="?value=40")
    check("PUT /goals/{id}/progress", status == 200 and ok_envelope(env), text[:160])
    status, env, text = call("DELETE", "/goals/%s" % gid, token=TOKEN)
    check("DELETE /goals/{id}（清理）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 9. 财务
# ----------------------------------------------------------------------
section("[9] 财务记账与资产")
status, env, text = call("GET", "/finance/records", token=TOKEN)
check("GET /finance/records", status == 200 and ok_envelope(env), text[:120])

status, env, text = call("GET", "/finance/overview", token=TOKEN)
check("GET /finance/overview（储蓄率聚合）",
      status == 200 and ok_envelope(env), text[:160])

status, env, text = call("POST", "/finance/records", {
    "recordDate": TODAY, "recordType": "EXPENSE", "category": "测试",
    "amount": 1.23, "currency": "CNY", "note": TAG,
}, token=TOKEN)
fin_ok = status == 200 and ok_envelope(env)
check("POST /finance/records（记账）", fin_ok, text[:200])
if fin_ok:
    fid = env["data"]["id"]
    status, env, text = call("DELETE", "/finance/records/%s" % fid, token=TOKEN)
    check("DELETE /finance/records/{id}（清理）", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("GET", "/finance/assets", token=TOKEN)
check("GET /finance/assets", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("PUT", "/finance/assets", {
    "snapshotMonth": MONTH, "cash": 50000, "cnyAssets": 120000,
    "usdAssets": 30000, "otherAssets": 10000, "note": TAG,
}, token=TOKEN)
check("PUT /finance/assets（快照 upsert）", status == 200 and ok_envelope(env), text[:200])


# ----------------------------------------------------------------------
# 10. 复盘（含 metricsJson）
# ----------------------------------------------------------------------
section("[10] 复盘")
status, env, text = call("GET", "/review", token=TOKEN, raw_query="?type=DAY&limit=10")
check("GET /review?type=DAY", status == 200 and ok_envelope(env), text[:120])

status, env, text = call("GET", "/review/current", token=TOKEN, raw_query="?type=DAY")
check("GET /review/current?type=DAY", status == 200 and ok_envelope(env), text[:160])
# 先把今天原有的复盘备份下来，跑完还原 —— 否则冒烟测试会把用户当天写的复盘冲掉
review_backup = (env.get("data") or {}).get("existing") if ok_envelope(env) else None

status, env, text = call("PUT", "/review", {
    "reviewType": "DAY", "reviewDate": TODAY, "completed": True,
    "goodThings": TAG + " 冒烟：写通复盘",
    "badThings": "无", "wastedTime": "无", "unfinished": "无",
    "biggestGain": "验证了 ON CONFLICT DO UPDATE 生效",
    "nextFocus": "接前端", "aiSummary": None,
    "metricsJson": json.dumps({"mood": 4, "focus": 3, "completion": 75}),
}, token=TOKEN)
check("PUT /review（upsert，含 metricsJson）",
      status == 200 and ok_envelope(env), text[:200])

status, env, text = call("GET", "/review/current", token=TOKEN, raw_query="?type=DAY")
saved_metrics = (env.get("data") or {}).get("existing", {}).get("metricsJson") if ok_envelope(env) else None
check("metricsJson 正确回存（证明 ON CONFLICT 真的 UPDATE 了）",
      bool(saved_metrics) and "mood" in str(saved_metrics), "实际 %r" % saved_metrics)

# 还原现场：原本有就写回去，原本没有就删掉
if review_backup:
    restore = {k: review_backup.get(k) for k in (
        "goodThings", "badThings", "wastedTime", "unfinished",
        "biggestGain", "nextFocus", "metricsJson")}
    restore["reviewType"] = "DAY"
    restore["reviewDate"] = TODAY
    status, env, text = call("PUT", "/review", restore, token=TOKEN)
    check("还原原有复盘（不留测试内容）", status == 200 and ok_envelope(env), text[:160])
else:
    status, env, text = call("DELETE", "/review", token=TOKEN,
                             raw_query="?type=DAY&date=" + TODAY)
    check("DELETE /review（清理）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 11. 数据导出
# ----------------------------------------------------------------------
section("[11] 数据导出")

# 未登录必须拿不到 —— 导出的是全部个人数据，这里漏了等于把整库公开
status, env, text = call("GET", "/export")
check("GET /export 未登录返回 401", status == 401, "HTTP %s" % status)

status, env, text = call("GET", "/export", token=TOKEN)
check("GET /export", status == 200 and isinstance(env, dict) and "data" in env, text[:120])

if status == 200 and isinstance(env, dict):
    payload = env
    meta = payload.get("meta") or {}
    data = payload.get("data") or {}
    counts = meta.get("counts") or {}

    check("导出含 meta（时间/用户/计数）",
          bool(meta.get("exportedAt")) and meta.get("userId") is not None,
          json.dumps({k: meta.get(k) for k in ("exportedAt", "userId")}, ensure_ascii=False))

    # 15 = 原 14 + aiOutputs（v1.8 让场景产出落库，属于要能带走的资产）
    check("导出含 15 个数据集合",
          len(data) == 15, "实际 %d 个：%s" % (len(data), ",".join(sorted(data))))

    check("没有取不到的表",
          not meta.get("failedTables"),
          "失败表：%s" % meta.get("failedTables"))

    # counts 必须和实际条数一致，否则「看起来成功、实际少了一半」
    mismatch = [k for k, v in counts.items() if len(data.get(k) or []) != v]
    check("counts 与实际条数一致", not mismatch, "不一致：%s" % mismatch)

    # 与列表接口对得上：/tasks 是「今日视图」（当天 + 无日期 + 逾期未完成），
    # 导出是全量，所以只能验「包含」而不是「相等」——
    # 写成相等会误判成 bug（第一版就踩了：导出 14 条、视图 13 条）。
    status, env2, text2 = call("GET", "/tasks", token=TOKEN)
    api_tasks = env2.get("data") or [] if ok_envelope(env2) else []
    exported_ids = {t.get("id") for t in (data.get("tasks") or [])}
    missing = [t.get("id") for t in api_tasks if t.get("id") not in exported_ids]
    check("导出的任务覆盖今日视图的全部任务",
          bool(api_tasks) and not missing,
          "视图中 %d 条，缺失 %s" % (len(api_tasks), missing or "无"))

    # 🔴 安全红线：凭据绝不能出现在导出文件里
    lowered = text.lower()
    leaked = [w for w in ("password_hash", "passwordhash", "$2a$", "user_token", "token_id") if w in lowered]
    check("导出不含口令哈希/令牌", not leaked, "命中：%s" % leaked)

    # 内容归属字段应该被带上（v1.6 的链路要能跟着数据一起走）
    first_content = (data.get("contentRecords") or [{}])[0]
    check("导出的内容记录带 projectId 字段",
          "project_id" in first_content or "projectId" in first_content,
          "字段：%s" % ",".join(list(first_content)[:6]))


# ----------------------------------------------------------------------
# 12. AI 网关（未配 Key 时应优雅降级，不是崩溃）
# ----------------------------------------------------------------------
section("[12] AI 网关")
status, env, text = call("GET", "/ai/status", token=TOKEN)
check("GET /ai/status", status == 200 and ok_envelope(env),
      "available=%s（未配 Key 时为 false 属正常）"
      % (env["data"].get("available") if ok_envelope(env) else "?"))

# 场景产出存档：没有 Key 也能读（空列表），关键是这个接口在、且按用户隔离。
# 生成侧要真调模型，属于 test-ai-chat.py 的范围，不进冒烟（耗 token）。
status, env, text = call("GET", "/ai/outputs", token=TOKEN)
check("GET /ai/outputs（产出存档可读）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 13. 用户管理（仅管理员）
# ----------------------------------------------------------------------
section("[13] 用户管理（仅管理员）")

status, env, text = call("GET", "/admin/users", token=TOKEN)
admin_ok = status == 200 and ok_envelope(env)
check("GET /admin/users（管理员可查）", admin_ok, text[:120])

USERS = (env.get("data") or {}).get("users") or [] if admin_ok else []
check("用户列表不含口令哈希",
      all("password_hash" not in u and "passwordHash" not in u for u in USERS),
      "字段：%s" % (",".join(USERS[0].keys()) if USERS else "-"))

new_username = "smoke" + str(int(time.time()))[-6:]
status, env, text = call("POST", "/admin/users",
                         {"username": new_username, "nickname": TAG + " 测试员"}, token=TOKEN)
created = status == 200 and ok_envelope(env)
check("POST /admin/users（建号并自动生成密码）", created, text[:200])

new_uid = None
new_pwd = None
if created:
    new_uid = env["data"]["id"]
    new_pwd = env["data"].get("plainPassword")
    check("返回一次性明文密码", bool(new_pwd) and len(new_pwd) >= 8,
          "长度 %d" % (len(new_pwd) if new_pwd else 0))

    # 新用户能登录，且看不到别人的数据（隔离）
    status, env2, text2 = call("POST", "/auth/login",
                              {"username": new_username, "password": new_pwd})
    check("新用户可登录", status == 200 and ok_envelope(env2), text2[:140])
    if ok_envelope(env2):
        sub_token = env2["data"]["token"]

        status, env3, _ = call("GET", "/projects", token=sub_token)
        check("新用户看不到他人项目（隔离）",
              status == 200 and ok_envelope(env3) and len(env3["data"]) == 0,
              "项目 %d 条" % (len(env3.get("data") or []) if ok_envelope(env3) else -1))

        # 🔴 越权红线：普通用户绝不能调用管理接口
        for m, path, body in (("GET", "/admin/users", None),
                              ("POST", "/admin/users", {"username": "hijack" + new_username})):
            status, env4, text4 = call(m, path, body, token=sub_token)
            check("%s %s 普通用户被拒" % (m, path),
                  status == 200 and not ok_envelope(env4), text4[:120])

        # 停用后：令牌立即失效 + 登录被拒
        status, env5, text5 = call("PUT", "/admin/users/%s/status" % new_uid,
                                   token=TOKEN, raw_query="?status=DISABLED")
        check("停用账号", status == 200 and ok_envelope(env5), text5[:140])

        status, _, _ = call("GET", "/dashboard", token=sub_token)
        check("停用后旧令牌立即失效（401）", status == 401, "HTTP %s" % status)

        status, env6, text6 = call("POST", "/auth/login",
                                   {"username": new_username, "password": new_pwd})
        check("停用后无法登录", status == 200 and not ok_envelope(env6), text6[:120])

    # 不能停用自己
    me_id = next((u["id"] for u in USERS if u["username"] == USERNAME), None)
    if me_id:
        status, env7, text7 = call("PUT", "/admin/users/%s/status" % me_id,
                                   token=TOKEN, raw_query="?status=DISABLED")
        check("不能停用自己", status == 200 and not ok_envelope(env7), text7[:120])

    # 用户名重复 / 非法格式
    status, env8, text8 = call("POST", "/admin/users", {"username": new_username}, token=TOKEN)
    check("重复用户名被拒", status == 200 and not ok_envelope(env8), text8[:120])

    status, env9, text9 = call("POST", "/admin/users", {"username": TAG + "bad"}, token=TOKEN)
    check("非法用户名被拒", status == 200 and not ok_envelope(env9), text9[:120])

    # 清理：删掉测试用户（停用接口不删数据，这里只清测试账号）
    if new_uid:
        call("DELETE", "/admin/users/%s" % new_uid, token=TOKEN)


# ----------------------------------------------------------------------
# 14. 修改密码（放在最后：它会改口令，不能影响前面用例）
# ----------------------------------------------------------------------
section("[14] 修改密码")

NEW_PWD = PASSWORD + "-smoke"

# 未登录不能改
status, env, text = call("POST", "/auth/password",
                         {"oldPassword": PASSWORD, "newPassword": NEW_PWD})
check("未登录改密码返回 401", status == 401, "HTTP %s" % status)

# 旧密码错必须被拒 —— 否则拿到令牌就能锁死本人
status, env, text = call("POST", "/auth/password",
                         {"oldPassword": "definitely-wrong", "newPassword": NEW_PWD}, token=TOKEN)
check("原密码不正确时被拒",
      status == 200 and not ok_envelope(env) and "原密码" in (env or {}).get("message", ""),
      text[:120])

# 新密码太短
status, env, text = call("POST", "/auth/password",
                         {"oldPassword": PASSWORD, "newPassword": "abc"}, token=TOKEN)
check("新密码少于 8 位被拒",
      status == 200 and not ok_envelope(env), text[:120])

# 新旧相同
status, env, text = call("POST", "/auth/password",
                         {"oldPassword": PASSWORD, "newPassword": PASSWORD}, token=TOKEN)
check("新旧密码相同被拒",
      status == 200 and not ok_envelope(env), text[:120])

# 正常改
status, env, text = call("POST", "/auth/password",
                         {"oldPassword": PASSWORD, "newPassword": NEW_PWD}, token=TOKEN)
changed = status == 200 and ok_envelope(env)
check("POST /auth/password（正常修改）", changed, text[:160])

if changed:
    # 旧密码失效
    status, env2, _ = call("POST", "/auth/login",
                           {"username": USERNAME, "password": PASSWORD})
    check("旧密码已失效", status == 200 and not ok_envelope(env2), "登录应失败")

    # 新密码可登录
    status, env2, _ = call("POST", "/auth/login",
                           {"username": USERNAME, "password": NEW_PWD})
    check("新密码可登录", status == 200 and ok_envelope(env2), "登录应成功")

    # 改回去，保持环境可重复跑
    if status == 200 and ok_envelope(env2):
        tmp_token = env2["data"]["token"]
        status, env3, text3 = call("POST", "/auth/password",
                                   {"oldPassword": NEW_PWD, "newPassword": PASSWORD},
                                   token=tmp_token)
        check("密码已还原（保证可重复执行）", status == 200 and ok_envelope(env3), text3[:120])


# ----------------------------------------------------------------------
# 15. 登出
# ----------------------------------------------------------------------
section("[15] 登出")
status, env, text = call("POST", "/auth/logout", token=TOKEN)
check("POST /auth/logout（撤销当前令牌）", status == 200 and ok_envelope(env), text[:160])

status, env, text = call("GET", "/dashboard", token=TOKEN)
check("登出后原令牌失效（401）", status == 401, "HTTP %s" % status)


# ----------------------------------------------------------------------
# 汇总
# ----------------------------------------------------------------------
print("\n" + "=" * 58)
print("通过 %d / %d" % (len(PASS), len(PASS) + len(FAIL)))
if FAIL:
    print("失败项：")
    for f in FAIL:
        print("  - " + f)
    sys.exit(1)
print("后端端到端冒烟测试全部通过 \u2713")
sys.exit(0)
