"""VidGravity P141 Razorpay adapter foundation.

Uses Python standard library only. Network calls are disabled unless explicitly invoked by
the host application with configured credentials. Use only Razorpay test keys until sandbox
tests pass. Never expose the key secret to the browser.
"""
from __future__ import annotations
import base64
import json
import os
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

RAZORPAY_API_BASE = "https://api.razorpay.com/v1"

class RazorpayConfigurationError(RuntimeError):
    pass

class RazorpayAPIError(RuntimeError):
    pass

@dataclass(frozen=True)
class RazorpayConfig:
    key_id: str
    key_secret: str
    mode: str = "test"

    @classmethod
    def from_env(cls) -> "RazorpayConfig":
        key_id = os.environ.get("RAZORPAY_KEY_ID", "")
        key_secret = os.environ.get("RAZORPAY_KEY_SECRET", "")
        mode = os.environ.get("RAZORPAY_MODE", "test").strip().lower()
        if not key_id or not key_secret:
            raise RazorpayConfigurationError("Razorpay credentials are not configured")
        if mode not in {"test", "live"}:
            raise RazorpayConfigurationError("RAZORPAY_MODE must be 'test' or 'live'")
        if mode == "live" and os.environ.get("VIDGRAVITY_ALLOW_LIVE_PAYMENTS") != "1":
            raise RazorpayConfigurationError("Live payments are locked; explicitly enable only after test-mode acceptance")
        return cls(key_id, key_secret, mode)

class RazorpayAdapter:
    def __init__(self, config: RazorpayConfig | None = None, timeout: float = 10.0):
        self.config = config or RazorpayConfig.from_env()
        self.timeout = timeout

    def _request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        raw = f"{self.config.key_id}:{self.config.key_secret}".encode()
        auth = base64.b64encode(raw).decode("ascii")
        body = json.dumps(payload).encode("utf-8") if payload is not None else None
        req = urllib.request.Request(
            RAZORPAY_API_BASE + path,
            data=body,
            method=method,
            headers={"Authorization": f"Basic {auth}", "Content-Type": "application/json", "Accept": "application/json"}
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                parsed = json.loads(response.read().decode("utf-8"))
                if not isinstance(parsed, dict):
                    raise RazorpayAPIError("Unexpected Razorpay response shape")
                return parsed
        except urllib.error.HTTPError as exc:
            # Do not echo credentials, request headers, or full body in user-facing errors.
            raise RazorpayAPIError(f"Razorpay API returned HTTP {exc.code}") from None
        except (urllib.error.URLError, TimeoutError) as exc:
            raise RazorpayAPIError("Razorpay API request failed or timed out") from None
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise RazorpayAPIError("Razorpay returned an invalid JSON response") from None

    def create_annual_order(self, account_id: str, receipt: str) -> dict[str, Any]:
        """Create ₹199/year INR order; caller must persist order-to-account mapping."""
        if not account_id or not receipt:
            raise ValueError("account_id and receipt are required")
        result = self._request("POST", "/orders", {
            "amount": 19900, "currency": "INR", "receipt": receipt,
            "notes": {"product": "VidGravity", "plan": "annual_199", "account_id": account_id}
        })
        if result.get("amount") != 19900 or result.get("currency") != "INR" or not result.get("id"):
            raise RazorpayAPIError("Razorpay order response did not match expected plan")
        return {"id": result["id"], "amount": result["amount"], "currency": result["currency"], "status": result.get("status")}

    def fetch_payment(self, payment_id: str) -> dict[str, Any]:
        if not payment_id:
            raise ValueError("payment_id is required")
        result = self._request("GET", f"/payments/{payment_id}")
        return {
            "id": result.get("id"), "order_id": result.get("order_id"),
            "amount": result.get("amount"), "currency": result.get("currency"),
            "status": result.get("status"), "captured": result.get("captured", False)
        }

    def verify_captured_payment(self, payment_id: str, expected_order_id: str, expected_account_id: str | None = None) -> dict[str, Any]:
        """Verify provider-side capture and exact order/amount/currency; account association is caller-owned."""
        payment = self.fetch_payment(payment_id)
        if payment.get("order_id") != expected_order_id:
            raise RazorpayAPIError("Payment does not belong to the expected order")
        if payment.get("amount") != 19900 or payment.get("currency") != "INR":
            raise RazorpayAPIError("Payment amount or currency does not match VidGravity annual plan")
        if payment.get("status") != "captured" or payment.get("captured") is not True:
            raise RazorpayAPIError("Payment has not been captured")
        return payment
