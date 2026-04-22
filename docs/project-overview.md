# 项目总览

## 1. 项目定位

`TimePaper Agent` 是一个前后端分离的原型项目，目标是把自然语言输入同时映射到两个轻量个人效率场景：

- 聊天式交互
- 记账记录
- 日程安排

当前形态更接近“可运行原型”而不是完整产品：

- Android 端负责 UI、配置、本地持久化和动作落地
- Python 后端负责调用大模型并把自然语言解析成结构化动作
- 真正的数据源目前在客户端本地 `DataStore`，后端不保存用户数据

## 2. 仓库结构

```text
.
|-- app/                    Android 应用（Jetpack Compose）
|   `-- src/main/java/com/example/myapplication/
|       |-- agent/         状态管理、数据模型、网络层、本地存储
|       `-- ui/            导航、页面、主题、设计组件
|-- backend/               FastAPI + LangChain 后端
|   |-- agent_backend/
|   |   |-- api.py         FastAPI 应用与路由
|   |   |-- orchestrator.py 动作编排与响应整理
|   |   |-- llm_client.py  模型调用与工具定义
|   |   `-- schemas.py     Pydantic 数据模型
|   |-- main.py            后端入口
|   `-- requirements.txt   Python 依赖
|-- docs/                  项目文档
|   |-- project-overview.md
|   `-- schedule-ui-optimization.md
|-- gradle/                Gradle Wrapper 与版本目录
|-- build.gradle.kts       根构建脚本
`-- settings.gradle.kts    模块声明（当前仅 app）
```

## 3. Android 端结构

### 3.1 入口与导航

- [MainActivity.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/MainActivity.kt) 负责设置 Compose 根节点。
- [AgentApp.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/ui/AgentApp.kt) 是应用壳层，包含底部导航和页面路由。
- 当前页面包含：
  - `agent`：聊天页
  - `schedule`：日程页
  - `ledger`：记账页
  - `settings`：设置页

### 3.2 状态与数据流

- [AppViewModel.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/agent/AppViewModel.kt) 是前端单一业务入口。
- `chatMessages`、`scheduleItems`、`ledgerEntries`、`modelSettings` 都从本地 `DataStore` 读取并转成 `StateFlow`。
- 用户在聊天页提交文本后：
  1. 先把用户消息写入本地聊天记录。
  2. 再携带聊天历史和模型配置请求后端。
  3. 后端返回 `reply + actions`。
  4. 前端按动作写入本地日程或账单。
  5. UI 依赖 `StateFlow` 自动刷新。

### 3.3 本地存储

- [LocalStore.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/agent/data/LocalStore.kt) 使用 `Preferences DataStore`。
- 数据以 JSON 字符串形式整体存储，主要 key 包括：
  - `ledger_entries`
  - `schedule_items`
  - `chat_messages`
  - `model_settings`
- API Key 已改为单独存放在加密本地存储中，不再写入普通 `DataStore`。
- 优点是实现简单、便于原型快速迭代。
- 代价是：
  - 不适合大量数据
  - 缺少结构化查询
  - 聊天、账单、日程仍然是本地 JSON 存储

### 3.4 网络与模型设置

- [NetworkModule.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/agent/net/NetworkModule.kt) 通过 Retrofit + Gson 发请求。
- 默认后端地址是 `http://10.0.2.2:8000/`，并会把 `localhost/127.0.0.1` 自动替换成模拟器可访问的 `10.0.2.2`。
- [SettingsScreen.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/ui/screen/SettingsScreen.kt) 允许用户在客户端设置：
  - 后端地址
  - 模型 Base URL
  - 模型名
  - API Key
- 这些设置会随请求一起通过 `model_config` 发给后端，所以前后端都支持“运行时切模型”。
- Android 网络日志已调整为：
  - Debug 只记录基础请求信息，不打印请求体
  - Release 关闭网络日志

### 3.5 页面职责

- [AgentHomeScreen.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/ui/screen/AgentHomeScreen.kt)
  - 聊天气泡列表
  - 输入框与发送按钮
  - 长按复制/删除消息
  - 加载态“处理中”指示
- [ScheduleScreen.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/ui/screen/ScheduleScreen.kt)
  - 日历视图与全部日程双视图
  - “今天 / 明天 / 7天后”快捷筛选
  - 未来优先 / 全部 / 历史筛选
  - 编辑、删除日程
- [LedgerScreen.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/ui/screen/LedgerScreen.kt)
  - 按日 / 月 / 年统计
  - 结余、收入、支出汇总
  - 分类占比饼图
  - 趋势柱状图
  - 编辑、删除账单

## 4. 后端结构

### 4.1 API 层

- [backend/main.py](/D:/wby/MyApplication/backend/main.py) 只负责导出应用实例。
- [backend/agent_backend/api.py](/D:/wby/MyApplication/backend/agent_backend/api.py) 创建 FastAPI 应用并暴露：
  - `GET /health`
  - `POST /agent/process`
- 当前 CORS 是全开放配置，方便原型联调。

### 4.2 编排层

- [backend/agent_backend/orchestrator.py](/D:/wby/MyApplication/backend/agent_backend/orchestrator.py) 负责：
  - 调用 `LLMClient.parse`
  - 过滤非法动作
  - 兜底生成回复文案
- 对前端真正开放的动作只有两类：
  - `add_schedule`
  - `add_ledger`

### 4.3 模型与工具层

- [backend/agent_backend/llm_client.py](/D:/wby/MyApplication/backend/agent_backend/llm_client.py) 使用：
  - `langchain`
  - `langchain-openai`
  - `ChatOpenAI`
  - `StructuredTool`
- 工具定义有三类：
  - `add_schedule`
  - `add_schedule_series`
  - `add_ledger`
- 其中 `add_schedule_series` 只在后端内部展开为多条 `add_schedule`，最终前端仍然只接收单条日程动作。

### 4.4 请求处理链路

1. Android 发送 `text + history + model_config` 到 `/agent/process`。
2. FastAPI 把 `model_config` 映射为 `runtime_config`。
3. `LLMClient` 优先使用请求内配置，否则回退到环境变量。
4. LangChain Agent 根据系统提示选择是否调用工具。
5. 工具把结果暂存在 `_runtime_actions`。
6. `orchestrator` 过滤动作并返回给客户端。
7. Android 客户端把动作落到本地 `DataStore`。

## 5. 关键数据模型

Android 侧 [DomainModels.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/agent/model/DomainModels.kt) 与后端 [schemas.py](/D:/wby/MyApplication/backend/agent_backend/schemas.py) 基本对齐：

- `AgentRequest`
  - `text`
  - `history`
  - `model_config`
- `AgentResponse`
  - `reply`
  - `actions`
- `AgentAction`
  - `type`
  - `payload`
- 客户端落地实体：
  - `ScheduleItem`
  - `LedgerEntry`
  - `ChatMessage`
  - `ModelSettings`

## 6. 运行与依赖

### 6.1 Android

- Kotlin `2.2.10`
- Android Gradle Plugin `9.1.0`
- Compose BOM `2024.09.00`
- 关键依赖：
  - Navigation Compose
  - DataStore Preferences
  - Retrofit + Gson
  - OkHttp Logging Interceptor

### 6.2 Backend

- FastAPI `0.116.1`
- Uvicorn `0.35.0`
- LangChain `0.3.27`
- langchain-openai `0.3.31`

### 6.3 环境变量

后端支持以下环境变量：

- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_MODEL`
- `OPENAI_TIMEOUT_SECONDS`

如果没有提供 API Key，后端会直接返回“未配置模型 API Key”的提示，不执行工具调用。

## 7. 文档结构约定

当前 `docs/` 已经可以按下面的职责理解和维护：

- `project-overview.md`
  - 面向接手项目的人
  - 记录结构、链路、运行方式、注意事项
- `schedule-ui-optimization.md`
  - 面向某个功能专题
  - 记录方案、目标、验收标准

建议后续在 `docs/` 中只提交需要长期维护的 Markdown 源文件和稳定素材；本地预览缓存、编辑器配置、临时输出目录不纳入版本控制。

## 8. 当前现状与注意事项

- 仓库中已包含 [backend/.env.example](/D:/wby/MyApplication/backend/.env.example)，后端环境变量可以基于它创建本地 `.env`。
- 后端不落库，真正的数据持久化都在客户端本地 `DataStore`。
- 设置页中的 API Key 现在使用加密本地存储；旧版保存在 `DataStore` 中的值会在应用启动时自动迁移。
- 自动化测试基本还是模板文件：
  - [ExampleUnitTest.kt](/D:/wby/MyApplication/app/src/test/java/com/example/myapplication/ExampleUnitTest.kt)
  - [ExampleInstrumentedTest.kt](/D:/wby/MyApplication/app/src/androidTest/java/com/example/myapplication/ExampleInstrumentedTest.kt)
- 当前根工程只包含 `:app` 模块，`backend` 作为独立 Python 服务存在，不受 Gradle 管理。

## 9. 后续维护建议

- 如果项目继续演进，优先补一份真实的 `.env.example`。
- 如果数据量会增长，优先评估把本地 JSON DataStore 迁移到 Room。
- 如果要让后端成为真正数据源，需要补用户体系、持久化层和动作执行层。
- 如果要提升可维护性，建议补最少一层：
  - Android ViewModel/Store 单元测试
  - 后端 `/agent/process` 接口测试
