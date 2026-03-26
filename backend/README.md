# TimePaper Agent Backend

## 1. 启动

```bash
cd backend
python -m venv .venv
.venv\\Scripts\\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn main:app --reload --port 8000
```

## 2. 环境变量

- `OPENAI_API_KEY`: 大模型 API Key
- `OPENAI_BASE_URL`: OpenAI 兼容服务地址，默认 `https://api.moonshot.cn/v1`
- `OPENAI_MODEL`: 模型名，默认 `moonshot-v1-8k`
- `OPENAI_TIMEOUT_SECONDS`: Agent 请求超时秒数，默认 `18`

如果不配置 API Key，后端仅返回提示信息，不会执行工具调用。

## 3. 接口

- `GET /health`
- `POST /agent/process`

请求示例：

```json
{
  "text": "明天下午3点和客户复盘，咖啡28元"
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
- Tool Calling（`add_schedule` / `add_ledger`）
- `langchain-openai` 连接 Kimi（OpenAI 兼容接口）

流程：

1. Agent 根据自然语言判断是否需要记日程、记账
2. 通过工具调用生成结构化动作
3. 后端统一返回 `reply + actions` 给安卓端

聊天行为：

- 普通聊天：只返回 reply，actions 为空
- 涉及日程：调用 add_schedule 并在 actions 返回记录动作
- 涉及记账：调用 add_ledger 并在 actions 返回记录动作
- 同时涉及日程和记账：会同时调用两个工具

## 5. 模块结构

- `main.py`: 仅作为入口导出 `app`
- `agent_backend/schemas.py`: Pydantic 数据模型
- `agent_backend/llm_client.py`: LangChain Agent 与工具实现
- `agent_backend/orchestrator.py`: 动作编排与后处理
- `agent_backend/api.py`: FastAPI 路由与应用组装
