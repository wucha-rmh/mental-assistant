# 服务器部署 · 端到端执行记录（FINAL）

> 项目：心理健康AI助手（Vue3 + Spring Boot 4）
> 目标机：lavm-likndeoyda（Ubuntu 22.04+）
> 记录时间：2026-10-15
> 本文件是**唯一权威的部署记录**，任何新会话都应以本文件为准，不要凭记忆重述。

---

## 一、当前进度总览

| 项 | 状态 |
|---|---|
| 云服务器 | 已就绪，Ubuntu 22.04+，2C4G |
| 依赖（JDK17 / MySQL8 / Nginx / 防火墙） | ✅ 已装 |
| 数据库 `mental_health_assistant` | ✅ 已建，8 张表（`db/schema.sql`），**0 行数据** |
| MySQL 专用账号 | ✅ `mha@127.0.0.1` 与 `mha@localhost`（两个 host 都要建，见 5.4），口令为随机串，只保存在服务器 `/etc/mha/mha.env`，**不入库** |
| `/etc/mha/mha.env` | ✅ 已生成（含随机 `JWT_SECRET`） |
| systemd 单元 `mha.service` | ⚠️ 已安装并 enable，首次启动失败（**根因已定位：MySQL 账号 host 不匹配 `mha@localhost` vs `mha@127.0.0.1`**，修复命令见 5.4） |
| Nginx 站点 | ✅ 已启用，`nginx -t` 通过，`curl -I http://127.0.0.1/` → 200 |
| 前端产物部署 | ✅ `dist/` 已拷贝到 `/var/www/mental-health/dist`（含 `users-CrW9Mqbs.js`） |
| 后端 jar 部署 | ✅ 已放到 `/opt/mha/backend/app.jar` |
| 管理员账号 | ⬜ 未创建（空库需要手工 SQL，见第六节） |
| 本机 → 服务器上传 | ✅ 已完成（`/root/mha-upload/` 下有 jar、dist、db、deploy） |

---

## 二、本机准备（Windows）

### 2.1 构建后端 jar

```cmd
cd /d D:\mental_health_assistant\backend
mvn -B package -DskipTests
```

产物：`backend\target\application-0.0.1-SNAPSHOT.jar`

- 大小：**130466580 字节**（124.42 MB）
- 内置：`BOOT-INF/classes/application.yaml`（占位符）、`BOOT-INF/classes/application-local.yaml`（204 B，含本机库口令 / DeepSeek key / 本机 JWT secret）
- 服务器上的环境变量优先级高于 jar 内配置，因此会覆盖其中的本机值

### 2.2 构建前端（⚠️ TEMP 必须指向工作区，见踩坑 17）

```cmd
cd /d D:\mental_health_assistant\frontend
mkdir D:\mental_health_assistant\.tmp-build
set "TEMP=D:\mental_health_assistant\.tmp-build"
set "TMP=%TEMP%"
npm run build
```

- 产物：`frontend\dist` = **39 个文件 / 3844624 字节（3.67 MB）**
- 校验点：`dir dist\assets | findstr /i "users-"` 必须出现 `users-CrW9Mqbs.js`（这是「用户管理」页的 chunk）
- `dist/` 已被 gitignore，**不进仓库，必须单独上传**

### 2.3 上传（FinalShell 的 SFTP 面板，或 scp）

| 左边（本机） | 右边（服务器） | 校验 |
|---|---|---|
| `backend\target\application-0.0.1-SNAPSHOT.jar` | `/root/mha-upload/`（可重命名为 `app.jar`） | 130466580 字节 |
| `frontend\dist\`（整个目录） | `/root/mha-upload/dist/` | 39 个文件 |

scp 备选（Windows 10+ 自带）：

```cmd
scp D:\mental_health_assistant\backend\target\application-0.0.1-SNAPSHOT.jar root@<公网IP>:/root/mha-upload/app.jar
scp -r D:\mental_health_assistant\frontend\dist root@<公网IP>:/root/mha-upload/
```

---

## 三、服务器侧首次初始化（已完成，可重复执行）

```bash
# 把 db/ 与 deploy/ 一起传上去后，在仓库根目录执行
sudo SERVER_NAME=<域名或IP> bash deploy/deploy.sh
```

脚本 7 步：装依赖 → 建 `mha` 用户与 `/opt/mha/backend`、`/var/www/mental-health` 目录 → 建库灌 `db/schema.sql` → 生成 `/etc/mha/mha.env` → 装 `mha.service` → 装 Nginx 站点 → 开防火墙（只留 22/80/443）。

**实测结果**：JDK 17.0.20.1 / MySQL 8.0.46 / Nginx 1.24.0；库已建（8 表）；账号 `mha@127.0.0.1` 口令随机生成并打印（本记录第一节）；`mha.env` 已生成；单元已 enable（因当时缺 app.jar 未 start）；Nginx `nginx -t` 通过。

---

## 四、部署 jar 与前端产物（已完成）

```bash
sudo mv /root/mha-upload/application-0.0.1-SNAPSHOT.jar /opt/mha/backend/app.jar
sudo chown mha:mha /opt/mha/backend/app.jar

sudo rm -rf /var/www/mental-health/dist/*
sudo cp -r /root/mha-upload/dist/. /var/www/mental-health/dist/
sudo chown -R mha:mha /var/www/mental-health/dist
ls /var/www/mental-health/dist/assets | grep users     # 期望：users-CrW9Mqbs.js

sudo systemctl start mha
```

---

## 五、⚠️ 当前阻塞点：后端启动失败（exit-code 1）

`sudo systemctl status mha --no-pager` 的实测输出：

```
● mha.service - Mental Health Assistant Backend (Spring Boot)
     Loaded: loaded (/etc/systemd/system/mha.service; enabled; preset: enabled)
     Active: activating (auto-restart) (Result: exit-code) since Thu 2026-10-15 20:07:47 CST
    Process: 30926 ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar app.jar (code=exited, status=1/FAILURE)
        CPU: 16.250s
```

- `activating (auto-restart)` 是 `Restart=on-failure` + `RestartSec=10` 造成的重启循环，**不是新的信息**
- `curl -s http://127.0.0.1:1236/api/test` → 空（后端没起来）
- `curl -sI http://127.0.0.1/` → `HTTP/1.1 200 OK`（**Nginx 与前端产物正常**）

### 5.1 标准诊断动作（前台跑，直接看真实原因）

systemd 只给退出码、不给原因。用下面这个 heredoc 脚本前台跑一次，报错会直接打在屏幕上：

```bash
sudo systemctl stop mha

sudo tee /tmp/run-mha.sh >/dev/null <<'SH'
#!/usr/bin/env bash
cd /opt/mha/backend
set -a; . /etc/mha/mha.env; set +a
export TZ=Asia/Shanghai
exec java -Xms256m -Xmx512m -jar app.jar
SH

sudo chmod +x /tmp/run-mha.sh
sudo -u mha /tmp/run-mha.sh
```

- **报错退出** → 看结尾的 `Caused by:` 那几行
- **打印 `Started AiSpringbootApplication in x seconds` 后停住** → 应用本身没问题（Ctrl+C 退出），问题在 systemd 环境层，继续跑 5.2
- **`no main manifest attribute` / `Unable to access jarfile`** → jar 没传完整，核对 `ls -l /opt/mha/backend/app.jar` = 130466580 字节

### 5.2 systemd 层对照（仅当前台能起来时才需要）

```bash
sudo sed -E 's/=.*/=<已设置>/' /etc/mha/mha.env       # 只看键名，值已掩码
sudo systemctl show mha -p EnvironmentFiles -p Environment -p User -p WorkingDirectory
sudo ls -l /etc/mha/mha.env /opt/mha/backend/app.jar
```

### 5.3 已知的候选原因（按概率）

| 现象（Caused by 关键词） | 原因 | 处置 |
|---|---|---|
| `jwt.secret 未配置或长度不足 32 位` | `JWT_SECRET` 没被读到 | 确认 `/etc/mha/mha.env` 里有 `JWT_SECRET=` 且非空；`systemctl show mha` 看 EnvironmentFiles |
| `Access denied for user 'mha'@'localhost'` | **MySQL 反向解析把 127.0.0.1 认成 localhost**，而账号只建了 `'mha'@'127.0.0.1'` —— 主机名对不上（就是本次踩到的坑，见 5.4） | 给 `localhost` 也建同名账号（5.4 的修复命令）；`deploy/deploy.sh` 已同步修好 |
| `Access denied for user 'mha'@'127.0.0.1'` | 口令不一致 | `sudo mysql -e "ALTER USER 'mha'@'127.0.0.1' IDENTIFIED BY '<mha.env 里的 DB_PASSWORD>'; FLUSH PRIVILEGES;"` |
| `Communications link failure` / `Connection refused` | MySQL 没起或端口不对 | `sudo systemctl status mysql; sudo ss -ltnp | grep 3306` |
| `OutOfMemoryError: Java heap space` | `-Xmx512m` 偏小 | 改 `deploy/mha.service` 的 `-Xmx1g`，`systemctl daemon-reload && systemctl restart mha` |
| 起了又停、无异常 | 前台能跑 = systemd 环境差异 | 用 5.2 对照 env 文件与单元参数 |

### 5.4 本次实测的根因与修复（MySQL 账号 host 不匹配）

前台跑出来的最后一行 `Caused by`：

```
java.sql.SQLException: Access denied for user 'mha'@'localhost' (using password: YES)
```

MySQL 默认开启反向域名解析：来自 `127.0.0.1` 的 TCP 连接在服务端被识别成主机名 **`localhost`**；
而 `deploy.sh` 只创建了 `'mha'@'127.0.0.1'` → 两条账号记录对不上 → 认证失败。
（MySQL 对「口令错」和「账号不存在」报的是同一句话，所以很容易误判成口令问题。）

修复（口令直接从 `mha.env` 读，不用手抄；实测执行后服务即正常启动）：

```bash
PW=$(sudo sed -n 's/^DB_PASSWORD=//p' /etc/mha/mha.env)
echo "读到口令长度: ${#PW}"

sudo mysql -e "CREATE USER IF NOT EXISTS 'mha'@'localhost' IDENTIFIED BY '${PW}'; ALTER USER 'mha'@'localhost' IDENTIFIED BY '${PW}'; GRANT ALL PRIVILEGES ON mental_health_assistant.* TO 'mha'@'localhost'; FLUSH PRIVILEGES;"

# 用与 App 相同的姿势验证（同口令、TCP、指定库）
mysql -umha -p"${PW}" -h127.0.0.1 -D mental_health_assistant -e "SELECT COUNT(*) AS users FROM user;"

sudo systemctl start mha
sleep 3 && sudo systemctl status mha --no-pager | head -n 6
curl -s http://127.0.0.1:1236/api/test; echo
```

`deploy/deploy.sh` 已同步改为**同时创建 `'127.0.0.1'` 与 `'localhost'` 两个 host** 并各自授权，避免重装时再踩。

---

## 六、创建管理员账号（空库必须做）

注册接口**只能创建普通用户**（2026-10-15 修复的提权漏洞、有意为之），所以空库必须用 SQL 建管理员。

口令**自己定一个强口令**（不要用 6 位纯数字），再算它的 BCrypt 哈希（强度 10，与后端 `BCryptPasswordEncoder` 默认一致）：
直接 `new BCryptPasswordEncoder().encode("你的口令")`，或从 fat jar 里抽出 `spring-security-crypto-*.jar` 跑
`BCrypt.hashpw(pw, BCrypt.gensalt(10))`（还需要 `spring-jcl`，否则 `NoClassDefFoundError`）。
把下面的 `$2a$10$...` 占位符换成你算出来的哈希：

```bash
sudo mysql --default-character-set=utf8mb4 -D mental_health_assistant <<'SQL'
INSERT INTO user (username, password, nickname, user_type, status, created_at, updated_at)
VALUES ('admin', '$2a$10$换成你自己算出来的哈希', '管理员', 2, 1, NOW(), NOW());
SQL

# 验证（期望 code=200 且带 token）
curl -s -X POST http://127.0.0.1:1236/api/user/login \
  -H 'Content-Type: application/json' \
  --data-binary '{"username":"admin","password":"你的口令"}'
```

> 若想让本机的演示数据一起过去，先在**本机**重新导一份 dump（`.db-backup/` 里的旧 dump 是精简表结构之前的，
> 会把已删除的表/列带回来），上传后 `sudo mysql < mha_now.sql`；那份 dump 里 admin 的口令与你本机一致，
> 无需重复执行本节 SQL。

---

## 七、上线后的验收清单

```bash
sudo systemctl status mha --no-pager | head -n 5      # active (running)
curl -s http://127.0.0.1:1236/api/test                # 后端健康
curl -sI http://127.0.0.1/ | head -n 1                # Nginx 200
```

浏览器：`http://<公网IP>/` → `admin / 你的口令` → 左侧菜单应出现「用户管理」页（列表 + 禁用/启用）。
云厂商**控制台安全组**也要放通 80（ufw 只管服务器内部这一层）。

---

## 八、常用运维命令

```bash
sudo journalctl -u mha -n 80 --no-pager        # 后端日志
sudo systemctl restart mha                      # 重启后端
sudo tail -f /var/log/nginx/error.log           # Nginx 错误
curl -sI http://127.0.0.1/api/test              # 经 Nginx 打后端
```

---

## 九、安全加固状态（已做 / 待做）

| 项 | 状态 |
|---|---|
| JWT 密钥、库口令环境变量化（`application.yaml` 只留占位符） | ✅ 已做 |
| `JwtConfig` 启动强校验密钥 ≥ 32 位 | ✅ 已做 |
| 登录失败限流（5 次 / 15 分钟，应用层）+ Nginx `limit_req` 模板 | ✅ 已做 |
| 上传扩展名白名单（拒绝 `.html`/`.svg` 等）+ `/files` 加固模板 | ✅ 已做 |
| 注册提权修复（注册不接受 `userType`） | ✅ 已做 |
| multipart 上限 5MB + 统一错误响应 | ✅ 已做 |
| **测试账号弱口令 `admin`/`tester` = `123456`** | ⬜ **上线前必须改**（生产 admin 也别用纯数字弱口令，本机库与代码文档里仍是弱口令） |
| 定时备份（mysqldump + uploads） | ⬜ 建议加 cron |
| 日志轮转 | ⬜ 建议配 logrotate |
| 只开放 22 / 80 / 443 | ✅ ufw 已做，控制台安全组需确认 |

---

## 十、相关文件索引

| 文件 | 用途 |
|---|---|
| `docs/deploy.md` | 完整部署手册（规格选型、建库、systemd、Nginx/SSE、HTTPS、备份、自检） |
| `deploy/deploy.sh` | 服务器侧一键初始化（幂等，已有配置不覆盖） |
| `deploy/mha.service` | systemd 单元模板（含 `TZ=Asia/Shanghai`、`EnvironmentFile=/etc/mha/mha.env`） |
| `deploy/mha.env.example` | 环境变量模板（`JWT_SECRET` / `DB_*` / 可选 `SPRING_AI_OPENAI_API_KEY`） |
| `deploy/nginx.conf.example` | Nginx 站点模板（`limit_req` 登录限流、SSE `proxy_buffering off`、`/files` 加固） |
| `deploy/pack.ps1` | 本机打包脚本 |
| `deploy/UPLOAD-CHEATSHEET.md` | 上传速查单（cmd 构建 → SFTP → 服务器命令 → 建 admin） |
| `db/schema.sql` | 表结构快照（8 张表，无数据，改表后需重新导出） |
| `CLAUDE.md` | 项目工作指南（关键约定 + 踩坑 0–17） |
