# 心理健康AI助手 · AI Mental Health Assistant

> 一个前后端分离的心理健康服务平台：用户注册登录后可与 AI 进行**流式**心理疏导对话，并记录情绪日记、浏览心理知识库；管理员拥有独立后台，可查看数据看板、管理用户与内容。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.8-6DB33F)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.1.0--M1-6DB33F)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![Vue](https://img.shields.io/badge/Vue-3.5-42B883)
![Vite](https://img.shields.io/badge/Vite-7-646CFF)

---

## 一、功能特性

**用户端**

- 注册 / 登录 / 修改头像，JWT 认证 + BCrypt 存储口令
- AI 心理疏导：**SSE 流式**回复、多会话管理、历史消息回看
- 情绪日记：一天一条（重复提交即更新当天），心情评分 + 今日感想，趋势与历史列表
- 心理知识库：分类树、文章列表与详情、阅读量计数、收藏

**管理后台**

- 数据看板：用户 / 会话 / 消息 / 日记 / 文章的总量与趋势（ECharts）
- 用户管理：关键字与状态筛选、分页、禁用 / 启用账号
- 咨询记录：全量会话与消息查看
- 内容管理：知识文章增删改与上下架、情绪日记查看与删除

前端路由按 `userType` 分流：管理员进入 `/back/**` 后台，普通用户进入前台，互不越界。

---

## 二、技术栈

| 层次 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 4.0.8、Spring MVC、Spring Security 7、Spring AI 2.1.0-M1 |
| 持久层 | MyBatis-Plus 3.5.17（含分页插件）、MySQL 8 |
| 认证 | JWT（java-jwt 4.4.0）+ BCrypt，自定义过滤器做每请求鉴权 |
| 其他 | Hutool、Lombok、Jackson 3（`tools.jackson.*`） |
| 前端 | Vue 3.5 + Vite 7、Element Plus 2.13、Pinia 3、Vue Router 4、axios、ECharts 6、wangEditor 5 |
| 流式对话 | 后端 `SseEmitter` 输出 `text/event-stream`，前端 `@microsoft/fetch-event-source` 接收 |

---

## 三、核心设计

- **流式心理疏导**：`ChatClient` + `MessageChatMemoryAdvisor` 组合，`MessageWindowChatMemory` 保留最近 30 条上下文；
  进程重启后首次对话会自动从 `consultation_message` 回灌最近 30 条，做到「重启不失忆」。
- **认证与即时失效**：`JwtAuthenticationFilter` 每个请求都按 token 中的 `userId` 查库校验账号状态，
  因此管理员禁用账号后，对方手里的 token 立即失效。
- **统一响应与异常**：`Result` + `GlobalExceptionHandler`，业务异常统一返回
  `{"code":"...","msg":"..."}`（HTTP 状态保持 200），前端拦截器只判断 `code`。
- **安全加固**：注册接口不接受 `userType`（防止越权提权）、JWT 密钥启动时强制校验长度、
  登录失败 5 次锁定 15 分钟、上传文件限制 5MB 并按扩展名白名单过滤。

---

## 四、项目结构

```
mental_health_assistant/
├── backend/                       # Spring Boot 后端（端口 1236）
│   ├── src/main/java/com/rmh/aispringboot/
│   │   ├── controller/ service/ mapper/ entity/ DTO/   # 分层实现
│   │   ├── AiService/                                  # Spring AI 对话与情绪分析
│   │   ├── config/ util/ task/ common/                 # 配置、JWT、定时任务、统一响应
│   │   └── ...
│   └── pom.xml
├── frontend/                      # Vue 3 前端（端口 5173）
│   ├── src/views/                 # 12 个页面（用户端 7 + 管理后台 5）
│   ├── src/components/            # 通用组件
│   ├── src/styles/tokens.scss     # 设计令牌（颜色 / 圆角 / 阴影 / 间距）
│   └── vite.config.js             # /api、/files 代理到 1236
├── db/
│   ├── schema.sql                 # 8 张表的库表结构
│   └── seed-demo-data.sql         # 演示数据（可重复执行）
└── README.md
```

---

## 五、快速开始

**环境要求**：JDK 17+、Maven 3.9+、MySQL 8+、Node.js 18+

### 1. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS mental_health_assistant DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p mental_health_assistant < db/schema.sql

# 可选：灌入演示数据（5 个演示账号、若干会话/日记/文章）
mysql -uroot -p mental_health_assistant -e "source db/seed-demo-data.sql"
```

### 2. 配置后端

`backend/src/main/resources/application.yaml` 中不含任何口令，只保留 `${JWT_SECRET:}` 这类占位符，
本机开发请手动创建 `backend/src/main/resources/application-local.yaml`（已加入 `.gitignore`）：

```yaml
spring:
  datasource:
    password: <你的 MySQL 口令>
  ai:
    openai:
      api-key: <大模型 API Key>
jwt:
  secret: <至少 32 位，可用 openssl rand -base64 48 生成>
```

生产环境改用环境变量注入：`JWT_SECRET`、`DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD`、`SPRING_AI_OPENAI_API_KEY`。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务地址：http://localhost:1236

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

### 5. 账号

| 账号 | 口令 | 类型 |
|---|---|---|
| `tester` | `123456` | 普通用户 |
| `demo_xiaoyu` 等 5 个（执行 `seed-demo-data.sql` 后存在） | `123456` | 普通用户 |
| 管理员 | 自行设定 | 管理员（`userType=2`） |

> 注册接口只能创建普通用户，管理员账号需直接写入数据库：`user` 表的 `password` 字段存 **BCrypt** 哈希
> （例如 `BCrypt.hashpw("你的口令", BCrypt.gensalt(10))`），并把 `user_type` 设为 `2`。

---

## 六、接口说明

- 认证：请求头 `token`，值为 JWT 原文
- 响应：统一为 `{"code":"200","msg":"...","data":{...}}`，成功码是**字符串** `"200"`
- 共 28 个 REST 端点：

<details>
<summary>展开接口清单</summary>

| 模块 | 端点 |
|---|---|
| 用户 | `POST /api/user/login`、`POST /api/user/register`、`GET /api/user/current`、`POST /api/user/avatar`、`POST /api/user/logout`、`GET /api/user/admin/page`、`PUT /api/user/admin/{id}/status` |
| 心理对话 | `POST /api/psychological-chat/session/start`、`POST /api/psychological-chat/stream`（SSE）、`GET /api/psychological-chat/sessions`、`GET /api/psychological-chat/sessions/{id}/messages`、`DELETE /api/psychological-chat/sessions/{id}`、`GET /api/psychological-chat/session/{id}/emotion` |
| 知识文章 | `GET /api/knowledge/category/tree`、`GET /api/knowledge/article/page`、`GET /api/knowledge/article/{id}`、`POST /api/knowledge/article`、`PUT /api/knowledge/article/{id}`、`PUT /api/knowledge/article/{id}/status`、`DELETE /api/knowledge/article/{id}` |
| 情绪日记 | `GET /api/emotion-diary/page`、`POST /api/emotion-diary`、`DELETE /api/emotion-diary/{id}`、`GET /api/emotion-diary/admin/page`、`DELETE /api/emotion-diary/admin/{id}` |
| 文件 | `POST /api/file/upload`（单文件 ≤ 5MB，图片 / 视频类型白名单） |
| 看板与其他 | `GET /api/data-analytics/overview`、`GET /api/test` |

</details>

---

## 七、数据库

8 张表：`user`、`consultation_session`、`consultation_message`、`emotion_diary`、
`knowledge_category`、`knowledge_article`、`user_favorite`、`sys_file_info`。

- `db/schema.sql`：库表结构快照（不含数据），可随时重建数据库
- `db/seed-demo-data.sql`：演示数据，可重复执行（只清理自己写入的演示数据，不影响已有账号与文章）

导出表结构快照：

```bash
mysqldump -uroot -p --no-data --skip-comments --no-tablespaces --single-transaction \
  --routines --triggers --events --default-character-set=utf8mb4 \
  mental_health_assistant > db/schema.sql
```

---

## 八、部署

后端打包为可执行 jar（`mvn -B package -DskipTests`），前端构建为静态文件（`npm run build`）：

- 静态文件交给 Nginx 托管，`/api` 反向代理到后端（流式接口需关闭 `proxy_buffering`）
- 后端用 systemd 托管，敏感配置通过 `/etc/mha/mha.env` 环境变量注入
- 数据库账号按最小权限授权，仅开放必要端口
