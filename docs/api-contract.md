# 前后端接口契约

> 本文档描述前端 `frontend/` 与后端 `backend/` 之间的接口约定。
> **修改任何接口前请先读本文档，改完请同步更新本文档。**

---

## 一、通用约定（必须遵守）

### 1. 统一响应体

```json
{ "code": "200", "msg": "操作成功", "data": { } }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | **字符串** | 成功是 **`"200"`**（字符串，不是数字）；失败取值见下表 |
| `msg` | 字符串 | 提示信息（**字段名是 `msg`，不是 `message`**） |
| `data` | 任意 | 业务数据；失败时可能为 `null` |

**`code` 取值一览**（后端 `ResultCode`）：

| code | 含义 | 前端应有的动作 |
|---|---|---|
| `"200"` | 成功 | 返回 `data` |
| `"-1"` | 通用失败（`ResultCode.ERROR`） | 弹 `msg` |
| `"400"` | 参数错误（Bean Validation 失败） | 弹 `msg`（`data` 是字段级错误信息） |
| `"401"` / `"A0301"` | 未登录 / 访问未授权 | 清 token → 跳登录页 |
| `"A0230"` / `"A0231"` | token 过期 / 无效或被禁用 | 清 token → 跳登录页 |
| `"500"` | 系统异常 | 弹 `msg` |
| `"6000"` | 业务异常（`BusinessException`） | 弹 `msg` |

> ⚠️ **鉴权失败时 HTTP 状态不是 200**：`JwtAuthenticationFilter` / `SecurityConfig` 通过
> `ResponseUtil.writeError()` **直接写响应体**，用 **HTTP 401 / 403** + 上面的统一响应体。
> 只处理 axios 成功分支会漏掉登录过期，必须在 **error 分支**里读 `error.response.data.msg`。

**前端 `src/utils/request.js` 的拦截器逻辑**：

```js
// 成功分支（HTTP 2xx）
if (String(data.code) === '200') return data.data     // 剥一层壳，业务代码不再取 .data
if (['401','A0230','A0231','A0300','A0301'].includes(String(data.code))) {
    toLogin(data.msg)                                 // 清 token + 跳 /auth/login
} else {
    ElMessage.error(data.msg)
}
return Promise.reject(new Error(data.msg))            // 失败一律 reject

// 失败分支（HTTP 4xx/5xx）
if (response.status === 401 || response.status === 403) return toLogin(response.data.msg)
ElMessage.error(response.data.msg)                    // 其余弹后端 msg
return Promise.reject(...)
```

**要点**：
- 成功码必须是字符串 `"200"`（后端 `ResultCode.SUCCESS` 已匹配）
- 业务异常由 `GlobalExceptionHandler` 转成 `Result.error(...)`，**HTTP 状态仍是 200**
- 前端拿到的返回值**已经剥掉一层**，不要再 `.data`
- **失败一律 `reject`**：`.then(res => …)` 里的 `res` 一定是业务数据，不会再是「整个响应体」。
  因此**每个请求都要写 `.catch()`**，否则控制台会出现 unhandled rejection。
- 组件里不要再自己判断 `res.code` / `res.message`——`res` 已经是 `data.data`。

### 2. 认证

| 项 | 约定 |
|---|---|
| 请求头 | **`token`**（axios 拦截器自动带上；SSE 手写时用的是 `Token`，Servlet 侧大小写不敏感） |
| 值 | 登录接口返回的 JWT 原文（**无 `Bearer ` 前缀**） |
| 白名单 | `/`、`/error`、`/api/test`、`/api/user/login`、`/api/user/register`、`/api/user/logout` |
| 未登录 / 无权限返回 | **HTTP 401** + `{"code":"A0301","msg":"访问未授权"}`（`ResponseUtil` 直写，不经过 `GlobalExceptionHandler`） |
| token 无效 / 过期 / 签名不匹配（例如 `jwt.secret` 轮换后用户手里的旧 token） | **HTTP 401** + `{"code":"A0230","msg":"token无效"}`。`util/JwtAuthenticationFilter.java` 把「验签 → 查用户 → 写认证」整段包在同一个 `try/catch` 里，异常统一回落成 `A0230`；漏出去就会变成 **HTTP 500 + Spring 默认错误体**（前端读不到 `msg`，也不会自动回登录页 —— 见 `CLAUDE.md` 踩坑 15） |
| 账号被禁用 | **HTTP 401** + `{"code":"A0231","msg":"token已被禁止访问"}`。`JwtAuthenticationFilter` **每次请求**都会按 token 里的 `userId` 查一次库并校验 `status == 1`（`UserStatus.NORMAL`），所以管理员一禁用用户，他手里已签发的 token **立即失效**，不用等过期 |

**登录失败限流（2026-10-15 加）**：同一「用户名 + 客户端 IP」连续失败 **5 次**即锁定 **15 分钟**，
期间返回 `{"code":"6000","msg":"登录失败次数过多，账号已被临时锁定，请 N 分钟后再试"}`（HTTP 200）；登录成功即清零，
「用户被禁用」不计入失败次数。实现见 `service/LoginAttemptLimiter.java`（进程内、重启即清空，多实例部署要换共享存储）；
按来源 IP 的那层 `limit_req` 交给 Nginx（模板 `deploy/nginx.conf.example`）。
该限流器键里的 IP 只取 `request.getRemoteAddr()`、**故意不信 `X-Forwarded-For`**（请求头可伪造，等于给攻击者一个「换个 IP 就重置计数」的开关）。

### 3. 分页

**请求参数有两套命名，后端都要兼容**：

| 场景 | 参数名 |
|---|---|
| 用户端 | `pageNum` / `pageSize` |
| 管理后台 | `currentPage` / `size` |

**响应结构**（MyBatis-Plus `Page` 序列化结果）：

```json
{ "records": [ ... ], "total": 11, "size": 10, "current": 1, "pages": 2 }
```

前端只取 `records` 和 `total`。

### 4. sessionId 两种格式

| 来源 | 格式 | 示例 |
|---|---|---|
| 列表接口返回的 `id` | **裸 id** | `27` |
| 前端其他接口拼接 | **带前缀** | `session_27` |

**后端接口必须两种都能解析**（见 `ConsultationSessionService.parseSessionId`）。

### 5. 日期时间格式

| 项 | 约定 |
|---|---|
| 输出格式 | **`yyyy-MM-dd HH:mm:ss`**（例：`2026-10-15 15:59:46`），不再带 `T`、不带毫秒 |
| 影响的字段 | 所有 `LocalDateTime`：`startedAt`、`lastMessageTime`、`lastEmotionUpdatedAt`、`createdAt`、`updatedAt`、`publishedAt` |
| 不受影响 | `LocalDate`（`birthday`、`diaryDate`）仍是 `yyyy-MM-dd`；数据看板趋势里的 `date` 也是 `yyyy-MM-dd` |
| 实现 | `config/JacksonConfig.java` 注册 `LocalDateTime` 序列化器（Spring Boot 4 = Jackson 3，扩展点是 `JsonMapperBuilderCustomizer`） |
| 前端 | 大部分地方是**直接渲染字符串**；需要二次处理时用 dayjs（`dayjs(item.updatedAt).format('YYYY-MM-DD')`），dayjs 的默认解析同时支持空格与 `T` 分隔 |

> ⚠️ `spring.jackson.date-format` 对上面这些字段**无效**（它只管 `java.util.Date`），必须像现在这样显式换序列化器。

---

## 二、接口清单

### 2.1 用户模块

#### 注册

```
POST /api/user/register      （白名单，无需 token）
```

请求体：
```json
{
  "username": "tester",           // 必填，3-50 位字母数字下划线
  "email": "tester@example.com",  // 必填
  "password": "123456",           // 必填，6-255 位
  "confirmPassword": "123456",    // 必填，需与 password 一致
  "phone": "13800138000",         // 可选
  "nickname": "小明",             // 可选
  "gender": 1                     // 可选
}
```

返回 `data`：用户详情对象（含 `id`、`username`、`userType` 等），**不含 token**。

> 🔒 **请求体不接受 `userType`**（2026-10-15 修复提权漏洞）：`UserRegisterCommandDTO` 已删除该字段，客户端传 `"userType": 2` 会被 Jackson 当未知属性忽略；`service/convert/UserConvert.java` 固定写 `UserType.USER.getCode()`（=1），所以注册出来的**永远是普通用户**。响应里仍返回 `userType`/`userTypeDisplayName`，取的是库里真实值。

> 可选项留空时传**空字符串**即可：`phone` 的正则是 `^$|^1[3-9]\d{9}$`（允许空串），`nickname` 允许空串。
> 字段名**大小写敏感**（Jackson 默认不做大小写对齐），首字母写错会被静默忽略。

> ⚠️ 前端调用路径是 **`/api/user/register`**，不是 `/api/user/add`（后者不存在）。

#### 登录

```
POST /api/user/login         （白名单）
```

请求体：
```json
{ "username": "tester", "password": "123456" }
```

返回 `data`：
```json
{
  "token": "eyJhbGci...",
  "roleType": "1",
  "userInfo": {
    "id": 6, "username": "tester", "nickname": null,
    "userType": 1, "userTypeDisplayName": "普通用户",
    "status": 1, "displayName": "tester", ...
  }
}
```

> 前端登录成功后把 `token` 和 `userInfo` 存入 `localStorage`，路由守卫依赖 `userInfo.userType`。

#### 获取当前用户

```
GET /api/user/current        （需 token）
```

返回 `data`：用户详情对象（从 `SecurityContext` 取，不查库）。

#### 退出登录

```
POST /api/user/logout        （白名单）
```

无请求体，返回 `{"code":"200","data":null,"msg":"操作成功"}`。

> **设计说明**：JWT 无状态，服务端没有 session 可销毁，此接口仅用于让前端继续执行清缓存流程。
> 放在白名单是为了**保证 token 过期时也能正常退出**（否则前端 `.then()` 不执行，会卡在已登录状态）。

#### 上传/更换头像

```
POST /api/user/avatar        （需 token）
Content-Type: multipart/form-data
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | 图片，**≤ 5MB**。上限由后端 multipart 强制（`spring.servlet.multipart.max-file-size: 5MB`），超限返回统一响应体 `{"code":"5004","msg":"上传的文件过大，单个文件不能超过 5MB"}`（HTTP 200）。扩展名必须落在白名单内（`jpg/jpeg/png/gif/bmp/webp`），否则返回 `{"code":"6000","msg":"不支持的文件类型（.xxx），仅支持图片 …"}`（见 2.6） |

返回 `data`：**更新后的完整用户详情**（字段同登录响应的 `userInfo`，其中 `avatar` 是项目内相对路径）。

```json
{
  "code": "200",
  "data": {
    "id": 6,
    "username": "tester",
    "nickname": null,
    "avatar": "/files/bussiness/user_avatar/1792047515898-493035e7.jpg",
    "userType": 1,
    "displayName": "tester",
    "updatedAt": "2026-10-15 14:58:35"
  },
  "msg": "操作成功"
}
```

> - 文件落在项目内 `backend/uploads/bussiness/user_avatar/`，元数据写 `sys_file_info`（`business_type='USER_AVATAR'`、`business_field='avatar'`）。
> - **换头像时会顺手删掉上一个头像文件**（仅限 `bussiness/user_avatar/` 前缀），避免 uploads 目录堆积孤儿文件；失败不影响主流程。
> - 非图片文件（如 `.md`）：先落盘判定为 `DOC`，随即删除文件与元数据行，返回 `{"code":"BUSINESS_ERROR","msg":"头像只能是图片文件（jpg / png / gif / webp / svg）"}`。
> - 未换过头像的用户 `avatar` 为 `null`，前端回退到本地默认图 `/img/default-avatar.svg`。
> - 前端：`api/frontend.js` 的 `uploadAvatar(file)` → `components/AvatarUploader.vue`（无可见 DOM，父组件 `pick()` 唤起文件框）→ 成功后 `stores/user.js` 的 `setInfo(返回的用户对象)`，导航栏/头部头像与昵称**无需刷新页面**即可更新。

#### 用户列表与状态管理（仅管理员）

```
GET  /api/user/admin/page            （需 token + 管理员）
PUT  /api/user/admin/{id}/status     （需 token + 管理员）
```

列表查询参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `keyword` | 否 | 模糊匹配 `username` / `nickname` / `email` |
| `status` | 否 | `1` 正常 / `0` 禁用 ⚠️ `0` 必须真的发出去，前端用 `??` 而不是 `\|\|` |
| `userType` | 否 | `1` 普通用户 / `2` 管理员 |
| `currentPage` / `size` | 否 | 管理后台命名，默认 `1` / `10`；同时兼容 `pageNum` / `pageSize` |

返回 `data`：MyBatis-Plus `Page`，`records` 每项是完整用户详情对象（字段同登录响应的 `userInfo`，含 `status`/`statusDisplayName`、`userType`/`userTypeDisplayName`，**不含 `password`**），按 `id` 倒序。

修改状态请求体与返回：

```json
// 请求
{ "status": 0 }
```
返回 `data`：**更新后的用户详情对象**（前端拿它就地替换表格里的那一行）。

> - **禁用立即生效**：`UserService.login` 的 `isActive()` 判断会拦下重新登录（`{"code":"BUSINESS_ERROR","msg":"用户被禁用，请联系管理员"}`），已签发的 token 也会被 `JwtAuthenticationFilter` 拒绝（见 一.2）。
> - **管理员不能修改自己的状态** → `{"code":"BUSINESS_ERROR","msg":"不能修改自己的账号状态"}`（防止唯一的超管把自己锁在门外）；前端也会把当前账号的「禁用」按钮置灰。
> - 非法状态（如 `5`）→ `不支持的用户状态：5`；`status` 缺失 → `{"code":"400","msg":"状态不能为空"}`；用户不存在 → `用户不存在`。
> - 非管理员调用 → `{"code":"BUSINESS_ERROR","msg":"无权访问，该接口仅管理员可用"}`（`SecurityUserUtil.requireAdmin()`）。
> - 前端：`api/admin.js#getUserPage` / `#updateUserStatus` → `views/users.vue`（后台侧栏「用户管理」，路由 `/back/users`，图标 `UserFilled`）。

---

### 2.2 心理对话模块

#### 创建会话

```
POST /api/psychological-chat/session/start     （需 token）
```

请求体：
```json
{
  "sessionTitle": "心理健康AI助手 - 10-15 14:30",   // 驼峰，大小写敏感；最长 200 字符
  "initialMessage": "我最近压力很大"             // 必填，最长 2000 字符
}
```

返回 `data`：
```json
{
  "sessionId": "session_28",     // 带前缀，前端后续对话要用它
  "userHash": 6,
  "initialMessage": "我最近压力很大",
  "startTime": 1792038865817,
  "expiryTime": 1792125265817,   // 24 小时后
  "messageCount": 1,
  "status": "ACTIVE"
}
```

#### 流式对话（SSE）

```
POST /api/psychological-chat/stream            （需 token）
Content-Type: application/json
Accept: text/event-stream
```

请求体：
```json
{ "sessionId": "session_28", "userMessage": "我该怎么办" }
```

**响应为 SSE 流**：

```
event: message
data: {"code":"200","data":{"content":"我能理解","Type":"normal"},"msg":"操作成功"}

event: message
data: {"code":"200","data":{"content":"你的感受","Type":"normal"},"msg":"操作成功"}

event: done
data: {}
```

**前端消费逻辑**（`src/views/consultation.vue`）：
- 收到 `event: done` → `abort()` 结束，并置 `isAiTyping = false`
- 收到普通 `message` → `JSON.parse(event.data)`，`code === '200'` 时取 `data.content` **累加**渲染
- 失败时读 `payload.msg`（统一响应体的字段是 `msg`，不是 `message`）
- 自己 `abort()` 引起的 `AbortError` 不算错误
- ⚠️ 2026-10-15 起**流关闭后不再触发情绪分析**（原 `onclose` 收尾已随情绪花园一起删除，见 2.2 末尾说明）

**⚠️ 关键约束**：
1. 响应 `Content-Type` 以 `text/event-stream` **开头**即可（前端用 `startsWith` 比对，容忍 `;charset=utf-8`）
2. 异常不能直接抛（会导致连接中断），必须用 `onErrorResume` 转成 `event: error` 帧

**对话记忆（第④步，2026-10-15 起「重启不失忆」）**：

记忆窗口是 Spring AI 的 `MessageWindowChatMemory`（`config/ChatClientConfig.java`，`maxMessages=30`），
它**只活在进程内存里**，后端一重启就全空。现在 `PsychologicalSupportService.ensureMemoryLoaded(...)`
会在某个会话重启后**第一次**调用 `/stream` 时，把 `consultation_message` 里的历史回灌进这个窗口：

- 进程内已有记忆的会话（`chatMemory.get(conversationId)` 非空）直接跳过，不重复回灌；
- 取该会话**最近 30 条**消息；本次用户消息若已落库则**从历史里剔除**（它随后会被 `MessageChatMemoryAdvisor` 作为当轮提问再追加一次，不剔除模型会看到重复的一句）；
- 首条若是 AI 回复则丢弃，保证上下文以用户消息开头；回灌失败只记 `log.warn`，不影响本轮对话；
- 成功会打印 `INFO ... 回灌对话记忆 sessionId=37 conversationId=conversation_session_37 messages=6`。

实测（2026-10-15）：新建会话告知「我叫小北 / 猫叫豆豆 / 喜欢攀岩」→ 杀掉后端重启 → 追问「我叫什么名字？我的猫叫什么？我最喜欢的运动是什么？」
→ 模型完整答出小北、豆豆、攀岩（3.1s）；重启前的进程内记忆同样有效（追问猫名答「豆豆」）。

> 取舍：Spring AI 2.1.0-M1 只自带 `InMemoryChatMemoryRepository`，**没有 JDBC 版 `ChatMemoryRepository`**，
> 所以没有引入新依赖/新表，而是「重启后按需回灌 + 之后仍由 advisor 增量维护」。
> 被删除会话的记忆仍留在进程内（每会话上限 30 条，进程重启即释放）。

#### 会话列表（分页）

```
GET /api/psychological-chat/sessions           （需 token）
```

查询参数（**两套命名都支持**）：
```
?pageNum=1&pageSize=10        （用户端）
?currentPage=1&size=10        （管理后台）
```

**权限规则**：
- 普通用户（`userType=1`）→ 只返回**自己的**会话
- 管理员（`userType=2`）→ 返回**全部用户**的会话，并附带 `userNickname`

返回 `data` 的 `records` 每项：
```json
{
  "id": 27,                          // 裸 id
  "sessionTitle": "心理健康AI助手 - 10-14",
  "startedAt": "2026-10-14 20:34:05",
  "lastMessageContent": "今天天气很好",  // 列表预览
  "messageCount": 12,
  "durationMinutes": 15,             // startedAt → 最后一条消息时间
  "lastMessageTime": "2026-10-14 20:49:05",
  "userNickname": "系统管理员"         // 仅管理员查询时有值
}
```

#### 查询某会话的消息记录

```
GET /api/psychological-chat/sessions/{sessionId}/messages    （需 token）
```

`{sessionId}` **两种格式都接受**：`27` 或 `session_27`。

返回 `data`：消息数组（按时间升序）
```json
[
  { "id": 117, "sessionId": 27, "senderType": 1, "senderTypeDesc": "用户",
    "messageType": 1, "content": "今天天气很好", "createdAt": "2026-10-14 20:34:05",
    "aiModel": "openai", "contentLength": 6 }
]
```

> `senderType`：**1 = 用户，2 = AI**（前端据此决定气泡左右）。
>
> 会话归属：普通用户只能查自己的会话，查不到与「不属于自己」返回同一句 `会话不存在或无权访问`；
> **管理员可以查任意会话的消息**（后台「咨询记录 → 详情」用的就是这个接口，列表接口对管理员同样是全量）。

#### 删除会话

```
DELETE /api/psychological-chat/sessions/{sessionId}          （需 token）
```

`{sessionId}` 同样兼容两种格式。**会级联删除该会话的全部消息**。

返回 `data`: `null`，只要 `code === "200"` 即可。

#### 会话情绪分析

```
GET /api/psychological-chat/session/{sessionId}/emotion      （需 token）
```

> ⚠️ 注意路径是**单数 `session`**，与列表接口的复数 `sessions` 不同。
> 前端会自动补 `session_` 前缀。

返回 `data`：
```json
{
  "primaryEmotion": "中性",
  "emotionScore": 50,                  // 0-100
  "isNegative": false,
  "riskLevel": 0,                      // 0正常 1关注 2预警 3危机
  "suggestion": "",
  "improvementSuggestions": [],
  "riskDescription": ""
}
```

**当前状态（2026-10-15 起已接入 AI）**：后端会把该会话的对话交给大模型做一次结构化情绪评估
（`AiService/EmotionAnalysisService.java`，用不带对话记忆 advisor 的独立 `ChatClient`），把结果 JSON 写入
`consultation_session.last_emotion_analysis` 并刷新 `last_emotion_updated_at`。

- **缓存复用**：`last_emotion_updated_at` 不早于会话最后一条消息的时间时，直接返回缓存，**不再调用 AI**
  （实测第二次请求 9ms，不重复消耗 token）。
- **自动重新分析**：会话又产生新消息后，下一次请求会重新分析并覆盖缓存（实测分析耗时 6-7 秒）。
- **兜底**：会话没有任何消息、模型返回不是合法 JSON、调用失败（超时/额度/网络）时返回上面的默认值；
  失败**不写缓存**，下次请求会重试。
- 服务端已做字段校验：`emotionScore` 夹到 0-100（缺省 50）、`riskLevel` 夹到 0-3（缺省 0）、
  `improvementSuggestions` **一定是数组**（最多 5 条）——前端 `:46` 直接 `.length`、`v-for`，为 null 会抛错。
- 参与分析的是**最近 20 条消息**（每条截断 500 字符），由「用户：/助手：」拼接。

真实返回示例（tester 会话，用户表达「压力大、失眠、觉得自己很没用」）：

```json
{
  "primaryEmotion": "压力",
  "emotionScore": 85,
  "isNegative": true,
  "riskLevel": 2,
  "suggestion": "你已经在承受很大的压力，还睡不好，这真的很难熬……",
  "improvementSuggestions": [
    "今晚睡前20分钟写下脑子里担心的事，并标注哪些可以明天处理……",
    "如果躺下20分钟睡不着，就起身到昏暗处做安静的事，困了再回床……"
  ],
  "riskDescription": "……目前未提及自伤、自杀念头或具体计划，需保持关注并建议寻求专业支持。"
}
```

> ⚠️ **当前前端不再调用这个接口**（2026-10-15 起）：原「情绪花园」面板已从 `frontend/src/views/consultation.vue`
> 移除，咨询页现在只有左侧会话列表 + 右侧对话区。接口、缓存与落库逻辑**全部保留**（`last_emotion_analysis`
> 仍在写、上面的实测数据仍成立），将来要在界面上重新展示时，只需把面板挂回并调用本接口即可；
> `frontend/src/api/frontend.js` 里的 `getSessionEmotion` 也原样保留着。
> 推论：`riskLevel` 目前**没有任何界面出口**（`riskDescription` 只在这个接口的返回体里），
> 后端 `PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT` 里「自杀倾向优先关怀」的提示词约束仍然生效。

---

### 2.3 知识文章模块

> 对应 Controller：`controller/KnowledgeController.java`（`@RequestMapping("/api/knowledge")`）。

#### 分类列表

```
GET /api/knowledge/category/tree                            （需 token，普通用户可读）
```

无参数。返回 `data` 是**裸数组**（不是树对象，也**不含 `children`**，前端不递归）：

```json
[
  { "id": 1, "parentId": 0, "categoryName": "心理健康基础" },
  { "id": 2, "parentId": 0, "categoryName": "情绪管理" }
]
```

排序：`sort_order` 升序，其次 `id` 升序。`knowledge.vue` 直接对它 `.map`（无兜底），**必须是数组**。

#### 文章分页

```
GET /api/knowledge/article/page                             （需 token）
```

| 参数 | 说明 |
|---|---|
| `pageNum` / `currentPage` / `current` | 页码，默认 `1`（三套命名都兼容） |
| `pageSize` / `size` | 每页条数，默认 `10`，**上限 100** |
| `title` | 标题模糊匹配，空串 = 不过滤 |
| `categoryId` | 分类筛选，空串 = 不过滤 |
| `status` | `"0"` / `"1"` / `"2"`，空串 = 全部 |
| `sortField` | `publishedAt`（默认）或 `readCount`，其它值按默认处理 |
| `sortDirection` | `desc`（默认）/ `asc` |

**普通用户强制只返回 `status = 1`（已发布）**，管理员不加该限制。

返回 `data`：`{ records: [...], total, size, current, pages }`，行字段：

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440007",   // UUID 字符串，不是自增数字
  "categoryId": 3,                                  // 数字
  "categoryName": "压力缓解",
  "title": "学生心理压力应对策略",
  "summary": "...",
  "content": "...",
  "coverImage": "",
  "tags": "学生心理,学业压力,青少年心理",              // 逗号分隔字符串
  "tagArray": ["学生心理", "学业压力", "青少年心理"],  // 字符串数组
  "authorId": 1,
  "authorName": "系统管理员",
  "readCount": 35,
  "status": 1,                                      // 数字
  "publishedAt": "2025-09-07 10:00:00",
  "createdAt": "...",
  "updatedAt": "2025-11-10 19:07:53"
}
```

> ⚠️ `knowledge.vue:22` 用 `categoryMap[row.categoryId]` 查分类名、`knowledge.vue:32-33` 用
> `row.status === 0 || row.status === 2` 判「发布」、`=== 1` 判「下线」——**两者必须是数字**，
> 返回字符串会静默不显示按钮/分类。
> ⚠️ 前台 `frontendKnowledge.vue` 会把 `coverImage` 拼上 `fileBaseUrl`（现在是**空字符串**，同源）
> 再加兜底图 `/img/cover-placeholder.svg`；上传与访问方式见 2.6。

#### 文章详情

```
GET /api/knowledge/article/{id}                             （需 token）
```

`{id}` 是 **UUID 字符串**（路由 `knowledge/article/:id` 传 `props: true`）。返回 `data` 与列表行同构，
另外保证 `tagArray` 是数组（供前台 `v-for` 与编辑弹窗 `tags = tagArray.join(',')` 使用）。
查不到 → `BusinessException("文章不存在或已删除")`（`code = "BUSINESS_ERROR"`，HTTP 200）。

> 📈 **阅读量自增规则（2026-10-15 起）**：本接口被前台阅读页（`api/frontend.js`）与后台编辑弹窗（`api/admin.js` ← `views/knowledge.vue`）**共用**，所以只在**阅读者不是管理员**时才计数：`KnowledgeController.articleDetail` 改调 `knowledgeService.getArticleDetailAndCountView(id)`，内部 `setSql("read_count = IFNULL(read_count, 0) + 1")` **原子自增**（避免并发覆盖），并把返回 DTO 的 `readCount` 同步 +1；管理员（后台预览/编辑）走单参 `getArticleDetail(id)`，**不计数**。实测：普通用户 GET → 库 +1 且响应同步；管理员 GET → 库不变。

#### 新增 / 编辑文章（仅管理员）

```
POST /api/knowledge/article                                 （仅管理员）
PUT  /api/knowledge/article/{id}                            （仅管理员）
```

请求体（前端 `components/ArticleDialog.vue` 实际发送的内容）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string \| null | **新增时由前端生成**：上传过封面就是那次上传用的 UUID（`crypto.randomUUID()`），没上传则是 `null`。后端沿用非空 id，为空则自己生成 |
| `title` | string | 必填，≤200 |
| `categoryId` | number \| `""` | 表单初始值是空串、且校验不会拦住请求，所以后端按字符串收再解析，解析不到报 `所选分类不存在` |
| `summary` | string | 可空，≤2000 |
| `content` | string | 必填，富文本 HTML，≤5000 |
| `coverImage` | string | `/files/...` 相对路径或空串；**空串入库，不是 `null`**（前端要拼 `fileBaseUrl + coverImage`，`null` 会拼出字符串 `"null"`） |
| `tags` | string | 英文逗号分隔，可为空 |

- 前端还会把详情旧值（`categoryName`/`authorId`/`authorName`/`readCount`/`publishedAt`/`createdAt`/`updatedAt`）一起回传（编辑弹窗 `Object.assign`），**后端只更新白名单列**，阅读量、作者、创建时间、状态都不会被覆盖。
- 新增一律按 `status = 1`（已发布）入库并盖上 `publishedAt`。
- `id` 已存在 → `BusinessException("文章已存在，请关闭弹窗后重试")`。
- 换过封面会删掉旧封面文件（`FileStorageService.deleteStoredFile`）。
- 响应 `data` 与文章详情同构（前端不读它）。

#### 上下架 / 删除（仅管理员）

```
PUT    /api/knowledge/article/{id}/status                   （仅管理员）
DELETE /api/knowledge/article/{id}                          （仅管理员）
```

- 上下架请求体 `{"status":1}`（发布）/ `{"status":2}`（下线），**JSON 数字**；`0` = 草稿。
- 发布时若 `publishedAt` 为空才补时间，下线再上线不会改首次发布时间。
- 删除会同时删掉该文章的本地封面文件。
- 查不到文章 → `文章不存在或已删除`。

---

### 2.4 情绪日记

#### 我的情绪日记（用户端，只返回自己的）

```
GET /api/emotion-diary/page                                 （需 token）
```

> 2026-10-15 新增。`userId` **强制取登录态**，请求里传什么都不生效，
> 因此普通用户不可能看到别人的日记；筛选只保留 `moodScreRange`。

| 参数 | 说明 |
|---|---|
| `pageNum` / `currentPage` / `current` | 页码，默认 `1`（三种命名都兼容） |
| `pageSize` / `size` | 每页条数，默认 `10`，上限 100 |
| `moodScreRange` | 可选，心情区间 `1-3` / `4-6` / `7-10` |
| `diaryDate` | 可选，精确取某一天（`yyyy-MM-dd`）。前端切换记录日期时用它判断「该天已有记录 → 编辑 / 没有 → 新增」 |

返回 `data` 的结构与下面后台列表**完全一致**（`records` 行里会带上自己的 `userId`/`username`/`nickname`）。
未登录 → `HTTP 401` + `code = "A0301"`。

> ⚠️ **一天一条 + 编辑就是重新提交那一天**：新增/编辑共用 `POST /api/emotion-diary`，
> 关键在 `diaryDate`：填今天就是记今天，填以前的某一天就是**改那一天**（服务端按 `(user_id, diary_date)` upsert，
> 只更新不新增）。前端为了不让用户误以为「被覆盖」，会：
> ① 提供**记录日期选择器**（不允许选未来）；② 该日期已有记录时**把内容回填进表单**并提示「保存会更新这一篇」；
> ③ 按钮文案在「保存记录 / 更新这篇记录」之间切换；④ 列表里被改过的记录显示「最后更新 …」。
>
> 服务端同样兜底：`diaryDate` 落在**未来**时直接 `BUSINESS_ERROR`、`msg = "不能记录未来日期的日记"`
> （2026-10-15 补，此前前端可手输日期绕过日历限制）。

#### 删除自己的一篇日记

```
DELETE /api/emotion-diary/{id}                              （需 token）
```

> `{id}` 是**日记主键**（不是日期、不是用户ID）。服务层按「主键 + 登录用户」双条件校验：
> 查不到与不属于自己都返回 `code = "BUSINESS_ERROR"`、`msg = "日记不存在或无权访问"`，
> 因此不能用来探测别人有没有这条日记。返回 `data: null`，成功即 `code === "200"`。

#### 情绪日记分页（后台，全部用户）

```
GET /api/emotion-diary/admin/page                           （仅管理员）
```

> 对应 Controller：`controller/EmotionDiaryController.java`，方法首行 `SecurityUserUtil.requireAdmin();`
> 普通用户调用 → `code = "BUSINESS_ERROR"`、`msg = "无权访问，该接口仅管理员可用"`。

| 参数 | 说明 |
|---|---|
| `current` / `currentPage` / `pageNum` | 页码，默认 `1`。**前端用的是 `current`** |
| `size` / `pageSize` | 每页条数，默认 `10`，上限 100 |
| `userId` | 按用户筛选 |
| `moodScreRange` | 心情区间 `1-3` / `4-6` / `7-10`。**拼写就是 `moodScreRange`**（前端如此，后端照收） |

返回 `data`：`{ records: [...], total, size, current, pages }`，行字段：

```json
{
  "id": 28, "userId": 4, "username": "ces", "nickname": "1561",
  "diaryDate": "2025-11-11", "moodScore": 6,
  "diaryContent": "今天……",
  "createdAt": "...", "updatedAt": "..."
}
```

> 2026-10-15 **精简**：`dominantEmotion`（主要情绪）、`emotionTriggers`（情绪触发因素）、
> `sleepQuality`（睡眠质量）、`stressLevel`（压力水平）四个字段已从**库表、实体、DTO、前端**里彻底删除，
> 日记只剩「心情评分 + 今日感想」两部分（备份见 `.db-backup/emotion_diary_before_slim_*.sql`）。
> 历史数据里这四列的内容不再提供，接口也不再返回它们。

> 2026-10-15 **再精简**：`aiEmotionAnalysis`、`aiAnalysisUpdatedAt` 两个字段已随那条**从未实现、已废弃**的
> 「日记 AI 情绪分析」链路一起从**库表、实体、DTO、后台页面**删除（`emotional.vue` 的「AI情绪分析结果」弹窗区块、
> `getAiEmotionTagType` 等四个函数、`.ai-analysis-result` 样式同步删净），废弃的任务表 `ai_analysis_task` 也已整表 DROP。
> 改前全库备份 `D:\mental_health_assistant\.db-backup\mental_health_assistant_full_20261015-175210.sql`。
> 注意与 **2.2 的会话级情绪分析**（`consultation_session.last_emotion_analysis`）区分：那个仍在工作、**未删**。

#### 记录心情日记（登录用户）

```
POST /api/emotion-diary                                     （需 token）
```

请求体就是前端 `views/emotionDiary.vue` 的 `diaryForm`：

| 字段 | 说明 |
|---|---|
| `diaryDate` | `YYYY-MM-DD`，留空按今天算；格式不对 → `日期格式不正确，应为 yyyy-MM-dd`；落在未来 → `不能记录未来日期的日记` |
| `moodScore` | 必填，1-10（越界 → `心情评分范围是 1-10`） |
| `diaryContent` | 可空，≤2000（今日感想） |

> 2026-10-15 精简后请求体只有这三个字段；多传旧字段（如 `dominantEmotion`）后端会忽略，不再报错也不落库。

- `emotion_diary` 上 `(user_id, diary_date)` 有唯一键：**同一天重复提交按覆盖更新处理**，不会产生第二条记录，返回的 `id` 不变。
- `userId` 一律取当前登录用户，前端传什么都不看。
- 覆盖更新**只改 `mood_score` / `diary_content`**（其余列都是自动维护的时间戳）。
- 响应 `data` 是该条日记（前端不读它）。

#### 删除心情日记（仅管理员）

```
DELETE /api/emotion-diary/admin/{id}                        （仅管理员）
```

路径 `{id}` 是**日记主键（Long）**——后台列表把这一列的表头误标成了「用户ID」。查不到 → `日记不存在或已删除`。

---

### 2.5 数据看板（后台读）

```
GET /api/data-analytics/overview                             （仅管理员）
```

无参数。返回 `data`：

```json
{
  "systemOverview": {
    "totalUsers": 5, "activeUsers": 4, "totalDiaries": 17, "todayNewDiaries": 0,
    "totalSessions": 12, "todayNewSessions": 1, "avgMoodScore": 6.1
  },
  "consultationStats": {
    "totalSessions": 12, "avgDurationMinutes": 0.4,
    "dailyTrend": [{ "date": "2026-10-15", "sessionCount": 1, "userCount": 1 }]
  },
  "emotionTrend": [{ "date": "2025-11-11", "avgMoodScore": 6.0, "recordCount": 2 }],
  "userActivity": [{ "date": "2026-10-15", "activeUsers": 1, "newUsers": 1, "diaryUsers": 0, "consultationUsers": 1 }]
}
```

口径：`activeUsers` = 有日记或有会话的去重用户数；`avgDurationMinutes` = 有消息的会话按
「最后一条消息时间 − 开始时间」求平均；`todayNewSessions` / `todayNewDiaries` 按**服务器当天**统计。

> ⚠️ `dashboard.vue:503-507` 的 `initCharts()` 在 `.then` 里**无条件执行**，`dashboard.vue:180,286,405`
> 三处 `.map` 无兜底 → `emotionTrend`、`consultationStats.dailyTrend`、`userActivity`
> **必须是数组且必须存在**，否则整页 TypeError；`consultationStats` 与 `systemOverview` 也要同时存在
> （`dashboard.vue:93` 在 `consultationStats` 的 `v-if` 内解引用 `systemOverview.activeUsers`）。
> ⚠️ 趋势窗口：`consultationStats.dailyTrend` / `userActivity` 以「今天与最后一条会话日期中较晚者」
> 为锚点取 14 天；**`emotionTrend` 单独以「最后一条日记日期」为锚点**——库里的日记数据远早于当前日期，
> 若同样以今天为锚点会一条都取不到、图表全为 0（横轴仍是真实日期，不伪造数据）。

### 2.6 文件上传与本地静态资源

> 2026-10-15 起，**上传的文件全部留在项目内**（`backend/uploads/`），前端不再依赖任何外部文件服务器/图床。

**上传**

| 项 | 值 |
|---|---|
| 路径 | `POST /api/file/upload` |
| 请求 | `multipart/form-data`：`file`（文件，必填）、`businessType`（`ARTICLE` / `USER_AVATAR`，其它值按临时文件处理）、`businessId`、`businessField`（`cover` / `avatar`） |
| 鉴权 | 需要 `token`（不在白名单里） |
| 落盘 | `file.upload-dir`（`application.yaml`，默认 `./uploads`）下按业务分目录：`ARTICLE` → `bussiness/article/`、`USER_AVATAR` → `bussiness/user_avatar/`、未识别 → `temp/`（临时文件 `is_temp=1`、`status=0`、24 小时后过期） |
| 文件名 | `<epochMillis>-<uuid8>.<ext>`，扩展名只保留字母数字（防路径穿越），不信任客户端文件名 |
| 大小上限 | `spring.servlet.multipart.max-file-size: 5MB` + `max-request-size: 10MB`（`application.yaml`）。超限 → `{"code":"5004","data":null,"msg":"上传的文件过大，单个文件不能超过 5MB"}`（**HTTP 200**、统一响应体，由 `common/GlobalExceptionHandler` 的 `MaxUploadSizeExceededException` 处理）。⚠️ 2026-10-15 之前没有 multipart 配置 → 走 Spring 默认 1MB，实测上传 2MB 得到 **HTTP 413 + 空响应体**（前端读不到 `msg`） |
| 类型校验 | ✅ 2026-10-15 起是**扩展名白名单**（`FileStorageService.ALLOWED_EXTENSIONS`）：只收 `jpg`/`jpeg`/`png`/`gif`/`bmp`/`webp` 与 `mp4`/`webm`，其余（含 `.html`、`.svg`、以及没有扩展名的文件）一律 `{"code":"6000","msg":"不支持的文件类型（.xxx），仅支持图片 jpg / jpeg / png / gif / bmp / webp 与视频 mp4 / webm"}`。`svg` 被**刻意排除**：它能内嵌 `<script>`，而 `/files/**` 匿名可读且按静态资源直出，同源脚本能读走 `localStorage` 里的 JWT；网关层再叠 `nosniff` + CSP `sandbox` + 拒绝脚本后缀（`deploy/nginx.conf.example`） |
| 响应 | `{"id":57,"originalName":"...","filePath":"/files/bussiness/article/1792046226236-6c6d204b.svg","fileSize":871,"fileType":"IMG","businessType":"ARTICLE","businessId":"...","businessField":"cover"}` |
| 元数据 | 同时写入 `sys_file_info`（`file_path` 存相对路径，`file_type` 取 `IMG`/`VIDEO`/`DOC`，`upload_user_id` 取当前登录用户） |

**访问**

- 后端把 `file.upload-dir` 挂到 `/files/**`（`config/WebMvcConfig.java`），**匿名可读**：`/files/**` 已加入 `SecurityConfig.PUBLIC_PATH`
  —— `<img src>` 带不了 `token`，不放行的话图片会被 401 拦掉。
- 前端 `frontend/src/config/index.js` 的 `fileBaseUrl` 现在是**空字符串**（同源）：`fileBaseUrl + filePath` → `/files/...`
  - 开发环境：`frontend/vite.config.js` 把 `/files` 代理到 `http://localhost:1236`；
  - 生产环境：由后端或反向代理提供 `/files/**`。
- 兜底图与默认头像也换成了项目内资源：`frontend/public/img/cover-placeholder.svg`、`frontend/public/img/default-avatar.svg`。

**临时文件清理（第⑤步，2026-10-15 起）**

`businessType` 不是 `ARTICLE` / `USER_AVATAR` 的上传一律按**临时文件**处理（`is_temp=1`、`status=0`、`expire_time = 上传时间 + 24h`，落 `uploads/temp/`），由 `task/TempFileCleanupTask.java` 定时清理：

| 项 | 值 |
|---|---|
| 触发 | `@Scheduled(cron = "${file.temp-cleanup-cron:0 30 3 * * ?}")`，默认**每天 03:30**；开关与周期改 `application.yaml` 的 `file.temp-cleanup-cron` 即可，`config/SchedulingConfig.java` 负责 `@EnableScheduling` |
| 条件 | `sys_file_info` 里 `is_temp = 1 AND expire_time < now()` |
| 动作 | 逐个 `FileStorageService.deleteStoredFile(file_path)`（删磁盘文件 + 删 `sys_file_info` 行），再按 id 兜底删一次；单条失败只 `log.warn` 不中断 |
| 日志 | 有删除时打 `临时文件清理完成：删除 N 个过期文件` |
| 实测 | 2026-10-15 用 `SPRING_APPLICATION_JSON` 把 cron 临时改成 `0/10 * * * * ?` 启动，1 个过期临时文件被删、库与磁盘均无残留（该实例仅用于验证，验证后已重启回正常配置） |

> `ARTICLE` / `USER_AVATAR` 的文件**不设过期**（`is_temp=0`），不会被清理任务碰到；换头像时由 `UserService.updateAvatar` 主动删旧文件。

> ⚠️ 历史遗留说明已过时：第②步已于 2026-10-15 给 7 篇文章补齐封面（`knowledge_article.cover_image` 全部指向 `/files/bussiness/article/...`）。
> `user.avatar` 目前只有 `admin` / `tester` 有（联调时上传的本地文件），其余用户显示默认头像。

---

## 三、鉴权与越权防护

所有会话相关接口在返回数据前都会校验**会话归属**：

```java
consultationSessionService.getSessionByIdAndUserId(sessionId, userId)
```

- 查不到 → `BusinessException("会话不存在或无权访问")`
- **故意不区分「不存在」与「不属于你」**，返回同一句提示，防止攻击者枚举他人会话 ID
- **管理员例外**：`GET /sessions/{id}/messages` 走 `PsychologicalChat.requireVisibleSessionId`，
  管理员只校验「会话是否存在」（不存在 → `会话不存在`），不校验归属 —— 否则后台能看到会话列表却打不开详情。
  删除会话（`DELETE /sessions/{id}`）与情绪分析仍是归属校验，管理员**不能**删除/分析他人的会话。

---

## 四、已知未实现项

| 项 | 说明 |
|---|---|
| 情绪分析 | ✅ 已于 2026-10-15 接入 AI（见 2.2 会话情绪分析）：结果落 `consultation_session.last_emotion_analysis`，缓存未过期时复用、有新消息则重算；无消息或调用失败退回默认值。⚠️ 同日起前端不再展示（情绪花园已从咨询页移除），接口保留 |
| 会话状态流转 | `consultation_session` 表没有 `status`/`endedAt` 字段，`ACTIVE` 只是返回值里的一个字符串 |
| Token 服务端失效 | 退出登录仅清前端缓存，token 在有效期内仍可用 |
| 对话记忆持久化 | ✅ 已于 2026-10-15 解决「重启失忆」（见 2.2 对话记忆）：重启后按需从 `consultation_message` 回灌最近 30 条；仍未做的是**持久化记忆仓库**（Spring AI 2.1.0-M1 没有 JDBC 版 `ChatMemoryRepository`），所以删掉的会话仍会短暂留在进程内存里 |
| 修改会话标题 | 前端没有这个调用，故未实现 |
| 文件上传 | ✅ 已于 2026-10-15 实现为**本地存储**（见 2.6）：落盘到 `backend/uploads/`，`/files/**` 由后端直接提供，不再指向 `http://159.75.169.224:1235` |
| 历史图片缺失 | ✅ 已于 2026-10-15 补齐：7 篇文章的 `cover_image` 全部指向本地 `/files/bussiness/article/...`（见 2.6）；`user.avatar` 目前只有 `admin` / `tester` 有图，其余用户显示默认头像 |
| 临时文件清理 | ✅ 已于 2026-10-15 实现（见 2.6）：`@Scheduled` 每天 03:30 清理 `is_temp=1` 且已过 `expire_time` 的文件，删磁盘 + 删 `sys_file_info` |
| 日期时间格式 | ✅ 已于 2026-10-15 统一（见 一.5）：`LocalDateTime` 输出 `yyyy-MM-dd HH:mm:ss`（`config/JacksonConfig.java` 注册 Jackson 3 序列化器） |
| 头像裁剪/多尺寸 | 上传的原图直接当头像，不生成缩略图；前端限制类型与 5MB，后端 multipart 也限制 5MB（见 2.6） |
| 知识文章 / 情绪日记写操作 | ✅ 已于 2026-10-15 实现（见 2.3、2.4）：文章新增/编辑/上下架/删除、日记新增/删除 |
| 情绪日记字段精简 | ✅ 已于 2026-10-15 完成（见 2.4）：日记只剩「心情评分 + 今日感想」，`dominantEmotion`/`emotionTriggers`/`sleepQuality`/`stressLevel` 四列已从库表到前端全部删除 |
| 库表冗余（全库级） | 🟡 2026-10-15 整库盘点后已清理一批：DROP 废弃表 `ai_analysis_task`（14 行、11 索引、代码 0 引用）、`emotion_diary.ai_emotion_analysis`/`ai_analysis_updated_at`、`consultation_message.emotion_tag` → **9 张表精简为 8 张**、外键 8 → 7。**尚未清理**：零引用遗留表 `user_favorite`（1 行）、重复索引（`emotion_diary.idx_user_diary`＝`user_date_unique`、`user.idx_username`＝`UNIQUE username`）、空列/常量列（`knowledge_category.category_code`/`parent_id`、`consultation_message.message_type`、`knowledge_article.status`、`user.status`、`sys_file_info.file_type`/`expire_time`/`is_temp`/`status`）、`consultation_message.ai_model`（写死假值 `openai`）；`knowledge_article.read_count` 从不自增 → ✅ 2026-10-15 已修（见 2.3） |
| 数据库可重建性 | ✅ 2026-10-15 导出 `db/schema.sql`（`mysqldump --no-data`，8 张 `CREATE TABLE`，已纳入 git）。⚠️ 含 `DROP TABLE IF EXISTS`、**不含** `CREATE DATABASE`/`USE`；改完表结构要重新导出（见 `CLAUDE.md` 二） |
| 注册提权 | ✅ 2026-10-15 修复：注册请求体不再接收 `userType`，固定建普通用户（见 2.1） |
| 上传大小与异常兜底 | ✅ 2026-10-15：multipart 上限 1MB → 5MB、`MaxUploadSizeExceededException` 转统一响应体 `5004`、`GlobalExceptionHandler` 增补 `Exception` 兜底（见 2.6） |
| 用户列表与状态管理 | ✅ 2026-10-15 实现（见 2.1）：管理员可看用户列表（关键字/状态/类型筛选 + 分页）并禁用/启用账号；禁用立即生效（登录被拦 + 已签发 token 返回 `A0231`），管理员不能改自己的状态 |
| JWT 密钥 / 数据库口令入库 | ✅ 2026-10-15 修复：`application.yaml` 只留 `${JWT_SECRET:}`/`${DB_PASSWORD:}` 占位符，真实值在 gitignored 的 `application-local.yaml`，生产走环境变量；`config/JwtConfig.java` 启动时强制校验密钥 ≥ 32 位。⚠️ 旧密钥已随 git 历史泄露，**绝不复用**（见 `CLAUDE.md` 二） |
| 登录限流 | ✅ 2026-10-15：`service/LoginAttemptLimiter.java`（同账号连错 5 次锁 15 分钟，见 一.2）；按来源 IP 的 `limit_req` 模板见 `deploy/nginx.conf.example` |
| 上传类型白名单 | ✅ 2026-10-15：只收 `jpg/jpeg/png/gif/bmp/webp` + `mp4/webm`，`.html`/`.svg` 直接拒（见 2.6），前端 `accept` 同步 |
| 过滤器异常兜底 | ✅ 2026-10-15：token 验签失败 / 用户已被删除不再冒成 **HTTP 500**，统一 `A0230`（HTTP 401，见 一.2 与 `CLAUDE.md` 踩坑 15） |
| 部署文档与模板 | ✅ 2026-10-15：`docs/deploy.md` + `deploy/`（`mha.service`、`mha.env.example`、`nginx.conf.example`，含 SSE `proxy_buffering off`、登录 `limit_req`、`/files` 加固） |
| CORS | ⬜ 仍未配置，且**同域部署不需要**（前端 `baseURL` 是 `/api`、`fileBaseUrl` 是空串）；只有前后端分域部署时才要补 |
| 前端表单校验 | `ArticleDialog.vue` 的 `handleSubmit` 原先校验不通过也照发请求（已修成 `if (!valid) return`）；其余页面仍是「后端校验 + 拦截器弹 `msg`」 |

---

## 五、前端调用 vs 后端实现（对齐状态）

后端目前有七个 Controller（`Test`、`User`、`PsychologicalChat`、`Knowledge`、`EmotionDiary`、`DataAnalytics`、`File`），
共 **28 个接口**（另有 `/files/**` 静态资源映射，不计入接口数）。`frontend/src/api/` 里的调用**已全部有后端实现**，下表保留作为对照：

| 前端调用 | 调用位置 | 后端 |
|---|---|---|
| `POST /api/emotion-diary` | `api/frontend.js` → `views/emotionDiary.vue` | ✅ 已实现（见 2.4） |
| `GET /api/emotion-diary/page` | `api/frontend.js` → `views/emotionDiary.vue`（「我的记录」） | ✅ 已实现（见 2.4） |
| `DELETE /api/emotion-diary/{id}` | `api/frontend.js` → `views/emotionDiary.vue`（「我的记录」里删除） | ✅ 已实现（见 2.4） |
| `GET /api/emotion-diary/admin/page` | `api/admin.js` → `views/emotional.vue` | ✅ 已实现（见 2.4） |
| `DELETE /api/emotion-diary/admin/{id}` | `api/admin.js` → `views/emotional.vue` | ✅ 已实现（见 2.4） |
| `GET /api/knowledge/category/tree` | `api/admin.js` → `views/knowledge.vue` | ✅ 已实现（见 2.3） |
| `GET /api/knowledge/article/page` | `api/admin.js` / `api/frontend.js` | ✅ 已实现（见 2.3） |
| `GET /api/knowledge/article/{id}` | `api/admin.js` / `api/frontend.js` | ✅ 已实现（见 2.3） |
| `POST /api/knowledge/article` | `api/admin.js` → `components/ArticleDialog.vue` | ✅ 已实现（见 2.3） |
| `PUT /api/knowledge/article/{id}` | `api/admin.js` → `components/ArticleDialog.vue` | ✅ 已实现（见 2.3） |
| `PUT /api/knowledge/article/{id}/status` | `api/admin.js` → `views/knowledge.vue` | ✅ 已实现（见 2.3） |
| `DELETE /api/knowledge/article/{id}` | `api/admin.js` → `views/knowledge.vue` | ✅ 已实现（见 2.3） |
| `POST /api/file/upload` | `api/admin.js` → `components/ArticleDialog.vue` | ✅ 已实现（本地存储，见 2.6） |
| `POST /api/user/avatar` | `api/frontend.js` → `components/AvatarUploader.vue` | ✅ 已实现（见 2.1） |
| `GET /api/user/admin/page` | `api/admin.js` → `views/users.vue` | ✅ 已实现（见 2.1） |
| `PUT /api/user/admin/{id}/status` | `api/admin.js` → `views/users.vue` | ✅ 已实现（见 2.1） |
| `GET /api/data-analytics/overview` | `api/admin.js` → `views/dashboard.vue` | ✅ 已实现（见 2.5） |


> 到 2026-10-15 为止，**数据看板、心理知识（含新增/编辑/上下架/删除）、情绪日记（含用户端记录/我的记录/后台查看与删除）、文件上传、头像**都有后端实现，前端不会再出现 404。
> 仍未实现的是**能力**而非接口：会话状态流转、token 服务端失效、对话记忆的持久化仓库（重启回灌已做，见 2.2）。

---

## 六、变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-15 | 统一前后端接口：修正前端注册/登录会话的调用路径与字段名（`/user/add`→`/user/register`、`sessiontitle`→`sessionTitle`、`createAt`→`createdAt`）；错误提示统一读 `msg`；拦截器失败一律 `reject` 并覆盖 HTTP 401/403；后端补 `UserRegisterCommandDTO.phone` 允许空串、参数校验明细放进 `msg`。 |
| 2026-10-15 | 管理员后台 404 修复（第一批，只做读接口）：新增 `KnowledgeController`（`GET /api/knowledge/category/tree`、`GET /api/knowledge/article/page`、`GET /api/knowledge/article/{id}`）、`EmotionDiaryController`（`GET /api/emotion-diary/admin/page`，仅管理员）、`DataAnalyticsController`（`GET /api/data-analytics/overview`，仅管理员），并补 `KnowledgeCategory`/`KnowledgeArticle`/`EmotionDiary` 实体、三个 Mapper、`WebParamUtil`/`SecurityUserUtil` 工具类与四个响应 DTO（详见 2.3–2.5）。写接口与文件上传仍未实现。 |
| 2026-10-15 | **外部资源本地化**：新增 `FileController`（`POST /api/file/upload`，落盘 `backend/uploads/`）与 `WebMvcConfig`（`/files/**` → 本地上传目录），`SecurityConfig.PUBLIC_PATH` 放行 `/files/**`；`SysFileInfo` 实体 + `SysFileInfoMapper` + `FileUploadResponseDTO`；`application.yaml` 新增 `file.upload-dir`。前端 `fileBaseUrl` 改为空（同源）、`vite.config.js` 增加 `/files` 代理、兜底图与默认头像换成 `frontend/public/img/` 下的本地 SVG，删除对 `159.75.169.224:1235`、`file.itndedu.com`、`cube.elemecdn.com` 的全部引用（详见 2.6）。 |
| 2026-10-15 | **用户头像与本地上传**：落盘逻辑抽到 `FileStorageService`（`FileController` 与头像接口共用）；新增 `POST /api/user/avatar`（见 2.1）→ `UserService.updateAvatar`，换头像时清理上一个头像文件；前端新增 `stores/user.js`（pinia，响应式用户信息）、`components/AvatarUploader.vue`（无 DOM 的通用上传器）、`api/frontend.js#uploadAvatar`；`Navbar.vue` 与 `FrontendLayout.vue` 改为显示**真实用户名 + 头像**（未上传则本地默认图），下拉菜单加「更换头像」；`login.vue` 登录后同步写 store。库里的旧失效头像路径与 `sys_file_info` 旧记录已清空。 |
| 2026-10-15 | **情绪分析接入 AI（第②步）**：新增 `AiService/EmotionAnalysisService.java`（注入 `OpenAiChatModel` 自建**不带记忆 advisor** 的 `ChatClient`，取最近 20 条消息、每条截断 500 字符做结构化评估）与 `PromptManage.EMOTION_ANALYSIS_SYSTEM_PROMPT`（只输出严格 JSON，含 riskLevel 0-3 判定标准）；`ConsultationSessionService` 增 `saveEmotionAnalysis`（显式 set `last_emotion_analysis`/`last_emotion_updated_at`）；`PsychologicalChat` 的 `GET /session/{sessionId}/emotion` 改为 `emotionAnalysisService.analyzeOrGet(...)`——缓存覆盖最后一条消息则直接复用（实测 9ms），有新消息则重算（实测 6-7s）并落库；模型输出经 `extractJson`（去 ``` 围栏）+ 逐字段夹紧校验（score 0-100、riskLevel 0-3、`improvementSuggestions` 保证为数组且最多 5 条）；无消息/非法 JSON/调用异常一律退化为 `SessionEmotionDTO.defaultValue()` 且**不写缓存**（下次重试）。9 项端到端实测全部通过（首次分析、缓存命中不重复调 AI、新消息后重算、越权/不存在会话、无 token 401）。 |
| 2026-10-15 | **AI 对话记忆回灌（第④步）**：`PsychologicalSupportService` 新增 `@Slf4j` 与 `@Autowired ChatMemory chatMemory`（与 advisor 共用同一个 `MessageWindowChatMemory`），`streamPsychologicalChat` 在组装模型流前调用新的私有方法 `ensureMemoryLoaded(dbSessionId, conversationId, currentUserMessage)`——进程内已有记忆则跳过；否则取该会话最近 `MEMORY_WINDOW_MESSAGES`(30) 条历史，剔除「已落库的本次用户消息」、丢弃可能出现在首条的 AI 回复，再用 `AssistantMessage`/`UserMessage` 写回窗口（`SENDER_TYPE_AI=2`），失败只 `log.warn`。实测：重启前告知「小北/豆豆/攀岩」，重启后追问仍全部答对（3.1s），日志 `回灌对话记忆 sessionId=37 ... messages=6`。测试会话已删除。 |
| 2026-10-15 | **收尾（第⑤步）**：① **日期时间统一**为 `yyyy-MM-dd HH:mm:ss`——新增 `config/JacksonConfig.java`（Spring Boot 4 用 Jackson 3，扩展点是 `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`，注册 `SimpleModule` + `LocalDateTimeSerializer`；`spring.jackson.date-format` 对 `LocalDateTime` 无效），前端 `consultation.vue` 的乐观消息时间改用 `dayjs().format('YYYY-MM-DD HH:mm:ss')`（见 一.5）。② **临时文件清理任务**——新增 `config/SchedulingConfig.java`(`@EnableScheduling`) 与 `task/TempFileCleanupTask.java`（`file.temp-cleanup-cron`，默认每天 03:30；实测删除过期临时文件后库与磁盘均无残留，见 2.6）。③ **工作区根 git 仓库**——新增根 `.gitignore`（`backend/` 保持独立仓库，忽略 `node_modules/`、`dist/`、`.acl-recovery/`、`.db-backup/`、`.e2e/` 等）+ `git init -b main`，首次提交 61 个文件（`frontend/`、`docs/`、`CLAUDE.md`、`README.md`）。④ 删除前端死文件 `frontend/README.md`、`frontend/index.md`（0 字节）、`frontend/src/assets/vue.svg`（均无引用）。⑤ 品牌名统一为「心理健康AI助手」（`consultation.vue` 里残留的「宁渡AI助手」已改掉）。 |
| 2026-10-15 | **用户端可查看自己的情绪日记**：新增 `GET /api/emotion-diary/page`（`EmotionDiaryController.myPage`，`userId` 强制取登录态，复用 `EmotionDiaryService.adminPage` 的查询与 DTO）。前端 `api/frontend.js#getMyEmotionDiaries` + `views/emotionDiary.vue` 新增「我的记录」卡片：按日期倒序、评分 / 情绪标签 / 正文（超 3 行可展开）/ 触发因素与睡眠压力指标、分页，提交后自动回到第一页并刷新（共 **25 个接口**）。7 项实测通过：只返回本人记录（tester 1 条，与库里 `user_id=6` 一致）、`pageNum` 与 `currentPage` 命名都兼容、无 token → `HTTP 401` + `A0301`、`moodScreRange` 区间筛选生效、管理员后台接口仍是全部 18 条、普通用户访问后台接口被拒。 |
| 2026-10-15 | **用户可编辑/删除自己的日记**：新增 `DELETE /api/emotion-diary/{id}`（`EmotionDiaryService.deleteOwnDiary`，按「主键 + 登录用户」双条件校验，删不到别人的记录）；`GET /api/emotion-diary/page` 增加 `diaryDate` 参数以精确取某一天；`EmotionDiaryService` 把分页查询抽成共用的 `query(...)`，后台 `adminPage` 与用户端 `userPage` 各自调用。编辑复用 `POST /api/emotion-diary`（改哪一天就传哪一天）。前端 `emotionDiary.vue` 增加日期选择器（禁选未来）、按日期回填/提示、按钮在「保存记录 / 更新这篇记录」间切换、列表每条给「编辑 / 删除」（删除有二次确认，删空末页会回退页码）。7 项实测：删自己的 → 200 且记录消失；删别人的 → `日记不存在或无权访问` 且对方记录仍在；删不存在的 id → 同一句提示；无 token → 401；普通用户调后台删除仍被拒；管理员后台删除仍可用；测试记录已清理，全库回到 18 条日记（共 **26 个接口**）。 |
| 2026-10-15 | 全站 UI 统一（温和疗愈风）：新增 `frontend/src/styles/tokens.scss`（设计令牌 + Element Plus CSS 变量映射）与 `styles/index.scss`（全局样式与组件微调），26 个页面/组件改用令牌；同时修掉侧栏高亮写死、看板 KPI 类名不匹配、分页参数用 `current`、翻页丢筛选、首页 CTA 无跳转、情绪面板数字写死、4 个 `@keyframes` 缺失、用户/AI 消息无法区分、Enter 不发送等问题（见 `CLAUDE.md` 三.9）。 |
| 2026-10-15 | **情绪日记精简为「心情评分 + 今日感想」**：库表 `emotion_diary` 删除 `dominant_emotion`、`emotion_triggers`、`sleep_quality`、`stress_level` 四列（列数 13 → 9，迁移前 `mysqldump` 备份到 `.db-backup/emotion_diary_before_slim_20261015-172549.sql`，另存一份 CSV 到 `.tmp-schema/`）；同步删掉 `entity/EmotionDiary.java` 的四个字段、`DTO/command/EmotionDiaryCreateCommandDTO.java` 与 `DTO/response/EmotionDiaryAdminItemDTO.java` 的对应字段、`EmotionDiaryService.saveDiary`/`toDTO` 里的读写逻辑（`grep DominantEmotion\|EmotionTriggers\|SleepQuality\|StressLevel` 在 `backend/src` 已 0 命中，`mvn -B -q compile` 通过，`DataAnalyticsService` 只读 `moodScore`/`userId`/`diaryDate` 故看板不受影响）。前端 `views/emotionDiary.vue` 删掉整个「主要情绪」卡片（8 张情绪图 + `emotionOptions`/`selectEmotion`）与睡眠/压力下拉、情绪触发因素输入，表单只剩「心情评分」+「今日感想」；`views/emotional.vue` 删掉「生活指标」列、「情绪触发因素」列与详情弹窗的对应四项。实测：新增（`id=34`）→ 200 且响应体只含三个字段、我的记录与后台分页都能查到、`moodScore=42` 仍被拦（`心情评分范围是 1-10`）、看板 overview 200、库列数 9；测试记录已删除，库回到 19 行。 |
| 2026-10-15 | **咨询页精简：删掉「AI 助手信息框」与「情绪花园」**：`frontend/src/views/consultation.vue` 左栏只剩「会话列表」（`.session-history` 改为 `flex:1` + 最小高度 0，`.session-list` 自己滚动），右侧对话区不变（`.chat-header` 保留）；随之删除 `defaultEmotion`/`currentEmotion`/`resetEmotion`/`loadSessionEmotion`/`getIntensityClass`/`getRiskText` 与 `fetchEventSource` 的 `onclose` 收尾调用，前端**不再请求** `GET /api/psychological-chat/session/{sessionId}/emotion`（接口、缓存、落库逻辑与 `api/frontend.js#getSessionEmotion` 全部保留，见 2.2 说明；`riskLevel`/`riskDescription` 因此暂时没有界面出口）。 |
| 2026-10-15 | **删除已废弃的「日记 AI 分析」链路**：整表 DROP `ai_analysis_task`（14 行、11 索引、代码 0 引用）；`emotion_diary` 删 `ai_emotion_analysis`/`ai_analysis_updated_at`（列数 9 → 7，`idx_ai_analysis_time` 随列消失）；`consultation_message` 删 `emotion_tag`（列数 8 → 7，全 NULL、从未写入）。库 **9 → 8 张表**、外键 8 → 7。后端 7 处（`entity/EmotionDiary`、`DTO/response/EmotionDiaryAdminItemDTO`、`EmotionDiaryService` 含整个 `normalizeAiAnalysis`、`entity/ConsultationMessage`、`ConsultationMessageResponseDTO`、`ConsultationMessageService.saveUserMessage` 签名、`PsychologicalSupportService` 两处调用）与前端 `views/emotional.vue` 6 处（「AI情绪分析结果」弹窗区块、四个 `getAi*/getRisk*` 函数、`.ai-analysis-result` 样式）同步删净；`grep aiEmotionAnalysis\|emotionTag\|normalizeAiAnalysis` 在 `backend/src` 只剩一句 javadoc 说明，`mvn -B -q compile` 通过，Vite 产物无残留标识符。**保留**：`consultation_session.last_emotion_analysis`/`last_emotion_updated_at` 与会话情绪分析整套（见 2.2）。10 项端到端实测全过：登录、会话列表、消息列表（响应已无 `emotionTag`）、会话情绪分析仍可用（`低落`/`riskLevel=1`）、后台与用户端日记分页（响应已无 ai 字段）、新增+删除日记（库回 19 行）、看板 overview、库结构核对（表 8、各表行数不变、查已删表报 `1146`、查已删列报 `1054`）。改前全库备份 `.db-backup/mental_health_assistant_full_20261015-175210.sql`。 |
| 2026-10-15 | **缺陷审计后的第一批修复：提权 + 上传上限 + 异常兜底 + 阅读量自增 + 库结构入库**。① **注册提权**——改动前实测 `POST /api/user/register` 带 `"userType":2` 会真的建出管理员（响应 `"userType":2,"userTypeDisplayName":"管理员"`，该账号可调 `GET /api/data-analytics/overview`）；修复：`DTO/command/UserRegisterCommandDTO.java` 删掉 `userType` 字段、`service/convert/UserConvert.java` 固定 `.userType(UserType.USER.getCode())`，实测响应恒为 `userType=1`、该账号调管理接口得 `无权访问，该接口仅管理员可用`。② **上传上限 1MB → 5MB + 异常兜底**——`application.yaml` 增加 `spring.servlet.multipart.max-file-size: 5MB` / `max-request-size: 10MB`；`common/GlobalExceptionHandler.java` 新增 `MaxUploadSizeExceededException`（→ `{"code":"5004","msg":"上传的文件过大，单个文件不能超过 5MB"}`，HTTP 200）与 `Exception` 兜底（→ `{"code":"500","msg":"服务器开小差了，请稍后再试"}` + `log.error`）。实测：2MB 上传由「HTTP 413 + 空响应体」变为 `code=200`；6MB 上传返回统一体 `5004` 且库里不留 `sys_file_info` 记录。③ **`read_count` 自增**（规则见 2.3）：实测普通用户读 → 15→16 且响应 `readCount=16`，管理员读 → 保持 16。④ **`db/schema.sql` 纳入仓库**（见第四节）。⑤ 顺带修掉三处隐患：`UserService.register` 里把**含明文密码**的注册体打到控制台的 `System.out.println(JSONUtil.parseObj(commandDTO))`、`login` 的 `WHERE username=? OR password=?` 误写（改为只按 `username` 查，与 `UserDetailsService` 一致）、`frontend/src/views/register.vue` 不再提交 `userType`。`mvn -B -q compile` 通过；6 组端到端实测通过（提权被拦、2MB 通过、6MB 统一报错、阅读量计数、登录的 正确/密码错/用户不存在 三种情形、清理后库回到 5 用户 / 10 文件 / 8 文章）。 |
| 2026-10-15 | **修复「后台咨询记录点详情报无权访问」**：`GET /api/psychological-chat/sessions/{sessionId}/messages` 原本无条件走归属校验（会话 `user_id` 必须等于登录用户），而后台「咨询记录 → 详情」调的就是这个接口 → 管理员点开用户会话得到 `会话不存在或无权访问`。新增 `PsychologicalChat.requireVisibleSessionId`（管理员只校验会话存在，不存在时返回 `会话不存在`；普通用户仍走原归属校验，且「不存在」与「非本人」返回同一句提示，不泄露存在性）与 `ConsultationSessionService.getSessionById`（不校验归属，仅供已判权限的调用方使用，见第三节）。删除会话与情绪分析**仍严格校验归属**。实测：管理员读他人会话 17（`user_id=2`）→ `code=200` + 消息数组；tester 读同一会话 → `会话不存在或无权访问`；管理员读 `999999` → `会话不存在`；tester 读自己的 35 → `code=200`（回归）；管理员传前缀格式 `session_17` → `code=200`；无 token → `A0301`；管理员删除他人会话仍被拒。 |
| 2026-10-15 | **新增「用户管理」：管理员可查看用户列表并禁用/启用账号**。后端 `controller/User.java` 新增 `GET /api/user/admin/page`（`keyword` 模糊匹配用户名/昵称/邮箱，`status`/`userType` 筛选 + 分页，参数同时兼容 `currentPage/size` 与 `pageNum/pageSize`）与 `PUT /api/user/admin/{id}/status`（请求体 `{"status":0\|1}`），两者都先过 `SecurityUserUtil.requireAdmin()`；`service/UserService.java` 新增 `pageUsers(...)`（返回体复用 `UserDetailResponseDTO`，**不含密码字段**，按 id 倒序）与 `updateStatus(...)`（用 `UserStatus.fromCode` 校验取值、**禁止管理员修改自己的状态**）；新增 `DTO/command/UserStatusUpdateCommandDTO.java`。前端新增 `views/users.vue`（筛选栏 + 表格 + 分页 + 禁用/启用二次确认；当前账号的「禁用」按钮置灰）与路由 `/back/users`（侧栏菜单由路由表自动生成，无需改 `Sidebar.vue`），`api/admin.js` 增 `getUserPage` / `updateUserStatus`。**「禁用立即生效」没有新增逻辑**，靠已有两处：`UserService.login` 的 `isActive()` 判断（`{"code":"BUSINESS_ERROR","msg":"用户被禁用，请联系管理员"}`）与 `JwtAuthenticationFilter` 每请求校验 `status == 1`（旧 token → `{"code":"A0231","msg":"token已被禁止访问"}`）。14 组端到端实测全过：列表（`total=5`、首行 16 个字段、无 `password`）、关键字/状态/类型三种筛选、普通用户调用被拒（`无权访问，该接口仅管理员可用`）、注册探针账号 → 管理员禁用（库中 `status=0`）→ 探针**旧 token 立即失效**且重新登录被拦 → 启用后恢复正常登录、改自己状态被拒（`不能修改自己的账号状态`）、非法状态（`不支持的用户状态：5`）、`status` 缺失（`{"code":"400","msg":"状态不能为空"}`）、不存在的用户（`用户不存在`）、tester 越权改 admin 状态被拒；清理后库回到 5 个用户、`total=5`。 |
| 2026-10-15 | **安全三项修复：密钥/口令出库 + 登录限流 + 上传白名单**。① **凭据不再入库**：`application.yaml` 的 `spring.datasource.password` 与 `jwt.secret` 改为 `${DB_PASSWORD:}` / `${JWT_SECRET:}` 占位符（`url`/`username` 也支持 `DB_HOST`/`DB_PORT`/`DB_USERNAME` 覆盖），真实值放 gitignored 的 `application-local.yaml`，生产走环境变量（优先级：环境变量 > `application-local.yaml` > `application.yaml`）；新增 `config/JwtConfig.java` 的 `@PostConstruct` 校验 —— secret 为空或 < 32 位直接**启动失败**（实测 `JWT_SECRET=tooshort` 稳定复现 `IllegalStateException: jwt.secret 未配置或长度不足 32 位`）。改动前实测：用旧 secret（已随 git 历史泄露）手搓 HS256 token 调 `GET /api/data-analytics/overview` 返回 `code=200` 真实数据。② **登录失败限流**：新增 `service/LoginAttemptLimiter.java`，同一「用户名 + 客户端 IP」连错 5 次锁 15 分钟（键里的 IP 只取 `remoteAddr`、**故意不信 `X-Forwarded-For`**），登录成功清零、「用户被禁用」不计失败；按 IP 的 `limit_req_zone` 模板见 `deploy/nginx.conf.example`。③ **上传扩展名白名单**：`FileStorageService` 只收 `jpg/jpeg/png/gif/bmp/webp` + `mp4/webm`，`.html`/`.svg`/无扩展名一律 `6000`（实测改动前上传 `.html` 可被**匿名** GET 成 `Content-Type: text/html` 直出 `<script>`，同源脚本能读走 `localStorage` 里的 JWT）；前端 `AvatarUploader.vue`/`ArticleDialog.vue` 的 `accept` 与提示同步。④ **顺带修掉两处 HTTP 500**：`util/JwtAuthenticationFilter.java` 原来把 `validateToken` 与 `getUserById` 放在 `try/catch` 之外 → 验签失败（含 secret 轮换后的**全部**旧 token）或「token 合法但用户已被删除」都会冒成 **HTTP 500 + Spring 默认错误体**；现在「验签 → 查用户 → 写认证」整段包在同一个 `try/catch` 里，异常统一回落 `A0230`（HTTP 401），并删掉每请求 `System.out.println(requestURL/method)` 的日志噪声。实测 7 种 token 情形（旧 secret / 乱码 / 已过期 / issuer 不符 / 用户已删除 / 无 token / 新 secret 正常签发）全部返回预期状态码。⑤ 新增 `deploy/`（`mha.service`、`mha.env.example`、`nginx.conf.example`）与 `docs/deploy.md`（部署规格、systemd、Nginx 与 SSE、HTTPS、安全清单、备份、自检）。 |

> 本批共 28 项接口级实测（含管理员/普通用户权限、参数校验、只读字段不被旧值回退、同日重复提交覆盖更新、以及读接口回归）全部通过；测试用过的临时文章与日记已删除，库回到 7 篇文章 / 17 条日记。


