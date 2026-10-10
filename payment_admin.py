"""P144 operational admin service. Put behind strong admin authentication."""
from __future__ import annotations
import hmac
import os
from payment_store import PaymentStore
from payment_recovery import PaymentRecoveryService

class AdminAuthError(PermissionError):
    pass

def require_admin(provided_token: str | None, expected_token: str | None = None) -> None:
    expected = expected_token if expected_token is not None else os.environ.get("VIDGRAVITY_PAYMENT_ADMIN_TOKEN", "")
    if not expected or not provided_token or not hmac.compare_digest(expected, provided_token):
        raise AdminAuthError("Admin authentication failed")

class PaymentAdminService:
    def __init__(self, store: PaymentStore, recovery: PaymentRecoveryService):
        self.store = store
        self.recovery = recovery

    def dashboard(self, admin_token: str) -> dict:
        require_admin(admin_token)
        data = self.store.admin_dashboard()
        data["stuck_payments"] = self.store.stuck_payments()
        return data

    def retry_reconciliation(self, admin_token: str, limit: int = 100) -> dict:
        require_admin(admin_token)
        result = self.recovery.reconcile(limit=max(1, min(int(limit), 500)))
        self.store.audit_event("manual_reconciliation", status="completed",
                               detail_code=f"repaired_{result['repaired']}_failed_{result['failed']}")
        return result

    def payment_status(self, admin_token: str, payment_id: str) -> dict | None:
        require_admin(admin_token)
        status = self.recovery.status(payment_id)
        if status is not None:
            self.store.audit_event("admin_status_view", payment_id=payment_id, status="viewed")
        return status
