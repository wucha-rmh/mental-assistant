# 部署手册（云服务器 · 从零到上线）

> 目标：把本项目部署到一台公网 Linux 服务器。**Nginx 统一对外（80/443），后端只监听 127.0.0.1:1236，MySQL 只监听 127.0.0.1:3306。**
> 配套模板在同级 `deploy/` 目录：`deploy.sh`（服务器初始化）、`mha.service`（systemd）、`mha.env.example`（环境变量）、`nginx.conf.example`（站点）、`pack.ps1`（本机构建 + 上传）。

---

## 0. 开始前：3 个决定 + 4 个值

| 决定 | 选项 | 影响 |
|---|---|---|
| ① 用不用域名 | 有域名 / 只用 IP | 用 IP 可以跳过第 6 节 HTTPS（但浏览器会提示不安全，且登录态走明文） |
| ② 要不要搬现有数据 | 全新空库 / 搬本机数据 | 搬的话多做第 5 节（当前本机有 5 用户、8 篇文章、19 条日记、10 个上传文件） |
| ③ 在哪构建产物 | 本机构建后上传 / 服务器上构建 | 本机上传更省服务器内存；服务器构建需要装 Maven + Node |

要准备好的 4 个值（后面每一节都会用到）：

| 值 | 怎么来 |
|---|---|
| `服务器IP` / `域名` | 云控制台 |
| `JWT_SECRET` | `openssl rand -base64 48`（**≥ 32 位**，后端启动会强校验） |
| `DB_PASSWORD` | 自己定的强口令（应用专用账号 `mha`，不要用 root） |
| DeepSeek API Key | 本机 `backend/src/main/resources/application-local.yaml` 里那个 `sk-...` |

**服务器不需要 GPU**：所有 AI 能力都是 HTTPS 调 `https://api.deepseek.com`（见 `backend/src/main/resources/application.yaml` 的 `spring.ai.openai`），2 核 4G 起步即可（`-Xmx512m`）。

---

## 1. 服务器初始化

`deploy/deploy.sh` 把第 1–4 节和 Nginx 站点一次做完（可重复执行，已存在的不会重建）：

```bash
# 在服务器上，仓库目录内执行（脚本会读 deploy/ 下的模板）
sudo SERVER_NAME=your-domain.com bash deploy/deploy.sh
```

想手动来一遍，就是下面这些（Ubuntu 22.04 / Debian 系）：

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk-headless mysql-server nginx curl
# 应用用户 + 目录
sudo useradd -r -s /usr/sbin/nologin mha || true
sudo mkdir -p /opt/mha/backend/uploads /var/www/mental-health/dist /etc/mha
sudo chown -R mha:mha /opt/mha /var/www/mental-health
# 防火墙：只开 SSH / HTTP / HTTPS
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw --force enable
```

> ⚠️ **云控制台的安全组**也要只开 22 / 80 / 443。`3306`、`1236` 一律不要对公网开放 —— 后端接口没有 HTTPS、MySQL 是裸协议，暴露即等于送人。

目录规划：

```
/opt/mha/backend/app.jar           后端产物（Java 进程的 -jar）
/opt/mha/backend/uploads/          上传文件：头像、文章封面（迁移时必须一起搬）
/var/www/mental-health/dist/       前端构建产物（Nginx 的 root）
/etc/mha/mha.env                   环境变量（chmod 600，属主 mha）
```

---

## 2. 数据库

```bash
# 建库 + 应用专用账号（不要用 root 跑应用）
sudo mysql -e "CREATE DATABASE IF NOT EXISTS mental_health_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
sudo mysql -e "CREATE USER IF NOT EXISTS 'mha'@'127.0.0.1' IDENTIFIED BY '换成强口令';"
sudo mysql -e "ALTER USER 'mha'@'127.0.0.1' IDENTIFIED BY '换成强口令';"
sudo mysql -e "GRANT ALL PRIVILEGES ON mental_health_assistant.* TO 'mha'@'127.0.0.1'; FLUSH PRIVILEGES;"

# 建 8 张表（只结构、无数据）
mysql -umha -p mental_health_assistant < db/schema.sql
```

确认没有对公网监听：

```bash
grep -E '^\s*bind-address' /etc/mysql/mysql.conf.d/mysqld.cnf   # 期望 127.0.0.1
ss -lntp | grep 3306                                            # 期望 127.0.0.1:3306
```

> `db/schema.sql` 是 2026-10-15 导出的结构快照，含 `DROP TABLE IF EXISTS`、**不含** `CREATE DATABASE`/`USE`。
> 以后每次 `ALTER TABLE` / 加实体，都要重新导出并提交（命令见 `CLAUDE.md` 二「数据库」）。

---

## 3. 后端

**3.1 构建产物**（本机执行；服务器上构建就把 `backend/` 整个传上去再跑同样的命令）

```bash
cd backend
mvn -B clean package -DskipTests -Pprod   # 本机若因 target 的 ACE 报 clean 失败，见第 10 节
# ✅ 上线的 jar **一定要带 -Pprod**：该 profile 会把 application-local.yaml（本机口令/Key）排除在 jar 之外，
#    否则它会盖掉服务器上的环境变量（2026-10-15 实测踩到 Access denied for user 'mha'@'localhost'，见 3.3 与第 10 节）。
#    排除只对干净的 target/classes 生效 —— 本机 mvn clean 被 ACE 挡住时，先手工删
#    backend/target/classes/application-local.yaml 再 package（或按 3.3 把变量配全，两者都做最稳）。
# 产物：backend/target/application-0.0.1-SNAPSHOT.jar  →  上传后重命名为 app.jar
```

**3.2 上传 jar**

```bash
scp backend/target/application-0.0.1-SNAPSHOT.jar root@你的IP:/opt/mha/backend/app.jar
sudo chown mha:mha /opt/mha/backend/app.jar
```

**3.3 环境变量**（照抄 `deploy/mha.env.example` → `/etc/mha/mha.env`）

```bash
JWT_SECRET=<openssl rand -base64 48 的输出>
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USERNAME=mha
DB_PASSWORD=<第 2 节设的数据库口令>
# ⚠️ 下面两行是「按属性名直连」的别名，必须和上面同值 —— 原因见下面的优先级说明：
#    若 jar 里混进了 application-local.yaml，占据位符的 DB_PASSWORD 会被它盖掉，只有这两个能赢。
SPRING_DATASOURCE_USERNAME=mha
SPRING_DATASOURCE_PASSWORD=<同 DB_PASSWORD>
SPRING_AI_OPENAI_API_KEY=sk-xxx
```

```bash
sudo chmod 600 /etc/mha/mha.env && sudo chown mha:mha /etc/mha/mha.env
```

> **配置优先级**：命令行参数 > **环境变量** > `application-local.yaml` > `application.yaml`。
> 仓库里的 `application.yaml` **不含任何口令**（只有 `${JWT_SECRET:}`、`${DB_PASSWORD:}` 这类占位符），
> 所以生产环境根本不需要 `application-local.yaml`。少配 `JWT_SECRET`（或少于 32 位）后端会**拒绝启动**，
> 这是故意的：历史上真实的密钥和 MySQL `root` 口令曾提交进 git。
>
> 🔴 **但「环境变量 > application-local.yaml」这条只管名字直接对应的变量**（`JWT_SECRET` → `jwt.secret`）：
> `application.yaml` 里的 `${DB_PASSWORD:}` 是**占位符**，占位符解析发生在属性源合并**之后**，
> 所以 profile 专属的 `application-local.yaml` 一旦被打进 jar，它的 `spring.datasource.password` 会盖掉服务器
> 环境变量里的 `DB_PASSWORD`（实测就是 `Access denied for user 'mha'@'localhost' (using password: YES)`）。
> 两个保险都要上：打包用 `-Pprod` 排除该文件 **且** 在 env 里同时写 `SPRING_DATASOURCE_USERNAME/PASSWORD`。

**3.4 systemd 托管**

```bash
sudo cp deploy/mha.service /etc/systemd/system/mha.service
sudo systemctl daemon-reload
sudo systemctl enable --now mha
systemctl status mha --no-pager
journalctl -u mha -f          # 看到 "Started AiSpringbootApplication" 即成功
```

| ⚠️ 时区 | `mha.service` 里必须保留 `Environment="TZ=Asia/Shanghai"`。看板的「今天/趋势」、日记日期、每天 03:30 的临时文件清理都按系统时区算，不设会整体偏 8 小时。 |
|---|---|

上传文件落在 `/opt/mha/backend/uploads/`（`file.upload-dir: ./uploads` 相对于 `WorkingDirectory`）。

---

## 4. 前端 + Nginx

```bash
cd frontend
npm ci
npm run build                 # 产物在 frontend/dist/
```

上传到服务器（本机 → 服务器）：

```bash
scp -r frontend/dist/* root@你的IP:/var/www/mental-health/dist/
```

配置站点：

```bash
sudo cp deploy/nginx.conf.example /etc/nginx/sites-available/mental-health.conf
sudo sed -i 's/your-domain.com/你的域名或IP/' /etc/nginx/sites-available/mental-health.conf
sudo ln -sf /etc/nginx/sites-available/mental-health.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default      # 避免默认站点抢 80
sudo nginx -t && sudo systemctl reload nginx
```

前端**不需要改任何配置**：`frontend/src/utils/request.js` 的 baseURL 是 `/api`，`frontend/src/config/index.js` 的
`fileBaseUrl` 是空串（同源）→ 前后端同域部署**不涉及跨域（CORS）**，后端也就没有 CORS 配置。

模板里已经写好的三个必踩点：

1. `try_files $uri $uri/ /index.html` —— 前端用 `createWebHistory()`，刷新 `/back/users` 这类子路径必须回落。
2. `proxy_buffering off` —— **AI 回复是 SSE 流**，不关缓冲就会等全文生成完一次性吐出（表现为「转半天然后整段冒出来」）。
3. `client_max_body_size 10m` —— 与后端 `spring.servlet.multipart.max-request-size` 对齐。

---

## 5. 数据在哪、怎么备份、怎么搬

### 5.1 数据分三层存，搬迁必须搬两样

| 层 | 内容 | 本机（Windows） | 服务器（Linux） | 备注 |
|---|---|---|---|---|
| ① 行数据 | 8 张表的记录 | `C:\ProgramData\MySQL\MySQL Server 8.0\Data\mental_health_assistant\*.ibd`（实测 `SELECT @@datadir` 就是这个） | `/var/lib/mysql/mental_health_assistant/*.ibd` | InnoDB 二进制页文件，**只能通过 SQL 读写**，直接打开是乱码 |
| ② 上传文件 | 头像 / 文章封面（**字节不在库里**） | `backend\uploads\bussiness\...`（实测 10 个文件 / 1.1 MB） | `/opt/mha/backend/uploads/bussiness/...` | 库里 `sys_file_info.file_path` 只存 `/files/bussiness/...` 这个字符串 |
| ③ 文本快照 | `.sql` 备份 | `backend\target\*.sql`、`.db-backup\*.sql`、`db\schema.sql` | 上传用的 `/root/mha-upload/*.sql` | 只有 `db/schema.sql` 入库跟踪（**结构、0 行**） |

两个数据在同一张图里是这么关联的：

```
浏览器 ──> Nginx ──/api/**──> 127.0.0.1:1236 (jar) ──JDBC──> MySQL :3306     ← 行数据在 .ibd 里
                    ──/files/**───────────────┘
                                    └─ 读 /opt/mha/backend/uploads/…         ← 图片字节在这里
```

所以 **只导 SQL、不传 `uploads/` 的结果就是「标题都在、封面全裂」**。库里存的是相对路径，两边的目录结构一致，**不需要改任何配置**。

### 5.2 从本机搬到服务器（2026-10-15 实测走通）

**① 本机导出**（整库结构 + 数据；不带 `--databases`，所以文件里**没有** `CREATE DATABASE` / `USE`）：

```powershell
mysqldump.exe -uroot -p123456 --result-file=D:\mental_health_assistant\backend\target\mha_full.sql --single-transaction --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 --triggers mental_health_assistant
```

- ⚠️ pwsh 里**必须用 `--result-file=`，不要用 `>` 重定向**：PowerShell 5.1 会把原生命令的输出当文本重新编码，中文全坏。
- 产物自带 `/*!40014 SET ... FOREIGN_KEY_CHECKS=0 */`，导入时**不怕外键顺序**。
- 自检（实测值）：`44585 字节`、`DROP TABLE` / `CREATE TABLE` / `INSERT INTO` **各 8 个**、抽一行能看到 `'你好，我叫小明'` 这样的中文（不是乱码）。

**② SFTP 上传两样**：

| 本机 | 服务器 |
|---|---|
| `backend\target\mha_full.sql` | `/root/mha-upload/mha_full.sql` |
| `backend\uploads`（整个**目录**） | `/root/mha-upload/uploads/`（保住 `bussiness/article`、`bussiness/user_avatar` 两层结构） |

**③ 服务器导入**（用应用账号 `mha` 就够，它对该库有 `ALL PRIVILEGES`）：

```bash
mysql -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 -D mental_health_assistant < /root/mha-upload/mha_full.sql
```

- 成功时**没有任何输出**；`-D mental_health_assistant` 不能少（dump 里没有 `USE`）。
- ⚠️ 这是**整库覆盖**：先 `DROP TABLE` 再重建这 8 张表，服务器上原有的行（包括手工 `INSERT` 的 admin）会被全部替换。

**④ 上传文件落位 + 自检**（落点 = `WorkingDirectory=/opt/mha/backend` + `file.upload-dir: ./uploads`）：

```bash
sudo mkdir -p /opt/mha/backend/uploads; sudo cp -r /root/mha-upload/uploads/. /opt/mha/backend/uploads/; sudo chown -R mha:mha /opt/mha/backend/uploads; find /opt/mha/backend/uploads -type f | wc -l; curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1/files/bussiness/user_avatar/1792047868545-d78bcf3e.jpg
```

期望：`10` 和 `200`（库里 10 行 `sys_file_info` 与 10 个文件一一对应，少一个就是一张裂图）。

**⑤ 验收**：刷新浏览器 → 重新登录 → 后台文章 / 咨询记录 / 情绪日记都应是本机的数据，封面头像不裂。

- **不用重启后端**：连接池读到的是新数据。
- 但**建议退出重登**：JWT 里带的是 `userId`，本机 admin 的 id 与服务器上手工建的那条可能不同，旧 token 会撞上「用户不存在 → `A0230 token无效`」。

### 5.3 备份（动数据之前先做这个）

```bash
TS=$(date +%Y%m%d-%H%M%S); D=/root/mha-backup/$TS; sudo mkdir -p $D; sudo mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 --result-file=$D/mha_full.sql mental_health_assistant; sudo tar czf $D/uploads.tar.gz -C /opt/mha/backend uploads; ls -lh $D
```

> ⚠️ 本机 `.db-backup/` 里那两份 `mental_health_assistant_full_20261015-1742/1752.sql`（52406 B）是**改 admin 口令之前**的快照：
> 拿它们恢复会把口令退回老值，并把已删的 `ai_analysis_task` 表和 `emotion_diary` 的 4 个已删列一起带回来。要留备份就用新导的那份。

### 5.4 恢复

```bash
# 结构（只结构、无数据；库要先存在）
mysql -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 -e "CREATE DATABASE IF NOT EXISTS mental_health_assistant DEFAULT CHARACTER SET utf8mb4;"
mysql -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 -D mental_health_assistant < db/schema.sql

# 数据（整库覆盖式）
mysql -umha -p"$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)" -h127.0.0.1 -D mental_health_assistant < /root/mha-backup/<TS>/mha_full.sql

# 文件
sudo tar xzf /root/mha-backup/<TS>/uploads.tar.gz -C /opt/mha/backend && sudo chown -R mha:mha /opt/mha/backend/uploads
```

- 恢复演练与更多细节见 `.db-backup/RESTORE.md`。
- 只想搬数据、不想重建结构：服务器先执行第 2 节的 `db/schema.sql`，再 `mysqldump --no-create-info` 单独导数据。
- ⚠️ 空库（0 行）启动时**没有管理员**，而注册接口只能建普通用户 → 必须手工 `INSERT` 一条 `user_type=2` 的账号（SQL 见 `docs/DEPLOY-PROGRESS.md` 第六节）。

---

## 6. HTTPS（有域名就做）

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com          # 自动改 nginx 配置并跳转 443
sudo systemctl status certbot.timer              # 自动续期（certbot 会装 timer）
```

签发后浏览器不再提示不安全；`/files/**`、`/api/**` 全部走 HTTPS。

---

## 7. 上线自检（6 步，每步都有明确期望）

| # | 检查 | 期望 |
|---|---|---|
| 1 | `curl -s http://127.0.0.1:1236/api/test` | 返回统一响应体 JSON（后端存活） |
| 2 | `curl -sI https://你的域名/` | `200`，返回前端 `index.html` |
| 3 | 浏览器登录 `tester / 123456` | 进前台「心理咨询」 |
| 4 | 发一条消息 | AI 回复**逐字**出现（整段一次性出现 = `proxy_buffering` 没关） |
| 5 | 后台上传一张封面 / 换个头像 | 列表能看到图（404 = `uploads/` 没搬或工作目录不对） |
| 6 | 管理员登录 → 后台「用户管理」 | 能看到列表、能禁用/启用；被禁用的账号立刻无法登录 |

---

## 8. 日常运维

**更新发布**（改完代码）

```bash
# 后端：构建 → 上传为 app.jar.new → 原子替换 → 重启
scp backend/target/application-0.0.1-SNAPSHOT.jar root@你的IP:/opt/mha/backend/app.jar.new
ssh root@你的IP "cd /opt/mha/backend && cp -f app.jar app.jar.bak && mv -f app.jar.new app.jar && chown mha:mha app.jar && systemctl restart mha"

# 前端：构建 → 覆盖 dist（静态文件，无需重启 Nginx）
scp -r frontend/dist/* root@你的IP:/var/www/mental-health/dist/
```

> 改了 `ALTER TABLE` 记得同步 `db/schema.sql`；`app.jar.bak` 就是回滚用的上一个版本（`mv app.jar.bak app.jar && systemctl restart mha`）。

**备份**（建议 crontab 每天跑，`crontab -e`）

```bash
30 3 * * * mysqldump -umha -p'数据库口令' --single-transaction --routines --triggers --events mental_health_assistant | gzip > /var/backups/mha-$(date +\%F).sql.gz
40 3 * * * tar czf /var/backups/mha-uploads-$(date +\%F).tar.gz -C /opt/mha/backend uploads
50 3 * * * find /var/backups -name 'mha-*' -mtime +14 -delete
```

**日志**：后端 stdout/stderr 进 journald（`journalctl -u mha --since today`），Nginx 走系统默认 logrotate。

---

## 9. 故障排查

| 现象 | 大概率原因 / 处理 |
|---|---|
| 502 Bad Gateway | 后端没起来：`systemctl status mha` + `journalctl -u mha -n 100`；或 `proxy_pass` 端口写错 |
| 启动即退出，日志 `IllegalStateException: jwt.secret 未配置或长度不足 32 位` | `mha.env` 没被读到（路径/权限）或 `JWT_SECRET` 太短 —— 生成：`openssl rand -base64 48` |
| 启动报数据库连接失败 | `DB_*` 写错、MySQL 没起、或 `mha` 账号没有该库权限（第 2 节） |
| 登录成功但立刻跳回登录页 | 浏览器里存的是**旧密钥签发的 token**（换过 `JWT_SECRET` 就会出现）；重新登录即可。前端拦截器对 `A0230/A0231/A0301` 会自动清 token 跳登录页 |
| AI 回复整段一次性出现 | `location /api/` 里缺 `proxy_buffering off;` 与 `proxy_read_timeout 300s;` |
| 上传大图报 413 / 空响应 | `client_max_body_size` 没设（Nginx 默认 1MB）；后端侧上限 5MB，超出会返回 `{"code":"5004"}` |
| 上传的文件 404 | `uploads/` 没搬、或 `WorkingDirectory` 不对（文件落在别处）；第 5 节与第 3.4 节 |
| 页面刷新 404 | `try_files ... /index.html` 缺失（前端是 history 路由） |
| 看板「今天」和日期差 8 小时 | `mha.service` 里 `TZ=Asia/Shanghai` 没了 |
| 中文乱码 | 库/表不是 `utf8mb4`，或连接串编码不对（`application.yaml` 的 url 已带 `utf8mb4` 相关参数） |
| 请求返回 429 | 触发了 Nginx 登录限流（默认同一 IP 每分钟 5 次，`deploy/nginx.conf.example` 可调）；应用层还有「同账号连错 5 次锁 15 分钟」 |
| 后台上传 `.html` 被拒 `不支持的文件类型` | 正常：白名单只收图片 `jpg/jpeg/png/gif/bmp/webp` 与 `mp4/webm`（防「上传即执行」） |

---

## 10. 上线前的安全清单

| 项 | 状态 |
|---|---|
| 数据库口令不在仓库里 | ✅ 已改 `${DB_PASSWORD:}`，真实值只在服务器环境变量 |
| JWT 密钥不在仓库里 + 启动强校验 | ✅ 已改 `${JWT_SECRET:}`，< 32 位拒绝启动；**旧密钥已随 git 历史泄露，绝不复用** |
| 登录失败限流 | ✅ 应用层同账号连错 5 次锁 15 分钟 + 网关层 `limit_req_zone` |
| 上传扩展名白名单 + `/files` 加固 | ✅ 后端白名单；Nginx 再加 `nosniff`、CSP `sandbox`、拒绝脚本后缀 |
| 令牌异常统一 401 | ✅ 旧密钥/过期/已删用户的 token 返回 `A0230`（不再 500） |
| 只暴露 22 / 80 / 443 | ⬜ 检查云安全组 + `ufw status`；`3306`/`1236` 必须只监听 127.0.0.1 |
| 测试账号弱口令 | ⬜ **必须处理**：`admin` / `tester` 的 `123456` 写在所有文档里 —— 改口令或删账号（后台「用户管理」可禁用） |
| MySQL 专用账号 | ⬜ 用 `mha` 账号跑应用，不要用 `root`；`mysql_secure_installation` 走一遍 |
| 定期备份 | ⬜ 按第 8 节加 crontab，并**演练一次恢复**（不然备份不算数） |
| HTTPS | ⬜ 有域名就走第 6 节；没有也建议自签或用云厂商证书 |

---

## 11. 本机（Windows/DSH 沙箱）才有的坑，服务器上不会遇到

- 本机 `mvn -B clean compile` 因 `backend/target` 上继承的 `Everyone:(DENY)(DC)` ACE 会失败 →
  用 `mvn -B compile`（或 `mvn -B package` 不加 `clean`）。服务器上没有这个 ACL，`mvn clean package` 正常。
- 本机沙箱里 `npm run build` 会因 esbuild 的 `spawn EPERM`（命名管道限制）失败 →
  构建类命令要用一次性提权跑。服务器上没有这个限制。
- 本机 `JWT_SECRET` 等真实值放在 gitignored 的 `backend/src/main/resources/application-local.yaml`；
  **服务器上不需要这个文件**，用 `/etc/mha/mha.env`。
