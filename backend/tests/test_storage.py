from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from agent_backend.storage import DOMAIN_LEDGER, DOMAIN_SCHEDULE, AgentStore
from agent_backend.llm_client import _backend_time_context, _resolve_relative_date, _resolve_relative_range


class AgentStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.store = AgentStore(Path(self.temp_dir.name) / "agent.db")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_schedule_crud(self) -> None:
        created = self.store.create_schedule("项目会", "2026-04-28", "10:00", "A 会议室")

        matches = self.store.list_schedules(date="2026-04-28", keyword="项目")
        self.assertEqual([created["id"]], [item["id"] for item in matches])

        updated = self.store.update_schedules([created["id"]], {"time": "16:00"})
        self.assertEqual("16:00", updated[0]["time"])

        deleted = self.store.delete_schedules([created["id"]])
        self.assertEqual(1, deleted)
        self.assertEqual([], self.store.list_schedules(date="2026-04-28"))

    def test_ledger_summary(self) -> None:
        self.store.create_ledger(28, "餐饮", "咖啡", "2026-04-20", "expense")
        self.store.create_ledger(18, "交通", "打车", "2026-04-21", "expense")
        self.store.create_ledger(200, "报销", "差旅", "2026-04-21", "income")

        summary = self.store.summarize_ledgers("2026-04-20", "2026-04-26", "expense")

        self.assertEqual(46.0, summary["total"])
        self.assertEqual(2, summary["count"])
        self.assertEqual({"餐饮", "交通"}, {item["category"] for item in summary["categories"]})

    def test_pending_operation_lifecycle(self) -> None:
        first = self.store.create_schedule("开会", "2026-04-28", "10:00")
        second = self.store.create_schedule("开会", "2026-04-28", "15:00")

        pending = self.store.create_pending_operation(
            session_id="session-1",
            domain=DOMAIN_SCHEDULE,
            operation="delete",
            candidate_ids=[first["id"], second["id"]],
            updates={},
        )

        loaded = self.store.get_pending_operation("session-1")
        self.assertIsNotNone(loaded)
        self.assertEqual(pending["id"], loaded["id"])
        self.assertEqual(DOMAIN_SCHEDULE, loaded["domain"])
        self.assertEqual([first["id"], second["id"]], loaded["candidate_ids"])

        self.store.clear_pending_operation("session-1")
        self.assertIsNone(self.store.get_pending_operation("session-1"))

    def test_ledger_update_uses_entry_type_storage_name(self) -> None:
        created = self.store.create_ledger(100, "报销", "午餐", "2026-04-21", "expense")

        updated = self.store.update_ledgers([created["id"]], {"entry_type": "income"})

        self.assertEqual("income", updated[0]["entryType"])
        income_items = self.store.list_ledgers(entry_type="income")
        self.assertEqual([created["id"]], [item["id"] for item in income_items])

    def test_relative_dates_are_resolved_from_client_date(self) -> None:
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


if __name__ == "__main__":
    unittest.main()
