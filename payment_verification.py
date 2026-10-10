"""VidGravity P140 payment verification primitives.

Security posture:
- No license is issued based on a browser/client success flag.
- Razorpay order creation requires server-side credentials and provider API integration.
- Checkout signatures and webhook signatures are verified with constant-time HMAC checks.
- Webhook handlers must consume the exact raw request body.
- This module intentionally does not call Razorpay or issue a license itself; the host app
  must persist verified payment state and call the P139 licensing API only after capture.
"""
from __future__ import annotations
import hashlib
import hmac
import json
import os
from typing import Any

class PaymentVerificationError(ValueError):
    pass

def _secret(name: str) -> bytes:
    value = os.environ.get(name, "")
    if not value:
        raise PaymentVerificationError(f"Required server environment variable is missing: {name}")
    return value.encode("utf-8")

def verify_checkout_signature(order_id: str, payment_id: str, signature: str, secret: str | None = None) -> bool:
    """Verify Razorpay Checkout: HMAC-SHA256(order_id + '|' + payment_id, key_secret)."""
    if not order_id or not payment_id or not signature:
        return False
    key = secret.encode("utf-8") if secret is not None else _secret("RAZORPAY_KEY_SECRET")
    expected = hmac.new(key, f"{order_id}|{payment_id}".encode("utf-8"), hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)

def verify_webhook_signature(raw_body: bytes, signature: str, secret: str | None = None) -> bool:
    """Verify webhook HMAC-SHA256 over the exact raw request bytes."""
    if not raw_body or not signature:
        return False
    key = secret.encode("utf-8") if secret is not None else _secret("RAZORPAY_WEBHOOK_SECRET")
    expected = hmac.new(key, raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)

def parse_verified_webhook(raw_body: bytes, signature: str, secret: str | None = None) -> dict[str, Any]:
    """Return a parsed event only after the signature is valid."""
    if not verify_webhook_signature(raw_body, signature, secret):
        raise PaymentVerificationError("Invalid webhook signature")
    try:
        payload = json.loads(raw_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PaymentVerificationError("Webhook body is not valid UTF-8 JSON") from exc
    if not isinstance(payload, dict) or not isinstance(payload.get("event"), str):
        raise PaymentVerificationError("Webhook payload is missing an event name")
    return payload

def payment_is_captured(event_payload: dict[str, Any]) -> bool:
    """Recognize only explicit provider events that indicate successful capture/paid order."""
    event = event_payload.get("event")
    if event == "payment.captured":
        payment = (event_payload.get("payload") or {}).get("payment", {}).get("entity", {})
        return payment.get("status") == "captured"
    if event == "order.paid":
        order = (event_payload.get("payload") or {}).get("order", {}).get("entity", {})
        return order.get("status") == "paid"
    return False

def create_order_configuration() -> dict[str, str]:
    """Fail closed until credentials and a real server-side order adapter are configured."""
    key_id = os.environ.get("RAZORPAY_KEY_ID", "")
    key_secret = os.environ.get("RAZORPAY_KEY_SECRET", "")
    if not key_id or not key_secret:
        raise PaymentVerificationError("Razorpay credentials are not configured; order creation is unavailable")
    return {"key_id": key_id, "integration_status": "credentials_present_adapter_required"}
