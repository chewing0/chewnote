from __future__ import annotations

import asyncio
import datetime as dt
import json
import logging
import os
import re
from datetime import date, timedelta
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import StructuredTool
from langchain_openai import ChatOpenAI

from .schemas import (
    AgentAction,
    CancelPendingToolInput,
    ConfirmPendingToolInput,
    LedgerDeleteToolInput,
    LedgerQueryToolInput,
    LedgerSummaryToolInput,
    LedgerToolInput,
    LedgerUpdateToolInput,
    ScheduleDeleteToolInput,
    ScheduleQueryToolInput,
    ScheduleSeriesToolInput,
    ScheduleToolInput,
    ScheduleUpdateToolInput,
)
from .storage import DOMAIN_LEDGER, DOMAIN_SCHEDULE, AgentStore


logger = logging.getLogger(__name__)


class LLMClient:
    def __init__(self, store: AgentStore) -> None:
        self.store = store
        self.default_api_key = os.getenv("OPENAI_API_KEY", "")
        self.default_base_url = os.getenv("OPENAI_BASE_URL", "https://api.moonshot.cn/v1").rstrip("/")
        self.default_model = os.getenv("OPENAI_MODEL", "moonshot-v1-8k")
        self.request_timeout_sec = float(os.getenv("OPENAI_TIMEOUT_SECONDS", "18"))
        self.timezone = os.getenv("APP_TIMEZONE", "Asia/Shanghai")

    def public_model_status(self) -> dict[str, Any]:
        return {
            "base_url": self.default_base_url,
            "model": self.default_model,
            "api_key_configured": bool(self.default_api_key.strip()),
            "source": "backend_env",
            "runtime_override_enabled": False,
        }

    def _build_executor(
        self,
        api_key: str,
        base_url: str,
        model: str,
        user_id: str,
        session_id: str,
        changed_domains: set[str],
        current_date: str,
        current_time: str,
        timezone: str,
        user_text: str,
    ) -> AgentExecutor:
        llm = ChatOpenAI(
            api_key=api_key,
            base_url=base_url,
            model=model,
            temperature=0.1,
            timeout=self.request_timeout_sec,
            max_retries=1,
        )

        def create_schedule(title: str, date: str, time: str, note: str = "") -> str:
            item_date = _resolve_relative_date(user_text, current_date) or date
            item = self.store.create_schedule(user_id, title, item_date, time, note)
            changed_domains.add(DOMAIN_SCHEDULE)
            return _json({"status": "created", "schedule": item})

        def create_schedule_series(
            title: str,
            startDate: str,
            time: str,
            days: int,
            note: str = "",
        ) -> str:
            try:
                start_date = date_cls.fromisoformat(_resolve_relative_date(user_text, current_date) or startDate)
            except ValueError:
                start_date = date_cls.fromisoformat(current_date)
            total_days = max(1, min(days, 31))
            items = [
                self.store.create_schedule(
                    user_id=user_id,
                    title=title,
                    date=(start_date + timedelta(days=index)).isoformat(),
                    item_time=time,
                    note=note,
                )
                for index in range(total_days)
            ]
            changed_domains.add(DOMAIN_SCHEDULE)
            return _json({"status": "created", "count": len(items), "schedules": items[:10]})

        def query_schedules(date: str = "", startDate: str = "", endDate: str = "", keyword: str = "") -> str:
            resolved_date = _resolve_relative_date(user_text, current_date)
            resolved_range = _resolve_relative_range(user_text, current_date)
            items = self.store.list_schedules(
                user_id,
                date=resolved_date or date,
                start_date=resolved_range[0] if resolved_range else startDate,
                end_date=resolved_range[1] if resolved_range else endDate,
                keyword=keyword,
            )
            return _json({"count": len(items), "schedules": _numbered(items)})

        def update_schedule(
            date: str = "",
            startDate: str = "",
            endDate: str = "",
            keyword: str = "",
            title: str = "",
            newDate: str = "",
            newTime: str = "",
            note: str = "",
            all: bool = False,
        ) -> str:
            updates = _clean_updates(
                {
                    "title": title,
                    "date": _resolve_relative_update_date(user_text, current_date) or newDate,
                    "time": newTime,
                    "note": note,
                }
            )
            resolved_date = _resolve_relative_date(user_text, current_date)
            resolved_range = _resolve_relative_range(user_text, current_date)
            return self._mutate_schedules(
                session_id=session_id,
                user_id=user_id,
                changed_domains=changed_domains,
                operation="update",
                date=resolved_date or date,
                start_date=resolved_range[0] if resolved_range else startDate,
                end_date=resolved_range[1] if resolved_range else endDate,
                keyword=keyword,
                updates=updates,
                all_matches=all,
            )

        def delete_schedule(
            date: str = "",
            startDate: str = "",
            endDate: str = "",
            keyword: str = "",
            all: bool = False,
        ) -> str:
            resolved_date = _resolve_relative_date(user_text, current_date)
            resolved_range = _resolve_relative_range(user_text, current_date)
            return self._mutate_schedules(
                session_id=session_id,
                user_id=user_id,
                changed_domains=changed_domains,
                operation="delete",
                date=resolved_date or date,
                start_date=resolved_range[0] if resolved_range else startDate,
                end_date=resolved_range[1] if resolved_range else endDate,
                keyword=keyword,
                updates={},
                all_matches=all,
            )

        def create_ledger(
            amount: float | None = None,
            category: str = "其他",
            note: str = "",
            date: str = "",
            entryType: str = "expense",
        ) -> str:
            if amount is None:
                return _json({"status": "missing_amount", "message": "没有识别到记账金额，请向用户追问金额。"})
            item = self.store.create_ledger(
                user_id=user_id,
                amount=float(amount),
                category=category,
                note=note,
                date=_resolve_relative_date(user_text, current_date) or date or current_date,
                entry_type=entryType,
            )
            changed_domains.add(DOMAIN_LEDGER)
            return _json({"status": "created", "ledger": item})

        def query_ledgers(
            date: str = "",
            startDate: str = "",
            endDate: str = "",
            keyword: str = "",
            entryType: str = "",
        ) -> str:
            resolved_date = _resolve_relative_date(user_text, current_date)
            resolved_range = _resolve_relative_range(user_text, current_date)
            items = self.store.list_ledgers(
                user_id,
                date=resolved_date or date,
                start_date=resolved_range[0] if resolved_range else startDate,
                end_date=resolved_range[1] if resolved_range else endDate,
                keyword=keyword,
                entry_type=entryType,
            )
            return _json({"count": len(items), "ledgers": _numbered(items)})

        def summarize_ledger(
            startDate: str,
            endDate: str,
            entryType: str = "expense",
            keyword: str = "",
        ) -> str:
            resolved_range = _resolve_relative_range(user_text, current_date)
            summary = self.store.summarize_ledgers(
                user_id,
                start_date=resolved_range[0] if resolved_range else startDate,
                end_date=resolved_range[1] if resolved_range else endDate,
                entry_type=entryType,
                keyword=keyword,
            )
            return _json(summary)

        def update_ledger(
            date: str = "",
            startDate: str = "",
            endDate: str = "",
            keyword: str = "",
            entryType: str = "",
            amount: float | None = None,
            category: str = "",
            note: str = "",
            newDate: str = "",
            newEntryType: str = "",
            all: bool = False,
        ) -> str:
            updates = _clean_updates(
                {
                    "amount": amount,
                    "category": category,
                    "note": note,
                    "date": _resolve_relative_update_date(user_text, current_date) or newDate,
                    "entry_type": newEntryType,
                }
            )
            resolved_date = _resolve_relative_date(user_text, current_date)
            resolved_range = _resolve_relative_range(user_text, current_date)
            return self._mutate_ledgers(
                session_id=session_id,
                user_id=user_id,
                changed_domains=changed_domains,
                operation="update",
                date=resolved_date or date,
                start_date=resolved_range[0] if resolved_range else startDate,
                end_date=resolved_range[1] if resolved_range else endDate,
                keyword=keyword,
                entry_type=entryType,
                updates=updates,
                all_matches=all,
            )

        def delete_ledger(
            date: str = "",
            startDate: str = "",
            endDate: str = "",
            keyword: str = "",
            entryType: str = "",
            all: bool = False,
        ) -> str:
            resolved_date = _resolve_relative_date(user_text, current_date)
            resolved_range = _resolve_relative_range(user_text, current_date)
            return self._mutate_ledgers(
                session_id=session_id,
                user_id=user_id,
                changed_domains=changed_domains,
                operation="delete",
                date=resolved_date or date,
                start_date=resolved_range[0] if resolved_range else startDate,
                end_date=resolved_range[1] if resolved_range else endDate,
                keyword=keyword,
                entry_type=entryType,
                updates={},
                all_matches=all,
            )

        def confirm_pending_operation(selection: str = "1") -> str:
            return self._confirm_pending(user_id, session_id, changed_domains, selection)

        def cancel_pending_operation(reason: str = "") -> str:
            self.store.clear_pending_operation(user_id, session_id)
            return _json({"status": "cancelled", "reason": reason})

        tools = [
            StructuredTool.from_function(
                name="create_schedule",
                description="新增一条日程安排。",
                func=create_schedule,
                args_schema=ScheduleToolInput,
            ),
            StructuredTool.from_function(
                name="create_schedule_series",
                description="批量新增连续多天的日程，例如未来 7 天每天 15:00 的安排。",
                func=create_schedule_series,
                args_schema=ScheduleSeriesToolInput,
            ),
            StructuredTool.from_function(
                name="query_schedules",
                description="查询日程，支持按日期、日期范围和关键词过滤。",
                func=query_schedules,
                args_schema=ScheduleQueryToolInput,
            ),
            StructuredTool.from_function(
                name="update_schedule",
                description="修改日程。多条或不确定命中时会创建待确认操作，不会直接修改。",
                func=update_schedule,
                args_schema=ScheduleUpdateToolInput,
            ),
            StructuredTool.from_function(
                name="delete_schedule",
                description="删除日程。多条或不确定命中时会创建待确认操作，不会直接删除。",
                func=delete_schedule,
                args_schema=ScheduleDeleteToolInput,
            ),
            StructuredTool.from_function(
                name="create_ledger",
                description="新增一条记账记录。",
                func=create_ledger,
                args_schema=LedgerToolInput,
            ),
            StructuredTool.from_function(
                name="query_ledgers",
                description="查询记账流水，支持按日期、日期范围、关键词和收支类型过滤。",
                func=query_ledgers,
                args_schema=LedgerQueryToolInput,
            ),
            StructuredTool.from_function(
                name="summarize_ledger",
                description="统计某个日期范围内的收入或支出总额、笔数和分类汇总。",
                func=summarize_ledger,
                args_schema=LedgerSummaryToolInput,
            ),
            StructuredTool.from_function(
                name="update_ledger",
                description="修改账单。多条或不确定命中时会创建待确认操作，不会直接修改。",
                func=update_ledger,
                args_schema=LedgerUpdateToolInput,
            ),
            StructuredTool.from_function(
                name="delete_ledger",
                description="删除账单。多条或不确定命中时会创建待确认操作，不会直接删除。",
                func=delete_ledger,
                args_schema=LedgerDeleteToolInput,
            ),
            StructuredTool.from_function(
                name="confirm_pending_operation",
                description="用户确认上一轮候选操作时调用，例如“删除第 1 条”、“全部取消”。",
                func=confirm_pending_operation,
                args_schema=ConfirmPendingToolInput,
            ),
            StructuredTool.from_function(
                name="cancel_pending_operation",
                description="用户取消上一轮候选操作时调用，例如“算了”、“取消”。",
                func=cancel_pending_operation,
                args_schema=CancelPendingToolInput,
            ),
        ]

        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "你是中文个人效率助手，名称是 MyLife Agent。"
                    "你必须通过工具完成日程和记账的增删改查。"
                    "日期必须是 YYYY-MM-DD，时间必须是 HH:mm。"
                    "周范围按中国常用口径：周一到周日。"
                    "用户说上周、本周、昨天、明天时，必须基于后端权威日期计算具体日期，禁止使用模型内置知识里的日期。"
                    "年份必须来自后端权威日期上下文；如果后端权威日期是 2026-04-27，那么明天就是 2026-04-28。"
                    "花费、支出、消费默认 entryType=expense；收入、入账、报销到账默认 entryType=income。"
                    "查询账单/流水但未说明收支类型时 entryType 留空。"
                    "删除或修改命中多条时，除非用户明确说全部/所有，否则不要执行，等待用户确认候选编号。"
                    "如果用户回复确认第几条、全部或取消，要调用确认或取消工具。"
                    "此前对话摘要只作为背景，不要复述给用户。"
                    "回复必须使用纯文本，不要使用 Markdown、标题、列表、项目符号、加粗、代码块、反引号或链接格式。",
                ),
                (
                    "system",
                    "后端权威日期: {today}\n后端权威时间: {current_time}\n后端权威时区: {timezone}\n会话 ID: {session_id}\n此前对话摘要（可能为空）: {context_summary}",
                ),
                MessagesPlaceholder(variable_name="chat_history", optional=True),
                (
                    "human",
                    "用户输入: {input}",
                ),
                MessagesPlaceholder(variable_name="agent_scratchpad"),
            ]
        )

        agent = create_tool_calling_agent(llm, tools, prompt)
        return AgentExecutor(agent=agent, tools=tools, verbose=False)

    def _resolve_runtime_config(self, model_config: dict[str, str] | None) -> tuple[str, str, str]:
        cfg = model_config or {}
        api_key = (cfg.get("api_key") or "").strip() or self.default_api_key
        base_url = (cfg.get("base_url") or "").strip() or self.default_base_url
        model = (cfg.get("model") or "").strip() or self.default_model
        return api_key, base_url.rstrip("/"), model

    async def parse(
        self,
        user_id: str,
        text: str,
        session_id: str,
        history: list[dict[str, str]] | None = None,
        model_config: dict[str, str] | None = None,
        context_summary: str = "",
    ) -> dict[str, Any]:
        api_key, base_url, model = self._resolve_runtime_config(model_config)
        if not api_key:
            return {
                "reply": "当前未配置模型 API Key，暂时无法执行 Agent 调用。",
                "actions": [],
                "changed_domains": [],
            }

        try:
            changed_domains: set[str] = set()
            backend_date, backend_time, backend_timezone = _backend_time_context(self.timezone)
            executor = self._build_executor(
                api_key,
                base_url,
                model,
                user_id,
                session_id,
                changed_domains,
                backend_date,
                backend_time,
                backend_timezone,
                text,
            )
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
                        "today": backend_date,
                        "current_time": backend_time,
                        "timezone": backend_timezone,
                        "session_id": session_id,
                        "input": text,
                        "context_summary": context_summary.strip(),
                        "chat_history": chat_history,
                    }
                ),
                timeout=self.request_timeout_sec,
            )
            reply = result.get("output") if isinstance(result, dict) else ""
            fallback = None
            if not changed_domains:
                fallback = self._try_create_simple_ledger(user_id, text, backend_date)
                if fallback is not None:
                    changed_domains.add(DOMAIN_LEDGER)
            return {
                "reply": fallback["reply"] if fallback is not None else (reply if isinstance(reply, str) else ""),
                "actions": [],
                "changed_domains": sorted(changed_domains),
            }
        except asyncio.TimeoutError:
            return {
                "reply": "模型响应超时了，请稍后重试，或换一个更稳定的模型配置。",
                "actions": [],
                "changed_domains": [],
            }
        except Exception as exc:
            logger.exception("Agent model/tool execution failed")
            fallback_date, _, _ = _backend_time_context(self.timezone)
            fallback = self._try_create_simple_ledger(user_id, text, fallback_date)
            if fallback is not None:
                return {
                    "reply": fallback["reply"],
                    "actions": [],
                    "changed_domains": [DOMAIN_LEDGER],
                }
            return {
                "reply": _friendly_model_error(str(exc)),
                "actions": [],
                "changed_domains": [],
            }

    def _try_create_simple_ledger(self, user_id: str, text: str, current_date: str) -> dict[str, Any] | None:
        inferred = _infer_simple_ledger(text, current_date)
        if inferred is None:
            return None
        item = self.store.create_ledger(
            user_id=user_id,
            amount=inferred["amount"],
            category=inferred["category"],
            note=inferred["note"],
            date=inferred["date"],
            entry_type=inferred["entry_type"],
        )
        type_label = "收入" if item["entryType"] == "income" else "支出"
        return {
            "ledger": item,
            "reply": f"已记录{type_label}：{item['date']} {item['category']} ¥{item['amount']:.2f}。",
        }

    def _mutate_schedules(
        self,
        session_id: str,
        user_id: str,
        changed_domains: set[str],
        operation: str,
        date: str,
        start_date: str,
        end_date: str,
        keyword: str,
        updates: dict[str, Any],
        all_matches: bool,
    ) -> str:
        candidates = self.store.list_schedules(user_id, date=date, start_date=start_date, end_date=end_date, keyword=keyword)
        if not candidates:
            return _json({"status": "not_found", "message": "没有找到匹配的日程。"})
        if operation == "update" and not updates:
            return _json({"status": "missing_updates", "message": "没有提供要修改的日程字段。"})
        ids = [item["id"] for item in candidates]
        if len(candidates) == 1 or all_matches:
            selected_ids = ids if all_matches else ids[:1]
            result = self.store.update_schedules(user_id, selected_ids, updates) if operation == "update" else self.store.delete_schedules(user_id, selected_ids)
            changed_domains.add(DOMAIN_SCHEDULE)
            return _json({"status": "updated" if operation == "update" else "deleted", "count": len(selected_ids), "result": result})
        pending = self.store.create_pending_operation(user_id, session_id, DOMAIN_SCHEDULE, operation, ids, updates)
        return _json(
            {
                "status": "needs_confirmation",
                "pendingId": pending["id"],
                "message": "找到多条匹配日程，请让用户确认编号、全部或取消。",
                "candidates": _numbered(candidates),
            }
        )

    def _mutate_ledgers(
        self,
        session_id: str,
        user_id: str,
        changed_domains: set[str],
        operation: str,
        date: str,
        start_date: str,
        end_date: str,
        keyword: str,
        entry_type: str,
        updates: dict[str, Any],
        all_matches: bool,
    ) -> str:
        candidates = self.store.list_ledgers(
            user_id,
            date=date,
            start_date=start_date,
            end_date=end_date,
            keyword=keyword,
            entry_type=entry_type,
        )
        if not candidates:
            return _json({"status": "not_found", "message": "没有找到匹配的账单。"})
        if operation == "update" and not updates:
            return _json({"status": "missing_updates", "message": "没有提供要修改的账单字段。"})
        ids = [item["id"] for item in candidates]
        if len(candidates) == 1 or all_matches:
            selected_ids = ids if all_matches else ids[:1]
            result = self.store.update_ledgers(user_id, selected_ids, updates) if operation == "update" else self.store.delete_ledgers(user_id, selected_ids)
            changed_domains.add(DOMAIN_LEDGER)
            return _json({"status": "updated" if operation == "update" else "deleted", "count": len(selected_ids), "result": result})
        pending = self.store.create_pending_operation(user_id, session_id, DOMAIN_LEDGER, operation, ids, updates)
        return _json(
            {
                "status": "needs_confirmation",
                "pendingId": pending["id"],
                "message": "找到多条匹配账单，请让用户确认编号、全部或取消。",
                "candidates": _numbered(candidates),
            }
        )

    def _confirm_pending(self, user_id: str, session_id: str, changed_domains: set[str], selection: str) -> str:
        pending = self.store.get_pending_operation(user_id, session_id)
        if pending is None:
            return _json({"status": "not_found", "message": "当前没有等待确认的操作。"})
        candidate_ids = pending["candidate_ids"]
        selected_ids = _select_candidate_ids(candidate_ids, selection)
        if not selected_ids:
            return _json({"status": "invalid_selection", "message": "没有识别到有效编号，请重新说明要操作第几条。"})

        domain = pending["domain"]
        operation = pending["operation"]
        updates = pending["updates"]
        if domain == DOMAIN_SCHEDULE:
            result = self.store.update_schedules(user_id, selected_ids, updates) if operation == "update" else self.store.delete_schedules(user_id, selected_ids)
        else:
            result = self.store.update_ledgers(user_id, selected_ids, updates) if operation == "update" else self.store.delete_ledgers(user_id, selected_ids)
        self.store.clear_pending_operation(user_id, session_id)
        changed_domains.add(domain)
        return _json({"status": "confirmed", "domain": domain, "operation": operation, "count": len(selected_ids), "result": result})

    async def summarize_context(
        self,
        existing_summary: str,
        summary_history: list[dict[str, str]] | None = None,
        model_config: dict[str, str] | None = None,
    ) -> str | None:
        history_text = _history_to_text(summary_history or [])
        if not history_text:
            return None

        api_key, base_url, model = self._resolve_runtime_config(model_config)
        if not api_key:
            return None

        llm = ChatOpenAI(
            api_key=api_key,
            base_url=base_url,
            model=model,
            temperature=0.0,
            timeout=self.request_timeout_sec,
            max_retries=1,
        )
        try:
            result = await asyncio.wait_for(
                llm.ainvoke(
                    [
                        SystemMessage(
                            content=(
                                "你负责维护 MyLife Agent 的会话上下文摘要。"
                                "请把已有摘要和新增对话合并为一段中文纯文本摘要，最多约 800 字。"
                                "重点保留用户偏好、已确认事实、未完成事项、日程/记账相关上下文，以及后续多轮指代需要的信息。"
                                "删除寒暄、重复内容、已经无用的过程描述。不要使用 Markdown、标题或列表。"
                            )
                        ),
                        HumanMessage(
                            content=(
                                f"已有摘要:\n{existing_summary.strip() or '暂无'}\n\n"
                                f"新增对话:\n{history_text}\n\n"
                                "请返回更新后的摘要。"
                            )
                        ),
                    ]
                ),
                timeout=self.request_timeout_sec,
            )
            summary = _message_content(result).strip()
            return summary[:1600] if summary else None
        except Exception:
            return None


date_cls = date


def _friendly_model_error(error_text: str) -> str:
    lowered = error_text.lower()
    if "api key" in lowered or "authentication" in lowered or "401" in lowered:
        return "模型 API Key 无效或没有权限，请检查设置页里的 Key 配置。"
    if "403" in lowered or "permission" in lowered or "access_terminated_error" in lowered:
        return "当前模型没有访问权限，请更换模型或使用有权限的 Key。"
    if "404" in lowered or "model does not exist" in lowered:
        return "模型名称或 Base URL 不正确，请检查后重试。"
    if "connection" in lowered or "dns" in lowered or "name or service not known" in lowered:
        return "模型服务当前不可达，请检查 Base URL 或网络连接。"
    if "tool_calls" in lowered or "tool call" in lowered or "function calling" in lowered or "tools" in lowered:
        return "当前模型或接口不支持工具调用，请在“我的”里更换支持 tool/function calling 的模型，或检查 Base URL 是否是 OpenAI 兼容的 /v1 接口。"
    if "rate limit" in lowered or "429" in lowered or "too many requests" in lowered:
        return "模型服务请求过于频繁或额度受限，请稍后重试。"
    if "timeout" in lowered or "timed out" in lowered:
        return "模型响应超时了，请稍后重试，或换一个更稳定的模型配置。"
    if os.getenv("AGENT_DEBUG_ERRORS", "").strip().lower() in {"1", "true", "yes", "on"}:
        return f"Agent 服务暂时不可用：{error_text[:300]}"
    return "Agent 服务暂时不可用，请稍后重试。"


def _safe_current_date(raw: str) -> str:
    value = raw.strip()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        return value
    return str(date_cls.today())


def _backend_time_context(timezone_name: str) -> tuple[str, str, str]:
    try:
        timezone = ZoneInfo(timezone_name)
        normalized_name = timezone_name
    except ZoneInfoNotFoundError:
        if timezone_name == "Asia/Shanghai":
            timezone = dt.timezone(dt.timedelta(hours=8))
            normalized_name = "Asia/Shanghai"
        else:
            try:
                timezone = ZoneInfo("Asia/Shanghai")
                normalized_name = "Asia/Shanghai"
            except ZoneInfoNotFoundError:
                timezone = dt.timezone(dt.timedelta(hours=8))
                normalized_name = "Asia/Shanghai"
    now = dt.datetime.now(timezone)
    return now.date().isoformat(), now.strftime("%H:%M"), normalized_name


def _resolve_relative_date(text: str, current_date: str) -> str:
    base = date_cls.fromisoformat(_safe_current_date(current_date))
    compact = text.replace(" ", "")
    mapping = [
        ("大后天", 3),
        ("后天", 2),
        ("明天", 1),
        ("昨天", -1),
        ("前天", -2),
        ("今天", 0),
        ("今晚", 0),
        ("今早", 0),
        ("上午", 0) if "今天" in compact else ("", 0),
    ]
    for keyword, offset in mapping:
        if keyword and keyword in compact:
            return (base + timedelta(days=offset)).isoformat()
    return ""


def _resolve_relative_update_date(text: str, current_date: str) -> str:
    if not any(marker in text for marker in ("改到", "改成", "调整到", "挪到", "延到", "提前到")):
        return ""
    return _resolve_relative_date(text, current_date)


def _resolve_relative_range(text: str, current_date: str) -> tuple[str, str] | None:
    base = date_cls.fromisoformat(_safe_current_date(current_date))
    compact = text.replace(" ", "")
    if "上周" in compact:
        start = base - timedelta(days=base.weekday() + 7)
        return start.isoformat(), (start + timedelta(days=6)).isoformat()
    if "本周" in compact or "这周" in compact:
        start = base - timedelta(days=base.weekday())
        return start.isoformat(), (start + timedelta(days=6)).isoformat()
    if "下周" in compact:
        start = base - timedelta(days=base.weekday()) + timedelta(days=7)
        return start.isoformat(), (start + timedelta(days=6)).isoformat()
    if "上个月" in compact or "上月" in compact:
        first = base.replace(day=1)
        last_month_end = first - timedelta(days=1)
        start = last_month_end.replace(day=1)
        return start.isoformat(), last_month_end.isoformat()
    if "本月" in compact or "这个月" in compact:
        start = base.replace(day=1)
        next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
        return start.isoformat(), (next_month - timedelta(days=1)).isoformat()
    return None


def _select_candidate_ids(candidate_ids: list[str], selection: str) -> list[str]:
    text = selection.strip().lower()
    if text in {"all", "全部", "所有", "都"}:
        return candidate_ids
    numbers = [int(value) for value in re.findall(r"\d+", text)]
    selected = []
    for number in numbers or [1]:
        index = number - 1
        if 0 <= index < len(candidate_ids):
            selected.append(candidate_ids[index])
    return list(dict.fromkeys(selected))


def _clean_updates(updates: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in updates.items()
        if value is not None and str(value).strip() != ""
    }


def _infer_simple_ledger(text: str, current_date: str) -> dict[str, Any] | None:
    normalized = text.strip()
    if not normalized:
        return None
    amount_match = re.search(r"(?:¥|￥)?\s*(\d+(?:\.\d{1,2})?)\s*(?:元|块|rmb|RMB)?", normalized)
    if amount_match is None:
        return None
    lowered = normalized.lower()
    has_ledger_intent = any(
        keyword in normalized
        for keyword in ("花了", "花费", "支出", "消费", "买", "付了", "付款", "收入", "入账", "到账", "工资", "报销")
    )
    if not has_ledger_intent:
        return None
    amount = float(amount_match.group(1))
    if amount <= 0:
        return None
    entry_type = "income" if any(keyword in normalized for keyword in ("收入", "入账", "到账", "工资", "报销", "奖金")) else "expense"
    note = _clean_ledger_note(normalized, amount_match.group(0))
    return {
        "amount": amount,
        "category": _infer_ledger_category(normalized, entry_type),
        "note": note,
        "date": _resolve_relative_date(normalized, current_date) or current_date,
        "entry_type": entry_type,
    }


def _clean_ledger_note(text: str, amount_text: str) -> str:
    note = text.replace(amount_text, " ")
    for keyword in ("帮我", "记一下", "记录一下", "记账", "已记录", "花了", "花费", "支出", "消费", "收入", "入账", "到账", "元", "块"):
        note = note.replace(keyword, " ")
    note = re.sub(r"\s+", " ", note).strip(" ，。,.")
    return note[:40]


def _infer_ledger_category(text: str, entry_type: str) -> str:
    if entry_type == "income":
        if any(keyword in text for keyword in ("工资", "薪资")):
            return "工资"
        if any(keyword in text for keyword in ("奖金", "提成", "年终奖")):
            return "奖金"
        if "报销" in text:
            return "报销"
        return "其他"
    category_keywords = [
        ("餐饮", ("饭", "午餐", "晚餐", "早餐", "外卖", "咖啡", "奶茶", "吃", "餐")),
        ("交通", ("打车", "地铁", "公交", "高铁", "火车", "机票", "油费", "停车")),
        ("购物", ("买", "购物", "衣服", "数码", "网购")),
        ("住房", ("房租", "水电", "燃气", "物业", "宽带")),
        ("医疗", ("医院", "买药", "药", "体检", "看病")),
        ("娱乐", ("电影", "游戏", "ktv", "聚会", "娱乐")),
        ("旅行", ("旅游", "酒店", "机票", "景点")),
    ]
    for category, keywords in category_keywords:
        if any(keyword in text for keyword in keywords):
            return category
    return "其他"


def _numbered(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{"index": index + 1, **item} for index, item in enumerate(items[:20])]


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False)


def _history_to_text(history: list[dict[str, str]]) -> str:
    lines: list[str] = []
    for item in history:
        role = (item.get("role") or "").strip()
        content = (item.get("content") or "").strip()
        if not role or not content:
            continue
        label = "用户" if role.lower() == "user" else "助手"
        lines.append(f"{label}: {content}")
    return "\n".join(lines)


def _message_content(message: Any) -> str:
    content = getattr(message, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(str(item) for item in content)
    return str(content)
