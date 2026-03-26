from __future__ import annotations

import re

from .llm_client import LLMClient
from .schemas import AgentAction, AgentResponse


class AgentOrchestrator:
    def __init__(self) -> None:
        self.llm = LLMClient()

    async def handle(self, text: str, history: list[dict[str, str]] | None = None) -> AgentResponse:
        parsed = await self.llm.parse(text, history)
        direct_actions = parsed.get("actions") if isinstance(parsed, dict) else None
        actions = [
            AgentAction(type=item["type"], payload=item["payload"])
            for item in (direct_actions or [])
            if isinstance(item, dict)
            and item.get("type") in {"add_schedule", "add_ledger"}
            and isinstance(item.get("payload"), dict)
        ]
        actions = self._align_schedule_titles_with_user_text(actions, text)

        reply = parsed.get("reply") if isinstance(parsed.get("reply"), str) else ""
        if not reply.strip():
            reply = (
                f"已处理 {len(actions)} 条任务，已同步到日程/记账。"
                if actions
                else "好的，我在这儿。你可以继续聊，也可以让我记账或安排日程。"
            )
        return AgentResponse(reply=reply, actions=actions)

    def _align_schedule_titles_with_user_text(
        self,
        actions: list[AgentAction],
        user_text: str,
    ) -> list[AgentAction]:
        keyword = self._extract_schedule_keyword(user_text)
        if not keyword:
            return actions

        updated: list[AgentAction] = []
        for action in actions:
            if action.type != "add_schedule":
                updated.append(action)
                continue

            title = str(action.payload.get("title") or "").strip()
            if keyword in title:
                updated.append(action)
                continue

            payload = dict(action.payload)
            payload["title"] = keyword
            updated.append(AgentAction(type=action.type, payload=payload))
        return updated

    def _extract_schedule_keyword(self, text: str) -> str | None:
        patterns = [
            r"(组会)",
            r"(例会)",
            r"(周会)",
            r"(晨会)",
            r"(开会|会议)",
            r"(上课|课程)",
            r"(党课)",
        ]
        for pattern in patterns:
            match = re.search(pattern, text)
            if match:
                return match.group(1)
        return None
