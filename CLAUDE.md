# CLAUDE.md —— 项目工作指南

> 本文件面向 AI 助手。开始工作前请先读完，尤其是「关键约定」和「踩坑记录」两节。

---

## 一、项目是什么

心理健康服务平台，**前后端分离**放在同一个工作区内：

```
mental_health_assistant/
├── backend/     Spring Boot 4 后端（Java 17），端口 1236
├── frontend/    Vue 3 + Vite 前端，端口 5173
├── docs/api-contract.md    前后端接口契约 ← 改接口前必读
└── README.md               项目总览与启动方式
```

核心业务：用户注册登录 → 与 AI 进行**流式**心理疏导对话 → 查看/管理历史咨询记录。

---

## 二、常用命令

### 后端

```bash
cd backend

mvn -B compile              # 编译（改完代码先跑这个）
mvn -B spring-boot:run      # 启动，服务在 1236
```

> ⚠️ 本机 `backend/target` 上有一条继承来的 `Everyone:(DENY)(DC)` ACE，
> `mvn -B clean compile` 会报 `Failed to clean project: Failed to delete ...\target\classes\application-local.yaml`；
> 用 `mvn -B compile` 即可（Maven 自己会增量重编译改动的类）。

**改完后端代码必须重启**（没有配 devtools 热部署）。

### 前端

```bash
cd frontend

npm install                 # 首次
npm run dev                 # 启动，服务在 5173
```

> ⚠️ `node_modules` 未纳入迁移（240M），首次需要 `npm install`。
> ⚠️ 改了 `vite.config.js`（如代理配置）后，Vite 7 会**自动重启 dev server**（实测日志：`vite.config.js changed, restarting server...`）；只有没看到这行时才需要手动重启。

### 仓库（只有一个，就在 `D:\mental_health_assistant`）

```bash
git add -A && git commit -m "..."        # 前端、后端、文档统一在根目录提交
```

> 2026-10-15 之前 `backend/` 是独立仓库，现已用 subtree 方式并入根仓库（**后端原有提交历史都保留在**）。
> 根 `.gitignore` 忽略 `node_modules/`、`dist/`、`.acl-recovery/`、`.db-backup/`、`.e2e/`、`.tmp*`、`.idea/`；
> `backend/.gitignore` 继续忽略 `target/`、`uploads/`、`application-local.yaml`（含 API Key）、`*.log`。详见踩坑 11。

### 数据库

```bash
# 表结构快照（已纳入仓库；改表后重新导出）
mysqldump -uroot -p123456 --no-data --skip-comments --no-tablespaces --single-transaction `
  --routines --triggers --events --default-character-set=utf8mb4 mental_health_assistant > db/schema.sql

# 用快照重建（⚠ 含 DROP TABLE IF EXISTS，会先删同名表；库要先存在）
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS mental_health_assistant DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p123456 mental_health_assistant < db/schema.sql
```

> `db/schema.sql` **不含** `CREATE DATABASE` / `USE`（所以 `source` 时不会误灌到别的库），也**不含数据**。
> 改完表结构（`ALTER TABLE`、新增实体）**必须重新导出并提交**，否则仓库与真实库会漂移。详见踩坑 13。

### 环境变量与本地配置（2026-10-15 起敏感信息不再入库）

`backend/src/main/resources/application.yaml` **不再含任何口令**，全部写成占位符，真实值只放在下面两处：

| 变量 | 说明 |
|---|---|
| `JWT_SECRET` | JWT 签名密钥，**必须 ≥ 32 位**，否则后端启动直接失败（`config/JwtConfig.java` 的 `@PostConstruct` 校验）。生成：`openssl rand -base64 48` |
| `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` | 数据库连接（占位符默认 `localhost` / `3306` / `root` / 空） |
| `SPRING_AI_OPENAI_API_KEY` | DeepSeek API Key（也可以继续放 `application-local.yaml`） |

- **本机开发**：真实值写进 `backend/src/main/resources/application-local.yaml`（已 gitignore）。
  除了原有的 `spring.ai.openai.api-key`，现在还需要 `spring.datasource.password` 与 `jwt.secret`，否则**起不来**。
- **优先级：环境变量 > `application-local.yaml` > `application.yaml`**（Spring 属性源顺序）。
  所以生产只设环境变量就能覆盖；本机也可以用它做负向验证（见踩坑 16）。
- 线上部署全套（规格、systemd、Nginx/SSE、HTTPS、安全清单、自检）见 `docs/deploy.md`，模板在 `deploy/`。

---

## 三、关键约定（必须遵守）

### 1. 统一响应体

```json
{ "code": "200", "msg": "...", "data": { } }
```

- **成功码是字符串 `"200"`，不是数字**
- 前端 axios 拦截器用 `=== '200'` 严格比对
- 业务异常由 `GlobalExceptionHandler` 转成 `Result.error(...)`，**HTTP 状态仍是 200**
- 前端拿到的已是 `data.data`，业务代码不再剥壳

### 2. 认证

- 请求头名 **`token`**，值是 **JWT 原文**（无 `Bearer ` 前缀）
- 白名单：`/`、`/error`、`/api/test`、`/api/user/login`、`/api/user/register`、`/api/user/logout`、`/files/**`
  - `/files/**` **必须在白名单里**：上传的图片走 `<img src>`，浏览器不会带 `token` 头，不放行就会被 401 拦掉
- 会话类接口内部还会用 `getSessionByIdAndUserId` **校验归属**（防越权）
- `util/JwtAuthenticationFilter.java` **每个请求**都会按 token 里的 `userId` 查一次库并校验 `status == UserStatus.NORMAL`（=1）；不是「正常」一律 **HTTP 401 + `A0231 token已被禁止访问`**。这就是「管理员禁用用户后，他手里的 token 立即失效」的实现方式（见 `docs/api-contract.md` 一.2），**不要把这段状态校验删掉**
  - ⚠️ **管理员例外**：`GET /api/psychological-chat/sessions/{id}/messages` 走 `PsychologicalChat.requireVisibleSessionId`，
    管理员只校验「会话是否存在」。后台「咨询记录 → 详情」用的就是这个接口，列表接口对管理员本来就是全量 ——
    如果给它加回归属校验，就会出现「列表看得到、点进去报『会话不存在或无权访问』」。
    删除会话与情绪分析仍严格校验归属（管理员不能动别人的会话）。
- **注册接口不接受 `userType`**（2026-10-15 修复提权漏洞）：请求体里带 `"userType": 2` 会被 Jackson 忽略，
  `UserConvert` 固定写 `UserType.USER.getCode()`（=1）→ 注册出来的**永远是普通用户**。加用户字段时别在注册 DTO 上开口子。
- **token 异常一律 `HTTP 401 + {"code":"A0230","msg":"token无效"}`**：`util/JwtAuthenticationFilter.java` 里那段
  `try { ... } catch (Exception e)` **不能删** —— `validateToken` 对「签名不匹配 / 已过期 / issuer 不符」抛
  `JWTVerificationException`，`getUserById` 对「已被删除的用户」抛 `BusinessException`，
  漏给 Tomcat 就变成 **HTTP 500 + Spring 默认错误体**（前端拿不到 msg，也不会自动回登录页）。没带 token 仍是 `A0301`。详见踩坑 15。
- **登录失败限流**：`service/LoginAttemptLimiter.java` —— 同一「用户名 + 客户端 IP」连续失败 **5 次锁 15 分钟**
  （提示 `登录失败次数过多，账号已被临时锁定，请 N 分钟后再试`），登录成功清零；「用户被禁用」不计入失败。
  键里的 IP 只取 `request.getRemoteAddr()`、**故意不信 `X-Forwarded-For`**（请求头可伪造 = 给攻击者「换个 IP 就重置计数」的开关）。
  进程内实现：重启即清空、多实例部署要换共享存储；按来源 IP 的那层限流交给 Nginx（`deploy/nginx.conf.example` 的 `limit_req_zone`）。

### 3. 端口与代理

| 服务 | 端口 |
|---|---|
| 后端 | 1236 |
| 前端 dev server | 5173 |

前端 `/api` 请求由 Vite 代理到 `http://localhost:1236`，`/files`（本地上传的图片）同样代理过去（`frontend/vite.config.js`）。

### 4. 文件存储（全部在项目内）

- 上传目录：`file.upload-dir`（`backend/src/main/resources/application.yaml`，默认 `./uploads` → `backend/uploads/`）
- 访问前缀：后端把该目录挂在 `/files/**`（`config/WebMvcConfig.java`）；库里 `sys_file_info.file_path` 存的就是 `/files/...` 相对路径
- 前端前缀：`frontend/src/config/index.js` 的 `fileBaseUrl` 是**空字符串**（同源），拼出来即 `/files/...`
- 兜底图/默认头像：`frontend/public/img/cover-placeholder.svg`、`frontend/public/img/default-avatar.svg`
- **不要**再引入外部图床/文件服务器的绝对地址（历史上 `159.75.169.224:1235` 那台是别人部署的另一份同项目，文件路径取不到图）
- **单文件上限 5MB**（`spring.servlet.multipart.max-file-size: 5MB` / `max-request-size: 10MB`，2026-10-15 加；改动前走 Spring 默认 1MB，上传 2MB 会得到 **HTTP 413 + 空响应体**，见踩坑 14）。
  超限由 `common/GlobalExceptionHandler` 转成统一响应体 `{"code":"5004","msg":"上传的文件过大，单个文件不能超过 5MB"}`（HTTP 200）。
- **有扩展名白名单**（2026-10-15 补，`service/FileStorageService.java`）：只收 `jpg/jpeg/png/gif/bmp/webp` 与 `mp4/webm`，
  其余（含 `.html`、`.svg`、以及没有扩展名的文件）一律抛 `BusinessException("不支持的文件类型（.xxx），仅支持图片 … 与视频 …")`。
  `svg` 被**刻意排除**：它能内嵌 `<script>`，而 `/files/**` 是匿名可读的静态直出，同源脚本能读走 `localStorage` 里的 JWT。
  `frontend/src/components/AvatarUploader.vue` 与 `ArticleDialog.vue` 的 `accept`、提示文案已同步。
- 网关层再加一层纵深防御（模板已写）：`nosniff` + CSP `sandbox` + 直接拒绝脚本后缀，见 `deploy/nginx.conf.example` 的 `location /files/`

### 5. 分页与 sessionId

- 分页参数**两套命名都要兼容**：`pageNum/pageSize`（用户端）、`currentPage/size`（后台）
- `sessionId` **两种格式都要兼容**：裸 id `27` 和带前缀 `session_27`

详见 `docs/api-contract.md`。

### 6. AI 对话记忆窗口

- 记忆由 `config/ChatClientConfig.java` 的 `MessageWindowChatMemory`（`maxMessages=30`）+ `MessageChatMemoryAdvisor` 维护，**只在进程内存里**。
- `AiService/PsychologicalSupportService.java` 另有 `MEMORY_WINDOW_MESSAGES = 30` 常量（回灌历史用）。
  ⚠️ **改 `maxMessages` 时要同步改它**，两者不一致会导致回灌出来的上下文长度与窗口不符。
- 重启后靠 `ensureMemoryLoaded(...)` 从 `consultation_message` 回灌；`conversationId` 的格式是 `conversation_` + `session_<id>`，必须与 advisor 传的 `ChatMemory.CONVERSATION_ID` 完全一致。

### 7. 日期时间格式

- 后端所有 `LocalDateTime` **统一输出 `yyyy-MM-dd HH:mm:ss`**（`config/JacksonConfig.java`，见踩坑 10）。
  改格式要动这个类，**别用 `spring.jackson.date-format`**（对 `LocalDateTime` 无效）。
- 前端**直接渲染**这些字符串即可；需要二次格式化用 `dayjs`（`import { dayjs } from 'element-plus'`）。
- 前端**自己造时间**（乐观渲染、新建会话标题等）也必须用同一格式：`dayjs().format('YYYY-MM-DD HH:mm:ss')`，
  不要再写 `new Date().toISOString()`（会得到 `2026-10-15T16:12:34.567Z`，与后端格式不一致）。
- `LocalDate`（`birthday`、`diaryDate`、看板 `date`）仍是 `yyyy-MM-dd`。

### 8. 品牌名统一为「心理健康AI助手」

页面、浏览器标题、会话默认标题都用 **心理健康AI助手**。发现残留的旧名「宁渡AI助手」按 bug 处理直接改掉
（2026-10-15 已在 `consultation.vue` 全部替换）。

### 9. 前端样式：只用设计令牌，不要再写死颜色

2026-10-15 做过一次全站 UI 统一（温和疗愈风：雾蓝 `#4e8098` 主色 + 薄荷绿 `#7fb69e` 辅色 + 杏色 `#e0a87e` 点缀 + 暖白底），
之前全项目有 **330 处硬编码颜色 / 165 种色值 / 17 种圆角 / 22 种阴影 / 仅 1 处 @media**，现在统一到两个文件：

| 文件 | 作用 |
|---|---|
| `frontend/src/styles/tokens.scss` | 设计令牌：颜色、圆角、阴影、间距、字号、布局尺寸，并**映射成 Element Plus 的 CSS 变量**（`--el-color-primary` 等），改这一处即可整体换肤 |
| `frontend/src/styles/index.scss` | 全局基础样式 + Element Plus 组件微调（card / table / dialog / pagination / menu / form / empty…）+ 通用类 `.mh-page`、`.mh-card`、`.mh-section-title`、`.mh-container` |

- `main.js` 的加载顺序必须是 `element-plus/dist/index.css` → `./styles/index.scss`，否则覆盖不生效。
- **写新样式时一律用 `var(--mh-*)`**（颜色/圆角/阴影/间距/字号），不要出现新的十六进制色值；
  `frontend/src/style.css` 只保留 reset，不要再往里面加组件样式。
- 图表（ECharts）无法用 CSS 变量，用与令牌对应的十六进制：主色 `#4e8098`、辅色 `#7fb69e`、暖色 `#e0a87e`、info `#8a9aa3`，
  轴/网格线用 `#d5e0e6` / `#eef3f5`，文字用 `#8a9aa3` / `#5c6b73`（见 `dashboard.vue`）。
- 图标 PNG（`assets/images/*.png`）是**白色剪影**，必须放在有底色的容器里（KPI 卡片、头部圆形图标），别放到浅底上。
- 页面级宽度用 `--mh-content-max`（1200px）/ 980px；`el-card` 已经是统一圆角 + 柔和阴影，不要再自己写一套。

---

## 四、踩坑记录（都是本机真实遇到过的）

### 0. 🔴 命令（pwsh）能改哪些文件：只有 `backend/` 和 `frontend/`

DSH 的沙箱会把 pwsh 子进程降级为 **Low 完整性级别**，只有打了 Low 标签的目录才可写。本工作区已给 `backend/` 与 `frontend/` **整棵树**打上这个标签（见下方「本机权限配置」），所以：

| 位置 | pwsh 里能否写入 |
|---|---|
| `backend/**`（含 `target/`、`uploads/`） | ✅ 可以 |
| `frontend/**`（含 `node_modules/`、`dist/`、`public/`、`src/`） | ✅ 可以 |
| 工作区根目录，以及打了标签之后新建的子目录（如 `_incoming/`） | ✅ 可以 |
| 工作区根的**其它子目录**（如 `docs/`、`.acl-recovery/`） | ❌ 被拒，需一次性提权 |
| 工作区之外（`~/.npm` 缓存、`~/.m2`） | ❌ 被拒（沙箱的本意） |

> ⚠️ 例外：`npm run build` 在沙箱里**仍然会失败** —— `esbuild` 的 `spawn EPERM`（命名管道 stdio 限制，与 ACL 无关）。装包/构建这类命令直接用一次性提权跑。

AI 编辑工具（write/edit）走宿主进程，不受此限制。

**本机权限配置（2026-10-15 做的，属机器状态不是代码约定）**

- `D:\mental_health_assistant`、`backend`、`frontend` 各被打了可继承的 Low 完整性标签（`icacls <dir> /setintegritylevel '(OI)(CI)L'`）。
- `backend` 原先缺少 `WRITE_OWNER`（其 DACL 里连当前用户都不显式出现），导致 `icacls /setintegritylevel` 报 `Access is denied.`；已补 `icacls backend /grant "<用户>:(OI)(CI)F"` 后成功，并递归 `/T` 打到整棵树。
- `frontend` 有同样的问题，而且更隐蔽：一开始只给 `frontend` 自身打了标签，子目录（`public/`、`src/`、`node_modules/`、`dist/`）**没继承到**——ACE 继承只在对象创建时生效，对已存在的子目录无效。2026-10-15 补做：`icacls frontend /grant "<用户>:(OI)(CI)F" /T /C` + `icacls frontend /setintegritylevel '(OI)(CI)L' /T /C`（19972 个对象，0 失败）。
- ⚠️ `icacls ... /T` **不带 `/C` 时会在第一个打不开的子目录处整体中止**（本次撞上 `.vscode`），并只报「Successfully processed 2 files」——看着成功、其实只动了两项。递归时必须加 `/C`，事后用 `icacls <dir>` 看每个子目录有没有 `Mandatory Label\Low` 来验证，别只看退出码。
- 2026-10-15 起整个工作区**只有一个 git 仓库**（就在工作区根），`backend/` 已并入根仓库（见踩坑 11）。
- ⚠️ Low 标签会**常驻**（DSH 设计如此，会话结束也不撤），同用户下任何 Low 完整性进程都能写这两个目录 —— 这是写隔离的代价，知悉即可。


### 1. 🔴 Git Bash 发中文请求体 → 400

在 Windows Git Bash 里用 `curl -d '{"msg":"中文"}'`，中文会按 **GBK** 编码发出，
后端按 UTF-8 解析失败 → `HttpMessageNotReadableException` → 400。

**对策**：用命令行测接口时**一律用纯 ASCII 请求体**，或改用 Apifox / Postman。

### 2. 🔴 停不干净的后端进程

`mvn spring-boot:run` 会派生出 **Java 子进程**。只终止 Maven 进程的话，
Java 进程会继续占着 1236，导致下次启动**端口冲突**，而你的测试请求会**打到旧代码上**（得到误导性结果）。

**对策**：
```bash
netstat -ano | grep ":1236" | grep LISTENING    # 找 PID
taskkill //F //PID <pid>                        # 杀掉
```

### 3. 🔴 `/error` 必须在安全白名单里

Spring Boot 会把异常转发到 `/error`。如果它不在白名单，
**所有错误（400/404/500）都会被 Security 拦成 403 空响应**，真实原因完全看不到。

已在 `SecurityConfig.PUBLIC_PATH` 中放行，**不要删掉**。

### 4. 🟡 MyBatis-Plus 分页插件需要单独依赖

MyBatis-Plus **3.5.9 起**把分页插件拆到了独立模块，
starter 不再自带 `PaginationInnerInterceptor`。

`pom.xml` 已引入 `mybatis-plus-jsqlparser`，**不要删**。

### 5. 🟡 SSE 的 Content-Type 必须严格匹配

前端用 `!== 'text/event-stream'` 比对。响应头若带 `;charset=utf-8`，前端会误判为「非流式数据」。

### 6. 🟡 前端路由按 userType 强制分流

`frontend/src/router/index.js` 的守卫：
- `userType == 2`（管理员）→ **只能进 `/back/**`**，访问其它路径强制跳后台
- `userType == 1`（普通用户）→ 只能进前台

**所以用 admin 登录永远看不到「心理咨询」页**，这是设计，不是 bug。
前端联调请用 `tester / 123456`。

### 7. 🟡 敏感文件

`backend/src/main/resources/application-local.yaml` 含 **AI API Key**，
已在 `.gitignore` 中忽略。**永远不要把它提交进 git**。
新增敏感配置时同理。

### 8. 🟡 用 curl 测 `/stream` 必须带 `Content-Type: application/json`

只写 `--data-binary @body.json` 时 curl 默认发 `application/x-www-form-urlencoded`，
后端直接 415 `Unsupported Media Type`，**响应体是 Spring 的默认错误 JSON**（`{"timestamp":...,"status":415,...}`，
不是统一响应体，别被它带偏）：
```
curl.exe -s -N --max-time 240 -o out.sse -X POST http://localhost:1236/api/psychological-chat/stream `
  -H "token: $token" -H "Content-Type: application/json" --data-binary "@body.json"
```
另外 `HttpWebRequest`（.NET）读 SSE 会抛「无法从传输连接中读取数据: 连接已关闭」，读流式响应一律用 `curl.exe`。

### 9. 🟡 SSE 流结束时日志里会出现两条 `Access Denied` ERROR（既有噪声，不影响功能）

每次 `/stream` 正常结束时，Tomcat 会做一次 async dispatch，Spring Security 7 会在这次 dispatch 上再走一遍
授权过滤链，于是日志出现：
```
ERROR ... AuthorizationDeniedException: Access Denied
ERROR ... Unable to handle the Spring Security Exception because the response is already committed.
```
客户端**已收完整流**（实测收到全部 60 个 chunk + `event: done`），所以这不是故障，只是噪声；
真要消除得给 ASYNC dispatch 放行（`shouldFilterAllDispatcherTypes(false)` 或对 `/stream` 单独放行）。

### 10. 🔴 日期格式：Spring Boot 4 用的是 **Jackson 3**，`spring.jackson.date-format` 对 `LocalDateTime` 无效

- 本项目 `LocalDateTime` 序列化统一为 `yyyy-MM-dd HH:mm:ss`，实现是 `config/JacksonConfig.java`。
- **包名是 `tools.jackson.*`，不是 `com.fasterxml.jackson.*`**：`spring-boot-autoconfigure-4.0.8.jar` 里
  **一个 jackson 类都没有**（已挪到 `spring-boot-jackson-4.0.8.jar`）。自定义扩展点是
  `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`（`customize(tools.jackson.databind.json.JsonMapper.Builder)`），
  **不是** Jackson 2 时代的 `Jackson2ObjectMapperBuilderCustomizer`；写成 Jackson 2 会直接编译失败
  （`程序包 com.fasterxml.jackson.datatype.jsr310.ser 不存在`）。
- 可用写法：`tools.jackson.databind.module.SimpleModule` + `tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer(DateTimeFormatter)`，再 `builder.addModule(...)`。
- 在 `application.yaml` 里配 `spring.jackson.date-format` **不会**改变这些字段（该属性只管 `java.util.Date`）——别以为配了没用是缓存问题。
- 前端拿到的就是 `2026-10-15 15:59:46` 这种字符串；需要二次处理时用 dayjs（它的默认解析同时接受空格与 `T` 分隔，老数据不会解析失败）。

### 11. 🟡 git：只有一个仓库（2026-10-15 起 `backend/` 已并入根仓库）

- 现在**只有一个仓库**：`D:\mental_health_assistant\.git`，纳管 `frontend/`、`backend/`、`docs/`、`CLAUDE.md`、`README.md`、根 `.gitignore`（受跟踪文件 140+ 个）。
- `backend/` 原本是独立仓库（remote `mental-health-assistant`），2026-10-15 用 **subtree** 方式并入根仓库：
  **后端原有提交历史都还在**（`git log --oneline` 能看到 `cfa36dc`、`a87496c`、`f4c4c94` 等），合并提交是 `574b3dc` + 收尾提交 `d0abeff`，工作树干净。
- 查后端**单个文件**的历史要回到旧路径空间查：`git log --oneline f4c4c94 -- pom.xml`（`f4c4c94` 是并入前的最后一个后端提交）。
  ⚠️ `git log --follow -- backend/pom.xml` 在合并提交上**不生效**（返回空），别用它判断历史有没有丢。
- ⚠️ 提交一律在**工作区根**做（`git add -A && git commit`）；`backend/.git` 已移除，**不要**再在 `backend/` 里 `git init`，否则又变成两个仓库。
- 忽略规则分两处：根 `.gitignore`（`node_modules/`、`dist/`、`.acl-recovery/`、`.db-backup/`、`.e2e/`、`.tmp*`、`.idea/`、`.vscode/`）与 `backend/.gitignore`（`target/`、`uploads/`、`application-local.yaml`、`*.log`）。
  **`application-local.yaml`（含 API Key）永远不要提交**；根 `git add -A` 正常是几十~上百个文件，**暴涨到上千先查 `node_modules` 有没有被加进来**。
- 根仓库**没有 remote**（只在本地提交，未 push）。旧的后端 GitHub 仓库
  `https://github.com/wucha-rmh/AI-SpringBoot-mental-health-assistant.git` 保留作历史归档；要发布的话建议新建一个仓库再把根仓库推上去。

### 12. 🟡 在 pwsh 里用 `curl.exe` 内联 `-d '{...}'` 传 JSON：引号会被吃掉

pwsh 5.1 给原生程序传参时会剥掉字符串里的双引号，于是 curl 实际发出的是
`{username:tester,password:123456}`，Spring 解析失败 → **HTTP 400**，响应体还是 Spring 默认错误 JSON
（`{"timestamp":...,"status":400,"error":"Bad Request",...}`，不是统一响应体）。实测对比：

```
# ❌ 400
curl.exe -s -X POST http://localhost:1236/api/user/login -H "Content-Type: application/json" `
  -d '{"username":"tester","password":"123456"}'
# ✅ 200：要么把引号转义
... -d '{\"username\":\"tester\",\"password\":\"123456\"}'
# ✅ 要么（推荐）把 body 写进文件再引用
... --data-binary "@D:\tmp\body.json"        # 文件用 UTF-8 无 BOM 写
```

**推荐一律用 `--data-binary @文件`**：既避开引号问题，也避开踩坑 1 的中文 GBK 问题。

### 13. 🟡 在 pwsh 里调 `mysql.exe`：`-e` 没有默认库 + 反引号会丢，报错还被 `2>$null` 吞掉

用 `mysql -uroot -p123456 -N -B -e "<SQL>"` 批量查库时踩了两次同一个坑（都表现为「静默空结果」而非报错）：

- **`-e` 执行时没有默认数据库**，SQL 里必须写全限定名（`mental_health_assistant.xxx`），
  否则报 `ERROR 1046 (3D000) No database selected`。**更省事的做法是加 `-D mental_health_assistant`**，之后表名就不用加库前缀。
- **不要把反引号字符当成参数传给原生命令**：`$bt=[char]96` 拼出来的 `` `user` `` 在
  `& mysql.exe ... -e "SELECT ... FROM ${bt}user${bt}"` 里**会丢掉**，SQL 变成 `FROM user` → 又回到 1046。
  ✅ 结论：用 `-D <库名>` + **完全不写反引号**（本项目表名都不是 MySQL 保留字，`user` 也可以直接写）。
- **别用 `2>$null` 压掉 mysql 的 stderr**：真实错误（1046/1054/1146）全在里面，压掉后只剩空字符串，
  上层再对空值 `.Trim()` 就报 `不能对 Null 值表达式调用方法`，排查方向会被带偏。
  改成 `2>$errFile` 并在 `$LASTEXITCODE -ne 0` 时把文件内容打出来。

### 14. 🔴 上传超过限制时会返回 **HTTP 413 + 空响应体**（不是统一响应体）

2026-10-15 之前 `application.yaml` 没有 `spring.servlet.multipart` 配置 → 走 Spring 默认 **1MB**。
实测上传 2097152 字节的文件：`HTTP/1.1 413`、`Content-Length: 0`，响应头是 Spring 的
`X-Content-Type-Options: nosniff` / `X-Frame-Options: DENY` 那一套 —— **没有 `code`/`msg`**，
前端的 axios 拦截器读不到提示（前端自己限制 5MB，两者不一致时用户看到的是「静默失败」）。

现在已把上限提到 5MB（`spring.servlet.multipart.max-file-size: 5MB` / `max-request-size: 10MB`），
并在 `common/GlobalExceptionHandler` 里补了 `MaxUploadSizeExceededException` → `{"code":"5004","msg":"上传的文件过大，单个文件不能超过 5MB"}`（HTTP 200）。

> 用 curl 复现/验证要 `-F "file=@<file>;type=image/png" -F businessType=ARTICLE -F businessId=xxx`，
> 大文件用 `[System.IO.File]::WriteAllBytes($path, (New-Object byte[] (6MB)))` 造，别用文本写入（会带 BOM 且大小不准）。

### 15. 🔴 过滤器里漏出的异常会变成 **HTTP 500 + Spring 默认错误体**

`common/GlobalExceptionHandler` 只兜 **Controller 抛出的异常**。过滤器（`util/JwtAuthenticationFilter.java`）里抛出的异常
直接冒到 Tomcat，响应体是 `{"timestamp":...,"status":500,"error":"Internal Server Error","path":...}`，
**不是统一响应体**，前端拦截器读不到 `msg`（用户只看到「服务器开小差了」，也不会被踢回登录页）。

实测会踩到的两种：
1. `JwtTokenUtil.validateToken` 对签名不匹配 / 已过期 / issuer 不符抛 `JWTVerificationException`
   —— **轮换 `jwt.secret` 之后，用户手里的旧 token 全部走这条路**（修复前实测就是 500）；
2. `UserService.getUserById` 对「已被删除的用户」抛 `BusinessException("用户不存在")`
   —— token 合法但里面的 userId 指向不存在的用户时触发。

现在「验证 token → 查用户 → 写认证信息」整段包在同一个 `try/catch` 里，统一回落到 `A0230`（HTTP 401）。
新增过滤器时记住：**过滤器里的异常没有兜底，必须自己 catch 成统一响应体**。

### 16. 🟡 `application-local.yaml` 里的值会被环境变量覆盖（拿来做负向验证很方便）

Spring 属性源优先级：**命令行参数 > OS 环境变量 > profile 专属配置文件（`application-local.yaml`）> `application.yaml`**。所以：

- 生产环境只要设 `JWT_SECRET` / `DB_PASSWORD` 就能盖掉打包进 jar 的值；
- 想验证「密钥太短必须拒绝启动」，不必改文件，直接：
  `$env:JWT_SECRET='tooshort'; mvn -B spring-boot:run`，稳定复现
  `Caused by: java.lang.IllegalStateException: jwt.secret 未配置或长度不足 32 位…`（实测 120s 内进程退出）。
  ⚠️ 注意 Windows 下把环境变量设成**空串等于删掉它**（会退回配置文件的值），要用一个「长度不足」的非空值来做这个测试。

---

## 五、端到端验证流程

改完代码的标准验证姿势：

```bash
# 1. 编译
cd backend && mvn -B clean compile

# 2. 启动后端（后台）
mvn -B spring-boot:run > /tmp/backend.log 2>&1 &

# 3. 等约 45 秒，确认起来了
tail -3 /tmp/backend.log | grep "Started AiSpringbootApplication"

# 4. 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:1236/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","password":"123456"}' | \
  sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

# 5. 验证接口（注意用 ASCII 请求体）
curl -s "http://localhost:1236/api/psychological-chat/sessions?pageNum=1&pageSize=5" -H "token: $TOKEN"

# 6. 验证完清理（记得杀 Java 进程）
netstat -ano | grep ":1236" | grep LISTENING
taskkill //F //PID <pid>
```

**前端页面验证**（必须人工在浏览器操作）：
`http://localhost:5173` → `tester / 123456` → 心理咨询页 → 新建会话 → 观察 AI 流式回复。

---

## 六、当前未实现项

| 项 | 说明 |
|---|---|
| 情绪分析 | ✅ 已接入 AI（`AiService/EmotionAnalysisService.java`）：结果落 `consultation_session.last_emotion_analysis`，缓存覆盖最后一条消息时复用、有新消息则重算；无消息或调用失败退回 `SessionEmotionDTO.defaultValue()` 且不写缓存。⚠️ 2026-10-15 起**前端不再展示**（咨询页的「情绪花园」已删，接口与落库逻辑保留，见 `docs/api-contract.md` 2.2） |
| 会话状态流转 | 实体没有 `status`/`endedAt` 字段 |
| Token 服务端失效 | 🟡 JWT 无状态：**退出登录**只清前端缓存，服务端并不知道（要真失效得加黑名单或「短过期 + refresh」）。注意与「禁用用户」区分——后者是**立即失效**的（`JwtAuthenticationFilter` 每请求查库校验 `status`，见三.2） |
| AI 对话记忆 | ✅ 已于 2026-10-15 做到「重启不失忆」：`AiService/PsychologicalSupportService.java` 的 `@Autowired ChatMemory chatMemory` 与 advisor 共用同一个 `MessageWindowChatMemory`，`ensureMemoryLoaded(...)` 在该会话重启后第一次 `/stream` 时从 `consultation_message` 回灌最近 30 条；仍未做的是持久化记忆仓库（Spring AI 2.1.0-M1 只自带 `InMemoryChatMemoryRepository`，没有 JDBC 版 `ChatMemoryRepository`），所以被删会话的记忆会短暂留在进程内。详见 `docs/api-contract.md` 2.2 |
| 历史图片缺失 | 7 篇文章封面已于 2026-10-15 补齐（本地生成，经 `POST /api/file/upload` 挂到 `cover_image`）；`user.avatar` 只有 admin/tester 有（联调测试留下的），其余用户显示默认头像；旧服务器那批原始文件已清库、不可恢复 |
| 头像裁剪/缩略图 | 上传的原图直接当头像，不生成多尺寸；前端只校验类型与 5MB 大小 |
| 临时文件清理 | ✅ 已于 2026-10-15 实现：`config/SchedulingConfig.java`(`@EnableScheduling`) + `task/TempFileCleanupTask.java`，`@Scheduled(cron = "${file.temp-cleanup-cron:0 30 3 * * ?}")` 每天 03:30 清理 `is_temp=1` 且已过 `expire_time` 的文件（删磁盘 + 删 `sys_file_info`）。详见 `docs/api-contract.md` 2.6 |
| 日期时间格式 | ✅ 已于 2026-10-15 统一为 `yyyy-MM-dd HH:mm:ss`（`config/JacksonConfig.java`）；⚠️ `spring.jackson.date-format` 对 `LocalDateTime` **无效**（见踩坑 10） |
| 前端表单校验 | 已修 `ArticleDialog.vue`（校验不通过不再发请求）；其余页面仍是「后端校验 + 拦截器弹 `msg`」 |
| 文章写操作与日记写操作 | ✅ 已于 2026-10-15 实现（见 `docs/api-contract.md` 2.3 / 2.4） |
| 情绪日记字段 | ✅ 已于 2026-10-15 精简为「心情评分 + 今日感想」：`emotion_diary` 删掉 `dominant_emotion`/`emotion_triggers`/`sleep_quality`/`stress_level` 四列（13 → 9 列），实体/DTO/服务/前台表单与后台页同步删净。**新增日记请求体只有 `diaryDate`+`moodScore`+`diaryContent`**；迁移前备份在 `.db-backup/emotion_diary_before_slim_*.sql` |
| 库表冗余（全库级） | 🟡 2026-10-15 已清理一批：DROP 废弃表 `ai_analysis_task`（14 行、11 索引、代码 0 引用）、`emotion_diary.ai_emotion_analysis`/`ai_analysis_updated_at`、`consultation_message.emotion_tag` → **9 张表精简为 8 张**。**尚未清理**：零引用遗留表 `user_favorite`、重复索引（`emotion_diary.idx_user_diary`＝`user_date_unique`、`user.idx_username`＝`UNIQUE username`）、空列/常量列（`knowledge_category.category_code`/`parent_id`、`consultation_message.message_type`、`knowledge_article.status`、`user.status`、`sys_file_info.file_type`/`expire_time`/`is_temp`/`status`）、`consultation_message.ai_model` 写死假值 `openai`；`knowledge_article.read_count` 从不自增 → ✅ 2026-10-15 已修（见 `docs/api-contract.md` 2.3） |
| 数据库结构可重建 | ✅ 2026-10-15 导出 `db/schema.sql` 并纳入仓库（`mysqldump --no-data`，8 张 `CREATE TABLE`；校验方式：导进临时库比对列数/索引/外键后 DROP）。⚠️ 改表后要重新导出并提交（见二「数据库」） |
| 注册提权 | ✅ 2026-10-15 修复：注册请求体不再接收 `userType`，固定建普通用户（见三.2） |
| 上传上限与异常兜底 | ✅ 2026-10-15：multipart 上限 1MB → 5MB、超限返回统一响应体 `5004`、`GlobalExceptionHandler` 增补 `Exception` 兜底（见三.4） |
| 用户管理 | ✅ 2026-10-15 实现：管理员可看**用户列表**（关键字/状态/类型筛选 + 分页）并**禁用/启用**账号 —— `GET /api/user/admin/page`、`PUT /api/user/admin/{id}/status`（都走 `SecurityUserUtil.requireAdmin()`，新增 `DTO/command/UserStatusUpdateCommandDTO.java`），后台页面 `frontend/src/views/users.vue`（路由 `/back/users`）。「禁用立即生效」靠三.2 的过滤器状态校验，未新增禁用逻辑；管理员不能改自己的状态。详见 `docs/api-contract.md` 2.1 |
| JWT 密钥 / 数据库口令入库 | ✅ 2026-10-15 修复：`application.yaml` 只留 `${JWT_SECRET:}`、`${DB_PASSWORD:}` 占位符，真实值在 gitignored 的 `application-local.yaml`，生产走环境变量；`config/JwtConfig.java` 启动时强制校验密钥 ≥ 32 位。⚠️ 旧密钥已随 git 历史泄露，**绝不要复用**（见二「环境变量与本地配置」） |
| 登录限流 | ✅ 2026-10-15：`service/LoginAttemptLimiter.java` —— 同账号连错 5 次锁 15 分钟（应用层按账号）；按来源 IP 的 `limit_req` 模板在 `deploy/nginx.conf.example` |
| 上传类型白名单 | ✅ 2026-10-15：`service/FileStorageService.java` 只收 `jpg/jpeg/png/gif/bmp/webp` + `mp4/webm`，`.html`/`.svg` 直接拒绝（见三.4）；前端 `accept` 同步 |
| CORS | ⬜ 仍未配置，且**同域部署不需要**（前端 baseURL 是 `/api`、`fileBaseUrl` 是空串）；只有前后端分域部署时才要补 |
| 部署文档 | ✅ 2026-10-15：`docs/deploy.md` + `deploy/`（`mha.service`、`mha.env.example`、`nginx.conf.example`，含 SSE `proxy_buffering off`、登录 `limit_req`、`/files` 加固） |
| git 仓库 | ✅ 2026-10-15 起**只有一个仓库**（工作区根），`backend/` 已用 subtree 方式并入（后端原有提交历史保留），受跟踪文件 140+ 个；根仓库**无 remote**（未 push），旧后端 GitHub 仓库保留作归档。详见踩坑 11 |

---

## 七、代码风格约定

- 统一响应：`Result.ok(...)` / `Result.error(...)`
- 业务异常：`throw new BusinessException("提示信息")`
- 分层：`controller` → `service` → `mapper` → `entity`，DTO 转换走 `service/convert`
- Controller 里不写业务逻辑，只做参数收取、权限预览、调用 Service
- 前端登录用户信息统一走 **`frontend/src/stores/user.js`**（pinia）：显示名/头像的兜底逻辑在 `frontend/src/utils/user.js`。
  任何直接改 `localStorage.userInfo` 的地方都要同步调用 store 的 `setInfo / setAvatar / clear`，否则导航栏会显示旧值
- 文件落盘统一走 `backend/src/main/java/com/rmh/aispringboot/service/FileStorageService.java`（文章封面与用户头像共用），不要在 Controller 里自己写 `Files.copy`
