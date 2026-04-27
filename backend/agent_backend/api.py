from __future__ import annotations

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from .orchestrator import AgentOrchestrator
from .schemas import (
    AgentRequest,
    AgentResponse,
    LedgerRecord,
    LedgerUpdate,
    ScheduleRecord,
    ScheduleUpdate,
    SyncResponse,
)
from .storage import AgentStore

load_dotenv()


def create_app() -> FastAPI:
    app = FastAPI(title="TimePaper Agent Backend", version="0.1.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    store = AgentStore()
    orchestrator = AgentOrchestrator(store=store)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/sync", response_model=SyncResponse)
    async def sync_data() -> SyncResponse:
        data = store.replace_cache_source()
        return SyncResponse(schedules=data["schedules"], ledgers=data["ledgers"])

    @app.get("/schedules", response_model=list[ScheduleRecord])
    async def list_schedules(
        date: str = "",
        startDate: str = "",
        endDate: str = "",
        keyword: str = "",
    ) -> list[ScheduleRecord]:
        return store.list_schedules(date=date, start_date=startDate, end_date=endDate, keyword=keyword)

    @app.post("/schedules", response_model=ScheduleRecord)
    async def create_schedule(item: ScheduleRecord) -> ScheduleRecord:
        return store.create_schedule(item.title, item.date, item.time, item.note)

    @app.patch("/schedules/{item_id}", response_model=ScheduleRecord)
    async def update_schedule(item_id: str, item: ScheduleUpdate) -> ScheduleRecord:
        updated = store.update_schedules([item_id], item.model_dump(exclude_none=True))
        if not updated:
            raise HTTPException(status_code=404, detail="Schedule not found")
        return updated[0]

    @app.delete("/schedules/{item_id}")
    async def delete_schedule(item_id: str) -> dict[str, int]:
        deleted = store.delete_schedules([item_id])
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
    ) -> list[LedgerRecord]:
        return store.list_ledgers(
            date=date,
            start_date=startDate,
            end_date=endDate,
            keyword=keyword,
            entry_type=entryType,
        )

    @app.post("/ledgers", response_model=LedgerRecord)
    async def create_ledger(item: LedgerRecord) -> LedgerRecord:
        return store.create_ledger(item.amount, item.category, item.note, item.date, item.entryType)

    @app.patch("/ledgers/{entry_id}", response_model=LedgerRecord)
    async def update_ledger(entry_id: str, item: LedgerUpdate) -> LedgerRecord:
        updates = item.model_dump(exclude_none=True)
        if "entryType" in updates:
            updates["entry_type"] = updates.pop("entryType")
        updated = store.update_ledgers([entry_id], updates)
        if not updated:
            raise HTTPException(status_code=404, detail="Ledger not found")
        return updated[0]

    @app.delete("/ledgers/{entry_id}")
    async def delete_ledger(entry_id: str) -> dict[str, int]:
        deleted = store.delete_ledgers([entry_id])
        if deleted == 0:
            raise HTTPException(status_code=404, detail="Ledger not found")
        return {"deleted": deleted}

    @app.post("/agent/process", response_model=AgentResponse)
    async def process_agent(req: AgentRequest) -> AgentResponse:
        model_config = req.runtime_config.model_dump() if req.runtime_config is not None else None
        return await orchestrator.handle(
            text=req.text,
            session_id=req.session_id,
            history=req.history,
            model_config=model_config,
            context_summary=req.context_summary,
            summary_history=req.summary_history,
        )

    return app


app = create_app()
