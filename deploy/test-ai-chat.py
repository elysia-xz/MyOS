#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AI 二期（问答 + 带确认的工具调用）端到端验证。

⚠️ 与 smoke-test.py 的区别：本脚本会真的调用 AI 网关，每一轮对话要几十秒、
   消耗 token，所以**不要**并进 CI 的每次构建，按需手动跑。

覆盖：
  1. 网关可用
  2. 纯问答（不带工具）→ status=DONE
  3. 问「有哪些任务」→ 触发只读工具，循环内执行后 DONE
  4. 说「创建任务」→ status=PENDING，且带 toolCalls（不落库！）
  5. 确认执行 → DONE，并回查 /tasks 真的多了一条
  6. 历史列表 / 会话详情 能读回来，toolCalls 已解析
  7. 伪造 id 去执行 → 必须被拒绝
  8. 不确认就继续聊 → 悬空调用被自动补成取消，对话不炸
  9. 取消接口、只确认一部分 → 其余自动取消，历史仍合法

用法：
    python deploy/test-ai-chat.py
    MYOS_PWD='改成你的口令' python deploy/test-ai-chat.py

退出码：全部通过为 0，有失败为 1。
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error

BASE = os.environ.get("MYOS_BASE", "http://localhost:8080")
USER = os.environ.get("MYOS_USER", "admin")
PWD = os.environ.get("MYOS_PWD", "admin123")

passed, failed = 0, 0


def check(name, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  [PASS] {name}")
    else:
        failed += 1
        print(f"  [FAIL] {name} :: {detail}")


def call(method, path, body=None, token=None, timeout=240):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode()), None, time.time() - t0
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode()), f"HTTP {e.code}", time.time() - t0
        except Exception:
            return None, f"HTTP {e.code}", time.time() - t0
    except Exception as e:
        return None, str(e), time.time() - t0


def brief(s, n=160):
    if s is None:
        return "(null)"
    s = str(s).replace("\n", " ")
    return s[:n] + ("…" if len(s) > n else "")


# ---------- 登录 ----------
login, err, _ = call("POST", "/api/v1/auth/login", {"username": USER, "password": PWD})
check("登录", login and login.get("success"), err or login)
if not (login and login.get("success")):
    sys.exit(1)
TOK = login["data"]["token"]

# ---------- 1. 网关可用 ----------
st, err, _ = call("GET", "/api/v1/ai/status", token=TOK)
check("AI 网关已配置", st and st["data"].get("available"), err or st)

# ---------- 2. 纯问答 ----------
r, err, sec = call("POST", "/api/v1/ai/chat", {"message": "用一句话跟我打个招呼，介绍你是做什么的"}, TOK)
check("纯问答返回成功", r and r.get("success"), err or r)
conv_id = None
if r and r.get("success"):
    conv_id = r["data"].get("conversationId")
    check("纯问答 status=DONE", r["data"].get("status") == "DONE", r["data"])
    check("纯问答有正文", bool(r["data"].get("content")), brief(r["data"].get("content")))

# ---------- 3. 只读工具（问任务）----------
r2, err, sec = call("POST", "/api/v1/ai/chat",
                    {"conversationId": conv_id, "message": "我现在有哪些任务没完成？直接告诉我"}, TOK)
check("问任务返回成功", r2 and r2.get("success"), err or r2)
if r2 and r2.get("success"):
    check("问任务结束仍是 DONE", r2["data"].get("status") == "DONE", r2["data"])
    check("问任务有正文", bool(r2["data"].get("content")), brief(r2["data"].get("content")))

# ---------- 4. 写工具 → 必须停在待确认 ----------
r3, err, sec = call("POST", "/api/v1/ai/chat",
                    {"conversationId": conv_id,
                     "message": "帮我创建一条任务：AI端到端验证用任务，优先级设为高"}, TOK)
check("创建意图返回成功", r3 and r3.get("success"), err or r3)
pending = []
if r3 and r3.get("success"):
    check("创建意图 status=PENDING", r3["data"].get("status") == "PENDING", r3["data"])
    pending = r3["data"].get("toolCalls") or []
    check("带出待确认调用", len(pending) >= 1, r3["data"])
    if pending:
        check("调用名是 create_task", pending[0]["name"] == "create_task", pending[0])
        check("risk=WRITE", pending[0].get("risk") == "WRITE", pending[0])
        args = pending[0].get("arguments") or ""
        check("参数是 JSON 字符串", args.strip().startswith("{"), args)

# ---------- 4b. 确认前任务数 ----------
before, err, _ = call("GET", "/api/v1/tasks", token=TOK)
n_before = len(before["data"]) if before and before.get("success") else -1
check("取到当前任务数", n_before >= 0, n_before)

# ---------- 5. 确认执行 ----------
if pending:
    r4, err, sec = call("POST", "/api/v1/ai/tool/execute",
                        {"conversationId": conv_id,
                         "calls": [{"id": c["id"]} for c in pending]}, TOK)
    check("执行返回成功", r4 and r4.get("success"), err or r4)
    if r4 and r4.get("success"):
        check("执行后 status=DONE", r4["data"].get("status") == "DONE", r4["data"])
        check("执行后有正文", bool(r4["data"].get("content")), brief(r4["data"].get("content")))

after, err, _ = call("GET", "/api/v1/tasks", token=TOK)
n_after = len(after["data"]) if after and after.get("success") else -1
check("确认后真的多了一条任务", n_after == n_before + 1, f"{n_before} -> {n_after}")

# ---------- 6. 历史 ----------
hist, err, _ = call("GET", "/api/v1/ai/conversations", token=TOK)
check("历史列表可读", hist and hist.get("success"), err or hist)
found = any(str(c.get("id")) == str(conv_id) for c in (hist or {}).get("data", []))
check("新会话出现在历史里", found, brief(hist))

det, err, _ = call("GET", f"/api/v1/ai/conversations/{conv_id}", token=TOK)
check("会话详情可读", det and det.get("success"), err or det)
if det and det.get("success"):
    msgs = det["data"]
    roles = [m["role"] for m in msgs]
    check("消息里三种角色都有", {"user", "assistant", "tool"} <= set(roles), roles)
    asst = [m for m in msgs if m["role"] == "assistant" and m.get("toolCalls")]
    check("历史里的 assistant 带 toolCalls", len(asst) >= 1, brief(msgs))
    toolm = [m for m in msgs if m["role"] == "tool"]
    check("历史里有 tool 结果", len(toolm) >= 1, brief(toolm))

# ---------- 7. 伪造调用必须被拒 ----------
r5, err, _ = call("POST", "/api/v1/ai/tool/execute",
                  {"conversationId": conv_id, "calls": [{"id": "call_fake_001"}]}, TOK)
check("伪造 id 被拒绝", r5 is not None and r5.get("success") is False, r5)

# 伪造别人会话 id 也必须被拒
r6, err, _ = call("POST", "/api/v1/ai/tool/execute",
                  {"conversationId": 999999, "calls": [{"id": "x"}]}, TOK)
check("不存在的会话被拒绝", r6 is not None and r6.get("success") is False, r6)

# ---------- 8. 不确认就继续聊：悬空调用必须自愈 ----------
# 第 4 步留下的 create_task 一直没确认。直接发下一句，
# 后端应该先把悬空的调用补成「已取消」，再正常回答。
r7, err, sec = call("POST", "/api/v1/ai/chat",
                    {"conversationId": conv_id, "message": "刚才那个先不管了，问我今天几号"}, TOK)
check("带着悬空调用继续聊能成功", r7 and r7.get("success"), err or r7)
if r7 and r7.get("success"):
    check("悬空自愈后 status=DONE", r7["data"].get("status") == "DONE", r7["data"])
    check("悬空自愈后有正文", bool(r7["data"].get("content")), brief(r7["data"].get("content")))

det2, err, _ = call("GET", f"/api/v1/ai/conversations/{conv_id}", TOK)
if det2 and det2.get("success"):
    cancels = [m for m in det2["data"] if m["role"] == "tool" and "取消" in (m.get("content") or "")]
    check("悬空调用被补成取消记录", len(cancels) >= 1, brief(det2["data"]))

# ---------- 9. 取消接口 ----------
r8, err, _ = call("POST", "/api/v1/ai/chat",
                  {"conversationId": conv_id,
                   "message": "再帮我创建一条任务：用于验证取消流程的任务"}, TOK)
pend2 = []
if r8 and r8.get("success") and r8["data"].get("status") == "PENDING":
    pend2 = r8["data"].get("toolCalls") or []
check("制造出第二组待确认", len(pend2) >= 1, r8 and r8["data"])
if pend2:
    r9, err, _ = call("POST", "/api/v1/ai/tool/cancel",
                      {"conversationId": conv_id, "calls": [{"id": c["id"]} for c in pend2]}, TOK)
    check("取消接口返回成功", r9 and r9.get("success"), err or r9)
    if r9 and r9.get("success"):
        check("取消后 status=DONE", r9["data"].get("status") == "DONE", r9["data"])
    # 取消之后必须还能继续聊（历史里不能留下悬空调用）
    r10, err, _ = call("POST", "/api/v1/ai/chat",
                       {"conversationId": conv_id, "message": "好的，那改成问我：我现在有几条任务"}, TOK)
    check("取消之后继续聊能成功", r10 and r10.get("success"), err or r10)

# ---------- 10. 部分确认：只确认一部分，其余自动取消 ----------
r11, err, _ = call("POST", "/api/v1/ai/chat",
                   {"conversationId": conv_id,
                    "message": "帮我同时创建两个任务：甲任务A、甲任务B"}, TOK)
pend3 = []
if r11 and r11.get("success") and r11["data"].get("status") == "PENDING":
    pend3 = r11["data"].get("toolCalls") or []
if len(pend3) >= 2:
    before2, _, _ = call("GET", "/api/v1/tasks", token=TOK)
    n2 = len(before2["data"]) if before2 and before2.get("success") else -1
    r12, err, _ = call("POST", "/api/v1/ai/tool/execute",
                       {"conversationId": conv_id, "calls": [{"id": pend3[0]["id"]}]}, TOK)
    check("部分确认返回成功", r12 and r12.get("success"), err or r12)
    after2, _, _ = call("GET", "/api/v1/tasks", token=TOK)
    n3 = len(after2["data"]) if after2 and after2.get("success") else -1
    check("只新增了一条任务", n3 == n2 + 1, f"{n2} -> {n3}")
    r13, err, _ = call("POST", "/api/v1/ai/chat",
                       {"conversationId": conv_id, "message": "谢谢，结束对话"}, TOK)
    check("部分确认后继续聊能成功", r13 and r13.get("success"), err or r13)
else:
    check("部分确认（模型只给了一个调用，跳过）", True, brief(r11 and r11["data"]))

print(f"\n结果：{passed} 通过 / {failed} 失败")
sys.exit(0 if failed == 0 else 1)
