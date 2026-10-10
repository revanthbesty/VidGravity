"""P142 coordinator for verified payment -> idempotent license issuance.

Framework-neutral service layer. HTTP handlers should authenticate users, validate CSRF/origin
as appropriate, and call these functions. Never trust client success flags or client-supplied
account/order associations.
"""
from __future__ import annotations
import hashlib
import json
import os
import secrets
from typing import Any
from payment_store import PaymentStore
from payment_verification import (
    PaymentVerificationError, parse_verified_webhook, verify_checkout_signature
)
from razorpay_adapter import RazorpayAdapter, RazorpayAPIError

EXPECTED_AMOUNT_PAISE = 19900
EXPECTED_CURRENCY = "INR"

class PaymentLicenseError(RuntimeError):
    pass

class PaymentLicenseService:
    def __init__(self, store: PaymentStore, razorpay: RazorpayAdapter, license_issuer):
        self.store = store
        self.razorpay = razorpay
        self.license_issuer = license_issuer

    def create_order(self, account_id: str) -> dict[str, Any]:
        if not account_id:
            raise PaymentLicenseError("Authenticated account is required")
        # Receipt is unique and contains no personally identifying data.
        receipt = "vg_" + secrets.token_hex(12)
        order = self.razorpay.create_annual_order(account_id, receipt)
        self.store.save_order(order["id"], account_id, EXPECTED_AMOUNT_PAISE, EXPECTED_CURRENCY, receipt)
        return {"order_id": order["id"], "amount": EXPECTED_AMOUNT_PAISE, "currency": EXPECTED_CURRENCY}

    def verify_checkout(self, account_id: str, order_id: str, payment_id: str, signature: str) -> dict[str, Any]:
        saved = self.store.get_order(order_id)
        if not saved or saved["account_id"] != account_id:
            raise PaymentLicenseError("Order not found for authenticated account")
        if not verify_checkout_signature(order_id, payment_id, signature):
            raise PaymentLicenseError("Checkout signature verification failed")
        try:
            payment = self.razorpay.verify_captured_payment(payment_id, order_id)
        except RazorpayAPIError as exc:
            raise PaymentLicenseError(str(exc)) from None
        self.store.record_captured_payment(payment_id, order_id, payment["amount"], payment["currency"], "provider_api")
        return self._issue_once(account_id, payment_id, order_id)

    def process_webhook(self, raw_body: bytes, signature: str, event_id: str) -> dict[str, Any]:
        payload = parse_verified_webhook(raw_body, signature)
        event_type = payload["event"]
        entity = {}
        if event_type == "payment.captured":
            entity = (payload.get("payload") or {}).get("payment", {}).get("entity", {})
        elif event_type == "order.paid":
            entity = (payload.get("payload") or {}).get("order", {}).get("entity", {})
        payment_id = entity.get("id") if event_type == "payment.captured" else entity.get("payment_id")
        order_id = entity.get("order_id") if event_type == "payment.captured" else entity.get("id")
        if not event_id:
            raise PaymentLicenseError("Provider event ID is required")
        inserted = self.store.record_event_once(event_id, event_type, payment_id, order_id)
        if not inserted:
            return {"status": "duplicate_ignored"}
        if event_type not in {"payment.captured", "order.paid"}:
            return {"status": "accepted_no_license"}
        # For webhooks, independently confirm capture and reconcile order/account from our DB.
        if not payment_id or not order_id:
            return {"status": "accepted_pending_reconciliation"}
        saved = self.store.get_order(order_id)
        if not saved:
            return {"status": "accepted_pending_reconciliation"}
        try:
            payment = self.razorpay.verify_captured_payment(payment_id, order_id)
        except RazorpayAPIError:
            return {"status": "accepted_pending_reconciliation"}
        self.store.record_captured_payment(payment_id, order_id, payment["amount"], payment["currency"], "webhook_and_provider_api")
        return self._issue_once(saved["account_id"], payment_id, order_id)

    def _issue_once(self, account_id: str, payment_id: str, order_id: str) -> dict[str, Any]:
        # Atomic one-time gate. If issuer fails after gate, manual/retry reconciliation is required.
        if not self.store.mark_license_issued_once(payment_id):
            return {"status": "already_issued_or_not_captured"}
        try:
            result = self.license_issuer(account_id=account_id, payment_id=payment_id,
                                         order_id=order_id, plan="annual_199", duration_days=365)
        except Exception:
            # Avoid falsely claiming success. A production implementation should use an outbox/
            # issuance state machine rather than flipping this flag back blindly.
            raise PaymentLicenseError("Payment captured; license issuance needs reconciliation") from None
        return {"status": "license_issued", "license": result}
