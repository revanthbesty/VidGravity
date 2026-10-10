import os, tempfile, unittest
from payment_store import PaymentStore
from payment_recovery import PaymentRecoveryService

class P143Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = PaymentStore(os.path.join(self.tmp.name, "payments.db"))
        self.calls = []
    def tearDown(self):
        self.tmp.cleanup()

    def test_captured_payment_registered(self):
        svc = PaymentRecoveryService(self.store, lambda **kw: self.calls.append(kw))
        svc.register_captured_payment("pay_1","order_1","acct_1",19900)
        self.assertEqual(self.store.get_recovery("pay_1")["issuance_status"], "pending")

    def test_wrong_amount_rejected(self):
        svc = PaymentRecoveryService(self.store, lambda **kw: self.calls.append(kw))
        with self.assertRaises(ValueError):
            svc.register_captured_payment("pay_1","order_1","acct_1",100)

    def test_successful_reconciliation(self):
        svc = PaymentRecoveryService(self.store, lambda **kw: self.calls.append(kw) or {"license_id":"x"})
        svc.register_captured_payment("pay_1","order_1","acct_1",19900)
        result = svc.reconcile()
        self.assertEqual(result["repaired"], 1)
        self.assertEqual(self.store.get_recovery("pay_1")["issuance_status"], "issued")
        self.assertEqual(self.calls[0]["idempotency_key"], "pay_1")

    def test_failed_issuance_becomes_retryable(self):
        def broken(**kw): raise RuntimeError("secret backend details should not persist")
        svc = PaymentRecoveryService(self.store, broken)
        svc.register_captured_payment("pay_1","order_1","acct_1",19900)
        result = svc.reconcile()
        self.assertEqual(result["failed"], 1)
        row = self.store.get_recovery("pay_1")
        self.assertEqual(row["issuance_status"], "retry")
        self.assertEqual(row["last_error_code"], "issuer_unavailable")

    def test_claim_prevents_concurrent_duplicate_issue(self):
        svc = PaymentRecoveryService(self.store, lambda **kw: self.calls.append(kw))
        svc.register_captured_payment("pay_1","order_1","acct_1",19900)
        self.assertTrue(self.store.claim_recovery("pay_1"))
        self.assertFalse(self.store.claim_recovery("pay_1"))

    def test_status_hides_account_details(self):
        svc = PaymentRecoveryService(self.store, lambda **kw: None)
        svc.register_captured_payment("pay_1","order_1","acct_secret",19900)
        status = svc.status("pay_1")
        self.assertNotIn("account_id", status)
        self.assertNotIn("next_retry_at", status)

if __name__ == "__main__":
    unittest.main(verbosity=2)
