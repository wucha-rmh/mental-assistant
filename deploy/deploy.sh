#!/usr/bin/env bash
# =============================================================================
# 心理健康AI助手 —— 服务器侧初始化（Ubuntu 22.04+ / Debian 系）
# =============================================================================
# 作用：装依赖 → 建应用用户与目录 → 建库/专用账号 → 灌 db/schema.sql →
#       装 systemd 服务与 mha.env → 装 Nginx 站点 → 开防火墙 → 打印后续步骤。
# 可重复执行：已存在的目录/配置不会被覆盖（mha.env 只在缺失时生成）。
#
# 用法（在仓库根目录）：
#   sudo SERVER_NAME=your-domain.com bash deploy/deploy.sh
# 可选环境变量：
#   SERVER_NAME   域名或 IP（默认 _ ，即任意 Host）
#   DB_PASSWORD   数据库专用账号口令（不传则随机生成并打印一次）
#   APP_USER      运行用户（默认 mha）
#   SKIP_NGINX=1 / SKIP_MYSQL=1   跳过对应步骤
# =============================================================================
set -euo pipefail

SERVER_NAME="${SERVER_NAME:-_}"
DB_NAME="${DB_NAME:-mental_health_assistant}"
DB_USER="${DB_USER:-mha}"
DB_PASSWORD="${DB_PASSWORD:-}"
APP_USER="${APP_USER:-mha}"
APP_DIR="/opt/mha/backend"
WEB_DIR="/var/www/mental-health/dist"
ENV_DIR="/etc/mha"
ENV_FILE="${ENV_DIR}/mha.env"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${REPO_DIR}/deploy"

step() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✓ %s\033[0m\n' "$*"; }

[[ $EUID -eq 0 ]] || { echo "请用 sudo 运行本脚本"; exit 1; }

# ── 1. 依赖 ──────────────────────────────────────────────────────────────────
step "1/7 安装依赖（openjdk-17 / mysql-server / nginx / curl）"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk-headless mysql-server nginx curl ca-certificates
ok "依赖就绪：$(java -version 2>&1 | head -n1)"

# ── 2. 用户与目录 ────────────────────────────────────────────────────────────
step "2/7 创建应用用户 ${APP_USER} 与目录"
id -u "${APP_USER}" >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin "${APP_USER}"
mkdir -p "${APP_DIR}/uploads" "${WEB_DIR}" "${ENV_DIR}"
chown -R "${APP_USER}:${APP_USER}" /opt/mha /var/www/mental-health
ok "${APP_DIR} / ${WEB_DIR} / ${ENV_FILE}"

# ── 3. 数据库 ────────────────────────────────────────────────────────────────
if [[ "${SKIP_MYSQL:-0}" != "1" ]]; then
  step "3/7 建库 ${DB_NAME} 与专用账号 ${DB_USER}@127.0.0.1"
  if [[ -z "${DB_PASSWORD}" ]]; then
    DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
    warn "未提供 DB_PASSWORD，已随机生成，请立刻记下来：${DB_PASSWORD}"
  fi
  systemctl enable --now mysql >/dev/null 2>&1 || true
  mysql -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  mysql -e "CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';"
  mysql -e "ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';"
  mysql -e "GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'127.0.0.1'; FLUSH PRIVILEGES;"

  TABLES="$(mysql -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")"
  if [[ "${TABLES}" == "0" ]]; then
    mysql "${DB_NAME}" < "${REPO_DIR}/db/schema.sql"
    ok "已灌入 db/schema.sql（$(mysql -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';") 张表）"
  else
    warn "库 ${DB_NAME} 已有 ${TABLES} 张表，跳过 schema.sql（要重建请手动执行）"
  fi

  BIND="$(grep -E '^\s*bind-address' /etc/mysql/mysql.conf.d/mysqld.cnf 2>/dev/null || true)"
  [[ "${BIND}" == *127.0.0.1* ]] && ok "MySQL 只监听 127.0.0.1" || warn "请确认 mysqld.cnf 的 bind-address = 127.0.0.1（当前：${BIND:-未设置}）"
else
  warn "SKIP_MYSQL=1，跳过数据库步骤"
fi

# ── 4. 环境变量文件 ──────────────────────────────────────────────────────────
step "4/7 生成 ${ENV_FILE}"
if [[ -f "${ENV_FILE}" ]]; then
  warn "${ENV_FILE} 已存在，保持不动（如需更新请手动编辑）"
else
  JWT_SECRET="$(openssl rand -base64 48)"
  cat > "${ENV_FILE}" <<EOF
# 由 deploy/deploy.sh 生成 $(date '+%F %T')
JWT_SECRET=${JWT_SECRET}
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USERNAME=${DB_USER}
DB_PASSWORD=${DB_PASSWORD:-请填写数据库口令}
# DeepSeek API Key（也可以继续放 application-local.yaml）
# SPRING_AI_OPENAI_API_KEY=sk-xxx
EOF
  ok "已生成（JWT_SECRET 48 位随机串，DB_PASSWORD 见上）"
fi
chmod 600 "${ENV_FILE}"
chown "${APP_USER}:${APP_USER}" "${ENV_FILE}"

# ── 5. systemd ───────────────────────────────────────────────────────────────
step "5/7 安装 systemd 服务 mha"
cp -f "${DEPLOY_DIR}/mha.service" /etc/systemd/system/mha.service
systemctl daemon-reload
if [[ -f "${APP_DIR}/app.jar" ]]; then
  systemctl enable --now mha
  systemctl status mha --no-pager | head -n 8 || true
else
  systemctl enable mha >/dev/null 2>&1 || true
  warn "${APP_DIR}/app.jar 还不存在，先上传 jar 再 systemctl start mha"
fi

# ── 6. Nginx ─────────────────────────────────────────────────────────────────
if [[ "${SKIP_NGINX:-0}" != "1" ]]; then
  step "6/7 安装 Nginx 站点（server_name=${SERVER_NAME}）"
  sed "s/your-domain.com/${SERVER_NAME}/" "${DEPLOY_DIR}/nginx.conf.example" > /etc/nginx/sites-available/mental-health.conf
  ln -sf /etc/nginx/sites-available/mental-health.conf /etc/nginx/sites-enabled/mental-health.conf
  rm -f /etc/nginx/sites-enabled/default
  nginx -t && systemctl reload nginx
  ok "站点已启用（含 SSE proxy_buffering off、登录 limit_req、/files 加固）"
else
  warn "SKIP_NGINX=1，跳过 Nginx 步骤"
fi

# ── 7. 防火墙 ────────────────────────────────────────────────────────────────
step "7/7 防火墙（只开 22 / 80 / 443）"
if command -v ufw >/dev/null 2>&1; then
  ufw allow OpenSSH >/dev/null && ufw allow 80/tcp >/dev/null && ufw allow 443/tcp >/dev/null
  ufw --force enable >/dev/null
  ok "ufw 已启用：$(ufw status | head -n1)"
else
  warn "没有 ufw，请自行确认安全组只开 22/80/443"
fi

cat <<'EOF'

────────────────────────────────────────────────────────────────────────────
接下来（在本机执行，详见 docs/deploy.md）：
  1. 上传后端： scp backend/target/application-0.0.1-SNAPSHOT.jar root@IP:/opt/mha/backend/app.jar
                sudo chown mha:mha /opt/mha/backend/app.jar && sudo systemctl restart mha
  2. 上传前端： cd frontend && npm ci && npm run build
                scp -r dist/* root@IP:/var/www/mental-health/dist/
  3. 有数据要搬：见 docs/deploy.md 第 5 节（mysqldump --databases + uploads 打包）
  4. 有域名要 HTTPS：sudo apt install -y certbot python3-certbot-nginx && sudo certbot --nginx -d 你的域名
  5. 记下 /etc/mha/mha.env 里的 DB_PASSWORD（脚本只在生成时打印一次）
  6. 上线前务必改掉 admin / tester 的 123456（见 docs/deploy.md 第 10 节）
────────────────────────────────────────────────────────────────────────────
EOF
