# TimePaper Agent Backend

## 1. 启动

Windows 推荐直接运行：

```powershell
cd backend
.\start_backend.bat
```

脚本会自动检查 `.env`、创建 `.venv`、安装 `requirements.txt`、初始化 PostgreSQL 数据库并启动 `uvicorn`。

可选参数：

```powershell
.\start_backend.bat -Port 8001
.\start_backend.bat -SkipInstall
.\start_backend.bat -SkipDbInit
```

手动启动方式：

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
python scripts/init_postgres_db.py
uvicorn main:app --reload --port 8000
```

## 2. 环境变量

- `OPENAI_API_KEY`: 大模型 API Key
- `OPENAI_BASE_URL`: OpenAI 兼容服务地址，默认 `https://api.moonshot.cn/v1`
- `OPENAI_MODEL`: 模型名，默认 `moonshot-v1-8k`
- `OPENAI_TIMEOUT_SECONDS`: Agent 请求超时秒数，默认 `18`
- `DATABASE_URL`: PostgreSQL 业务库连接地址
- `AUTH_SECRET_KEY`: JWT 签名密钥，正式使用时请改成长随机字符串
- `AUTH_DEV_RETURN_RESET_TOKEN`: 开发期是否在找回密码响应中返回一次性重置 Token
- `AGENT_DEBUG_ERRORS`: 开发期可设为 `true`，让 Agent 返回更具体的模型/工具异常
- `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` / `SMTP_FROM`: 可选邮件配置，用于发送密码重置邮件

如果不配置 API Key，后端仅返回提示信息，不会执行工具调用。

说明：

- 模型配置以后端 `.env` 为唯一来源，Android 只配置后端地址
- `GET /config/model` 可查看后端当前模型名、Base URL 和 API Key 是否已配置，但不会返回明文 Key
- `/agent/process` 不再接受客户端模型覆盖，避免前后端配置不一致

## 3. 接口

- `GET /health`
- `GET /config/model`
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/me`
- `POST /auth/password/forgot`
- `POST /auth/password/reset`
- `POST /auth/password/change`
- `GET /sync`
- `POST /sync/import`
- `POST /agent/process`

除 `/health` 和 `/auth/*` 登录注册类接口外，日程、记账、同步和 Agent 接口都需要请求头：

```http
Authorization: Bearer your_access_token
```

请求示例：

```json
{
  "text": "明天下午3点和客户复盘，咖啡28元",
  "conversation_id": "可选，留空则后端新建对话"
}
```

返回示例：

```json
{
  "reply": "已处理 2 条任务，已同步到日程/记账。",
  "actions": [
    {
      "type": "add_schedule",
      "payload": {
        "title": "客户复盘",
        "date": "2026-03-27",
        "time": "15:00",
        "note": "..."
      }
    },
    {
      "type": "add_ledger",
      "payload": {
        "amount": 28,
        "category": "餐饮",
        "note": "咖啡",
        "date": "2026-03-26",
        "entryType": "expense"
      }
    }
  ]
}
```

## 4. Agent 技术栈

- LangChain AgentExecutor
- Tool Calling（`add_schedule` / `add_schedule_series` / `add_ledger`）
- `langchain-openai` 连接 Kimi（OpenAI 兼容接口）

流程：

1. Agent 根据自然语言判断是否需要记日程、记账
2. 通过工具调用生成结构化动作
3. 连续多天日程会先在后端展开为多条 `add_schedule`
4. 后端统一返回 `reply + changed_domains` 给安卓端，安卓端据此 `/sync` 刷新本地只读缓存

聊天行为：

- 普通聊天：只返回 reply，actions 为空
- 涉及日程：调用 schedule tools 并直接写入后端 PostgreSQL
- 涉及连续多天重复日程：调用 create_schedule_series 并直接写入多条日程
- 涉及记账：调用 ledger tools 并直接写入后端 PostgreSQL
- 同时涉及日程和记账：会同时调用两个工具
- 如果模型回复了“已记录”但没有真正发起工具调用，后端会对常见“花了/收入 + 金额”语句做保底解析并写入账单，避免聊天显示成功但统计页没有数据

## 5. 模块结构

- `main.py`: 仅作为入口导出 `app`
- `agent_backend/schemas.py`: Pydantic 数据模型
- `agent_backend/llm_client.py`: LangChain Agent 与工具实现
- `agent_backend/orchestrator.py`: 动作编排与后处理
- `agent_backend/api.py`: FastAPI 路由与应用组装

## 6. 联调约定

- Android 模拟器默认访问 `http://10.0.2.2:8000/`
- 客户端设置页只保存后端地址，模型 Base URL、模型名和 API Key 均在后端 `.env` 修改
- 客户端当前已收紧网络日志，不会打印完整请求体
