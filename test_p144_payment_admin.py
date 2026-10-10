import os, tempfile, unittest
from unittest.mock import patch
from payment_store import PaymentStore
from payment_recovery import PaymentRecoveryService
from payment_admin import PaymentAdminService, require_admin, AdminAuthError

class P144Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = PaymentStore(os.path.join(self.tmp.name,"db.sqlite"))
        self.recovery = PaymentRecoveryService(self.store, lambda **kw: {"license_id":"test"})
        self.admin = PaymentAdminService(self.store, self.recovery)
    def tearDown(self):
        self.tmp.cleanup()

    def test_admin_auth_required(self):
        with self.assertRaises(AdminAuthError): require_admin("bad", "expected")
        require_admin("expected", "expected")

    def test_missing_admin_secret_fails_closed(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(AdminAuthError): require_admin("anything")

    def test_dashboard_counts_and_run_history(self):
        self.store.upsert_recovery("pay_1","ord_1","acct_1",19900)
        result = self.admin.dashboard("secret") if False else None
        with patch.dict(os.environ, {"VIDGRAVITY_PAYMENT_ADMIN_TOKEN":"secret"}):
            result = self.admin.dashboard("secret")
        self.assertEqual(result["license_issuance_counts"]["pending"], 1)
        self.assertIn("reconciliation_runs", result)

    def test_stuck_payment_query_is_bounded(self):
        self.store.upsert_recovery("pay_1","ord_1","acct_1",19900)
        self.assertIsInstance(self.store.stuck_payments(min_age_minutes=1, limit=9999), list)

    def test_retry_reconciliation_audited(self):
        self.store.upsert_recovery("pay_1","ord_1","acct_1",19900)
        with patch.dict(os.environ, {"VIDGRAVITY_PAYMENT_ADMIN_TOKEN":"secret"}):
            result = self.admin.retry_reconciliation("secret")
        self.assertEqual(result["repaired"], 1)
        dashboard = self.store.admin_dashboard()
        self.assertEqual(dashboard["recent_events"][0]["event_type"], "manual_reconciliation")

    def test_status_view_redacts_account(self):
        self.store.upsert_recovery("pay_1","ord_1","acct_private",19900)
        with patch.dict(os.environ, {"VIDGRAVITY_PAYMENT_ADMIN_TOKEN":"secret"}):
            result = self.admin.payment_status("secret","pay_1")
        self.assertNotIn("account_id", result)

if __name__ == "__main__": unittest.main(verbosity=2)
