#!/usr/bin/env bash
# ============================================================
# MyOS 本地 PostgreSQL（免安装版）启停脚本
#
# 用途：在没有 Docker、也不装系统级 PostgreSQL 的机器上，
#       用免安装包起一个只监听本机的数据库实例。
#
# 安全说明：认证方式为 trust（免密）但仅监听 localhost，
#           仅供本地开发使用；如需对外暴露请改用 scram-sha-256。
#
# 用法：
#   bash deploy/local-pg.sh start     # 首次自动 initdb + 建角色 + 建库
#   bash deploy/local-pg.sh stop
#   bash deploy/local-pg.sh status
# ============================================================
set -uo pipefail

PGROOT="${MYOS_PG_ROOT:-C:/Users/Lenovo/.workbuddy-ai/binaries/postgres}"
PGBIN="$PGROOT/pgsql/bin"
PGDATA="$PGROOT/data"
PGLOG="$PGROOT/pg.log"
PGPORT="${PGPORT:-5432}"

DB_NAME="${DB_NAME:-myos}"
DB_USER="${DB_USER:-myos}"
DB_PASSWORD="${DB_PASSWORD:-myos123}"

# 任一步骤失败就明确报错退出，绝不继续往下跑导致"假成功"
fail() { echo "❌ $1" >&2; exit 1; }

if [ ! -x "$PGBIN/pg_ctl" ]; then
  fail "找不到 PostgreSQL：$PGBIN/pg_ctl（可用 MYOS_PG_ROOT 指定安装路径）"
fi

case "${1:-start}" in
  start)
    # ---- 1. 初始化数据目录 ----
    if [ ! -f "$PGDATA/PG_VERSION" ]; then
      echo "==> 首次初始化数据目录：$PGDATA"
      mkdir -p "$PGROOT"
      "$PGBIN/initdb" -D "$PGDATA" -U postgres -E UTF8 --locale=C -A trust \
        || fail "initdb 失败，请检查上方输出"
      [ -f "$PGDATA/PG_VERSION" ] || fail "initdb 未生成数据目录，初始化未真正完成"
      echo "==> 初始化完成"
    else
      echo "==> 数据目录已存在，跳过初始化"
    fi

    # ---- 2. 启动服务 ----
    if "$PGBIN/pg_isready" -h localhost -p "$PGPORT" >/dev/null 2>&1; then
      echo "==> PostgreSQL 已在运行"
    else
      echo "==> 启动 PostgreSQL（仅监听本机，端口 $PGPORT）"
      "$PGBIN/pg_ctl" -D "$PGDATA" -l "$PGLOG" \
        -o "-p $PGPORT -c listen_addresses=localhost" -w start \
        || fail "启动失败，日志见 $PGLOG"
      "$PGBIN/pg_isready" -h localhost -p "$PGPORT" >/dev/null 2>&1 \
        || fail "启动后端口仍不可达，日志见 $PGLOG"
    fi

    # ---- 3. 角色 ----
    if "$PGBIN/psql" -h localhost -p "$PGPORT" -U postgres -tAc \
         "select 1 from pg_roles where rolname='$DB_USER'" 2>/dev/null | grep -q 1; then
      echo "==> 角色 $DB_USER 已存在"
    else
      "$PGBIN/psql" -h localhost -p "$PGPORT" -U postgres -q -c \
        "create role $DB_USER login password '$DB_PASSWORD' superuser" \
        || fail "创建角色 $DB_USER 失败"
      echo "==> 已创建角色 $DB_USER"
    fi

    # ---- 4. 数据库 ----
    if "$PGBIN/psql" -h localhost -p "$PGPORT" -U postgres -lqt 2>/dev/null \
         | cut -d'|' -f1 | grep -qw "$DB_NAME"; then
      echo "==> 数据库 $DB_NAME 已存在"
    else
      "$PGBIN/createdb" -h localhost -p "$PGPORT" -U postgres -O "$DB_USER" "$DB_NAME" \
        || fail "创建数据库 $DB_NAME 失败"
      echo "==> 已创建数据库 $DB_NAME"
    fi

    echo "✅ PostgreSQL 就绪：localhost:$PGPORT/$DB_NAME"
    ;;

  stop)
    "$PGBIN/pg_ctl" -D "$PGDATA" -w stop || fail "停止失败"
    ;;

  status)
    "$PGBIN/pg_isready" -h localhost -p "$PGPORT"
    ;;

  *)
    echo "用法：$0 {start|stop|status}"
    exit 1
    ;;
esac
