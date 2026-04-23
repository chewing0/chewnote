from __future__ import annotations

import asyncio
import datetime as dt
import os
from datetime import date, timedelta
from typing import Any

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import StructuredTool
from langchain_openai import ChatOpenAI

from .schemas import AgentAction, LedgerToolInput, ScheduleSeriesToolInput, ScheduleToolInput


class LLMClient:
    def __init__(self) -> None:
        self.default_api_key = os.getenv("OPENAI_API_KEY", "")
        self.default_base_url = os.getenv("OPENAI_BASE_URL", "https://api.moonshot.cn/v1").rstrip("/")
        self.default_model = os.getenv("OPENAI_MODEL", "moonshot-v1-8k")
        self.request_timeout_sec = float(os.getenv("OPENAI_TIMEOUT_SECONDS", "18"))
        self._runtime_actions: list[AgentAction] = []

    def _build_executor(self, api_key: str, base_url: str, model: str) -> AgentExecutor:
        llm = ChatOpenAI(
            api_key=api_key,
            base_url=base_url,
            model=model,
            temperature=0.1,
            timeout=self.request_timeout_sec,
            max_retries=1,
        )

        tools = [
            StructuredTool.from_function(
                name="add_schedule",
                description="新增一条日程安排。",
                func=self._tool_add_schedule,
                args_schema=ScheduleToolInput,
            ),
            StructuredTool.from_function(
                name="add_schedule_series",
                description="批量新增连续多天的日程，例如未来 7 天每天 15:00 的安排。",
                func=self._tool_add_schedule_series,
                args_schema=ScheduleSeriesToolInput,
            ),
            StructuredTool.from_function(
                name="add_ledger",
                description="新增一条记账记录。",
                func=self._tool_add_ledger,
                args_schema=LedgerToolInput,
            ),
        ]

        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "你是中文个人效率助手。"
                    "你的名称是 MyLife Agent。"
                    "你的职责是根据用户输入，调用可用工具完成记录。"
                    "如果有单条日程信息，调用 add_schedule。"
                    "如果是多天重复日程，例如接下来一周每天或连续七天每天，必须调用 add_schedule_series。"
                    "如果有记账信息，必须调用 add_ledger。"
                    "如果日程和记账都有，就都调用。"
                    "事项标题必须忠实于用户原文，不要凭空改写活动名称。"
                    "日期必须是 YYYY-MM-DD，时间必须是 HH:mm。"
                    "完成工具调用后再给出简短中文总结。"
                    "回复必须使用纯文本，不要使用 Markdown。"
                    "不要使用标题、列表、项目符号、加粗、代码块、反引号或链接格式。",
                ),
                MessagesPlaceholder(variable_name="chat_history", optional=True),
                (
                    "human",
                    "当前日期: {today}\n用户输入: {input}",
                ),
                MessagesPlaceholder(variable_name="agent_scratchpad"),
            ]
        )

        agent = create_tool_calling_agent(llm, tools, prompt)
        return AgentExecutor(
            agent=agent,
            tools=tools,
            verbose=False,
        )

    def _resolve_runtime_config(self, model_config: dict[str, str] | None) -> tuple[str, str, str]:
        cfg = model_config or {}
        api_key = (cfg.get("api_key") or "").strip() or self.default_api_key
        base_url = (cfg.get("base_url") or "").strip() or self.default_base_url
        model = (cfg.get("model") or "").strip() or self.default_model
        return api_key, base_url.rstrip("/"), model

    def _tool_add_schedule(self, title: str, date: str, time: str, note: str = "") -> str:
        self._runtime_actions.append(
            AgentAction(
                type="add_schedule",
                payload={
                    "title": title,
                    "date": date,
                    "time": time,
                    "note": note,
                },
            )
        )
        return "schedule_recorded"

    def _tool_add_schedule_series(
        self,
        title: str,
        startDate: str,
        time: str,
        days: int,
        note: str = "",
    ) -> str:
        try:
            start_date = date.fromisoformat(startDate)
        except ValueError:
            start_date = date.today()

        total_days = max(1, min(days, 31))
        for index in range(total_days):
            self._runtime_actions.append(
                AgentAction(
                    type="add_schedule",
                    payload={
                        "title": title,
                        "date": (start_date + timedelta(days=index)).isoformat(),
                        "time": time,
                        "note": note,
                    },
                )
            )
        return f"schedule_series_recorded:{total_days}"

    def _tool_add_ledger(
        self,
        amount: float,
        category: str = "其他",
        note: str = "",
        date: str = "",
        entryType: str = "expense",
    ) -> str:
        ledger_date = date or str(dt.date.today())
        self._runtime_actions.append(
            AgentAction(
                type="add_ledger",
                payload={
                    "amount": float(amount),
                    "category": category,
                    "note": note,
                    "date": ledger_date,
                    "entryType": entryType,
                },
            )
        )
        return "ledger_recorded"

    async def parse(
        self,
        text: str,
        history: list[dict[str, str]] | None = None,
        model_config: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        api_key, base_url, model = self._resolve_runtime_config(model_config)
        if not api_key:
            return {
                "reply": "当前未配置模型 API Key，暂时无法执行 Agent 调用。",
                "actions": [],
            }

        try:
            self._runtime_actions = []
            executor = self._build_executor(api_key, base_url, model)
            chat_history = []
            for item in history or []:
                role = (item.get("role") or "").lower()
                content = (item.get("content") or "").strip()
                if not content:
                    continue
                if role == "assistant":
                    chat_history.append(AIMessage(content=content))
                elif role == "user":
                    chat_history.append(HumanMessage(content=content))

            result = await asyncio.wait_for(
                executor.ainvoke(
                    {
                        "today": str(date.today()),
                        "input": text,
                        "chat_history": chat_history,
                    }
                ),
                timeout=self.request_timeout_sec,
            )
            reply = result.get("output") if isinstance(result, dict) else ""
            return {
                "reply": reply if isinstance(reply, str) else "",
                "actions": [action.model_dump() for action in self._runtime_actions],
            }
        except asyncio.TimeoutError:
            return {
                "reply": "模型响应超时了，请稍后重试，或换一个更稳定的模型配置。",
                "actions": [],
            }
        except Exception as exc:
            error_text = str(exc)
            lowered = error_text.lower()

            if "api key" in lowered or "authentication" in lowered or "401" in lowered:
                reply = "模型 API Key 无效或没有权限，请检查设置页里的 Key 配置。"
            elif "403" in lowered or "permission" in lowered or "access_terminated_error" in lowered:
                reply = "当前模型没有访问权限，请更换模型或使用有权限的 Key。"
            elif "404" in lowered or "model does not exist" in lowered:
                reply = "模型名称或 Base URL 不正确，请检查后重试。"
            elif "connection" in lowered or "dns" in lowered or "name or service not known" in lowered:
                reply = "模型服务当前不可达，请检查 Base URL 或网络连接。"
            else:
                reply = "Agent 服务暂时不可用，请稍后重试。"

            return {
                "reply": reply,
                "actions": [],
            }
