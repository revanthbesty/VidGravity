"""P143 durable payment-to-license recovery and reconciliation."""
from __future__ import annotations
from typing import Any
from payment_store import PaymentStore

class PaymentRecoveryService:
    def __init__(self, store: PaymentStore, license_issuer):
        self.store = store
        self.license_issuer = license_issuer

    def register_captured_payment(self, payment_id: str, order_id: str, account_id: str,
                                  amount_paise: int, currency: str = "INR") -> None:
        """Call only after verified provider capture and order/account/amount validation."""
        if amount_paise != 19900 or currency != "INR":
            raise ValueError("Payment does not match VidGravity annual plan")
        self.store.upsert_recovery(payment_id, order_id, account_id, amount_paise, currency)

    def reconcile(self, limit: int = 100) -> dict[str, Any]:
        run_id = self.store.start_reconciliation()
        inspected = repaired = failed = 0
        try:
            for row in self.store.pending_recoveries(limit):
                inspected += 1
                payment_id = row["provider_payment_id"]
                if not self.store.claim_recovery(payment_id):
                    continue
                try:
                    # Issuer must use payment_id as its own idempotency key as well.
                    self.license_issuer(
                        account_id=row["account_id"],
                        payment_id=payment_id,
                        order_id=row["provider_order_id"],
                        plan="annual_199",
                        duration_days=365,
                        idempotency_key=payment_id,
                    )
                    if self.store.finish_recovery(payment_id):
                        repaired += 1
                except Exception:
                    self.store.fail_recovery(payment_id, "issuer_unavailable")
                    failed += 1
        finally:
            self.store.finish_reconciliation(run_id, inspected, repaired, failed,
                                             "issuer errors redacted; retryable records retained")
        return {"run_id": run_id, "inspected": inspected, "repaired": repaired, "failed": failed}

    def status(self, payment_id: str) -> dict[str, Any] | None:
        row = self.store.get_recovery(payment_id)
        if not row:
            return None
        # Avoid returning internal retry timestamps or account identifiers to clients.
        return {
            "payment_id": row["provider_payment_id"],
            "payment_status": row["payment_status"],
            "license_status": row["issuance_status"],
            "attempts": row["attempts"],
        }
