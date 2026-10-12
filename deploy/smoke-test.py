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

    status, env, text = call("DELETE", "/projects/%s" % pid, token=TOKEN)
    check("DELETE /projects/{id}（清理）", status == 200 and ok_envelope(env), text[:160])


# ----------------------------------------------------------------------
# 7. 目标层级（年 → 月 → 周）
# ----------------------------------------------------------------------
section("[7] 目标层级")
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
# 8. 财务
# ----------------------------------------------------------------------
section("[8] 财务记账与资产")
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
# 9. 复盘（含 metricsJson）
# ----------------------------------------------------------------------
section("[9] 复盘")
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
# 10. AI 网关（未配 Key 时应优雅降级，不是崩溃）
# ----------------------------------------------------------------------
section("[10] AI 网关")
status, env, text = call("GET", "/ai/status", token=TOKEN)
check("GET /ai/status", status == 200 and ok_envelope(env),
      "available=%s（未配 Key 时为 false 属正常）"
      % (env["data"].get("available") if ok_envelope(env) else "?"))


# ----------------------------------------------------------------------
# 11. 登出
# ----------------------------------------------------------------------
section("[11] 登出")
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
