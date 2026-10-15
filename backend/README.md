# 心理健康助手 · AI Mental Health Assistant

基于 **Spring Boot 4 + Spring AI** 的心理健康服务后端，提供用户认证与 AI 心理疏导对话能力。

用户可通过 JWT 完成注册登录，创建咨询会话并与 AI 进行流式（SSE）心理疏导对话，对话记录持久化到数据库。

---

## ✨ 功能特性

- **用户模块** —— 注册 / 登录，用户名与邮箱唯一性校验，密码 BCrypt 加盐加密存储
- **JWT 无状态认证** —— 基于 Spring Security 自定义 `OncePerRequestFilter` 实现 Token 的签发、校验与安全上下文装配，支持公开路径白名单
- **AI 心理疏导对话** —— 集成 Spring AI 接入大模型，支持 SSE 流式输出
- **对话记忆** —— 基于 `ChatMemory` 维护多轮上下文（保留最近 30 条消息）
- **会话与消息持久化** —— 咨询会话、对话消息落库
- **统一响应与全局异常处理** —— `Result<T>` 统一响应体 + `@RestControllerAdvice`，过滤器层异常单独封装保证返回格式统一

## 🛠 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Java 17 |
| 核心框架 | Spring Boot 4.0.8 · Spring Security 7 · Spring AI 2.1.0 |
| 持久层 | MyBatis-Plus 3.5.17 · MySQL 8 |
| 认证安全 | JWT（Auth0 java-jwt）· BCrypt |
| 工具库 | Hutool · Lombok |
| 构建 | Maven |

## 📁 项目结构

```
src/main/java/com/rmh/aispringboot/
├── AiService/       # AI 服务层（提示词管理、心理疏导服务、结构化输出）
├── common/          # 统一响应体、状态码枚举、全局异常处理
├── config/          # Security / JWT / ChatClient 配置
├── controller/      # 接口层
├── DTO/             # command（入参）/ response（出参）
├── entity/          # 数据库实体
├── enumClass/       # 枚举（用户类型、用户状态）
├── exception/       # 自定义业务异常
├── mapper/          # MyBatis-Plus Mapper
├── service/         # 业务层（含 convert 对象转换）
└── util/            # JWT 工具、认证过滤器、响应工具
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- MySQL 8+
- Maven 3.9+

### 1. 初始化数据库

```sql
CREATE DATABASE mental_health_assistant DEFAULT CHARACTER SET utf8mb4;
```

建表语句见下方「数据库表结构」章节。

### 2. 配置敏感信息

项目采用**配置分离**：基础配置在 `application.yaml`，**敏感信息（AI API Key）放在 `application-local.yaml`** —— 该文件已被 `.gitignore` 忽略，不会提交到仓库。

克隆项目后，在 `src/main/resources/` 下新建 `application-local.yaml`：

```yaml
spring:
  ai:
    openai:
      api-key: <你的 API Key>
```

`application.yaml` 中已配好模型与接口地址，使用 OpenAI 兼容协议：

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          model: deepseek-flash
      base-url: https://api.deepseek.com
```

如需更换模型服务商，修改 `model` 与 `base-url` 即可。

### 3. 配置数据库连接

修改 `application.yaml` 中的 `spring.datasource`，填入你自己的数据库地址、账号与密码。

### 4. 启动

```bash
mvn spring-boot:run
```

服务默认运行在 `http://localhost:1236`。

## 📡 接口文档

### 认证说明

除公开接口外，其余接口均需在请求头携带 JWT：

```
token: <登录接口返回的 token>
```

### 用户模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/user/register` | 用户注册 | 否 |
| POST | `/api/user/login` | 用户登录，返回 JWT | 否 |
| GET | `/api/user/current` | 获取当前用户信息 🚧 | 是 |

**注册**

```json
POST /api/user/register
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "123456",
  "confirmPassword": "123456"
}
```

**登录**

```json
POST /api/user/login
{
  "username": "testuser",
  "password": "123456"
}
```

### 心理疏导对话

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/session/start` | 创建咨询会话 | 是 |
| POST | `/api/stream` | 流式对话（SSE） | 是 |

**创建会话**

```json
POST /api/session/start
{
  "initialMessage": "我最近压力很大，晚上总是睡不着"
}
```

返回会话 ID（形如 `session_1`），后续对话需带上它。

**流式对话**

```
POST /api/stream
Content-Type: application/json

{
  "sessionId": "session_1",
  "userMessage": "我该怎么缓解这种压力？"
}
```

响应为 `text/event-stream`，逐段推送内容：

```
event: message
data: {"code":"200","data":{"content":"我能理解","Type":"normal"}}

event: done
data: {}
```

## 🗄 数据库表结构

```sql
-- 咨询会话表
CREATE TABLE consultation_session (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user_id                 BIGINT       NOT NULL COMMENT '用户ID',
    session_title           VARCHAR(200)          COMMENT '会话标题',
    started_at              DATETIME              COMMENT '开始时间',
    last_emotion_analysis   TEXT                  COMMENT '最后一次情绪分析结果(JSON)',
    last_emotion_updated_at DATETIME              COMMENT '情绪分析更新时间'
) COMMENT '咨询会话表';

-- 咨询消息表
CREATE TABLE consultation_message (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    session_id   BIGINT      NOT NULL COMMENT '会话ID',
    sender_type  TINYINT     NOT NULL COMMENT '发送者类型 1:用户 2:AI助手',
    message_type TINYINT     NOT NULL COMMENT '消息类型 1:文本',
    content      TEXT        NOT NULL COMMENT '消息内容',
    emotion_tag  VARCHAR(50)          COMMENT '情绪标签',
    ai_model     VARCHAR(50)          COMMENT '使用的AI模型',
    created_at   DATETIME             COMMENT '创建时间'
) COMMENT '咨询消息表';
```

> `user` 表字段参见 `entity/User.java`。

## 🗺 后续计划

- [ ] 完成「获取当前用户信息」接口
- [ ] 情绪分析与标签自动生成
- [ ] 会话历史列表与详情查询
- [ ] 前端页面
- [ ] 部署与容器化

---

> 本项目为个人学习实践项目，用于演示 Spring Boot 4 + Spring AI 的工程实践。
