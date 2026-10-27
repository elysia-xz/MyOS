#!/usr/bin/env bash
# ============================================================
# MyOS 创建用户（测试期手工建号）
#
# 为什么用手工建号而不是自助注册：
#   测试只加 3-5 个人，一次性建完就再也不用；
#   而开放注册意味着公网上任何爬虫都能批量注册，
#   还得配验证码/邮箱校验/清理垃圾账号 —— 为省五分钟写一套用不上的机制不划算。
#
# 用法：
#   bash deploy/create-user.sh <用户名> [昵称] [--password <密码>]
#   bash deploy/create-user.sh zhangsan 张三
#   bash deploy/create-user.sh lisi 李四 --password 'MyPass2026'
#
# 不加 --password 时自动生成强随机密码并打印一次（只显示这一次）。
#
# ⚠️ bcrypt 哈希必须由后端同版本库生成，不能在 shell 里手拼：
#    哈希与算法版本/随机盐绑定，一旦对不上就是「登录页永远密码错误」
#    且没有任何报错线索（这个坑已经踩过两次）。所以这里调用后端的
#    一段 Java 单文件程序生成，与本项目 AuthController 使用同一个库。
# ============================================================
set -uo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-myos}"
DB_USER="${DB_USER:-myos}"
export PGPASSWORD="${PGPASSWORD:-myos123}"

PGROOT="${MYOS_PG_ROOT:-C:/Users/Lenovo/.workbuddy-ai/binaries/postgres}"
if [ -x "$PGROOT/pgsql/bin/psql" ]; then
  PSQL_BIN="$PGROOT/pgsql/bin/psql"
  PG_ISREADY="$PGROOT/pgsql/bin/pg_isready"
else
  PSQL_BIN="psql"
  PG_ISREADY="pg_isready"
fi

# Java：优先用与后端一致的 JDK
JAVA_BIN="${MYOS_JAVA:-}"
if [ -z "$JAVA_BIN" ]; then
  if [ -x "C:/Users/Lenovo/.workbuddy-ai/binaries/jdk/jdk-25.0.4.1+1/bin/java.exe" ]; then
    JAVA_BIN="C:/Users/Lenovo/.workbuddy-ai/binaries/jdk/jdk-25.0.4.1+1/bin/java.exe"
  else
    JAVA_BIN="java"
  fi
fi

fail() { echo "❌ $1" >&2; exit 1; }

# ---- 解析参数 ----
USERNAME="${1:-}"
NICKNAME="${2:-}"
PASSWORD=""
shift 2 2>/dev/null || true
while [ $# -gt 0 ]; do
  case "$1" in
    --password) PASSWORD="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done

[ -z "$USERNAME" ] && fail "用法：bash deploy/create-user.sh <用户名> [昵称] [--password <密码>]"
[ -z "$NICKNAME" ] && NICKNAME="$USERNAME"

# 用户名限制为字母数字下划线：避免中文/空格在登录框里出问题
echo "$USERNAME" | grep -qE '^[A-Za-z0-9_]{3,32}$' \
  || fail "用户名只能是 3-32 位字母、数字或下划线"

# ---- 生成密码 ----
if [ -z "$PASSWORD" ]; then
  # 去掉容易混淆的字符（0/O、1/l），方便口头转交
  PASSWORD="$(LC_ALL=C tr -dc 'A-HJ-NP-Za-km-z2-9' </dev/urandom | head -c 12)"
fi
[ ${#PASSWORD} -lt 8 ] && fail "密码至少 8 位"

# ---- 探活 ----
"$PG_ISREADY" -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1 \
  || fail "数据库不可达（$DB_HOST:$DB_PORT）。先启动 PostgreSQL。"

# ---- 查重 ----
EXISTS="$("$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc \
  "select count(*) from my_user where username = '$USERNAME'")"
[ "$EXISTS" != "0" ] && fail "用户名 $USERNAME 已存在。改密码请用界面上的「修改密码」，不要重复建号"

# ---- 用后端同版本库生成 bcrypt ----
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

CP=""
for j in \
  "D:/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar" \
  "D:/repository/org/springframework/spring-core/7.0.9/spring-core-7.0.9.jar" \
  "D:/repository/org/springframework/spring-jcl/7.0.9/spring-jcl-7.0.9.jar" \
  "D:/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar" ; do
  [ -f "$j" ] && CP="$CP;$j"
done
CP="${CP#;}"
[ -z "$CP" ] && fail "找不到 spring-security-crypto 依赖，无法生成 bcrypt。可用 --password 前先确认 ~/.m2 存在"

cat > "$TMP/GenHash.java" <<'JAVA'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenHash {
    public static void main(String[] a) {
        BCryptPasswordEncoder e = new BCryptPasswordEncoder();
        String h = e.encode(a[0]);
        // 自校验：生成后立刻验一遍，不让一个错的哈希流进数据库
        if (!e.matches(a[0], h)) {
            System.err.println("SELFCHECK_FAILED");
            System.exit(2);
        }
        System.out.println(h);
    }
}
JAVA

HASH="$(MSYS_NO_PATHCONV=1 "$JAVA_BIN" -cp "$CP" "$TMP/GenHash.java" "$PASSWORD" 2>/dev/null | tail -1)"
case "$HASH" in
  \$2a\$*|\$2b\$*|\$2y\$*) : ;;
  *) fail "bcrypt 生成失败（输出：$HASH）" ;;
esac

# ---- 写库 ----
# ⚠️ 中文昵称不能拼进 psql 的 -c 参数：Windows 下命令行参数会被转成 GBK，
#    而库的 client_encoding 是 UTF8，直接报
#    「invalid byte sequence for encoding "UTF8"」。
#    改走 -f 文件（UTF-8 字节原样传给 psql），已验证中文正常。
#    昵称里的单引号要转义，否则会拼坏 SQL。
SAFE_NICK="${NICKNAME//\'/\'\'}"
SAFE_USER="${USERNAME//\'/\'\'}"

{
  echo "\\pset tuples_only on"
  echo "insert into my_user(username, email, password_hash, nickname, status)"
  echo "values ('$SAFE_USER', null, '$HASH', '$SAFE_NICK', 'ACTIVE')"
  echo "returning id;"
} > "$TMP/insert.sql"

# -q 关掉命令标签（否则最后一行是「INSERT 0 1」而不是 id），
# -tA 只要值。不加 -q 时 tail -1 取到的是标签，会把「已插入」误判成失败。
RAW="$("$PSQL_BIN" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -v ON_ERROR_STOP=1 -qtA -f "$TMP/insert.sql" 2>&1 | tr -d '\r')"
NEW_ID="$(echo "$RAW" | grep -E '^[0-9]+$' | head -1)"

# psql 出错时输出的是 ERROR 文本而不是数字，必须显式判断，
# 否则会把错误信息当成 id 继续往下走、打印出「创建成功」
case "$NEW_ID" in
  ''|*[!0-9]*) fail "写库失败：$RAW" ;;
esac

echo ""
echo "✅ 已创建用户"
echo "   用户名  $USERNAME"
echo "   昵称    $NICKNAME"
echo "   用户 ID $NEW_ID"
echo "   密码    $PASSWORD"
echo ""
echo "   ⚠️ 这个密码只显示这一次，请立即转交给对方，并让对方登录后自己修改。"
echo "   给对方的信息：地址 http://<你的局域网IP>:4173   账号 $USERNAME / $PASSWORD"
echo ""
echo "   新用户是空的：没有项目、没有任务、没有 AI 对话配置（各自独立，互不可见）。"
