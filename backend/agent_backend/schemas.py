from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class ModelConfig(BaseModel):
    base_url: str = Field(default="", description="LLM API base URL")
    model: str = Field(default="", description="LLM model name")
    api_key: str = Field(default="", description="LLM API key")


class AgentRequest(BaseModel):
    text: str = Field(min_length=1)
    history: list[dict[str, str]] = Field(default_factory=list)
    context_summary: str = ""
    summary_history: list[dict[str, str]] = Field(default_factory=list)
    runtime_config: ModelConfig | None = Field(default=None, alias="model_config")


class AgentAction(BaseModel):
    type: Literal["add_schedule", "add_ledger"]
    payload: dict[str, Any]


class AgentResponse(BaseModel):
    reply: str
    actions: list[AgentAction]
    context_summary: str | None = None


class ScheduleToolInput(BaseModel):
    title: str = Field(description="日程标题")
    date: str = Field(description="日期，格式 YYYY-MM-DD")
    time: str = Field(description="时间，格式 HH:mm")
    note: str = Field(default="", description="日程备注")


class ScheduleSeriesToolInput(BaseModel):
    title: str = Field(description="日程标题")
    startDate: str = Field(description="开始日期，格式 YYYY-MM-DD")
    time: str = Field(description="时间，格式 HH:mm")
    days: int = Field(description="连续天数，例如 7")
    note: str = Field(default="", description="日程备注")


class LedgerToolInput(BaseModel):
    amount: float | None = None
    category: str = Field(default="其他", description="账单分类")
    note: str = Field(default="", description="账单备注")
    date: str = Field(description="日期，格式 YYYY-MM-DD")
    entryType: str = Field(default="expense", description="expense 或 income")
