# 心理健康AI助手

前后端分离的心理健康服务平台：用户可与 AI 进行**流式**心理疏导对话，记录情绪日记、浏览知识库、维护个人资料；管理员在独立后台查看看板并管理用户、内容与留言。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.8-6DB33F)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![Vue](https://img.shields.io/badge/Vue-3.5-42B883)
![Vite](https://img.shields.io/badge/Vite-7-646CFF)

## 功能

**用户端**：注册登录与个人中心（头像、昵称、改密需验原密码）· AI 流式疏导（SSE、多会话、历史回看）· 情绪日记（一天一条，趋势图）· 知识库（分类树、文章详情支持正文插图）· 忘记密码时给管理员留言

**管理后台**：数据看板（ECharts）· 用户管理（筛选、禁用/启用立即生效、重置密码）· 咨询记录 · 内容管理（文章增删改上下架、日记查看）· 留言回复

## 技术栈

- **后端**：Java 17、Spring Boot 4.0.8、Spring Security 7、Spring AI 2.1.0-M1、MyBatis-Plus 3.5.17、MySQL 8
- **前端**：Vue 3.5、Vite 7、Element Plus 2.13、Pinia 3、axios、ECharts 6、wangEditor 5
- **认证与流式**：JWT（java-jwt）+ BCrypt；后端返回 `Flux<ServerSentEvent<String>>`，前端用 `fetch-event-source` 接收

## 快速开始

环境要求：JDK 17+、Maven 3.9+、MySQL 8+、Node.js 18+

```bash
# 1. 建库并导入表结构（可选：再灌演示数据）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS mental_health_assistant DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p mental_health_assistant < db/schema.sql
mysql -uroot -p mental_health_assistant -e "source db/seed-demo-data.sql"

# 2. 后端 → http://localhost:1236
cd backend && mvn spring-boot:run

# 3. 前端 → http://localhost:5173
cd frontend && npm install && npm run dev
```

`application.yaml` 不含任何口令，只有 `${JWT_SECRET:}` 这类占位符。本机开发自建 `backend/src/main/resources/application-local.yaml`（已 gitignore）：

```yaml
spring:
  datasource:
    password: <MySQL 口令>
  ai:
    openai:
      api-key: <大模型 API Key>
jwt:
  secret: <至少 32 位，openssl rand -base64 48>
```

生产改用环境变量：`JWT_SECRET`、`DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD`、`SPRING_AI_OPENAI_API_KEY`。

**账号**：`tester / 123456`（普通用户）；执行 `seed-demo-data.sql` 后另有 5 个 `demo_*` 账号（口令相同）。注册接口只能建普通用户，管理员需直接写库（`user_type=2`，密码存 BCrypt 哈希）。

## 接口

请求头 `token` 携带 JWT 原文；响应统一为 `{"code":"200","msg":"...","data":{...}}`，成功码是**字符串** `"200"`。共 38 个端点：

<details>
<summary>展开接口清单</summary>

| 模块 | 端点 |
|---|---|
| 用户 | `POST /api/user/login`、`POST /api/user/register`、`GET /api/user/current`、`POST /api/user/avatar`、`POST /api/user/logout`、`PUT /api/user/profile`、`PUT /api/user/password`、`GET /api/user/admin/page`、`PUT /api/user/admin/{id}/status`、`PUT /api/user/admin/{id}/password` |
| 心理对话 | `POST /api/psychological-chat/session/start`、`POST /api/psychological-chat/stream`（SSE）、`GET /api/psychological-chat/sessions`、`GET /api/psychological-chat/sessions/{id}/messages`、`DELETE /api/psychological-chat/sessions/{id}`、`GET /api/psychological-chat/session/{id}/emotion` |
| 知识文章 | `GET /api/knowledge/category/tree`、`GET /api/knowledge/article/page`、`GET /api/knowledge/article/{id}`、`POST/PUT/DELETE /api/knowledge/article[/{id}]`、`PUT /api/knowledge/article/{id}/status` |
| 情绪日记 | `GET /api/emotion-diary/page`、`POST /api/emotion-diary`、`DELETE /api/emotion-diary/{id}`、`GET /api/emotion-diary/admin/page`、`DELETE /api/emotion-diary/admin/{id}` |
| 留言反馈 | `POST /api/feedback`、`POST /api/feedback/guest`（免登录）、`GET /api/feedback/mine`、`GET /api/feedback/admin/page`、`GET /api/feedback/admin/pending-count`、`PUT /api/feedback/admin/{id}/reply`、`DELETE /api/feedback/admin/{id}` |
| 文件与看板 | `POST /api/file/upload`（≤ 5MB，类型白名单）、`GET /api/data-analytics/overview`、`GET /api/test` |

</details>

## 数据库

8 张表：`user`、`consultation_session`、`consultation_message`、`emotion_diary`、`knowledge_category`、`knowledge_article`、`sys_file_info`、`user_feedback`。

- `db/schema.sql`：表结构快照（⚠️ 每条建表前都有 `DROP TABLE`，**只用于建空库**）；已在跑的库改结构请用 `db/migrations/*.sql` 里的幂等脚本
- `db/seed-demo-data.sql`：演示数据，可重复执行

## 部署

- 后端 `mvn -B package -DskipTests -Pprod`（`-Pprod` 排除本机配置），前端 `npm run build`
- Nginx 托管静态文件并把 `/api` 反代到后端；SSE 接口需 `proxy_buffering off`
- 后端由 systemd 托管，敏感配置走环境变量，数据库账号按最小权限授权
