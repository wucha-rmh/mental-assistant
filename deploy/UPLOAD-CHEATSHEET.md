# 本机构建并上传（给 Windows cmd 用；PowerShell 用户可跳过 /d）

## 1. 构建前端（cmd 必须带 /d，否则只换 D 盘的“记忆目录”、当前盘还是 C:）
cd /d D:\mental_health_assistant\frontend
npm run build

:: 确认产物里出现了用户管理页的 chunk（会打印 users-xxxx.js）
dir dist\assets | findstr /i "users-"

## 2. 构建后端 jar（已构建过可跳过；产物 124 MB）
cd /d D:\mental_health_assistant\backend
mvn -B package -DskipTests

## 3. FinalShell 的 SFTP 面板上传（左 → 右）
:: D:\mental_health_assistant\backend\target\application-0.0.1-SNAPSHOT.jar  →  /root/mha-upload/app.jar
:: D:\mental_health_assistant\frontend\dist\  (整个目录)                     →  /root/mha-upload/dist/

## 4. 服务器上放好并启动（FinalShell 的 SSH 终端）
sudo mv /root/mha-upload/app.jar /opt/mha/backend/app.jar
sudo chown mha:mha /opt/mha/backend/app.jar
sudo rm -rf /var/www/mental-health/dist/*
sudo cp -r /root/mha-upload/dist/. /var/www/mental-health/dist/
sudo chown -R mha:mha /var/www/mental-health/dist
sudo systemctl start mha
sleep 3 && sudo systemctl status mha --no-pager
curl -s http://127.0.0.1:1236/api/test; echo
curl -sI http://127.0.0.1/ | head -n 1

## 4.5 起不来时：前台跑一次看真实报错（systemd 只给 exit-code 1，看不到原因）

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
sudo -u mha /tmp/run-mha.sh          # 报错看 Caused by；起成功会停住，Ctrl+C 退出
```

> ⚠️ 别写成带反斜杠续行的单引号一行短命令 —— 粘贴到交互式 bash 时反斜杠后容易被插空格，
> 报 `bash: line 1:  : command not found`。用上面这个 heredoc 脚本最稳。

## 5. 建管理员（空库必须做：注册接口只能建普通用户）
# $2a$10$... 处换成你自己口令的 BCrypt 哈希（强度 10，生成方式见 docs/DEPLOY-PROGRESS.md 第六节）
sudo mysql --default-character-set=utf8mb4 -D mental_health_assistant <<'SQL'
INSERT INTO user (username, password, nickname, user_type, status, created_at, updated_at)
VALUES ('admin', '$2a$10$换成你自己算出来的哈希', '管理员', 2, 1, NOW(), NOW());
SQL

curl -s -X POST http://127.0.0.1:1236/api/user/login -H 'Content-Type: application/json' --data-binary '{"username":"admin","password":"你的口令"}'
