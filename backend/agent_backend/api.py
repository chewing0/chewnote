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
    ChangePasswordRequest,
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
        model_config = req.runtime_config.model_dump() if req.runtime_config is not None else None
        return await orchestrator.handle(
            user_id=user["id"],
            text=req.text,
            session_id=req.session_id,
            history=req.history,
            model_config=model_config,
            context_summary=req.context_summary,
            summary_history=req.summary_history,
        )

    return app


def _bearer_token(authorization: str) -> str:
    value = authorization.strip()
    if not value.lower().startswith("bearer "):
        return ""
    return value[7:].strip()


app = create_app()
