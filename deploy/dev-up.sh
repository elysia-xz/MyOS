#!/usr/bin/env bash
# ============================================================
# MyOS 本地一键启动
#   1) 启动 PostgreSQL（免安装版，仅监听本机）
#   2) 初始化 / 升级数据库结构
#   3) 启动后端 Spring Boot  → http://localhost:8080
#   4) 启动前端 Vite         → http://localhost:5173
#
# 用法：
#   bash deploy/dev-up.sh
#
# 停止：Ctrl+C 会一并停掉前后端；数据库请另跑
#       bash deploy/local-pg.sh stop
# ============================================================
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

export JAVA_HOME="${JAVA_HOME:-C:/Users/Lenovo/.workbuddy-ai/binaries/jdk/jdk-25.0.4.1+1}"
MVN="${MVN_HOME:-/c/Users/Lenovo/.workbuddy-ai/binaries/maven/apache-maven-3.9.16}/bin/mvn"

echo "==> [1/4] 启动 PostgreSQL"
bash "$HERE/local-pg.sh" start || exit 1

echo ""
echo "==> [2/4] 初始化 / 升级数据库结构"
bash "$HERE/init-db.sh" || exit 1

echo ""
echo "==> [3/4] 启动后端  → http://localhost:8080"
(cd "$ROOT/myos-server" && "$MVN" -q spring-boot:run) &
BACK_PID=$!

echo "==> [4/4] 启动前端  → http://localhost:5173"
(cd "$ROOT/myos-web" && npm run dev) &
FRONT_PID=$!

trap 'echo; echo "==> 停止前后端..."; kill $BACK_PID $FRONT_PID 2>/dev/null; exit 0' INT TERM
wait
