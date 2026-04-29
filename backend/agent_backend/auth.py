from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import smtplib
import time
from email.message import EmailMessage
from typing import Any

try:
    import bcrypt
except ImportError:  # pragma: no cover - used only before requirements are installed
    bcrypt = None

from .storage import AgentStore


class AuthError(Exception):
    pass


class AuthService:
    def __init__(self, store: AgentStore) -> None:
        self.store = store
        self.secret = os.getenv("AUTH_SECRET_KEY", "dev-change-me-auth-secret")
        self.access_ttl_sec = int(os.getenv("AUTH_ACCESS_TOKEN_MINUTES", "30")) * 60
        self.refresh_ttl_sec = int(os.getenv("AUTH_REFRESH_TOKEN_DAYS", "30")) * 24 * 60 * 60
        self.reset_ttl_sec = int(os.getenv("AUTH_PASSWORD_RESET_MINUTES", "30")) * 60

    def register(self, username: str, email: str, password: str) -> dict[str, Any]:
        _validate_username(username)
        _validate_email(email)
        _validate_password(password)
        user = self.store.create_user(username, email, hash_password(password))
        return self.issue_auth_response(user)

    def login(self, identifier: str, password: str) -> dict[str, Any]:
        user = self.store.get_user_by_identifier(identifier)
        if user is None or not verify_password(password, user["password_hash"]):
            raise AuthError("用户名、邮箱或密码不正确")
        return self.issue_auth_response(user)

    def issue_auth_response(self, user: dict[str, Any]) -> dict[str, Any]:
        refresh_token = secrets.token_urlsafe(48)
        self.store.create_refresh_token(
            user_id=user["id"],
            token_hash=_token_hash(refresh_token),
            expires_at=_now_ms() + self.refresh_ttl_sec * 1000,
        )
        return {
            "user": public_user(user),
            "accessToken": self.create_access_token(user["id"]),
            "refreshToken": refresh_token,
            "expiresIn": self.access_ttl_sec,
        }

    def refresh(self, refresh_token: str) -> dict[str, Any]:
        token_hash = _token_hash(refresh_token)
        stored = self.store.get_refresh_token(token_hash)
        if stored is None:
            raise AuthError("登录状态已过期，请重新登录")
        user = self.store.get_user(stored["user_id"])
        if user is None:
            raise AuthError("用户不存在")
        self.store.revoke_refresh_token(token_hash)
        return self.issue_auth_response(user)

    def logout(self, refresh_token: str) -> None:
        if refresh_token.strip():
            self.store.revoke_refresh_token(_token_hash(refresh_token))

    def create_access_token(self, user_id: str) -> str:
        now = int(time.time())
        payload = {
            "sub": user_id,
            "type": "access",
            "iat": now,
            "exp": now + self.access_ttl_sec,
        }
        return _encode_jwt(payload, self.secret)

    def verify_access_token(self, token: str) -> dict[str, Any]:
        payload = _decode_jwt(token, self.secret)
        if payload.get("type") != "access":
            raise AuthError("无效登录凭证")
        user_id = str(payload.get("sub") or "")
        user = self.store.get_user(user_id)
        if user is None:
            raise AuthError("用户不存在")
        return user

    def forgot_password(self, email: str) -> dict[str, Any]:
        user = self.store.get_user_by_identifier(email)
        reset_token = ""
        if user is not None and user["email"].lower() == email.strip().lower():
            reset_token = secrets.token_urlsafe(40)
            self.store.create_password_reset_token(
                user_id=user["id"],
                token_hash=_token_hash(reset_token),
                expires_at=_now_ms() + self.reset_ttl_sec * 1000,
            )
            _send_reset_email(user["email"], reset_token)
        response = {"message": "如果邮箱存在，重置链接已经发送。", "devResetToken": None}
        if reset_token and _dev_return_reset_token():
            response["devResetToken"] = reset_token
        return response

    def reset_password(self, token: str, new_password: str) -> None:
        _validate_password(new_password)
        stored = self.store.consume_password_reset_token(_token_hash(token))
        if stored is None:
            raise AuthError("重置链接无效或已过期")
        self.store.update_user_password(stored["user_id"], hash_password(new_password))

    def change_password(self, user_id: str, old_password: str, new_password: str) -> None:
        _validate_password(new_password)
        user = self.store.get_user(user_id)
        if user is None or not verify_password(old_password, user["password_hash"]):
            raise AuthError("旧密码不正确")
        self.store.update_user_password(user_id, hash_password(new_password))


def public_user(user: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": user["id"],
        "username": user["username"],
        "email": user["email"],
        "createdAt": int(user.get("createdAt", 0)),
        "updatedAt": int(user.get("updatedAt", 0)),
    }


def hash_password(password: str) -> str:
    if bcrypt is not None:
        hashed = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("utf-8")
        return f"bcrypt${hashed}"
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 240_000)
    return "pbkdf2$" + base64.urlsafe_b64encode(salt).decode("ascii") + "$" + base64.urlsafe_b64encode(digest).decode("ascii")


def verify_password(password: str, password_hash: str) -> bool:
    if password_hash.startswith("bcrypt$") and bcrypt is not None:
        expected = password_hash.removeprefix("bcrypt$").encode("utf-8")
        return bool(bcrypt.checkpw(password.encode("utf-8"), expected))
    if password_hash.startswith("pbkdf2$"):
        _, salt_raw, digest_raw = password_hash.split("$", 2)
        salt = base64.urlsafe_b64decode(salt_raw.encode("ascii"))
        expected = base64.urlsafe_b64decode(digest_raw.encode("ascii"))
        actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 240_000)
        return hmac.compare_digest(actual, expected)
    return False


def _encode_jwt(payload: dict[str, Any], secret: str) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    signing_input = ".".join(
        [
            _b64url_json(header),
            _b64url_json(payload),
        ]
    )
    signature = hmac.new(secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256).digest()
    return signing_input + "." + _b64url(signature)


def _decode_jwt(token: str, secret: str) -> dict[str, Any]:
    parts = token.split(".")
    if len(parts) != 3:
        raise AuthError("无效登录凭证")
    signing_input = parts[0] + "." + parts[1]
    expected = hmac.new(secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256).digest()
    actual = _b64url_decode(parts[2])
    if not hmac.compare_digest(actual, expected):
        raise AuthError("无效登录凭证")
    payload = json.loads(_b64url_decode(parts[1]).decode("utf-8"))
    if int(payload.get("exp") or 0) <= int(time.time()):
        raise AuthError("登录状态已过期，请重新登录")
    return payload


def _b64url_json(value: dict[str, Any]) -> str:
    return _b64url(json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _b64url_decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode((value + padding).encode("ascii"))


def _token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _validate_username(value: str) -> None:
    if not re.fullmatch(r"[A-Za-z0-9_\-\u4e00-\u9fff]{3,32}", value.strip()):
        raise AuthError("用户名需要 3-32 位，可包含中文、字母、数字、下划线或短横线")


def _validate_email(value: str) -> None:
    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", value.strip()):
        raise AuthError("请输入有效邮箱")


def _validate_password(value: str) -> None:
    if len(value) < 8:
        raise AuthError("密码至少需要 8 位")


def _dev_return_reset_token() -> bool:
    return os.getenv("AUTH_DEV_RETURN_RESET_TOKEN", "true").strip().lower() in {"1", "true", "yes", "on"}


def _send_reset_email(email: str, token: str) -> None:
    host = os.getenv("SMTP_HOST", "").strip()
    if not host:
        return
    port = int(os.getenv("SMTP_PORT", "587"))
    username = os.getenv("SMTP_USERNAME", "").strip()
    password = os.getenv("SMTP_PASSWORD", "")
    from_email = os.getenv("SMTP_FROM", username or "noreply@example.com")
    reset_base_url = os.getenv("PASSWORD_RESET_BASE_URL", "").rstrip("/")
    link = f"{reset_base_url}?token={token}" if reset_base_url else token

    message = EmailMessage()
    message["Subject"] = "MyLife Agent 密码重置"
    message["From"] = from_email
    message["To"] = email
    message.set_content(f"请使用下面的一次性链接或验证码重置密码，30 分钟内有效：\n{link}")
    with smtplib.SMTP(host, port, timeout=10) as smtp:
        smtp.starttls()
        if username:
            smtp.login(username, password)
        smtp.send_message(message)


def _now_ms() -> int:
    return int(time.time() * 1000)
