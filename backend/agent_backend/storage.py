from __future__ import annotations

import json
import sqlite3
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from collections.abc import Iterator
from typing import Any


DOMAIN_LEDGER = "ledger"
DOMAIN_SCHEDULE = "schedule"


class AgentStore:
    def __init__(self, db_path: str | Path | None = None) -> None:
        backend_root = Path(__file__).resolve().parents[1]
        self.db_path = Path(db_path) if db_path is not None else backend_root / "data" / "agent.db"
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.init_db()

    def init_db(self) -> None:
        with self._connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS schedule_items (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    date TEXT NOT NULL,
                    time TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_schedule_date ON schedule_items(date);

                CREATE TABLE IF NOT EXISTS ledger_entries (
                    id TEXT PRIMARY KEY,
                    amount REAL NOT NULL,
                    category TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    date TEXT NOT NULL,
                    entry_type TEXT NOT NULL DEFAULT 'expense',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_ledger_date ON ledger_entries(date);
                CREATE INDEX IF NOT EXISTS idx_ledger_entry_type ON ledger_entries(entry_type);

                CREATE TABLE IF NOT EXISTS pending_agent_operations (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    candidate_ids_json TEXT NOT NULL,
                    updates_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_pending_session ON pending_agent_operations(session_id, expires_at);
                """
            )

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        conn = sqlite3.connect(self.db_path, timeout=10)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
        try:
            yield conn
            conn.commit()
        finally:
            conn.close()

    def create_schedule(self, title: str, date: str, item_time: str, note: str = "") -> dict[str, Any]:
        now = _now_ms()
        item = {
            "id": str(uuid.uuid4()),
            "title": title.strip() or "未命名日程",
            "date": date,
            "time": item_time or "09:00",
            "note": note.strip(),
            "created_at": now,
            "updated_at": now,
        }
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO schedule_items(id, title, date, time, note, created_at, updated_at)
                VALUES (:id, :title, :date, :time, :note, :created_at, :updated_at)
                """,
                item,
            )
        return _schedule_to_wire(item)

    def list_schedules(
        self,
        date: str = "",
        start_date: str = "",
        end_date: str = "",
        keyword: str = "",
        ids: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        clauses: list[str] = []
        params: dict[str, Any] = {}
        if ids is not None:
            if not ids:
                return []
            placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
            clauses.append(f"id IN ({placeholders})")
            params.update({f"id_{index}": item_id for index, item_id in enumerate(ids)})
        if date:
            clauses.append("date = :date")
            params["date"] = date
        if start_date:
            clauses.append("date >= :start_date")
            params["start_date"] = start_date
        if end_date:
            clauses.append("date <= :end_date")
            params["end_date"] = end_date
        if keyword:
            clauses.append("(title LIKE :keyword OR note LIKE :keyword)")
            params["keyword"] = f"%{keyword.strip()}%"
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        with self._connect() as conn:
            rows = conn.execute(
                f"""
                SELECT * FROM schedule_items
                {where}
                ORDER BY date ASC, time ASC, created_at ASC
                """,
                params,
            ).fetchall()
        return [_schedule_to_wire(dict(row)) for row in rows]

    def update_schedules(self, ids: list[str], updates: dict[str, Any]) -> list[dict[str, Any]]:
        allowed = {"title", "date", "time", "note"}
        clean_updates = {
            key: value
            for key, value in updates.items()
            if key in allowed and value is not None and str(value).strip() != ""
        }
        if not ids or not clean_updates:
            return self.list_schedules(ids=ids)
        clean_updates["updated_at"] = _now_ms()
        assignments = ", ".join(f"{key} = :{key}" for key in clean_updates)
        params = dict(clean_updates)
        params.update({f"id_{index}": item_id for index, item_id in enumerate(ids)})
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            conn.execute(
                f"UPDATE schedule_items SET {assignments} WHERE id IN ({placeholders})",
                params,
            )
        return self.list_schedules(ids=ids)

    def delete_schedules(self, ids: list[str]) -> int:
        if not ids:
            return 0
        params = {f"id_{index}": item_id for index, item_id in enumerate(ids)}
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            cursor = conn.execute(f"DELETE FROM schedule_items WHERE id IN ({placeholders})", params)
            return cursor.rowcount

    def create_ledger(
        self,
        amount: float,
        category: str,
        note: str,
        date: str,
        entry_type: str = "expense",
    ) -> dict[str, Any]:
        now = _now_ms()
        item = {
            "id": str(uuid.uuid4()),
            "amount": float(amount),
            "category": category.strip() or "其他",
            "note": note.strip(),
            "date": date,
            "entry_type": _normalize_entry_type(entry_type),
            "created_at": now,
            "updated_at": now,
        }
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO ledger_entries(id, amount, category, note, date, entry_type, created_at, updated_at)
                VALUES (:id, :amount, :category, :note, :date, :entry_type, :created_at, :updated_at)
                """,
                item,
            )
        return _ledger_to_wire(item)

    def list_ledgers(
        self,
        date: str = "",
        start_date: str = "",
        end_date: str = "",
        keyword: str = "",
        entry_type: str = "",
        ids: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        clauses: list[str] = []
        params: dict[str, Any] = {}
        if ids is not None:
            if not ids:
                return []
            placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
            clauses.append(f"id IN ({placeholders})")
            params.update({f"id_{index}": item_id for index, item_id in enumerate(ids)})
        if date:
            clauses.append("date = :date")
            params["date"] = date
        if start_date:
            clauses.append("date >= :start_date")
            params["start_date"] = start_date
        if end_date:
            clauses.append("date <= :end_date")
            params["end_date"] = end_date
        normalized_type = _normalize_entry_type(entry_type, allow_blank=True)
        if normalized_type:
            clauses.append("entry_type = :entry_type")
            params["entry_type"] = normalized_type
        if keyword:
            clauses.append("(category LIKE :keyword OR note LIKE :keyword)")
            params["keyword"] = f"%{keyword.strip()}%"
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        with self._connect() as conn:
            rows = conn.execute(
                f"""
                SELECT * FROM ledger_entries
                {where}
                ORDER BY date DESC, created_at DESC
                """,
                params,
            ).fetchall()
        return [_ledger_to_wire(dict(row)) for row in rows]

    def update_ledgers(self, ids: list[str], updates: dict[str, Any]) -> list[dict[str, Any]]:
        allowed = {"amount", "category", "note", "date", "entry_type"}
        clean_updates = {
            key: value
            for key, value in updates.items()
            if key in allowed and value is not None and str(value).strip() != ""
        }
        if "amount" in clean_updates:
            clean_updates["amount"] = float(clean_updates["amount"])
        if "entry_type" in clean_updates:
            clean_updates["entry_type"] = _normalize_entry_type(str(clean_updates["entry_type"]))
        if not ids or not clean_updates:
            return self.list_ledgers(ids=ids)
        clean_updates["updated_at"] = _now_ms()
        assignments = ", ".join(f"{key} = :{key}" for key in clean_updates)
        params = dict(clean_updates)
        params.update({f"id_{index}": item_id for index, item_id in enumerate(ids)})
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            conn.execute(f"UPDATE ledger_entries SET {assignments} WHERE id IN ({placeholders})", params)
        return self.list_ledgers(ids=ids)

    def delete_ledgers(self, ids: list[str]) -> int:
        if not ids:
            return 0
        params = {f"id_{index}": item_id for index, item_id in enumerate(ids)}
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            cursor = conn.execute(f"DELETE FROM ledger_entries WHERE id IN ({placeholders})", params)
            return cursor.rowcount

    def summarize_ledgers(
        self,
        start_date: str,
        end_date: str,
        entry_type: str = "expense",
        keyword: str = "",
    ) -> dict[str, Any]:
        entries = self.list_ledgers(
            start_date=start_date,
            end_date=end_date,
            entry_type=entry_type,
            keyword=keyword,
        )
        total = sum(float(item["amount"]) for item in entries)
        by_category: dict[str, dict[str, Any]] = {}
        for item in entries:
            bucket = by_category.setdefault(item["category"], {"category": item["category"], "total": 0.0, "count": 0})
            bucket["total"] += float(item["amount"])
            bucket["count"] += 1
        categories = sorted(by_category.values(), key=lambda item: item["total"], reverse=True)
        return {
            "startDate": start_date,
            "endDate": end_date,
            "entryType": _normalize_entry_type(entry_type, allow_blank=True) or "",
            "total": round(total, 2),
            "count": len(entries),
            "categories": [
                {**item, "total": round(float(item["total"]), 2)}
                for item in categories
            ],
            "entries": entries[:20],
        }

    def replace_cache_source(self) -> dict[str, list[dict[str, Any]]]:
        return {
            "schedules": self.list_schedules(),
            "ledgers": self.list_ledgers(),
        }

    def create_pending_operation(
        self,
        session_id: str,
        domain: str,
        operation: str,
        candidate_ids: list[str],
        updates: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        now = _now_ms()
        item = {
            "id": str(uuid.uuid4()),
            "session_id": session_id,
            "domain": domain,
            "operation": operation,
            "candidate_ids_json": json.dumps(candidate_ids, ensure_ascii=False),
            "updates_json": json.dumps(updates or {}, ensure_ascii=False),
            "created_at": now,
            "expires_at": now + 15 * 60 * 1000,
        }
        with self._connect() as conn:
            conn.execute("DELETE FROM pending_agent_operations WHERE session_id = :session_id", {"session_id": session_id})
            conn.execute(
                """
                INSERT INTO pending_agent_operations(
                    id, session_id, domain, operation, candidate_ids_json, updates_json, created_at, expires_at
                )
                VALUES (
                    :id, :session_id, :domain, :operation, :candidate_ids_json, :updates_json, :created_at, :expires_at
                )
                """,
                item,
            )
        return self.get_pending_operation(session_id) or item

    def get_pending_operation(self, session_id: str) -> dict[str, Any] | None:
        now = _now_ms()
        with self._connect() as conn:
            conn.execute("DELETE FROM pending_agent_operations WHERE expires_at < :now", {"now": now})
            row = conn.execute(
                """
                SELECT * FROM pending_agent_operations
                WHERE session_id = :session_id
                ORDER BY created_at DESC
                LIMIT 1
                """,
                {"session_id": session_id},
            ).fetchone()
        if row is None:
            return None
        item = dict(row)
        return {
            **item,
            "candidate_ids": json.loads(item["candidate_ids_json"]),
            "updates": json.loads(item["updates_json"]),
        }

    def clear_pending_operation(self, session_id: str) -> None:
        with self._connect() as conn:
            conn.execute("DELETE FROM pending_agent_operations WHERE session_id = :session_id", {"session_id": session_id})


def _schedule_to_wire(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": item["id"],
        "title": item["title"],
        "date": item["date"],
        "time": item["time"],
        "note": item.get("note", ""),
        "createdAt": int(item.get("created_at", item.get("createdAt", _now_ms()))),
        "updatedAt": int(item.get("updated_at", item.get("updatedAt", _now_ms()))),
    }


def _ledger_to_wire(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": item["id"],
        "amount": float(item["amount"]),
        "category": item["category"],
        "note": item.get("note", ""),
        "date": item["date"],
        "entryType": item.get("entry_type", item.get("entryType", "expense")),
        "createdAt": int(item.get("created_at", item.get("createdAt", _now_ms()))),
        "updatedAt": int(item.get("updated_at", item.get("updatedAt", _now_ms()))),
    }


def _normalize_entry_type(raw: str, allow_blank: bool = False) -> str:
    value = (raw or "").strip().lower()
    if allow_blank and not value:
        return ""
    return "income" if value in {"income", "收入", "入账", "进账", "earning", "earnings"} else "expense"


def _now_ms() -> int:
    return int(time.time() * 1000)
