# 部署指南（云服务器）

> 目标：把本项目部署到一台公网 Linux 服务器，Nginx 统一对外，后端只监听 127.0.0.1。
> 配套模板在同目录上级的 `deploy/`：`mha.service`（systemd）、`mha.env.example`（环境变量）、`nginx.conf.example`（站点）。

## 0. 一条硬事实：服务器不需要 GPU

所有 AI 能力都通过 HTTPS 调 `https://api.deepseek.com`（见 `backend/src/main/resources/application.yaml` 的 `spring.ai.openai`），
服务器只跑 Spring Boot + MySQL + Nginx，**2 核 4G 起步就够**（`-Xmx512m`）。

## 1. 规格与软件

| 项 | 建议 |
|---|---|
| 服务器 | 2 核 4G / 系统盘 40G+（Ubuntu 22.04 LTS 最省事） |
| 安全组 | 只开 22（SSH，建议限你的 IP）、80、443；**3306 和 1236 一律不要对公网开** |
| JDK | 17（`backend/pom.xml` 里 `<java.version>17</java.version>`；装 17 或 21 都行） |
| MySQL | 8.x，`bind-address = 127.0.0.1` |
| Nginx | 1.18+ |
| Node.js / Maven | **只在构建时需要**，跑起来不需要（可本机构建好再上传产物） |

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk mysql-server nginx
```

## 2. 目录规划

```
/opt/mha/backend/app.jar          后端产物
/opt/mha/backend/uploads/         上传文件（头像、封面）—— 迁移时必须一起搬
/var/www/mental-health/dist/      前端构建产物
/etc/mha/mha.env                  环境变量（chmod 600，属主 mha）
```

```bash
sudo useradd -r -s /usr/sbin/nologin mha
sudo mkdir -p /opt/mha/backend/uploads /var/www/mental-health /etc/mha
sudo chown -R mha:mha /opt/mha
```

## 3. 数据库

```bash
# 建库 + 专用账号（不要用 root 跑应用）
sudo mysql -e "CREATE DATABASE mental_health_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
sudo mysql -e "CREATE USER 'mha'@'127.0.0.1' IDENTIFIED BY '换成强口令';"
sudo mysql -e "GRANT ALL PRIVILEGES ON mental_health_assistant.* TO 'mha'@'127.0.0.1'; FLUSH PRIVILEGES;"

# 只建结构（8 张表，无数据）——仓库里的 db/schema.sql
mysql -umha -p mental_health_assistant < db/schema.sql

# 或者：连数据一起恢复（.db-backup/mental_health_assistant_full_*.sql 里带 USE 语句，
# 用 source 恢复时不要在别的库里执行，见 .db-backup/RESTORE.md）
```

> ⚠️ `db/schema.sql` 是 2026-10-15 导出的结构快照，**以后每次改表都要重新导出**（命令见 `CLAUDE.md` 第二节「数据库」）。

## 4. 后端

构建（本机或服务器都行）：

```bash
cd backend && mvn -B clean package -DskipTests
# 产物：backend/target/application-0.0.1-SNAPSHOT.jar
```

环境变量（`/etc/mha/mha.env`，照抄 `deploy/mha.env.example`）：

```bash
JWT_SECRET=<openssl rand -base64 48>
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USERNAME=mha
DB_PASSWORD=<数据库口令>
# AI Key 也可放这里：SPRING_AI_OPENAI_API_KEY=sk-xxx
```

> **优先级**：环境变量 > `application-local.yaml` > `application.yaml`。
> 仓库里的 `application.yaml` 已经不含任何口令；本机开发把真实值放进
> `backend/src/main/resources/application-local.yaml`（已 gitignore）。
> `jwt.secret` 少于 32 位**后端会拒绝启动**（`config/JwtConfig.java` 的 `@PostConstruct`）。

systemd 托管：

```bash
sudo cp deploy/mha.service /etc/systemd/system/mha.service
sudo systemctl daemon-reload && sudo systemctl enable --now mha
journalctl -u mha -f
```

| ⚠️ 时区 | `mha.service` 里必须保留 `Environment="TZ=Asia/Shanghai"`。看板的「今天/趋势」、日记日期、每天 03:30 的临时文件清理 cron 都按系统时区算，不设会整体偏 8 小时。 |
|---|---|

## 5. 前端 + Nginx

```bash
cd frontend && npm ci && npm run build
sudo cp -r dist/* /var/www/mental-health/dist/
```

前端**不需要改任何配置**：`frontend/src/utils/request.js` 的 baseURL 是 `/api`，`frontend/src/config/index.js` 的
`fileBaseUrl` 是空串（同源），所以前后端同域部署时**不涉及跨域（CORS）**。

```bash
sudo cp deploy/nginx.conf.example /etc/nginx/conf.d/mental-health.conf
# 改 server_name 与 root 路径
sudo nginx -t && sudo systemctl reload nginx
```

三个最容易踩的点（模板里都已写好）：

1. `try_files $uri $uri/ /index.html` —— 前端用 `createWebHistory()`，刷新 `/back/users` 这类子路径必须回落。
2. `proxy_buffering off` —— **AI 回复是 SSE 流**，不关缓冲就会等全文生成完一次性吐出。
3. `client_max_body_size 10m` —— 上传接口上限（后端 `max-request-size` 也是 10MB）。

## 6. HTTPS

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

签发后浏览器不再提示不安全；`/files/**` 也会自动走 HTTPS。

## 7. 安全加固清单

| 项 | 状态 |
|---|---|
| 数据库口令不在仓库里 | ✅ 改为 `${DB_PASSWORD:}`，本机值在 gitignored 的 `application-local.yaml` |
| JWT 密钥不在仓库里 + 启动强校验 | ✅ `${JWT_SECRET:}`，少于 32 位拒绝启动；旧值已随 git 历史泄露，**绝不要复用** |
| 登录失败限流 | ✅ 应用层：同账号连错 5 次锁 15 分钟（`service/LoginAttemptLimiter.java`）；网关层：`limit_req_zone`（模板里） |
| 上传扩展名白名单 | ✅ 只收 jpg/jpeg/png/gif/bmp/webp + mp4/webm（`service/FileStorageService.java`）；`.html`/`.svg` 会被拒 |
| `/files/**` 防「上传即执行」 | ✅ 后端白名单 + 网关层 `nosniff`、CSP `sandbox`、拒绝脚本后缀 |
| 上传超限返回统一响应体 | ✅ 5MB 上限，超出返回 `{"code":"5004"}`（不再是不带响应体的 413） |
| 令牌异常不再 500 | ✅ 旧密钥/过期/已删用户的 token 一律 `401 + {"code":"A0230"}` |
| 测试账号弱口令 | ⬜ **上线前必须处理**：把 `admin` / `tester` 的 `123456` 改掉或删掉 |
| 数据库定期备份 | ⬜ 建议 crontab 每天 `mysqldump` + 打包 `uploads/`（模板见 `.db-backup/RESTORE.md`） |
| 日志轮转 | ⬜ 后端 stdout 交给 journald 即可；Nginx 用默认 logrotate |

## 8. 备份

```bash
# 结构 + 数据
mysqldump -umha -p --single-transaction --routines --triggers --events \
  mental_health_assistant > /var/backups/mha-$(date +%F).sql
# 上传文件（头像/封面，丢了页面就是 404）
tar czf /var/backups/mha-uploads-$(date +%F).tar.gz -C /opt/mha/backend uploads
```

## 9. 上线自检

1. `curl -s http://127.0.0.1:1236/api/test` → 后端存活。
2. `curl -sI https://你的域名/` → 200，前端首页。
3. 浏览器登录 `tester / 123456` → 能进前台。
4. 发一条消息 → **逐字出现**（不逐字就是 `proxy_buffering` 没关）。
5. 上传一张封面图 → 列表能看到图（404 就是 `uploads/` 没搬或工作目录不对）。
6. 管理员登录 → 后台「用户管理」能看列表、能禁用/启用（禁用后该账号立刻无法登录，已登录的也会被踢）。

## 10. 本机才有的坑（服务器上不会遇到）

- 本机 `mvn -B clean compile` 因 `backend/target` 上继承的 ACE 会失败，用 `mvn -B compile`；
  服务器上没有这个 ACL，`mvn clean package` 正常。
- 本机沙箱里 `npm run build` 会因 esbuild 的 `spawn EPERM` 失败（命名管道限制），
  装包/构建这类命令要用一次性提权跑；服务器上没有这个限制。
