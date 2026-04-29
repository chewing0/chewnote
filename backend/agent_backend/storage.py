from __future__ import annotations

import json
import os
import re
import time
import uuid
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

import psycopg2
from psycopg2.extensions import connection as PgConnection
from psycopg2.extras import RealDictCursor


DOMAIN_LEDGER = "ledger"
DOMAIN_SCHEDULE = "schedule"

DEFAULT_DATABASE_URL = "postgresql://postgres@localhost:5432/chewnote_agent"


class AgentStore:
    def __init__(self, database_url: str | None = None) -> None:
        self.database_url = database_url or os.getenv("DATABASE_URL") or DEFAULT_DATABASE_URL
        self.init_db()

    def init_db(self) -> None:
        with self._connect() as conn:
            for statement in (
                """
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    username TEXT NOT NULL,
                    email TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """,
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users (LOWER(username))",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower ON users (LOWER(email))",
                """
                CREATE TABLE IF NOT EXISTS refresh_tokens (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    token_hash TEXT NOT NULL UNIQUE,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    revoked_at BIGINT NOT NULL DEFAULT 0
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id, expires_at)",
                """
                CREATE TABLE IF NOT EXISTS password_reset_tokens (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    token_hash TEXT NOT NULL UNIQUE,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    used_at BIGINT NOT NULL DEFAULT 0
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_reset_user ON password_reset_tokens(user_id, expires_at)",
                """
                CREATE TABLE IF NOT EXISTS schedule_items (
                    id TEXT PRIMARY KEY,
                    user_id TEXT,
                    title TEXT NOT NULL,
                    date TEXT NOT NULL,
                    time TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """,
                "ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS user_id TEXT",
                "CREATE INDEX IF NOT EXISTS idx_schedule_user_date ON schedule_items(user_id, date)",
                """
                CREATE TABLE IF NOT EXISTS ledger_entries (
                    id TEXT PRIMARY KEY,
                    user_id TEXT,
                    amount DOUBLE PRECISION NOT NULL,
                    category TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    date TEXT NOT NULL,
                    entry_type TEXT NOT NULL DEFAULT 'expense',
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """,
                "ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS user_id TEXT",
                "CREATE INDEX IF NOT EXISTS idx_ledger_user_date ON ledger_entries(user_id, date)",
                "CREATE INDEX IF NOT EXISTS idx_ledger_user_entry_type ON ledger_entries(user_id, entry_type)",
                """
                CREATE TABLE IF NOT EXISTS pending_agent_operations (
                    id TEXT PRIMARY KEY,
                    user_id TEXT,
                    session_id TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    candidate_ids_json TEXT NOT NULL,
                    updates_json TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )
                """,
                "ALTER TABLE pending_agent_operations ADD COLUMN IF NOT EXISTS user_id TEXT",
                "CREATE INDEX IF NOT EXISTS idx_pending_user_session ON pending_agent_operations(user_id, session_id, expires_at)",
            ):
                conn.execute(statement)

    @contextmanager
    def _connect(self) -> Iterator["_PgSession"]:
        conn = psycopg2.connect(self.database_url, cursor_factory=RealDictCursor)
        session = _PgSession(conn)
        try:
            yield session
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def create_user(self, username: str, email: str, password_hash: str) -> dict[str, Any]:
        now = _now_ms()
        clean_username = username.strip()
        clean_email = email.strip().lower()
        if self.get_user_by_identifier(clean_username) is not None or self.get_user_by_identifier(clean_email) is not None:
            raise ValueError("User already exists")
        item = {
            "id": str(uuid.uuid4()),
            "username": clean_username,
            "email": clean_email,
            "password_hash": password_hash,
            "created_at": now,
            "updated_at": now,
        }
        with self._connect() as conn:
            count = conn.execute("SELECT COUNT(*) AS count FROM users").fetchone()["count"]
            conn.execute(
                """
                INSERT INTO users(id, username, email, password_hash, created_at, updated_at)
                VALUES (:id, :username, :email, :password_hash, :created_at, :updated_at)
                """,
                item,
            )
            if int(count) == 0:
                conn.execute("UPDATE schedule_items SET user_id = :user_id WHERE user_id IS NULL", {"user_id": item["id"]})
                conn.execute("UPDATE ledger_entries SET user_id = :user_id WHERE user_id IS NULL", {"user_id": item["id"]})
                conn.execute("DELETE FROM pending_agent_operations WHERE user_id IS NULL")
        return _user_to_wire(item)

    def get_user(self, user_id: str) -> dict[str, Any] | None:
        if not user_id:
            return None
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM users WHERE id = :id", {"id": user_id}).fetchone()
        return _user_to_wire(dict(row)) if row else None

    def get_user_by_identifier(self, identifier: str) -> dict[str, Any] | None:
        value = identifier.strip().lower()
        if not value:
            return None
        with self._connect() as conn:
            row = conn.execute(
                """
                SELECT * FROM users
                WHERE LOWER(username) = :value OR LOWER(email) = :value
                LIMIT 1
                """,
                {"value": value},
            ).fetchone()
        return _user_to_wire(dict(row)) if row else None

    def update_user_password(self, user_id: str, password_hash: str) -> None:
        with self._connect() as conn:
            conn.execute(
                "UPDATE users SET password_hash = :password_hash, updated_at = :updated_at WHERE id = :id",
                {"id": user_id, "password_hash": password_hash, "updated_at": _now_ms()},
            )
            conn.execute(
                "UPDATE refresh_tokens SET revoked_at = :revoked_at WHERE user_id = :user_id AND revoked_at = 0",
                {"user_id": user_id, "revoked_at": _now_ms()},
            )

    def create_refresh_token(self, user_id: str, token_hash: str, expires_at: int) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO refresh_tokens(id, user_id, token_hash, created_at, expires_at, revoked_at)
                VALUES (:id, :user_id, :token_hash, :created_at, :expires_at, 0)
                """,
                {
                    "id": str(uuid.uuid4()),
                    "user_id": user_id,
                    "token_hash": token_hash,
                    "created_at": _now_ms(),
                    "expires_at": expires_at,
                },
            )

    def get_refresh_token(self, token_hash: str) -> dict[str, Any] | None:
        now = _now_ms()
        with self._connect() as conn:
            row = conn.execute(
                """
                SELECT * FROM refresh_tokens
                WHERE token_hash = :token_hash AND revoked_at = 0 AND expires_at > :now
                LIMIT 1
                """,
                {"token_hash": token_hash, "now": now},
            ).fetchone()
        return dict(row) if row else None

    def revoke_refresh_token(self, token_hash: str) -> None:
        with self._connect() as conn:
            conn.execute(
                "UPDATE refresh_tokens SET revoked_at = :revoked_at WHERE token_hash = :token_hash",
                {"token_hash": token_hash, "revoked_at": _now_ms()},
            )

    def create_password_reset_token(self, user_id: str, token_hash: str, expires_at: int) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO password_reset_tokens(id, user_id, token_hash, created_at, expires_at, used_at)
                VALUES (:id, :user_id, :token_hash, :created_at, :expires_at, 0)
                """,
                {
                    "id": str(uuid.uuid4()),
                    "user_id": user_id,
                    "token_hash": token_hash,
                    "created_at": _now_ms(),
                    "expires_at": expires_at,
                },
            )

    def consume_password_reset_token(self, token_hash: str) -> dict[str, Any] | None:
        now = _now_ms()
        with self._connect() as conn:
            row = conn.execute(
                """
                SELECT * FROM password_reset_tokens
                WHERE token_hash = :token_hash AND used_at = 0 AND expires_at > :now
                LIMIT 1
                """,
                {"token_hash": token_hash, "now": now},
            ).fetchone()
            if row is None:
                return None
            conn.execute(
                "UPDATE password_reset_tokens SET used_at = :used_at WHERE id = :id",
                {"id": row["id"], "used_at": now},
            )
        return dict(row)

    def create_schedule(self, user_id: str, title: str, date: str, item_time: str, note: str = "") -> dict[str, Any]:
        now = _now_ms()
        item = {
            "id": str(uuid.uuid4()),
            "user_id": user_id,
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
                INSERT INTO schedule_items(id, user_id, title, date, time, note, created_at, updated_at)
                VALUES (:id, :user_id, :title, :date, :time, :note, :created_at, :updated_at)
                """,
                item,
            )
        return _schedule_to_wire(item)

    def import_schedules(self, user_id: str, items: list[dict[str, Any]]) -> None:
        now = _now_ms()
        with self._connect() as conn:
            for raw in items:
                item = {
                    "id": (raw.get("id") or str(uuid.uuid4())).strip() or str(uuid.uuid4()),
                    "user_id": user_id,
                    "title": (raw.get("title") or "未命名日程").strip() or "未命名日程",
                    "date": raw.get("date") or "",
                    "time": raw.get("time") or "09:00",
                    "note": (raw.get("note") or "").strip(),
                    "created_at": int(raw.get("createdAt") or raw.get("created_at") or now),
                    "updated_at": int(raw.get("updatedAt") or raw.get("updated_at") or now),
                }
                if not item["date"]:
                    continue
                conn.execute(
                    """
                    INSERT INTO schedule_items(id, user_id, title, date, time, note, created_at, updated_at)
                    VALUES (:id, :user_id, :title, :date, :time, :note, :created_at, :updated_at)
                    ON CONFLICT (id) DO UPDATE SET
                        title = EXCLUDED.title,
                        date = EXCLUDED.date,
                        time = EXCLUDED.time,
                        note = EXCLUDED.note,
                        updated_at = EXCLUDED.updated_at
                    WHERE schedule_items.user_id = EXCLUDED.user_id
                    """,
                    item,
                )

    def list_schedules(
        self,
        user_id: str,
        date: str = "",
        start_date: str = "",
        end_date: str = "",
        keyword: str = "",
        ids: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        clauses: list[str] = ["user_id = :user_id"]
        params: dict[str, Any] = {"user_id": user_id}
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
            clauses.append("(title ILIKE :keyword OR note ILIKE :keyword)")
            params["keyword"] = f"%{keyword.strip()}%"
        where = f"WHERE {' AND '.join(clauses)}"
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

    def update_schedules(self, user_id: str, ids: list[str], updates: dict[str, Any]) -> list[dict[str, Any]]:
        allowed = {"title", "date", "time", "note"}
        clean_updates = {
            key: value
            for key, value in updates.items()
            if key in allowed and value is not None and str(value).strip() != ""
        }
        if not ids or not clean_updates:
            return self.list_schedules(user_id, ids=ids)
        clean_updates["updated_at"] = _now_ms()
        assignments = ", ".join(f"{key} = :{key}" for key in clean_updates)
        params = dict(clean_updates)
        params["user_id"] = user_id
        params.update({f"id_{index}": item_id for index, item_id in enumerate(ids)})
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            conn.execute(f"UPDATE schedule_items SET {assignments} WHERE user_id = :user_id AND id IN ({placeholders})", params)
        return self.list_schedules(user_id, ids=ids)

    def delete_schedules(self, user_id: str, ids: list[str]) -> int:
        if not ids:
            return 0
        params = {"user_id": user_id, **{f"id_{index}": item_id for index, item_id in enumerate(ids)}}
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            cursor = conn.execute(f"DELETE FROM schedule_items WHERE user_id = :user_id AND id IN ({placeholders})", params)
            return cursor.rowcount

    def create_ledger(
        self,
        user_id: str,
        amount: float,
        category: str,
        note: str,
        date: str,
        entry_type: str = "expense",
    ) -> dict[str, Any]:
        now = _now_ms()
        item = {
            "id": str(uuid.uuid4()),
            "user_id": user_id,
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
                INSERT INTO ledger_entries(id, user_id, amount, category, note, date, entry_type, created_at, updated_at)
                VALUES (:id, :user_id, :amount, :category, :note, :date, :entry_type, :created_at, :updated_at)
                """,
                item,
            )
        return _ledger_to_wire(item)

    def import_ledgers(self, user_id: str, items: list[dict[str, Any]]) -> None:
        now = _now_ms()
        with self._connect() as conn:
            for raw in items:
                item = {
                    "id": (raw.get("id") or str(uuid.uuid4())).strip() or str(uuid.uuid4()),
                    "user_id": user_id,
                    "amount": float(raw.get("amount") or 0.0),
                    "category": (raw.get("category") or "其他").strip() or "其他",
                    "note": (raw.get("note") or "").strip(),
                    "date": raw.get("date") or "",
                    "entry_type": _normalize_entry_type(raw.get("entryType") or raw.get("entry_type") or "expense"),
                    "created_at": int(raw.get("createdAt") or raw.get("created_at") or now),
                    "updated_at": int(raw.get("updatedAt") or raw.get("updated_at") or now),
                }
                if not item["date"]:
                    continue
                conn.execute(
                    """
                    INSERT INTO ledger_entries(id, user_id, amount, category, note, date, entry_type, created_at, updated_at)
                    VALUES (:id, :user_id, :amount, :category, :note, :date, :entry_type, :created_at, :updated_at)
                    ON CONFLICT (id) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        category = EXCLUDED.category,
                        note = EXCLUDED.note,
                        date = EXCLUDED.date,
                        entry_type = EXCLUDED.entry_type,
                        updated_at = EXCLUDED.updated_at
                    WHERE ledger_entries.user_id = EXCLUDED.user_id
                    """,
                    item,
                )

    def list_ledgers(
        self,
        user_id: str,
        date: str = "",
        start_date: str = "",
        end_date: str = "",
        keyword: str = "",
        entry_type: str = "",
        ids: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        clauses: list[str] = ["user_id = :user_id"]
        params: dict[str, Any] = {"user_id": user_id}
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
            clauses.append("(category ILIKE :keyword OR note ILIKE :keyword)")
            params["keyword"] = f"%{keyword.strip()}%"
        where = f"WHERE {' AND '.join(clauses)}"
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

    def update_ledgers(self, user_id: str, ids: list[str], updates: dict[str, Any]) -> list[dict[str, Any]]:
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
            return self.list_ledgers(user_id, ids=ids)
        clean_updates["updated_at"] = _now_ms()
        assignments = ", ".join(f"{key} = :{key}" for key in clean_updates)
        params = dict(clean_updates)
        params["user_id"] = user_id
        params.update({f"id_{index}": item_id for index, item_id in enumerate(ids)})
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            conn.execute(f"UPDATE ledger_entries SET {assignments} WHERE user_id = :user_id AND id IN ({placeholders})", params)
        return self.list_ledgers(user_id, ids=ids)

    def delete_ledgers(self, user_id: str, ids: list[str]) -> int:
        if not ids:
            return 0
        params = {"user_id": user_id, **{f"id_{index}": item_id for index, item_id in enumerate(ids)}}
        placeholders = ", ".join(f":id_{index}" for index in range(len(ids)))
        with self._connect() as conn:
            cursor = conn.execute(f"DELETE FROM ledger_entries WHERE user_id = :user_id AND id IN ({placeholders})", params)
            return cursor.rowcount

    def summarize_ledgers(
        self,
        user_id: str,
        start_date: str,
        end_date: str,
        entry_type: str = "expense",
        keyword: str = "",
    ) -> dict[str, Any]:
        entries = self.list_ledgers(
            user_id,
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

    def replace_cache_source(self, user_id: str) -> dict[str, list[dict[str, Any]]]:
        return {
            "schedules": self.list_schedules(user_id),
            "ledgers": self.list_ledgers(user_id),
        }

    def create_pending_operation(
        self,
        user_id: str,
        session_id: str,
        domain: str,
        operation: str,
        candidate_ids: list[str],
        updates: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        now = _now_ms()
        item = {
            "id": str(uuid.uuid4()),
            "user_id": user_id,
            "session_id": session_id,
            "domain": domain,
            "operation": operation,
            "candidate_ids_json": json.dumps(candidate_ids, ensure_ascii=False),
            "updates_json": json.dumps(updates or {}, ensure_ascii=False),
            "created_at": now,
            "expires_at": now + 15 * 60 * 1000,
        }
        with self._connect() as conn:
            conn.execute(
                "DELETE FROM pending_agent_operations WHERE user_id = :user_id AND session_id = :session_id",
                {"user_id": user_id, "session_id": session_id},
            )
            conn.execute(
                """
                INSERT INTO pending_agent_operations(
                    id, user_id, session_id, domain, operation, candidate_ids_json, updates_json, created_at, expires_at
                )
                VALUES (
                    :id, :user_id, :session_id, :domain, :operation, :candidate_ids_json, :updates_json, :created_at, :expires_at
                )
                """,
                item,
            )
        return self.get_pending_operation(user_id, session_id) or item

    def get_pending_operation(self, user_id: str, session_id: str) -> dict[str, Any] | None:
        now = _now_ms()
        with self._connect() as conn:
            conn.execute("DELETE FROM pending_agent_operations WHERE expires_at < :now", {"now": now})
            row = conn.execute(
                """
                SELECT * FROM pending_agent_operations
                WHERE user_id = :user_id AND session_id = :session_id
                ORDER BY created_at DESC
                LIMIT 1
                """,
                {"user_id": user_id, "session_id": session_id},
            ).fetchone()
        if row is None:
            return None
        item = dict(row)
        return {
            **item,
            "candidate_ids": json.loads(item["candidate_ids_json"]),
            "updates": json.loads(item["updates_json"]),
        }

    def clear_pending_operation(self, user_id: str, session_id: str) -> None:
        with self._connect() as conn:
            conn.execute(
                "DELETE FROM pending_agent_operations WHERE user_id = :user_id AND session_id = :session_id",
                {"user_id": user_id, "session_id": session_id},
            )


class _PgSession:
    def __init__(self, conn: PgConnection) -> None:
        self.conn = conn

    def execute(self, query: str, params: dict[str, Any] | None = None):
        cursor = self.conn.cursor()
        cursor.execute(_to_psycopg_query(query), params or {})
        return cursor


def _to_psycopg_query(query: str) -> str:
    return re.sub(r":([A-Za-z_][A-Za-z0-9_]*)", r"%(\1)s", query)


def _user_to_wire(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": item["id"],
        "username": item["username"],
        "email": item["email"],
        "password_hash": item.get("password_hash", ""),
        "createdAt": int(item.get("created_at", item.get("createdAt", _now_ms()))),
        "updatedAt": int(item.get("updated_at", item.get("updatedAt", _now_ms()))),
    }


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
