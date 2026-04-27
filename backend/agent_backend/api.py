from __future__ import annotations

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .orchestrator import AgentOrchestrator
from .schemas import AgentRequest, AgentResponse

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

    orchestrator = AgentOrchestrator()

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/agent/process", response_model=AgentResponse)
    async def process_agent(req: AgentRequest) -> AgentResponse:
        model_config = req.runtime_config.model_dump() if req.runtime_config is not None else None
        return await orchestrator.handle(
            text=req.text,
            history=req.history,
            model_config=model_config,
            context_summary=req.context_summary,
            summary_history=req.summary_history,
        )

    return app


app = create_app()
