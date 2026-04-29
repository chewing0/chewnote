from __future__ import annotations

import os
import sys
from pathlib import Path

import psycopg2
from dotenv import load_dotenv
from psycopg2 import sql

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from agent_backend.storage import AgentStore  # noqa: E402


DEFAULT_DATABASE_NAME = "chewnote_agent"


def main() -> None:
    load_dotenv(ROOT / ".env")
    database_name = os.getenv("POSTGRES_DATABASE", DEFAULT_DATABASE_NAME)
    admin_url = os.getenv("POSTGRES_ADMIN_URL", "postgresql://postgres@localhost:5432/postgres")
    database_url = os.getenv("DATABASE_URL", f"postgresql://postgres@localhost:5432/{database_name}")

    try:
        admin_conn = psycopg2.connect(admin_url)
    except psycopg2.OperationalError as exc:
        raise SystemExit(
            "无法连接 PostgreSQL 管理库。请在 backend/.env 中配置 POSTGRES_ADMIN_URL，"
            "例如 postgresql://postgres:你的密码@localhost:5432/postgres，然后重试。"
        ) from exc
    admin_conn.autocommit = True
    try:
        with admin_conn.cursor() as cursor:
            cursor.execute("SELECT 1 FROM pg_database WHERE datname = %s", (database_name,))
            exists = cursor.fetchone() is not None
            if not exists:
                cursor.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(database_name)))
                print(f"created database: {database_name}")
            else:
                print(f"database already exists: {database_name}")
    finally:
        admin_conn.close()

    AgentStore(database_url)
    print("initialized application tables")


if __name__ == "__main__":
    main()
