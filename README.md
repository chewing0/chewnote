# TimePaper Agent (Android + Backend)

这是一个前后端分离的安卓 Agent 应用原型：

- 安卓端：聊天交互、记账页、日程日历页
- 后端：聊天式 Agent，按需调用工具并返回动作（`add_schedule` / `add_ledger`）

## 架构

- Android (Jetpack Compose)
  - `agent`：ViewModel、网络层、数据模型
  - `ui`：交互页、记账页、日程日历页、底部导航
  - 本地存储：DataStore（聊天记录、记账、日程）
  - 敏感配置：API Key 通过加密本地存储保存，不再落到普通 DataStore
- Backend (FastAPI)
  - `POST /agent/process`
  - 优先使用请求内 `model_config`，否则回退到服务端环境变量
  - 有可用 API Key 时走 LangChain Agent + Tool Calling
  - 无 Key 时仅返回不可用提示，不执行任何动作

## 快速启动

### 1. 启动后端

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn main:app --reload --port 8000
```

可选：编辑 `backend/.env`，填入你的模型配置。

### 2. 运行安卓端

- 直接在 Android Studio / VS Code 启动模拟器运行 app
- 安卓端默认请求地址：`http://10.0.2.2:8000/`
- 也可以在应用“设置”页覆盖后端地址、模型 Base URL、模型名和 API Key

## 模型配置来源

项目现在支持两种模型配置方式：

1. 仅在后端配置
   - 在 `backend/.env` 中设置 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`
   - 安卓端只负责把自然语言请求发给后端
2. 在安卓端设置页按请求透传
   - 设置页填写后端地址、模型 Base URL、模型名、API Key
   - API Key 会加密保存在设备本地
   - 请求日志不会打印请求体，避免把 `model_config.api_key` 暴露到网络日志

如果不需要前端透传模型配置，设置页里的 API Key 可以留空，后端会继续使用 `.env`。

## 功能说明

- 交互页：聊天气泡形式，支持多轮对话；涉及日程/记账时自动调用工具
- 记账页：展示收入/支出统计和流水列表
- 日程页：日历选择日期并查看当天日程
- 聊天记录：本地持久化，重启应用后保留历史消息

## 安全说明

- `backend/.env` 已被 `.gitignore` 忽略，不会默认进入版本控制
- 设备端 API Key 使用加密存储，不写入普通 `DataStore`
- Android 网络日志在调试模式下只保留基础请求信息，发布模式关闭

## 示例输入

- `明天下午3点和客户复盘，咖啡28元`
- `后天10点项目周会，打车18元`
- `今天报销200元，晚上7点健身`

## 后续可扩展方向

- 后端增加真正的工具执行层（数据库、通知、第三方日历）
- 增加多轮上下文与用户画像
- 引入账户体系和云同步
- 增加语音输入、图片票据识别
