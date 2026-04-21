from __future__ import annotations

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
        return AgentResponse(reply=reply, actions=actions)
