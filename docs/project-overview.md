# 项目接手总览

这份文档是 `MyLife Agent` 的统一接手入口，目标是帮助新的对话或新的协作者在几分钟内建立上下文。  
如果只读一份文档，优先读这一份。

## 1. 项目目标

`MyLife Agent` 是一个以聊天为主入口的个人效率应用，当前围绕三类核心任务展开：

1. 用自然语言记录和管理日程。
2. 用自然语言记录和查看记账数据。
3. 在一个移动端应用里，把聊天、日程、记账和个人账号体系串成闭环。

当前项目已经不是最早的“纯本地原型”，而是演进成了“Android 客户端 + Python 后端 + PostgreSQL”的可运行应用。

## 2. 当前产品形态

当前应用的默认入口仍然是聊天页，而不是仪表盘首页。整体结构如下：

- `agent`：聊天主入口，用户通过自然语言发起操作。
- `schedule`：日程查看、筛选、编辑、删除。
- `ledger`：账单统计、洞察、编辑、删除。
- `profile/settings`：账号、后端连接、模型状态与个人设置。

现在的核心交互链路是：

1. 用户登录。
2. 在聊天页输入自然语言。
3. 后端解析意图并生成回复与动作。
4. 后端持久化会话、日程、账单。
5. 客户端同步结构化数据到本地缓存并刷新界面。

## 3. 现在已经完成的内容

### 3.1 基础架构

- Android 端已经完成 Jetpack Compose 单应用壳层。
- 后端已经完成 FastAPI 服务和 PostgreSQL 存储。
- 已经有账号体系，包括注册、登录、刷新 token、登出、修改密码、找回密码。
- 聊天、日程、账单都已经接入后端持久化。

### 3.2 聊天闭环

- 聊天页已经支持多轮对话。
- 会话列表已经独立出来，支持创建、切换、删除、重命名。
- 消息可以删除，聊天历史以“会话”为单位存放在后端。
- Agent 状态已经接入连通性探测，进入聊天页会自动检查后端和模型是否可用。
- 回复文本已经做过纯文本约束，不再依赖 Markdown 渲染。

### 3.3 日程功能

- 支持通过 Agent 新增日程。
- 支持查看某天日程、全部日程、未来优先和历史筛选。
- 支持编辑、删除日程。
- 日程页已经做过一轮可用性优化，包括更轻的日历展示、快捷日期切换、最近新增高亮和按聊天回执定位。

### 3.4 记账功能

- 支持通过 Agent 新增账单。
- 支持收入 / 支出两类数据。
- 支持编辑、删除账单。
- 账单数据已经做了日期与类型标准化，修过“记录成功但统计不显示”的问题。
- 统计页已经升级为“分析页”，包含周期汇总、同比/环比式对比、关键洞察、分类分布、趋势图和重点记录。
- 收入和支出的分类已经收口为常见类别加“其他”，不再允许无限发散。

### 3.5 设置与安全

- API Key 不再以明文写入普通本地存储。
- 客户端网络日志已经做过收紧，避免把敏感请求体直接打到日志。
- 设置页已经支持连接测试、当前后端状态说明和模型可用性反馈。
- 当前策略是“模型配置以后端为准”，客户端不再主导实际模型选择。

## 4. 当前真实架构

### 4.1 Android 端

主要代码位于：

- [app/src/main/java/com/example/myapplication/ui/AgentApp.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/ui/AgentApp.kt)
- [app/src/main/java/com/example/myapplication/agent/AppViewModel.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/agent/AppViewModel.kt)
- [app/src/main/java/com/example/myapplication/agent/data/LocalStore.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/agent/data/LocalStore.kt)
- [app/src/main/java/com/example/myapplication/agent/data/AgentDatabase.kt](/D:/wby/MyApplication/app/src/main/java/com/example/myapplication/agent/data/AgentDatabase.kt)

职责划分：

- `AppViewModel` 是应用侧主要业务入口，负责聊天、同步、账号状态、连接探测和页面状态管理。
- `LocalStore` 负责本地缓存和迁移逻辑。
- `Room` 现在用于缓存结构化数据：
  - `ledger_entries`
  - `schedule_items`
- `DataStore` 现在主要保留轻量配置和部分会话级辅助数据，不再承担结构化数据主存储。

### 4.2 后端

主要代码位于：

- [backend/agent_backend/api.py](/D:/wby/MyApplication/backend/agent_backend/api.py)
- [backend/agent_backend/orchestrator.py](/D:/wby/MyApplication/backend/agent_backend/orchestrator.py)
- [backend/agent_backend/llm_client.py](/D:/wby/MyApplication/backend/agent_backend/llm_client.py)
- [backend/agent_backend/storage.py](/D:/wby/MyApplication/backend/agent_backend/storage.py)

当前后端已经不只是一个“转发模型请求”的轻量服务，而是承担这些职责：

- 用户认证与 token 管理。
- 会话和消息持久化。
- 日程和账单的增删改查。
- Agent 请求编排。
- PostgreSQL 数据存储。

当前已经暴露的能力包括：

- 健康检查。
- 模型状态检查。
- 注册 / 登录 / 刷新 / 登出 / 密码相关接口。
- 会话列表、会话消息、删除消息。
- 日程 CRUD。
- 账单 CRUD。
- Agent 自然语言处理入口。

## 5. 当前数据流

### 5.1 聊天请求链路

1. 客户端确保用户已登录。
2. 客户端拿当前会话 ID 调用 `/agent/process`。
3. 后端结合当前会话上下文和模型配置执行 Agent。
4. 后端把聊天消息和结构化结果落库。
5. 客户端根据 `changedDomains` 再次同步日程 / 账单缓存。
6. UI 根据本地 `StateFlow` 刷新。

### 5.2 结构化数据链路

- 后端是日程和账单的真实数据源。
- Android 端本地 `Room` 是缓存层，不是最终真相源。
- 登录后会执行同步，退出或换账号时会清理 / 切换本地缓存归属。

这点和项目早期版本很不一样。  
以后继续改功能时，要优先按“后端主存储、客户端本地缓存”理解，而不是按“纯本地本”理解。

## 6. 主要页面现状

### 6.1 聊天页

- 是默认入口。
- 已做过输入区与软键盘联动修复。
- 有 Agent 在线状态展示。
- 支持自然语言记录日程和账单。
- 仍然是产品最核心的使用入口。

### 6.2 日程页

- 页面方向偏“轻量日历 + 列表浏览”。
- 日历体积已经收紧，不再是很重的整块组件。
- 支持最近新增高亮、目标日期定位、未来优先筛选。

### 6.3 记账页

- 默认更偏“统计分析”而不是只看流水。
- 已经加入关键洞察、结构占比、趋势和重点记录。
- 明细列表近期做过压缩和分类收口，但视觉密度仍然可能继续微调。

### 6.4 设置 / 个人页

- 设置页偏“联调与状态确认”。
- 个人页承担账号相关流程。
- 当前连接探测已经成为判断 Agent 是否可用的主要入口之一。

## 7. 最近已经做过的重要迭代

为了让后续对话快速理解项目现状，这里只保留高价值变化：

- 项目命名从早期 `TimePaper Agent` 统一到了 `MyLife Agent`，但后端标题和个别旧文档里仍可能残留旧名称。
- 结构化数据从早期本地 JSON 存储迁移到了 `Room` 缓存。
- 后端已经补齐账号体系、会话体系和 PostgreSQL 存储。
- 模型配置策略改成“以后端为准”，客户端只做连接与状态辅助。
- 聊天页做过多轮输入框、键盘联动和状态展示优化。
- 日程页做过一轮专题优化，原先独立的日程 UI 文档内容已经并入本总览，不再单独维护。
- 记账页统计能力已经从基础汇总演进到分析页。

## 8. 已知边界和注意事项

- 仓库里仍有一些历史文案、旧命名或终端显示乱码痕迹，维护时要以当前代码行为为准。
- 后端 CORS 目前仍偏宽松，更适合开发联调，不是严格生产配置。
- 项目虽然已经有后端和数据库，但整体仍偏单人使用、快速迭代阶段。
- 自动化测试覆盖仍然不算完整，很多改动主要依赖实际联调验证。
- 记账和日程已经后端化，但 UI 层仍有不少体验优化空间，尤其是视觉密度、空状态、细节文案和操作反馈。

## 9. 继续迭代时的优先理解顺序

新对话或新协作者建议按这个顺序理解项目：

1. 先看这份总览，确认项目当前真实形态。
2. 再看 [docs/README.md](/D:/wby/MyApplication/docs/README.md) 了解文档分工。
3. 如果要改聊天主流程，优先看 `AppViewModel`、`AgentRepository`、后端 `api.py / orchestrator.py / llm_client.py`。
4. 如果要改数据行为，优先确认“后端是真实源，客户端是缓存”这条原则。
5. 如果要改界面细节，再分别进入 `AgentHomeScreen`、`ScheduleScreen`、`LedgerScreen`、`SettingsScreen`。

## 10. 后续文档维护约定

- 这份文档负责回答“项目是什么、现在做到哪里、主要结构如何工作”。
- 不再把短期专题优化拆成很多散落的小文档，除非后续真的形成长期维护的独立模块。
- 如果后端架构、存储策略、登录方式或主页面职责发生变化，要优先更新这份文档。
