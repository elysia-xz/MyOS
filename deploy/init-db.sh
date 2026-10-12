#!/usr/bin/env bash
# ============================================================
# MyOS 数据库初始化 / 升级
#
# 适用两种情况：
#   1) 全新库：按顺序灌全部脚本
#   2) 已有旧版库：只补灌增量（脚本本身幂等，重复执行安全）
#
# 用法：
#   bash deploy/init-db.sh
#   DB_NAME=other bash deploy/init-db.sh
# ============================================================
set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-myos}"
DB_USER="${DB_USER:-myos}"
export PGPASSWORD="${PGPASSWORD:-myos123}"

# 优先用免安装包自带的 psql，其次回退到系统 PATH
PGROOT="${MYOS_PG_ROOT:-C:/Users/Lenovo/.workbuddy-ai/binaries/postgres}"
if [ -x "$PGROOT/pgsql/bin/psql" ]; then
  PSQL_BIN="$PGROOT/pgsql/bin/psql"
else
  PSQL_BIN="psql"
fi

DIR="$(cd "$(dirname "$0")" && pwd)/../myos-server/src/main/resources/db"

echo "==> 目标库 $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME（psql: $PSQL_BIN）"

psql() { "$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"; }

echo "==> [1/8] schema.sql（基础表）"
psql -q -f "$DIR/schema.sql"
echo "==> [2/8] schema-v1.sql（V1 增量，幂等）"
psql -q -f "$DIR/schema-v1.sql"
echo "==> [3/8] schema-v1.1.sql（多端令牌表，幂等）"
psql -q -f "$DIR/schema-v1.1.sql"
echo "==> [4/8] schema-v1.2.sql（输出语录字段，幂等）"
psql -q -f "$DIR/schema-v1.2.sql"
echo "==> [5/8] schema-v1.3.sql（外键删除规则，幂等）"
psql -q -f "$DIR/schema-v1.3.sql"
echo "==> [6/8] schema-v1.4.sql（应用配置表，幂等）"
psql -q -f "$DIR/schema-v1.4.sql"
echo "==> [7/8] seed.sql（基础演示数据）"
psql -q -f "$DIR/seed.sql"
echo "==> [8/8] seed-v1.sql（V1 演示数据，幂等）"
psql -q -f "$DIR/seed-v1.sql"

echo ""
echo "==> 校验表清单"
psql -c "\dt"

echo ""
echo "✅ 完成。若服务在运行中，重启后端即可生效。"
