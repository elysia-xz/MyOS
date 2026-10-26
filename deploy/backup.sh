#!/usr/bin/env bash
# ============================================================
# MyOS 数据库备份
#
# 用途：把整库打成一个可完整恢复的归档文件（pg_dump 自定义格式，
#       已压缩，能选择性恢复单表）。
#
# 为什么必须有：
#   这是个自建的个人系统，最怕的不是功能少，是数据没了。
#   服务器磁盘故障、误删、升级脚本出错 —— 任一情况都可能让几年的
#   记录一次性消失。有这个脚本，最坏情况只损失「上次备份到现在」。
#
# 用法：
#   bash deploy/backup.sh                 # 备份到 deploy/../backups/
#   bash deploy/backup.sh /d/backups      # 指定目录
#   MYOS_BACKUP_KEEP_DAYS=30 bash deploy/backup.sh
#
# 恢复（⚠️ 会覆盖目标库里同名对象，先确认库名）：
#   pg_restore -h localhost -U myos -d myos -c <备份文件>
#   或先看内容：pg_restore -l <备份文件>
# ============================================================
set -uo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-myos}"
DB_USER="${DB_USER:-myos}"
export PGPASSWORD="${PGPASSWORD:-myos123}"

# 保留天数：每天备份一次的话，14 天够覆盖「发现得晚」的情况
KEEP_DAYS="${MYOS_BACKUP_KEEP_DAYS:-14}"

PGROOT="${MYOS_PG_ROOT:-C:/Users/Lenovo/.workbuddy-ai/binaries/postgres}"
if [ -x "$PGROOT/pgsql/bin/pg_dump" ]; then
  PG_DUMP="$PGROOT/pgsql/bin/pg_dump"
  PG_RESTORE="$PGROOT/pgsql/bin/pg_restore"
  PG_ISREADY="$PGROOT/pgsql/bin/pg_isready"
else
  PG_DUMP="pg_dump"
  PG_RESTORE="pg_restore"
  PG_ISREADY="pg_isready"
fi

DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${1:-$DIR/../backups}"

fail() { echo "❌ $1" >&2; exit 1; }

# ---- 0. 先确认数据库活着 ----
# 不先查的话，pg_dump 也会报错，但错误信息埋在长输出里容易误判成「备份成功」
"$PG_ISREADY" -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1 \
  || fail "数据库不可达（$DB_HOST:$DB_PORT）。先启动 PostgreSQL 再备份。"

mkdir -p "$OUT_DIR" || fail "无法创建备份目录：$OUT_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$OUT_DIR/myos-$STAMP.dump"

echo "==> 备份 $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME"
echo "==> 输出 $OUT"

# ---- 1. 备份 ----
# -Fc 自定义格式：自带压缩，pg_restore 可只恢复单表
# --no-owner / --no-privileges：换机器恢复时不必先手工建出同名角色
"$PG_DUMP" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -Fc --no-owner --no-privileges -f "$OUT" \
  || fail "pg_dump 失败（是否还有权限问题？）"

# ---- 2. 校验归档真的可读 ----
# 只信退出码不够：磁盘满、被杀进程都可能留下一个「存在但坏掉」的文件。
# 那个文件平时看不出问题，等你要恢复时才发现 —— 所以每次都要验。
"$PG_RESTORE" -l "$OUT" >/dev/null 2>&1 \
  || fail "备份文件无法解析，可能已损坏：$OUT"

SIZE="$(du -h "$OUT" | cut -f1)"
TABLES="$("$PG_RESTORE" -l "$OUT" 2>/dev/null | grep -c 'TABLE DATA' || true)"

# ---- 3. 清理过期备份 ----
# 只删本脚本产出的 myos-*.dump，绝不碰目录里的其他文件
DELETED=0
while IFS= read -r old; do
  [ -z "$old" ] && continue
  rm -f "$old" && DELETED=$((DELETED + 1))
done < <(find "$OUT_DIR" -maxdepth 1 -name 'myos-*.dump' -type f -mtime "+$KEEP_DAYS" 2>/dev/null)

echo ""
echo "✅ 备份完成：$OUT"
echo "   大小 $SIZE，含 $TABLES 张表数据"
[ "$DELETED" -gt 0 ] && echo "   已清理 $DELETED 个超过 $KEEP_DAYS 天的旧备份"
echo ""
echo "   恢复命令（会覆盖目标库同名对象，先确认库名）："
echo "   $PG_RESTORE -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c \"$OUT\""
