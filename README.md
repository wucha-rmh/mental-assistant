# 心理健康AI助手 · AI Mental Health Assistant

> 前后端分离的心理健康服务平台：注册登录 → 与 AI 进行**流式**心理疏导对话 → 咨询记录 / 情绪日记 / 心理知识库，另有一套后台管理（用户、内容、数据看板）。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.8-6DB33F)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.1.0--M1-6DB33F)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![Vue](https://img.shields.io/badge/Vue-3.5-42B883)
![Vite](https://img.shields.io/badge/Vite-7-646CFF)

---

## 一、功能一览

**前台（普通用户）**

| 模块 | 能力 |
|---|---|
| 账号 | 注册 / 登录 / 退出、修改头像；JWT 认证 + BCrypt 存口令 |
| AI 心理疏导 | **SSE 流式**回复、多会话管理、历史消息回看、刷新后回到上次会话 |
| 情绪日记 | 一天一条（重复提交即更新当天），心情评分 + 今日感想，趋势与历史列表 |
| 心理知识库 | 分类树、文章列表与详情、阅读量计数、收藏 |

**后台（管理员）**

| 模块 | 能力 |
|---|---|
| 数据看板 | 用户 / 会话 / 消息 / 日记 / 文章的总量与趋势（ECharts） |
| 用户管理 | 关键字、状态、类型筛选 + 分页，禁用 / 启用账号（**禁用即时生效**） |
| 咨询记录 | 全量会话与消息查看 |
| 知识文章 | 新增 / 修改 / 上下架 / 删除 |
| 情绪日记 | 全量查看与删除 |

> 前端路由按 `userType` 强制分流：管理员只能进 `/back/**`，普通用户只能进前台，互不越界。

---

## 二、技术栈

| 层次 | 技术 |
|---|---|
| 语言 / 构建 | Java 17、Maven |
| 后端框架 | Spring Boot 4.0.8、Spring MVC、Spring Security 7、Spring AI 2.1.0-M1 |
| 持久层 | MyBatis-Plus 3.5.17（含 `mybatis-plus-jsqlparser` 分页插件）、MySQL 8 |
| 认证 | JWT（Auth0 java-jwt 4.4.0）+ BCrypt；自定义过滤器做每请求鉴权 |
| 其他 | Hutool、Lombok、Jackson 3（`tools.jackson.*`） |
| 前端 | Vue 3.5 + Vite 7、Element Plus 2.13、Pinia 3、Vue Router 4、axios、ECharts 6、wangEditor 5 |
| 流式对话 | 后端 `SseEmitter` 输出 `text/event-stream`，前端 `@microsoft/fetch-event-source` 接收 |
| 部署 | Nginx（静态托管 + 反向代理 + SSE 调优）、systemd、环境变量配置、ufw |

---

## 三、项目结构

```
mental_health_assistant/
├── backend/                          # Spring Boot 4 后端（端口 1236）
│   ├── src/main/java/com/rmh/aispringboot/
│   │   ├── controller/               # 28 个 REST 端点（用户 / 对话 / 知识 / 日记 / 文件 / 看板）
│   │   ├── service/  mapper/  entity/  DTO/
│   │   ├── AiService/                # Spring AI 对话与情绪分析
│   │   ├── config/  util/  task/  common/
│   │   └── ...
│   ├── src/main/resources/application.yaml    # 只含 ${JWT_SECRET:} 这类占位符，无明文口令
│   └── pom.xml
├── frontend/                         # Vue 3 + Vite 前端（端口 5173）
│   ├── src/views/                    # 12 个页面（前台 7 + 后台 5）
│   ├── src/styles/tokens.scss        # 设计令牌（颜色 / 圆角 / 阴影 / 间距）
│   └── vite.config.js                # /api 与 /files 代理到 1236
├── db/
│   ├── schema.sql                    # 8 张表的库表结构快照（可重建）
│   └── seed-demo-data.sql            # 演示数据种子（可重复执行，丰富展示效果）
├── deploy/                           # 部署产物：deploy.sh、mha.service、mha.env.example、nginx.conf.example
├── docs/
│   ├── api-contract.md               # 前后端接口契约（改接口前必读）
│   ├── deploy.md                     # 云服务器部署手册（含备份与迁移）
│   └── DEPLOY-PROGRESS.md            # 一次真实上线的排障记录
├── CLAUDE.md                         # 项目约定与踩坑记录
└── README.md
```

---

## 四、快速开始

### 环境要求

- JDK 17+、Maven 3.9+
- MySQL 8+
- Node.js 18+

### 1. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS mental_health_assistant DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p mental_health_assistant < db/schema.sql

# 可选：灌一批演示数据（5 个演示账号、6 场会话、25 条日记、6 篇文章，可重复执行）
mysql -uroot -p mental_health_assistant -e "source db/seed-demo-data.sql"
```

`db/schema.sql` 是**结构快照**（不含数据，含 `DROP TABLE IF EXISTS`）。改完表结构请重新导出并提交，否则仓库与真实库会漂移。

### 2. 配置后端敏感信息

`application.yaml` 里**没有任何口令**，只有 `${JWT_SECRET:}`、`${DB_PASSWORD:}` 这类占位符，真实值放两处之一。

**本机开发** —— 手动创建 `backend/src/main/resources/application-local.yaml`（已 gitignore）：

```yaml
spring:
  datasource:
    password: <你的 MySQL 口令>
  ai:
    openai:
      api-key: <你的大模型 API Key>
jwt:
  secret: <至少 32 位，生成：openssl rand -base64 48>
```

**生产** —— 用环境变量注入：

| 变量 | 说明 |
|---|---|
| `JWT_SECRET` | JWT 签名密钥，**必须 ≥ 32 位**，否则后端拒绝启动（`config/JwtConfig.java` 启动时校验） |
| `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` | 数据库连接（默认 `localhost` / `3306` / `root` / 空） |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 需要直接映射属性名时用（同名变量优先级最高） |
| `SPRING_AI_OPENAI_API_KEY` | 大模型 API Key |

> ⚠️ 两个实测过的坑：① 环境变量只对**同名的属性**生效 —— `DB_PASSWORD` 走的是 `application.yaml` 里的 `${...}` 占位符，而占位符解析发生在属性源合并**之后**，一旦 jar 里混进 `application-local.yaml`（profile 专属配置优先级更高）就会盖掉它，所以打包用 `mvn -B package -DskipTests -Pprod`（`prod` profile 会排除该文件）；② MySQL 会把来自 `127.0.0.1` 的连接反解成 `localhost`，建库账号时两个 host 都要授权。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run          # 编译：mvn -B compile
```

服务在 **http://localhost:1236**

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 **http://localhost:5173**（`/api`、`/files` 由 Vite 代理到 1236）

### 5. 演示账号

| 账号 | 口令 | 类型 | 入口 |
|---|---|---|---|
| `tester` | `123456` | 普通用户（userType=1） | 前台「心理咨询」 |
| `demo_*`（5 个） | `123456` | 普通用户 | 由 `db/seed-demo-data.sql` 创建 |
| 管理员 | 自定 | 管理员（userType=2） | 后台 |

> 注册接口**只能创建普通用户**（有意为之，防止水平/垂直越权），所以空库的管理员要用 SQL 建，
> 口令请自行设定强口令并算 BCrypt 哈希 —— 见 [docs/DEPLOY-PROGRESS.md](docs/DEPLOY-PROGRESS.md) 第六节。

---

## 五、接口一览（28 个端点）

统一响应体 `{"code":"200","msg":"...","data":{...}}`（成功码是**字符串** `"200"`）；认证头为 `token`，值是 JWT 原文。
完整字段与示例见 **[docs/api-contract.md](docs/api-contract.md)**。

<details>
<summary>展开接口清单</summary>

| 模块 | 端点 |
|---|---|
| 用户 | `POST /api/user/login`、`POST /api/user/register`、`GET /api/user/current`、`POST /api/user/avatar`、`POST /api/user/logout`、`GET /api/user/admin/page`、`PUT /api/user/admin/{id}/status` |
| 心理对话 | `POST /api/psychological-chat/session/start`、`POST /api/psychological-chat/stream`（SSE）、`GET /api/psychological-chat/sessions`、`GET /api/psychological-chat/sessions/{id}/messages`、`DELETE /api/psychological-chat/sessions/{id}`、`GET /api/psychological-chat/session/{id}/emotion` |
| 知识文章 | `GET /api/knowledge/category/tree`、`GET /api/knowledge/article/page`、`GET /api/knowledge/article/{id}`、`POST /api/knowledge/article`、`PUT /api/knowledge/article/{id}`、`PUT /api/knowledge/article/{id}/status`、`DELETE /api/knowledge/article/{id}` |
| 情绪日记 | `GET /api/emotion-diary/page`、`POST /api/emotion-diary`、`DELETE /api/emotion-diary/{id}`、`GET /api/emotion-diary/admin/page`、`DELETE /api/emotion-diary/admin/{id}` |
| 文件 | `POST /api/file/upload`（单文件 ≤ 5MB，图片/视频类型白名单） |
| 看板 / 健康检查 | `GET /api/data-analytics/overview`、`GET /api/test` |

</details>

---

## 六、几个实现要点

- **AI 流式对话**：`AiService/PsychologicalSupportService.java` + Spring AI 的 `ChatClient`、`MessageChatMemoryAdvisor`，
  用 `MessageWindowChatMemory`（30 条）控制上下文；进程重启后首次对话会从 `consultation_message`
  回灌最近 30 条，做到「重启不失忆」。SSE 出口在 `controller/PsychologicalChat.java`。
- **JWT 与即时失效**：`util/JwtAuthenticationFilter.java` 每个请求都按 token 里的 `userId` 查库校验账号状态，
  所以管理员禁用用户后，对方手里的 token **立刻失效**（401 + `A0231`）；过滤器里的异常统一 catch 成
  标准响应体，避免漏成 HTTP 500 裸错误。
- **统一响应与异常**：`common/Result` + `common/GlobalExceptionHandler`，业务异常统一转 `Result.error(...)`，
  HTTP 状态保持 200，前端 axios 拦截器只看 `code`。
- **安全加固**：注册不接受 `userType`（修越权提权）、JWT 密钥 ≥ 32 位启动校验、登录失败 5 次 / 15 分钟限流、
  上传扩展名白名单（拒绝 `.html`/`.svg`，阻断存储型 XSS）、`/files/**` 匿名放行但加 `nosniff` + CSP。
- **时间与序列化**：所有 `LocalDateTime` 统一 `yyyy-MM-dd HH:mm:ss`（`config/JacksonConfig.java`，
  Spring Boot 4 用的是 Jackson 3，`spring.jackson.date-format` 对 `LocalDateTime` 无效）。
- **文件存储**：统一走 `service/FileStorageService.java` 落盘到 `backend/uploads/`，通过 `/files/**` 直出，
  库里只存相对路径。

---

## 七、数据库

8 张表：`user`、`consultation_session`、`consultation_message`、`emotion_diary`、
`knowledge_category`、`knowledge_article`、`user_favorite`、`sys_file_info`。

- `db/schema.sql` —— 结构快照，随时可重建
- `db/seed-demo-data.sql` —— 演示数据种子，幂等（只清理自己造的 `demo%` / `d3m0%` 数据，不动真实账号与已有文章）

导出快照：

```bash
mysqldump -uroot -p --no-data --skip-comments --no-tablespaces --single-transaction \
  --routines --triggers --events --default-character-set=utf8mb4 \
  mental_health_assistant > db/schema.sql
```

---

## 八、部署

云服务器（Ubuntu + JDK 17 + MySQL 8 + Nginx）上的完整流程、备份与迁移方案见 **[docs/deploy.md](docs/deploy.md)**；
`deploy/` 下是可复用的模板：

| 文件 | 用途 |
|---|---|
| `deploy/deploy.sh` | 一键初始化依赖、建库建号、写环境变量、装 systemd 单元与 Nginx 站点 |
| `deploy/mha.env.example` | `/etc/mha/mha.env` 环境变量模板（含 JWT_SECRET、库口令、大模型 Key） |
| `deploy/mha.service` | systemd 单元（`Restart=on-failure`） |
| `deploy/nginx.conf.example` | 静态托管 + `/api` 反向代理（SSE `proxy_buffering off`）+ 登录 `limit_req` + `/files` 加固 |
| `deploy/pack.ps1` | 本机打包（后端 jar + 前端 dist） |

打包与上线要点：`mvn -B package -DskipTests -Pprod` 产出 fat jar，`npm run build` 产出 `dist/`，
两者上传后分别放到 `/opt/mha/backend/app.jar` 与 `/var/www/mental-health/dist`，再 `systemctl restart mha`。

---

## 九、文档

- **[docs/api-contract.md](docs/api-contract.md)** —— 接口契约：通用约定、28 个端点、鉴权与越权防护
- **[docs/deploy.md](docs/deploy.md)** —— 部署手册：规格、systemd、Nginx/SSE、HTTPS、安全清单、自检
- **[docs/DEPLOY-PROGRESS.md](docs/DEPLOY-PROGRESS.md)** —— 一次真实上线的执行记录与排障过程
- **[CLAUDE.md](CLAUDE.md)** —— 项目约定、常用命令与踩坑记录（含 18 条实测坑）

---

## 十、说明

- 本项目用于学习与作品展示：**后端接口、鉴权与安全加固、数据库设计与服务器部署由作者实现**；
  前端页面部分借助 AI 编码工具生成，作者负责接口契约、联调与问题修复。
- 仓库内的接口地址、口令等均为占位符或本地开发值，**不含任何生产环境的密钥**。
