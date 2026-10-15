# 心理健康助手 · AI Mental Health Assistant

前后端分离的心理健康服务平台：用户可注册登录，与 AI 进行心理疏导对话（流式），并查看/管理历史咨询记录。

---

## 目录结构

```
mental_health_assistant/
├── backend/                  Spring Boot 后端
│   ├── src/main/java/com/rmh/aispringboot/
│   ├── src/main/resources/
│   │   ├── application.yaml          # 基础配置（已提交）
│   │   └── application-local.yaml    # 敏感配置（已 gitignore，含 AI API Key）
│   └── pom.xml
├── frontend/                 Vue 3 前端
│   ├── src/
│   └── vite.config.js
├── docs/
│   └── api-contract.md       # 前后端接口契约（改接口前必读）
├── CLAUDE.md                 # 给 AI 助手的项目说明
└── README.md
```

---

## 技术栈

### 后端

| 分类 | 技术 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 4.0.8、Spring Security 7、Spring AI 2.1.0 |
| 持久层 | MyBatis-Plus 3.5.17、MySQL 8 |
| 认证 | JWT（Auth0 java-jwt）、BCrypt |
| 其他 | Hutool、Lombok |
| 构建 | Maven |

### 前端

| 分类 | 技术 |
|---|---|
| 框架 | Vue 3 + Vite |
| UI | Element Plus |
| 请求 | axios（统一响应拦截）+ `@microsoft/fetch-event-source`（SSE） |
| 路由 | Vue Router（按 userType 区分前台/后台） |

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8+
- Node.js 18+

### 1. 初始化数据库

```sql
CREATE DATABASE mental_health_assistant DEFAULT CHARACTER SET utf8mb4;
```

需要三张表：`user`、`consultation_session`、`consultation_message`。

### 2. 配置后端敏感信息

`application-local.yaml` 已被 `.gitignore` 忽略，需要**手动创建**：

`backend/src/main/resources/application-local.yaml`

```yaml
spring:
  ai:
    openai:
      api-key: <你的 API Key>
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务运行在 **http://localhost:1236**

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 **http://localhost:5173**

> 前端 `/api` 请求通过 Vite 代理转发到 `http://localhost:1236`（见 `frontend/vite.config.js`）。

### 5. 测试账号

| 账号 | 密码 | 类型 | 入口 |
|---|---|---|---|
| `tester` | `123456` | 普通用户（userType=1） | 前台「心理咨询」 |
| `admin` | `123456` | 管理员（userType=2） | 后台「咨询记录」 |

> ⚠️ 前端路由守卫按 `userType` 强制分流：**管理员永远进不了前台页面**，反之亦然。

---

## 文档

- **[接口契约](docs/api-contract.md)** —— 前后端接口清单与约定
- **[CLAUDE.md](CLAUDE.md)** —— 给 AI 助手的项目说明（构建命令、关键约定、已知问题）
