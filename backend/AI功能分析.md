# AI 心理疏导功能 · 模块分析

> 分析对象：本次新增的 AI 对话相关代码（Spring AI 集成）
> 生成时间：2026-10-14

---

## 一、模块概览

本模块在原有的「用户 + 认证」基础上，新增了**AI 心理疏导对话**能力，核心目标：

1. 用户登录后创建咨询会话
2. 与 AI 进行**流式（SSE）**多轮对话
3. 对话内容持久化到数据库
4. 通过系统提示词约束 AI 扮演专业心理疏导角色

技术选型：**Spring AI 2.1.0-M1** + OpenAI 兼容协议（实际接入 DeepSeek）。

---

## 二、组件清单

### 新增文件（14 个）

| 分层 | 文件 | 职责 |
|---|---|---|
| 配置 | `config/ChatClientConfig.java` | 装配 `ChatClient` 与 `ChatMemory` |
| AI 服务 | `AiService/PsychologicalSupportService.java` | **核心**：会话创建 + 流式对话编排 |
| AI 服务 | `AiService/PromptManage.java` | 系统提示词常量 |
| AI 服务 | `AiService/StructOutPut.java` | 会话返回结构（record） |
| 接口 | `controller/PsychologicalChat.java` | `/session/start`、`/stream` |
| 业务 | `service/ConsultationSessionService.java` | 会话 CRUD |
| 业务 | `service/ConsultationMessageService.java` | 消息 CRUD |
| 实体 | `entity/ConsultationSession.java` | `consultation_session` 表 |
| 实体 | `entity/ConsultationMessage.java` | `consultation_message` 表 |
| Mapper | `mapper/ConsultationSessionMapper.java` | MyBatis-Plus Mapper |
| Mapper | `mapper/ConsultationMessageMapper.java` | MyBatis-Plus Mapper |
| DTO | `DTO/command/ConsultationSessionCreateDTO.java` | 创建会话入参 |
| DTO | `DTO/command/ConsultationstreamDTO.java` | 对话入参 |
| DTO | `DTO/response/ConsultationMessageResponseDTO.java` | 消息出参 |

### 依赖变更

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
    <version>2.1.0-M1</version>
</dependency>
```

该 starter 会传递引入 `spring-ai-client-chat`、`reactor-core`、`openai-java-core`、`spring-webflux` 等，为流式输出提供响应式底座。

---

## 三、核心调用链路

### 3.1 创建会话

```
POST /api/session/start
  └─> PsychologicalChat.startSession()
       ├─ JwtTokenUtil.getCurrentUserId()          // 从 SecurityContext 取当前用户
       └─> PsychologicalSupportService.startSession(userId, dto)
            ├─ ConsultationSessionService.createSession(userId, dto)
            │    ├─ userMapper.selectById(userId)      // 校验用户存在
            │    └─ consultationSessionMapper.insert() // 落库会话记录
            ├─ ConsultationMessageService.saveUserMessage() // 保存首条用户消息
            └─ 返回 StructOutPut.StreamChatSession(sessionId = "session_" + id, ...)
```

### 3.2 流式对话

```
POST /api/stream  (produces = text/event-stream)
  └─> PsychologicalChat.streamChat()
       ├─ JwtTokenUtil.getCurrentUserId();  userId == null 时返回 error 事件
       └─> PsychologicalSupportService.streamPsychologicalChat(sessionId, userMessage)
            └─ Flux.create(sink -> {                          // 手动创建响应流
                 ├─ extractSessionId("session_1") -> 1L       // 解析业务 ID
                 ├─ 幂等判断：若为初始消息则跳过重复保存
                 ├─ ConsultationMessageService.saveUserMessage()   // 用户消息落库
                 ├─ chatMemory.add(conversationId, [UserMessage])  // 写入记忆
                 ├─ chatClient.prompt(prompt)
                 │     .user(userMessage)
                 │     .advisors(spec -> spec.param(CONVERSATION_ID, conversationId))
                 │     .stream().content()                    // 返回 Flux<String>
                 │     ├─ doOnNext: 累积全文 + sink.next(片段)
                 │     ├─ doOnComplete: 保存 AI 回复落库 + chatMemory.add(AssistantMessage) + sink.complete()
                 │     └─ doOnError: sink.error()
               })
       └─> Controller 层包装为 Flux<ServerSentEvent<String>>
            ├─ 每个片段 -> event: message, data: {code,data:{content,Type:"normal"}}
            ├─ concatWith -> event: done, data: {}
            └─ delayElements(50ms)  // 人为延时，改善前端呈现节奏
```

---

## 四、关键技术实现

### 4.1 ChatClient 装配（`ChatClientConfig`）

```java
@Bean("open-ai")
public ChatClient chatClient(OpenAiChatModel openAiChatModel){
    return ChatClient.builder(openAiChatModel)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
            .defaultSystem("你是一个专业的心理疏导师")
            .build();
}
```

- **具名 Bean** `"open-ai"`：便于后续接入多个模型时按名注入（服务层用 `@Qualifier("open-ai")` 引用）
- **`MessageChatMemoryAdvisor`**：把对话记忆挂到 Advisor 链上，每次请求自动携带历史上下文
- **`MessageWindowChatMemory.maxMessages(30)`**：滑动窗口策略，只保留最近 30 条消息，防止 Token 无限增长

### 4.2 SSE 流式输出

三段式实现：

| 层次 | 做法 |
|---|---|
| 模型层 | `chatClient.prompt(...).stream().content()` → `Flux<String>` |
| 服务层 | `Flux.create(sink -> ...)` 手动控制流生命周期，边收边转发 |
| 接口层 | 包装为 `Flux<ServerSentEvent<String>>`，遵循 SSE 规范 |

**事件协议**（前端对接约定）：

```
event: message
data: {"code":"200","data":{"content":"<片段>","Type":"normal"}}

event: done
data: {}
```

出现错误时返回 `event: error`，data 为统一响应体格式的错误信息。

### 4.3 提示词工程（`PromptManage`）

系统提示词是一段结构化的「角色设定」，包含四个部分：

1. **角色定位** —— 专业、温暖、有同理心的 AI 心理健康助手，面向大学生群体
2. **角色特点** —— 温暖友善 / 专业不冷漠 / 善于倾听 / 鼓励积极思考
3. **对话原则** —— 先共情 → 梳理情绪 → 温和建议 → 必要时引导求助 → 强调用户价值
4. **特殊处理** —— **检测到自杀倾向时优先表达关心并引导求助**、严重问题建议联系学校心理咨询中心
5. **输出约束** —— 简体中文、长度适中、结合大学生生活场景

> 其中「危机干预兜底」是心理健康类应用必须具备的安全设计。

### 4.4 双层存储设计

| 存储 | 载体 | 作用 | 生命周期 |
|---|---|---|---|
| 长期 | `consultation_message` 表 | 完整对话记录、可回溯 | 永久 |
| 短期 | `ChatMemory`（内存） | 提供多轮对话上下文 | 应用存活期 |

两者通过 `conversationId = "conversation_" + sessionId` 关联，实现「**数据库存底、内存供上下文**」的分工。

---

## 五、设计亮点

1. **响应式流式输出**：用 `Flux` + SSE 实现「打字机」效果，而非等模型生成完再一次性返回，显著改善等待体验
2. **对话记忆与持久化解耦**：ChatMemory 负责上下文、MySQL 负责归档，各司其职
3. **提示词外置**：`PromptManage` 统一管理提示词，便于调整与维护
4. **幂等设计**：通过「消息数 == 1 且与首条内容一致」判断是否为重复提交的初始消息，避免重复落库
5. **用户身份从认证上下文获取**：`JwtTokenUtil.getCurrentUserId()` 而非从请求参数取，避免越权

---

## 六、潜在问题与改进建议

> 以下为代码审查中发现的问题，按严重程度排序，**尚未修改**。

### 🔴 高

**1. 会话归属未校验（越权风险）**

`streamPsychologicalChat` 只从 `sessionId` 解析出数据库 ID，**没有校验该会话是否属于当前登录用户**。攻击者拿到（或猜到）他人的 `session_1`、`session_2`，即可读写他人的对话记录。

建议：进入对话前，用 `sessionId + userId` 联合查询校验归属。

**2. `createSession` 返回 null 未处理**

`ConsultationSessionService.createSession()` 在用户不存在时返回 `null`，而 `PsychologicalSupportService.startSession()` 未做判空，紧接着调用 `session.getId()` → **NPE**。建议抛出明确的业务异常。

### 🟡 中

**3. 系统提示词存在两份，语义不清**

`ChatClientConfig` 里设置了 `defaultSystem("你是一个专业的心理疏导师")`，而 `streamPsychologicalChat` 中又构造了 `new Prompt(List.of(new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)))`。两者会**叠加或其中之一被覆盖**，最终生效的是哪一个不明确。建议统一为一处。

**4. 用户消息可能重复进入上下文**

同一条 `userMessage` 被添加了**两次**：
- `chatMemory.add(conversationId, [new UserMessage(userMessage)])` —— 手动加入记忆
- `.user(userMessage)` —— 又作为本次请求的输入

而 `MessageChatMemoryAdvisor` 本身也会从 ChatMemory 读取历史。三者叠加可能导致上下文里出现重复的用户消息，既浪费 Token 也可能干扰模型判断。

**5. `chatMemory` 为进程内内存**

应用重启即丢失上下文；多实例部署时无法共享。若需跨重启保留，建议改用持久化的 `ChatMemory` 实现（如基于 JDBC/Redis）。

**6. `startSession` 未校验 `initialMessage` 为空**

`createDTO.getInitialMessage()` 直接落库，若为 `null` 会写入空消息。`streamChat` 侧做了 `userId` 判空，`startSession` 侧没有同等保护。

### 🟢 低

**7. 命名不一致**：`StructOutPut.StreamChatSession` 中字段名为 `userHash`，实际传入的是 `userId`。

**8. 类名拼写**：`StructOutPut` 建议改为 `StructOutput`。

**9. 会话标题重复拼接**：`String.format("心理健康AI助手" + DateUtil.format(...))` —— 用 `String.format` 但格式串里没有占位符，虽然结果正确，但写法不当，直接用字符串拼接即可。

**10. 流式延时硬编码**：`delayElements(Duration.ofMillis(50))` 写死在 Controller，建议提取为配置项。

**11. 调试日志**：`PsychologicalChat` 及各服务中残留多处 `System.out.println`，建议改用 SLF4J 并降为 debug 级别。

---

## 七、改进优先级建议

| 优先级 | 事项 | 理由 |
|---|---|---|
| P0 | 会话归属校验 | 数据安全，属于功能缺陷 |
| P0 | `createSession` 判空 | 会直接抛 NPE，影响可用性 |
| P1 | 统一系统提示词 | 影响 AI 输出质量，且难以排查 |
| P1 | 排查用户消息重复入上下文 | 直接影响回答质量与 Token 成本 |
| P2 | ChatMemory 持久化 | 影响多轮对话体验的连续性 |
| P3 | 代码规范类（命名、日志、硬编码） | 可维护性 |
