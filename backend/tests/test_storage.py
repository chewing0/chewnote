from __future__ import annotations

import os
import unittest

from agent_backend.auth import AuthError, AuthService
from agent_backend.llm_client import _backend_time_context, _infer_simple_ledger, _resolve_relative_date, _resolve_relative_range
from agent_backend.storage import DOMAIN_SCHEDULE, AgentStore, _to_psycopg_query


class AgentStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.database_url = os.getenv("TEST_DATABASE_URL")
        if not self.database_url:
            self.skipTest("TEST_DATABASE_URL is not configured")
        self.store = AgentStore(self.database_url)
        self._clear_store()
        self.auth = AuthService(self.store)
        self.user_a = self.auth.register("alice_test", "alice@example.com", "password123")["user"]
        self.user_b = self.auth.register("bob_test", "bob@example.com", "password123")["user"]

    def tearDown(self) -> None:
        if hasattr(self, "store"):
            self._clear_store()

    def _clear_store(self) -> None:
        with self.store._connect() as conn:
            conn.execute("DELETE FROM pending_agent_operations")
            conn.execute("DELETE FROM chat_messages")
            conn.execute("DELETE FROM conversations")
            conn.execute("DELETE FROM password_reset_tokens")
            conn.execute("DELETE FROM refresh_tokens")
            conn.execute("DELETE FROM ledger_entries")
            conn.execute("DELETE FROM schedule_items")
            conn.execute("DELETE FROM users")

    def test_schedule_crud_is_user_scoped(self) -> None:
        created = self.store.create_schedule(self.user_a["id"], "项目会", "2026-04-28", "10:00", "A 会议室")
        self.store.create_schedule(self.user_b["id"], "项目会", "2026-04-28", "15:00", "B 会议室")

        matches = self.store.list_schedules(self.user_a["id"], date="2026-04-28", keyword="项目")
        self.assertEqual([created["id"]], [item["id"] for item in matches])

        updated = self.store.update_schedules(self.user_a["id"], [created["id"]], {"time": "16:00"})
        self.assertEqual("16:00", updated[0]["time"])

        deleted = self.store.delete_schedules(self.user_a["id"], [created["id"]])
        self.assertEqual(1, deleted)
        self.assertEqual([], self.store.list_schedules(self.user_a["id"], date="2026-04-28"))
        self.assertEqual(1, len(self.store.list_schedules(self.user_b["id"], date="2026-04-28")))

    def test_ledger_summary_is_user_scoped(self) -> None:
        self.store.create_ledger(self.user_a["id"], 28, "餐饮", "咖啡", "2026-04-20", "expense")
        self.store.create_ledger(self.user_a["id"], 18, "交通", "打车", "2026-04-21", "expense")
        self.store.create_ledger(self.user_b["id"], 999, "购物", "不应出现", "2026-04-21", "expense")

        summary = self.store.summarize_ledgers(self.user_a["id"], "2026-04-20", "2026-04-26", "expense")

        self.assertEqual(46.0, summary["total"])
        self.assertEqual(2, summary["count"])
        self.assertEqual({"餐饮", "交通"}, {item["category"] for item in summary["categories"]})

    def test_pending_operation_lifecycle_is_user_scoped(self) -> None:
        first = self.store.create_schedule(self.user_a["id"], "开会", "2026-04-28", "10:00")
        second = self.store.create_schedule(self.user_a["id"], "开会", "2026-04-28", "15:00")

        pending = self.store.create_pending_operation(
            user_id=self.user_a["id"],
            session_id="session-1",
            domain=DOMAIN_SCHEDULE,
            operation="delete",
            candidate_ids=[first["id"], second["id"]],
            updates={},
        )

        loaded = self.store.get_pending_operation(self.user_a["id"], "session-1")
        self.assertIsNotNone(loaded)
        self.assertEqual(pending["id"], loaded["id"])
        self.assertEqual(DOMAIN_SCHEDULE, loaded["domain"])
        self.assertEqual([first["id"], second["id"]], loaded["candidate_ids"])
        self.assertIsNone(self.store.get_pending_operation(self.user_b["id"], "session-1"))

        self.store.clear_pending_operation(self.user_a["id"], "session-1")
        self.assertIsNone(self.store.get_pending_operation(self.user_a["id"], "session-1"))

    def test_conversations_and_messages_are_user_scoped(self) -> None:
        conversation = self.store.create_conversation(self.user_a["id"], "项目讨论")
        self.store.append_chat_message(self.user_a["id"], conversation["id"], "user", "明天开会")
        self.store.append_chat_message(self.user_a["id"], conversation["id"], "assistant", "已记录")

        self.assertEqual(1, len(self.store.list_conversations(self.user_a["id"])))
        self.assertEqual([], self.store.list_conversations(self.user_b["id"]))
        messages = self.store.list_chat_messages(self.user_a["id"], conversation["id"])
        self.assertEqual(["user", "assistant"], [item["role"] for item in messages])
        self.assertEqual([], self.store.list_chat_messages(self.user_b["id"], conversation["id"]))

        self.store.update_context_snapshot(self.user_a["id"], conversation["id"], "用户提到明天开会", 2)
        snapshot = self.store.get_context_snapshot(self.user_a["id"], conversation["id"])
        self.assertEqual("用户提到明天开会", snapshot["summary"])
        self.assertEqual(2, snapshot["summarizedMessageCount"])

    def test_auth_refresh_password_reset_and_change(self) -> None:
        login = self.auth.login("alice_test", "password123")
        refreshed = self.auth.refresh(login["refreshToken"])
        with self.assertRaises(AuthError):
            self.auth.refresh(login["refreshToken"])

        forgot = self.auth.forgot_password("alice@example.com")
        self.assertTrue(forgot.get("devResetToken"))
        self.auth.reset_password(forgot["devResetToken"], "newpassword123")
        with self.assertRaises(AuthError):
            self.auth.login("alice_test", "password123")
        login = self.auth.login("alice@example.com", "newpassword123")
        self.auth.change_password(login["user"]["id"], "newpassword123", "anotherpassword123")
        self.auth.login("alice_test", "anotherpassword123")
        self.assertTrue(refreshed["accessToken"])


class StorageSqlTest(unittest.TestCase):
    def test_named_parameters_are_converted_for_psycopg(self) -> None:
        self.assertEqual(
            "SELECT * FROM ledger_entries WHERE id = %(id)s AND date >= %(start_date)s",
            _to_psycopg_query("SELECT * FROM ledger_entries WHERE id = :id AND date >= :start_date"),
        )


class BackendTimeTest(unittest.TestCase):
    def test_relative_dates_are_resolved_from_backend_date(self) -> None:
        self.assertEqual(
            "2026-04-28",
            _resolve_relative_date("明天下午三点出去玩", "2026-04-27"),
        )
        self.assertEqual(
            ("2026-04-20", "2026-04-26"),
            _resolve_relative_range("查看我上周花费了多少钱", "2026-04-27"),
        )

    def test_backend_time_context_uses_configured_timezone(self) -> None:
        current_date, current_time, timezone = _backend_time_context("Asia/Shanghai")

        self.assertRegex(current_date, r"^\d{4}-\d{2}-\d{2}$")
        self.assertRegex(current_time, r"^\d{2}:\d{2}$")
        self.assertEqual("Asia/Shanghai", timezone)

    def test_simple_ledger_fallback_infers_common_expense(self) -> None:
        inferred = _infer_simple_ledger("今天午饭花了28元", "2026-05-08")

        self.assertIsNotNone(inferred)
        self.assertEqual(28.0, inferred["amount"])
        self.assertEqual("餐饮", inferred["category"])
        self.assertEqual("expense", inferred["entry_type"])
        self.assertEqual("2026-05-08", inferred["date"])


if __name__ == "__main__":
    unittest.main()
