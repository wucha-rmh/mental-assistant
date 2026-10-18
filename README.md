# 心理健康AI助手 · AI Mental Health Assistant

> 前后端分离的心理健康服务平台：用户注册登录后可与 AI 进行**流式**心理疏导对话，记录情绪日记、浏览心理知识库、维护个人资料；管理员在独立后台查看数据看板并管理用户、内容与留言。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.8-6DB33F)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![Vue](https://img.shields.io/badge/Vue-3.5-42B883)
![Vite](https://img.shields.io/badge/Vite-7-646CFF)

## 功能

**用户端**

- 注册 / 登录 / 头像上传 / 个人中心（昵称、性别、生日；修改密码需验证原密码）
- AI 心理疏导：SSE 流式回复、多会话管理、历史消息回看
- 情绪日记：一天一条（重复提交即更新当天），心情评分 + 今日感想，含趋势与历史列表
- 心理知识库：分类树、文章列表与详情、阅读量计数
- 忘记密码：未登录也能给管理员留言求助

**管理后台**（前端路由按 `userType` 分流，管理员只进 `/back/**`）

- 数据看板：用户 / 会话 / 消息 / 日记 / 文章的总量与趋势（ECharts）
- 用户管理：关键字与状态筛选、分页、禁用 / 启用、**重置用户密码**
- 咨询记录：全量会话与消息查看
- 内容管理：知识文章增删改与上下架、情绪日记查看与删除
- 留言管理：查看 / 回复 / 删除用户留言

## 技术栈

- **后端**：Java 17、Spring Boot 4.0.8、Spring MVC、Spring Security 7、Spring AI 2.1.0-M1、MyBatis-Plus 3.5.17、MySQL 8、Hutool、Lombok
- **认证**：JWT（java-jwt 4.4.0）+ BCrypt，自定义过滤器逐请求鉴权
- **前端**：Vue 3.5、Vite 7、Element Plus 2.13、Pinia 3、Vue Router 4、axios、ECharts 6、wangEditor 5
- **流式对话**：后端 `SseEmitter` 输出 `text/event-stream`，前端 `@microsoft/fetch-event-source` 接收

## 项目结构

```
mental_health_assistant/
├── backend/                       # Spring Boot 后端（端口 1236）
│   └── src/main/java/com/rmh/aispringboot/
│       ├── controller/ service/ mapper/ entity/ DTO/   # 分层实现
│       ├── AiService/                                  # AI 对话与情绪分析
│       └── config/ util/ task/ common/                 # 配置、JWT、定时任务、统一响应
├── frontend/                      # Vue 3 前端（端口 5173）
│   └── src/
│       ├── views/                 # 12 个页面（用户端 7 + 管理后台 5）
│       ├── components/ api/ stores/
│       └── styles/tokens.scss     # 设计令牌（颜色 / 圆角 / 阴影 / 间距）
└── db/
    ├── schema.sql                 # 库表结构（8 张表，不含数据）
    └── seed-demo-data.sql         # 演示数据（可重复执行）
```

## 快速开始

**环境要求**：JDK 17+、Maven 3.9+、MySQL 8+、Node.js 18+

```bash
# 1. 建库并灌入表结构（可选：再加演示数据）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS mental_health_assistant DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p mental_health_assistant < db/schema.sql
mysql -uroot -p mental_health_assistant -e "source db/seed-demo-data.sql"
```

```bash
# 2. 启动后端（http://localhost:1236）
cd backend && mvn spring-boot:run
```

```bash
# 3. 启动前端（http://localhost:5173）
cd frontend && npm install && npm run dev
```

`application.yaml` 不含任何口令，只有 `${JWT_SECRET:}` 这类占位符；本机开发请自建 `backend/src/main/resources/application-local.yaml`（已在 `.gitignore` 中）：

```yaml
spring:
  datasource:
    password: <MySQL 口令>
  ai:
    openai:
      api-key: <大模型 API Key>
jwt:
  secret: <至少 32 位，openssl rand -base64 48 生成>
```

生产环境改用环境变量注入：`JWT_SECRET`、`DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD`、`SPRING_AI_OPENAI_API_KEY`。

**账号**：`tester / 123456`（普通用户）；执行 `seed-demo-data.sql` 后另有 5 个 `demo_*` 账号（口令同为 `123456`）。
注册接口只能创建普通用户，**管理员账号需直接写库**：`user` 表 `password` 存 BCrypt 哈希（`BCrypt.hashpw(口令, BCrypt.gensalt(10))`），`user_type` 设为 `2`。

## 接口

- 认证：请求头 `token`，值为 JWT 原文
- 响应统一为 `{"code":"200","msg":"...","data":{...}}`，成功码是**字符串** `"200"`
- 共 38 个 REST 端点：

<details>
<summary>展开接口清单</summary>

| 模块 | 端点 |
|---|---|
| 用户 | `POST /api/user/login`、`POST /api/user/register`、`GET /api/user/current`、`POST /api/user/avatar`、`POST /api/user/logout`、`PUT /api/user/profile`、`PUT /api/user/password`、`GET /api/user/admin/page`、`PUT /api/user/admin/{id}/status`、`PUT /api/user/admin/{id}/password` |
| 心理对话 | `POST /api/psychological-chat/session/start`、`POST /api/psychological-chat/stream`（SSE）、`GET /api/psychological-chat/sessions`、`GET /api/psychological-chat/sessions/{id}/messages`、`DELETE /api/psychological-chat/sessions/{id}`、`GET /api/psychological-chat/session/{id}/emotion` |
| 知识文章 | `GET /api/knowledge/category/tree`、`GET /api/knowledge/article/page`、`GET /api/knowledge/article/{id}`、`POST /api/knowledge/article`、`PUT /api/knowledge/article/{id}`、`PUT /api/knowledge/article/{id}/status`、`DELETE /api/knowledge/article/{id}` |
| 情绪日记 | `GET /api/emotion-diary/page`、`POST /api/emotion-diary`、`DELETE /api/emotion-diary/{id}`、`GET /api/emotion-diary/admin/page`、`DELETE /api/emotion-diary/admin/{id}` |
| 留言反馈 | `POST /api/feedback`、`POST /api/feedback/guest`（无需登录）、`GET /api/feedback/mine`、`GET /api/feedback/admin/page`、`GET /api/feedback/admin/pending-count`、`PUT /api/feedback/admin/{id}/reply`、`DELETE /api/feedback/admin/{id}` |
| 文件与看板 | `POST /api/file/upload`（单文件 ≤ 5MB，类型白名单）、`GET /api/data-analytics/overview`、`GET /api/test` |

</details>

## 数据库

8 张表：`user`、`consultation_session`、`consultation_message`、`emotion_diary`、`knowledge_category`、`knowledge_article`、`sys_file_info`、`user_feedback`。

- `db/schema.sql`：表结构快照，可随时重建（⚠️ 每张表前都有 `DROP TABLE`，**只用于建空库**）
- `db/seed-demo-data.sql`：演示数据，可重复执行（只清理自己写入的演示数据）

## 部署

- 后端 `mvn -B clean package -DskipTests -Pprod` 打成可执行 jar（`-Pprod` 会排除本机配置），前端 `npm run build`
- 静态文件交 Nginx 托管，`/api` 反向代理到后端；SSE 接口需关闭 `proxy_buffering`
- 后端由 systemd 托管，敏感配置经环境变量注入；数据库账号按最小权限授权，仅开放必要端口
