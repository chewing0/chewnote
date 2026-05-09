from __future__ import annotations

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from .auth import AuthError, AuthService, public_user
from .orchestrator import AgentOrchestrator
from .schemas import (
    AgentRequest,
    AgentResponse,
    AuthResponse,
    AuthUser,
    BackendModelStatus,
    ChatMessageRecord,
    ChangePasswordRequest,
    ConversationCreate,
    ConversationRecord,
    ConversationUpdate,
    ForgotPasswordRequest,
    ForgotPasswordResponse,
    LedgerRecord,
    LedgerUpdate,
    LoginRequest,
    RefreshTokenRequest,
    RegisterRequest,
    ResetPasswordRequest,
    ScheduleRecord,
    ScheduleUpdate,
    SyncImportRequest,
    SyncResponse,
)
from .storage import AgentStore

load_dotenv()

RECENT_CONTEXT_MESSAGE_LIMIT = 12
SUMMARY_REFRESH_THRESHOLD = 8
SUMMARY_SOURCE_MESSAGE_LIMIT = 40
SUMMARY_SOURCE_CHAR_LIMIT = 12_000


def create_app() -> FastAPI:
    app = FastAPI(title="TimePaper Agent Backend", version="0.2.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    store = AgentStore()
    auth = AuthService(store)
    orchestrator = AgentOrchestrator(store=store)

    def current_user(authorization: str = Header(default="")) -> dict:
        token = _bearer_token(authorization)
        if not token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="请先登录")
        try:
            return auth.verify_access_token(token)
        except AuthError as exc:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(exc)) from exc

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/config/model", response_model=BackendModelStatus)
    async def model_config_status() -> BackendModelStatus:
        return BackendModelStatus(**orchestrator.llm.public_model_status())

    @app.post("/auth/register", response_model=AuthResponse)
    async def register(req: RegisterRequest) -> AuthResponse:
        try:
            return AuthResponse(**auth.register(req.username, req.email, req.password))
        except AuthError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        except ValueError as exc:
            raise HTTPException(status_code=409, detail="用户名或邮箱已存在") from exc

    @app.post("/auth/login", response_model=AuthResponse)
    async def login(req: LoginRequest) -> AuthResponse:
        try:
            return AuthResponse(**auth.login(req.identifier, req.password))
        except AuthError as exc:
            raise HTTPException(status_code=401, detail=str(exc)) from exc

    @app.post("/auth/refresh", response_model=AuthResponse)
    async def refresh(req: RefreshTokenRequest) -> AuthResponse:
        try:
            return AuthResponse(**auth.refresh(req.refreshToken))
        except AuthError as exc:
            raise HTTPException(status_code=401, detail=str(exc)) from exc

    @app.post("/auth/logout")
    async def logout(req: RefreshTokenRequest) -> dict[str, str]:
        auth.logout(req.refreshToken)
        return {"status": "ok"}

    @app.get("/auth/me", response_model=AuthUser)
    async def me(user: dict = Depends(current_user)) -> AuthUser:
        return AuthUser(**public_user(user))

    @app.post("/auth/password/forgot", response_model=ForgotPasswordResponse)
    async def forgot_password(req: ForgotPasswordRequest) -> ForgotPasswordResponse:
        return ForgotPasswordResponse(**auth.forgot_password(req.email))

    @app.post("/auth/password/reset")
    async def reset_password(req: ResetPasswordRequest) -> dict[str, str]:
        try:
            auth.reset_password(req.token, req.newPassword)
        except AuthError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return {"status": "ok"}

    @app.post("/auth/password/change")
    async def change_password(req: ChangePasswordRequest, user: dict = Depends(current_user)) -> dict[str, str]:
        try:
            auth.change_password(user["id"], req.oldPassword, req.newPassword)
        except AuthError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return {"status": "ok"}

    @app.get("/sync", response_model=SyncResponse)
    async def sync_data(user: dict = Depends(current_user)) -> SyncResponse:
        data = store.replace_cache_source(user["id"])
        return SyncResponse(schedules=data["schedules"], ledgers=data["ledgers"])

    @app.post("/sync/import", response_model=SyncResponse)
    async def import_sync(req: SyncImportRequest, user: dict = Depends(current_user)) -> SyncResponse:
        store.import_schedules(user["id"], [item.model_dump() for item in req.schedules])
        store.import_ledgers(user["id"], [item.model_dump() for item in req.ledgers])
        data = store.replace_cache_source(user["id"])
        return SyncResponse(schedules=data["schedules"], ledgers=data["ledgers"])

    @app.get("/conversations", response_model=list[ConversationRecord])
    async def list_conversations(user: dict = Depends(current_user)) -> list[ConversationRecord]:
        return store.list_conversations(user["id"])

    @app.post("/conversations", response_model=ConversationRecord)
    async def create_conversation(req: ConversationCreate, user: dict = Depends(current_user)) -> ConversationRecord:
        return store.create_conversation(user["id"], req.title)

    @app.patch("/conversations/{conversation_id}", response_model=ConversationRecord)
    async def update_conversation(
        conversation_id: str,
        req: ConversationUpdate,
        user: dict = Depends(current_user),
    ) -> ConversationRecord:
        updated = store.update_conversation(user["id"], conversation_id, req.title or "")
        if updated is None:
            raise HTTPException(status_code=404, detail="Conversation not found")
        return updated

    @app.delete("/conversations/{conversation_id}")
    async def delete_conversation(conversation_id: str, user: dict = Depends(current_user)) -> dict[str, int]:
        deleted = store.delete_conversation(user["id"], conversation_id)
        if deleted == 0:
            raise HTTPException(status_code=404, detail="Conversation not found")
        return {"deleted": deleted}

    @app.get("/conversations/{conversation_id}/messages", response_model=list[ChatMessageRecord])
    async def list_conversation_messages(
        conversation_id: str,
        user: dict = Depends(current_user),
    ) -> list[ChatMessageRecord]:
        if store.get_conversation(user["id"], conversation_id) is None:
            raise HTTPException(status_code=404, detail="Conversation not found")
        return store.list_chat_messages(user["id"], conversation_id)

    @app.delete("/conversations/{conversation_id}/messages/{message_id}")
    async def delete_conversation_message(
        conversation_id: str,
        message_id: str,
        user: dict = Depends(current_user),
    ) -> dict[str, int]:
        deleted = store.delete_chat_message(user["id"], conversation_id, message_id)
        if deleted == 0:
            raise HTTPException(status_code=404, detail="Message not found")
        return {"deleted": deleted}

    @app.get("/schedules", response_model=list[ScheduleRecord])
    async def list_schedules(
        date: str = "",
        startDate: str = "",
        endDate: str = "",
        keyword: str = "",
        user: dict = Depends(current_user),
    ) -> list[ScheduleRecord]:
        return store.list_schedules(user["id"], date=date, start_date=startDate, end_date=endDate, keyword=keyword)

    @app.post("/schedules", response_model=ScheduleRecord)
    async def create_schedule(item: ScheduleRecord, user: dict = Depends(current_user)) -> ScheduleRecord:
        return store.create_schedule(user["id"], item.title, item.date, item.time, item.note)

    @app.patch("/schedules/{item_id}", response_model=ScheduleRecord)
    async def update_schedule(item_id: str, item: ScheduleUpdate, user: dict = Depends(current_user)) -> ScheduleRecord:
        updated = store.update_schedules(user["id"], [item_id], item.model_dump(exclude_none=True))
        if not updated:
            raise HTTPException(status_code=404, detail="Schedule not found")
        return updated[0]

    @app.delete("/schedules/{item_id}")
    async def delete_schedule(item_id: str, user: dict = Depends(current_user)) -> dict[str, int]:
        deleted = store.delete_schedules(user["id"], [item_id])
        if deleted == 0:
            raise HTTPException(status_code=404, detail="Schedule not found")
        return {"deleted": deleted}

    @app.get("/ledgers", response_model=list[LedgerRecord])
    async def list_ledgers(
        date: str = "",
        startDate: str = "",
        endDate: str = "",
        keyword: str = "",
        entryType: str = "",
        user: dict = Depends(current_user),
    ) -> list[LedgerRecord]:
        return store.list_ledgers(
            user["id"],
            date=date,
            start_date=startDate,
            end_date=endDate,
            keyword=keyword,
            entry_type=entryType,
        )

    @app.post("/ledgers", response_model=LedgerRecord)
    async def create_ledger(item: LedgerRecord, user: dict = Depends(current_user)) -> LedgerRecord:
        return store.create_ledger(user["id"], item.amount, item.category, item.note, item.date, item.entryType)

    @app.patch("/ledgers/{entry_id}", response_model=LedgerRecord)
    async def update_ledger(entry_id: str, item: LedgerUpdate, user: dict = Depends(current_user)) -> LedgerRecord:
        updates = item.model_dump(exclude_none=True)
        if "entryType" in updates:
            updates["entry_type"] = updates.pop("entryType")
        updated = store.update_ledgers(user["id"], [entry_id], updates)
        if not updated:
            raise HTTPException(status_code=404, detail="Ledger not found")
        return updated[0]

    @app.delete("/ledgers/{entry_id}")
    async def delete_ledger(entry_id: str, user: dict = Depends(current_user)) -> dict[str, int]:
        deleted = store.delete_ledgers(user["id"], [entry_id])
        if deleted == 0:
            raise HTTPException(status_code=404, detail="Ledger not found")
        return {"deleted": deleted}

    @app.post("/agent/process", response_model=AgentResponse)
    async def process_agent(req: AgentRequest, user: dict = Depends(current_user)) -> AgentResponse:
        # Model settings are intentionally backend-owned. Ignore request-level
        # model_config so Android and backend cannot drift into different models.
        model_config = None

        if req.session_id == "connection-test":
            return await orchestrator.handle(
                user_id=user["id"],
                text=req.text,
                session_id=req.session_id,
                history=[],
                model_config=model_config,
                context_summary="",
                summary_history=[],
            )

        conversation = _ensure_conversation(store, user["id"], req.conversation_id, req.text)
        messages_before = store.list_chat_messages(user["id"], conversation["id"])
        snapshot = store.get_context_snapshot(user["id"], conversation["id"])
        context = _build_context_request(messages_before, snapshot)
        store.append_chat_message(user["id"], conversation["id"], role="user", content=req.text)

        response = await orchestrator.handle(
            user_id=user["id"],
            text=req.text,
            session_id=conversation["id"],
            history=context["history"],
            model_config=model_config,
            context_summary=context["context_summary"],
            summary_history=context["summary_history"],
        )
        if response.context_summary and context["summary_history"]:
            store.update_context_snapshot(
                user["id"],
                conversation["id"],
                response.context_summary,
                context["next_summarized_message_count"],
            )
        if response.reply.strip():
            store.append_chat_message(user["id"], conversation["id"], role="assistant", content=response.reply)
        response.conversation_id = conversation["id"]
        return response

    return app


def _bearer_token(authorization: str) -> str:
    value = authorization.strip()
    if not value.lower().startswith("bearer "):
        return ""
    return value[7:].strip()


def _ensure_conversation(store: AgentStore, user_id: str, conversation_id: str, text: str) -> dict:
    conversation = store.get_conversation(user_id, conversation_id)
    if conversation is not None:
        return conversation
    return store.create_conversation(user_id, _title_from_text(text))


def _title_from_text(text: str) -> str:
    value = " ".join(text.strip().split())
    if not value:
        return "新对话"
    return value[:18]


def _build_context_request(messages: list[dict], snapshot: dict) -> dict[str, object]:
    payloads = [
        {"role": item.get("role", ""), "content": item.get("content", "")}
        for item in messages
        if item.get("kind", "MESSAGE") == "MESSAGE" and item.get("content", "").strip()
    ]
    recent_start = max(0, len(payloads) - RECENT_CONTEXT_MESSAGE_LIMIT)
    history = payloads[recent_start:]
    summarized_count = min(max(int(snapshot.get("summarizedMessageCount") or 0), 0), len(payloads))
    unsummarized_start = min(summarized_count, recent_start)
    unsummarized = payloads[unsummarized_start:recent_start] if recent_start > unsummarized_start else []
    summary_history = _take_for_summary(unsummarized) if len(unsummarized) >= SUMMARY_REFRESH_THRESHOLD else []
    return {
        "history": history,
        "context_summary": snapshot.get("summary", ""),
        "summary_history": summary_history,
        "next_summarized_message_count": unsummarized_start + len(summary_history),
    }


def _take_for_summary(messages: list[dict[str, str]]) -> list[dict[str, str]]:
    selected: list[dict[str, str]] = []
    total_chars = 0
    for message in messages[:SUMMARY_SOURCE_MESSAGE_LIMIT]:
        role = message.get("role", "")
        content = message.get("content", "")
        message_chars = len(role) + len(content)
        if not selected and message_chars > SUMMARY_SOURCE_CHAR_LIMIT:
            allowed = max(0, SUMMARY_SOURCE_CHAR_LIMIT - len(role))
            selected.append({"role": role, "content": content[:allowed]})
            break
        if selected and total_chars + message_chars > SUMMARY_SOURCE_CHAR_LIMIT:
            break
        selected.append({"role": role, "content": content})
        total_chars += message_chars
    return selected


app = create_app()
