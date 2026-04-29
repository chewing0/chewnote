from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class ModelConfig(BaseModel):
    base_url: str = Field(default="", description="LLM API base URL")
    model: str = Field(default="", description="LLM model name")
    api_key: str = Field(default="", description="LLM API key")


class AgentRequest(BaseModel):
    text: str = Field(min_length=1)
    session_id: str = ""
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
    changed_domains: list[str] = Field(default_factory=list)


class ScheduleRecord(BaseModel):
    id: str = ""
    title: str
    date: str
    time: str = "09:00"
    note: str = ""
    createdAt: int = 0
    updatedAt: int = 0


class ScheduleUpdate(BaseModel):
    title: str | None = None
    date: str | None = None
    time: str | None = None
    note: str | None = None


class LedgerRecord(BaseModel):
    id: str = ""
    amount: float
    category: str
    note: str = ""
    date: str
    entryType: str = "expense"
    createdAt: int = 0
    updatedAt: int = 0


class LedgerUpdate(BaseModel):
    amount: float | None = None
    category: str | None = None
    note: str | None = None
    date: str | None = None
    entryType: str | None = None


class SyncResponse(BaseModel):
    schedules: list[ScheduleRecord] = Field(default_factory=list)
    ledgers: list[LedgerRecord] = Field(default_factory=list)


class SyncImportRequest(BaseModel):
    schedules: list[ScheduleRecord] = Field(default_factory=list)
    ledgers: list[LedgerRecord] = Field(default_factory=list)


class AuthUser(BaseModel):
    id: str
    username: str
    email: str
    createdAt: int = 0
    updatedAt: int = 0


class AuthResponse(BaseModel):
    user: AuthUser
    accessToken: str
    refreshToken: str
    expiresIn: int


class RegisterRequest(BaseModel):
    username: str
    email: str
    password: str


class LoginRequest(BaseModel):
    identifier: str
    password: str


class RefreshTokenRequest(BaseModel):
    refreshToken: str


class ForgotPasswordRequest(BaseModel):
    email: str


class ForgotPasswordResponse(BaseModel):
    message: str
    devResetToken: str | None = None


class ResetPasswordRequest(BaseModel):
    token: str
    newPassword: str


class ChangePasswordRequest(BaseModel):
    oldPassword: str
    newPassword: str


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


class ScheduleQueryToolInput(BaseModel):
    date: str = Field(default="", description="单日查询日期，格式 YYYY-MM-DD")
    startDate: str = Field(default="", description="开始日期，格式 YYYY-MM-DD")
    endDate: str = Field(default="", description="结束日期，格式 YYYY-MM-DD")
    keyword: str = Field(default="", description="标题或备注关键词，例如 会议")


class ScheduleUpdateToolInput(ScheduleQueryToolInput):
    title: str = Field(default="", description="新标题，留空表示不修改")
    newDate: str = Field(default="", description="新日期，格式 YYYY-MM-DD，留空表示不修改")
    newTime: str = Field(default="", description="新时间，格式 HH:mm，留空表示不修改")
    note: str = Field(default="", description="新备注，留空表示不修改")
    all: bool = Field(default=False, description="用户明确要求全部匹配项时为 true")


class ScheduleDeleteToolInput(ScheduleQueryToolInput):
    all: bool = Field(default=False, description="用户明确要求全部匹配项时为 true")


class LedgerQueryToolInput(BaseModel):
    date: str = Field(default="", description="单日查询日期，格式 YYYY-MM-DD")
    startDate: str = Field(default="", description="开始日期，格式 YYYY-MM-DD")
    endDate: str = Field(default="", description="结束日期，格式 YYYY-MM-DD")
    keyword: str = Field(default="", description="分类或备注关键词，例如 咖啡")
    entryType: str = Field(default="", description="expense、income 或留空")


class LedgerSummaryToolInput(BaseModel):
    startDate: str = Field(description="开始日期，格式 YYYY-MM-DD")
    endDate: str = Field(description="结束日期，格式 YYYY-MM-DD")
    entryType: str = Field(default="expense", description="expense、income 或留空")
    keyword: str = Field(default="", description="分类或备注关键词")


class LedgerUpdateToolInput(LedgerQueryToolInput):
    amount: float | None = Field(default=None, description="新金额，留空表示不修改")
    category: str = Field(default="", description="新分类，留空表示不修改")
    note: str = Field(default="", description="新备注，留空表示不修改")
    newDate: str = Field(default="", description="新日期，格式 YYYY-MM-DD，留空表示不修改")
    newEntryType: str = Field(default="", description="新类型 expense 或 income，留空表示不修改")
    all: bool = Field(default=False, description="用户明确要求全部匹配项时为 true")


class LedgerDeleteToolInput(LedgerQueryToolInput):
    all: bool = Field(default=False, description="用户明确要求全部匹配项时为 true")


class ConfirmPendingToolInput(BaseModel):
    selection: str = Field(default="1", description="确认项，例如 1、1,3、全部")


class CancelPendingToolInput(BaseModel):
    reason: str = Field(default="", description="取消原因，可留空")
