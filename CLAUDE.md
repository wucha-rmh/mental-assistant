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

### 仓库（两个，别搞混）

```bash
# 工作区根仓库：frontend/、docs/、CLAUDE.md、README.md（backend/ 被 .gitignore 整个忽略）
git add -A && git commit -m "..."        # 在 D:\mental_health_assistant 下执行

# 后端仓库：仓库根就在 backend/ 里
cd backend && git add -A && git commit -m "..."
```

根 `.gitignore` 会忽略 `node_modules/`、`dist/`、`.acl-recovery/`、`.db-backup/`、`.e2e/`、`.tmp*`、`.idea/`；
后端仓库的 `application-local.yaml`（含 API Key）也已被忽略。详见踩坑 11。

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
- `backend` 的远程指向是 `mental-health-assistant`（仓库根在 `backend/` 里）；工作区根 2026-10-15 也建了自己的仓库（见踩坑 11）。
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

### 11. 🟡 有两个 git 仓库：工作区根 + `backend/`

- **工作区根仓库**（2026-10-15 新建，`git init -b main`，首次提交 `f18a39a`）只管
  `frontend/`、`docs/`、`CLAUDE.md`、`README.md`、根 `.gitignore` —— 根 `.gitignore` **整个忽略 `backend/`**。
- **`backend/` 有自己的仓库**（remote `mental-health-assistant`，仓库根就在 `backend/` 里），
  后端改动要在 `backend/` 目录里单独 `git add` / `commit`。
- ⚠️ 在**工作区根**执行 `git status` 看不到后端改动，别以为后端没改；两个仓库都还没有 remote push 配置（根仓库完全没有 remote）。
- 根 `.gitignore` 忽略了 `node_modules/`、`dist/`、`.acl-recovery/`、`.db-backup/`、`.e2e/`、`.tmp*`、`.plan-recover.txt`、`.idea/`、`.vscode/`；
  第一次 `git add -A` 是 61 个文件，**如果数字突然暴涨到上千，先检查 `node_modules` 有没有被加进来**。

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
| 情绪分析 | ✅ 已接入 AI（`AiService/EmotionAnalysisService.java`）：结果落 `consultation_session.last_emotion_analysis`，缓存覆盖最后一条消息时复用、有新消息则重算；无消息或调用失败退回 `SessionEmotionDTO.defaultValue()` 且不写缓存 |
| 会话状态流转 | 实体没有 `status`/`endedAt` 字段 |
| Token 服务端失效 | JWT 无状态，退出仅清前端缓存 |
| AI 对话记忆 | ✅ 已于 2026-10-15 做到「重启不失忆」：`AiService/PsychologicalSupportService.java` 的 `@Autowired ChatMemory chatMemory` 与 advisor 共用同一个 `MessageWindowChatMemory`，`ensureMemoryLoaded(...)` 在该会话重启后第一次 `/stream` 时从 `consultation_message` 回灌最近 30 条；仍未做的是持久化记忆仓库（Spring AI 2.1.0-M1 只自带 `InMemoryChatMemoryRepository`，没有 JDBC 版 `ChatMemoryRepository`），所以被删会话的记忆会短暂留在进程内。详见 `docs/api-contract.md` 2.2 |
| 历史图片缺失 | 7 篇文章封面已于 2026-10-15 补齐（本地生成，经 `POST /api/file/upload` 挂到 `cover_image`）；`user.avatar` 只有 admin/tester 有（联调测试留下的），其余用户显示默认头像；旧服务器那批原始文件已清库、不可恢复 |
| 头像裁剪/缩略图 | 上传的原图直接当头像，不生成多尺寸；前端只校验类型与 5MB 大小 |
| 临时文件清理 | ✅ 已于 2026-10-15 实现：`config/SchedulingConfig.java`(`@EnableScheduling`) + `task/TempFileCleanupTask.java`，`@Scheduled(cron = "${file.temp-cleanup-cron:0 30 3 * * ?}")` 每天 03:30 清理 `is_temp=1` 且已过 `expire_time` 的文件（删磁盘 + 删 `sys_file_info`）。详见 `docs/api-contract.md` 2.6 |
| 日期时间格式 | ✅ 已于 2026-10-15 统一为 `yyyy-MM-dd HH:mm:ss`（`config/JacksonConfig.java`）；⚠️ `spring.jackson.date-format` 对 `LocalDateTime` **无效**（见踩坑 10） |
| 前端表单校验 | 已修 `ArticleDialog.vue`（校验不通过不再发请求）；其余页面仍是「后端校验 + 拦截器弹 `msg`」 |
| 文章写操作与日记写操作 | ✅ 已于 2026-10-15 实现（见 `docs/api-contract.md` 2.3 / 2.4） |
| git 仓库 | 工作区根仓库只管 `frontend/`、`docs/`、`CLAUDE.md`、`README.md`（`backend/` 保持独立仓库，见踩坑 11）；两个仓库都还没有 remote，提交要各自 `git add` |

---

## 七、代码风格约定

- 统一响应：`Result.ok(...)` / `Result.error(...)`
- 业务异常：`throw new BusinessException("提示信息")`
- 分层：`controller` → `service` → `mapper` → `entity`，DTO 转换走 `service/convert`
- Controller 里不写业务逻辑，只做参数收取、权限预览、调用 Service
- 前端登录用户信息统一走 **`frontend/src/stores/user.js`**（pinia）：显示名/头像的兜底逻辑在 `frontend/src/utils/user.js`。
  任何直接改 `localStorage.userInfo` 的地方都要同步调用 store 的 `setInfo / setAvatar / clear`，否则导航栏会显示旧值
- 文件落盘统一走 `backend/src/main/java/com/rmh/aispringboot/service/FileStorageService.java`（文章封面与用户头像共用），不要在 Controller 里自己写 `Files.copy`
