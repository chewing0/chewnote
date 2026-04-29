from __future__ import annotations

import importlib
import os
import unittest

from fastapi.testclient import TestClient


class ApiAuthTest(unittest.TestCase):
    def setUp(self) -> None:
        self.database_url = os.getenv("TEST_DATABASE_URL")
        if not self.database_url:
            self.skipTest("TEST_DATABASE_URL is not configured")
        os.environ["DATABASE_URL"] = self.database_url
        api_module = importlib.import_module("agent_backend.api")
        self.client = TestClient(api_module.create_app())
        self._clear_store()

    def tearDown(self) -> None:
        if hasattr(self, "client"):
            self._clear_store()

    def _clear_store(self) -> None:
        from agent_backend.storage import AgentStore

        agent_store = AgentStore(self.database_url)
        with agent_store._connect() as conn:
            conn.execute("DELETE FROM pending_agent_operations")
            conn.execute("DELETE FROM password_reset_tokens")
            conn.execute("DELETE FROM refresh_tokens")
            conn.execute("DELETE FROM ledger_entries")
            conn.execute("DELETE FROM schedule_items")
            conn.execute("DELETE FROM users")

    def test_protected_routes_require_auth_and_sync_is_user_scoped(self) -> None:
        self.assertEqual(401, self.client.get("/sync").status_code)

        first = self.client.post(
            "/auth/register",
            json={"username": "api_alice", "email": "api_alice@example.com", "password": "password123"},
        ).json()
        second = self.client.post(
            "/auth/register",
            json={"username": "api_bob", "email": "api_bob@example.com", "password": "password123"},
        ).json()

        first_auth = {"Authorization": f"Bearer {first['accessToken']}"}
        second_auth = {"Authorization": f"Bearer {second['accessToken']}"}
        created = self.client.post(
            "/ledgers",
            headers=first_auth,
            json={"amount": 12, "category": "餐饮", "note": "午餐", "date": "2026-04-28", "entryType": "expense"},
        )
        self.assertEqual(200, created.status_code)

        self.assertEqual(1, len(self.client.get("/sync", headers=first_auth).json()["ledgers"]))
        self.assertEqual(0, len(self.client.get("/sync", headers=second_auth).json()["ledgers"]))

    def test_refresh_logout_and_reset_password(self) -> None:
        auth = self.client.post(
            "/auth/register",
            json={"username": "api_reset", "email": "api_reset@example.com", "password": "password123"},
        ).json()
        refreshed = self.client.post("/auth/refresh", json={"refreshToken": auth["refreshToken"]})
        self.assertEqual(200, refreshed.status_code)
        old_refresh = self.client.post("/auth/refresh", json={"refreshToken": auth["refreshToken"]})
        self.assertEqual(401, old_refresh.status_code)

        forgot = self.client.post("/auth/password/forgot", json={"email": "api_reset@example.com"}).json()
        self.assertTrue(forgot["devResetToken"])
        reset = self.client.post(
            "/auth/password/reset",
            json={"token": forgot["devResetToken"], "newPassword": "newpassword123"},
        )
        self.assertEqual(200, reset.status_code)
        self.assertEqual(
            200,
            self.client.post(
                "/auth/login",
                json={"identifier": "api_reset", "password": "newpassword123"},
            ).status_code,
        )


if __name__ == "__main__":
    unittest.main()
