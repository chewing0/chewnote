from __future__ import annotations

import re

from .llm_client import LLMClient
from .schemas import AgentAction, AgentResponse


class AgentOrchestrator:
    def __init__(self) -> None:
        self.llm = LLMClient()

    async def handle(
        self,
        text: str,
        history: list[dict[str, str]] | None = None,
        model_config: dict[str, str] | None = None,
    ) -> AgentResponse:
        parsed = await self.llm.parse(text, history, model_config)
        direct_actions = parsed.get("actions") if isinstance(parsed, dict) else None
        actions = [
            AgentAction(type=item["type"], payload=item["payload"])
            for item in (direct_actions or [])
            if isinstance(item, dict)
            and item.get("type") in {"add_schedule", "add_ledger"}
            and isinstance(item.get("payload"), dict)
        ]

        reply = parsed.get("reply") if isinstance(parsed.get("reply"), str) else ""
        if not reply.strip():
            reply = (
                f"已处理 {len(actions)} 条任务，已同步到日程/记账。"
                if actions
                else "好的，我在这儿。你可以继续聊，也可以让我记账或安排日程。"
            )
        return AgentResponse(reply=_plain_text_reply(reply), actions=actions)


def _plain_text_reply(text: str) -> str:
    cleaned = text.replace("\r\n", "\n")
    cleaned = re.sub(r"```[\s\S]*?```", "", cleaned)
    cleaned = re.sub(r"`([^`]*)`", r"\1", cleaned)
    cleaned = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1", cleaned)
    cleaned = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", r"\1", cleaned)
    cleaned = re.sub(r"(^|\n)\s{0,3}#{1,6}\s*", r"\1", cleaned)
    cleaned = re.sub(r"(^|\n)\s*>\s?", r"\1", cleaned)
    cleaned = re.sub(r"(^|\n)\s*[-*+]\s+", r"\1", cleaned)
    cleaned = re.sub(r"(^|\n)\s*\d+\.\s+", r"\1", cleaned)
    cleaned = re.sub(r"(\*\*|__|\*|_)", "", cleaned)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()
