# TimePaper Agent (Android + Backend)

这是一个前后端分离的安卓 Agent 应用原型：

- 安卓端：聊天交互、记账页、日程日历页
- 后端：聊天式 Agent，按需调用工具并返回动作（add_schedule / add_ledger）

## 架构

- Android (Jetpack Compose)
  - `agent`：ViewModel、网络层、数据模型
  - `ui`：交互页、记账页、日程日历页、底部导航
  - 本地存储：DataStore（持久化聊天记录、记账和日程）
- Backend (FastAPI)
  - `POST /agent/process`
  - 有 `OPENAI_API_KEY` 时走 LangChain Agent + Tool Calling
  - 无 Key 时走规则解析，便于离线调试

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

### 2. 运行安卓端

- 直接在 Android Studio / VS Code 启动模拟器运行 app
- 安卓端默认请求地址：`http://10.0.2.2:8000/`

## 功能说明

- 交互页：聊天气泡形式，支持多轮对话；涉及日程/记账时自动调用工具
- 记账页：展示收入/支出统计和流水列表
- 日程页：日历选择日期并查看当天日程
- 聊天记录：本地持久化，重启应用后保留历史消息

## 示例输入

- `明天下午3点和客户复盘，咖啡28元`
- `后天10点项目周会，打车18元`
- `今天报销200元，晚上7点健身`

## 后续可扩展方向

- 后端增加真正的工具执行层（数据库、通知、第三方日历）
- 增加多轮上下文与用户画像
- 引入账户体系和云同步
- 增加语音输入、图片票据识别
