import hashlib, hmac, json, os, tempfile, unittest
from unittest.mock import patch
from payment_store import PaymentStore
from payment_license_service import PaymentLicenseService, PaymentLicenseError
from payment_verification import PaymentVerificationError

class FakeRazorpay:
    def create_annual_order(self, account_id, receipt):
        return {"id":"order_1","amount":19900,"currency":"INR","status":"created"}
    def verify_captured_payment(self, payment_id, order_id):
        if order_id != "order_1":
            from razorpay_adapter import RazorpayAPIError
            raise RazorpayAPIError("wrong order")
        return {"id":payment_id,"order_id":order_id,"amount":19900,"currency":"INR","status":"captured","captured":True}

class P142Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = PaymentStore(os.path.join(self.tmp.name, "payments.db"))
        self.issued = []
        self.service = PaymentLicenseService(self.store, FakeRazorpay(),
            lambda **kw: self.issued.append(kw) or {"license_id":"lic_test_1"})
    def tearDown(self):
        self.tmp.cleanup()

    def test_order_is_bound_to_account(self):
        order = self.service.create_order("acct_1")
        self.assertEqual(order["amount"], 19900)
        self.assertEqual(self.store.get_order(order["order_id"])["account_id"], "acct_1")

    def test_checkout_issues_only_after_capture_and_signature(self):
        self.service.create_order("acct_1")
        sig = hmac.new(b"checkout_secret", b"order_1|pay_1", hashlib.sha256).hexdigest()
        with patch("payment_license_service.verify_checkout_signature", return_value=True):
            result = self.service.verify_checkout("acct_1", "order_1", "pay_1", sig)
        self.assertEqual(result["status"], "license_issued")
        self.assertEqual(len(self.issued), 1)

    def test_account_mismatch_rejected(self):
        self.service.create_order("acct_1")
        with patch("payment_license_service.verify_checkout_signature", return_value=True):
            with self.assertRaises(PaymentLicenseError):
                self.service.verify_checkout("acct_other", "order_1", "pay_1", "sig")

    def test_duplicate_checkout_does_not_issue_twice(self):
        self.service.create_order("acct_1")
        with patch("payment_license_service.verify_checkout_signature", return_value=True):
            self.service.verify_checkout("acct_1", "order_1", "pay_1", "sig")
            result = self.service.verify_checkout("acct_1", "order_1", "pay_1", "sig")
        self.assertEqual(len(self.issued), 1)
        self.assertEqual(result["status"], "already_issued_or_not_captured")

    def test_invalid_webhook_signature_rejected(self):
        with patch("payment_license_service.parse_verified_webhook", side_effect=PaymentVerificationError("bad signature")):
            with self.assertRaises(PaymentVerificationError):
                self.service.process_webhook(b'{"event":"payment.captured"}', "bad", "evt_1")

    def test_webhook_duplicate_is_ignored(self):
        self.service.create_order("acct_1")
        payload = {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_1","status":"captured"}}}}
        raw = json.dumps(payload).encode()
        with patch("payment_license_service.parse_verified_webhook", return_value=payload):
            first = self.service.process_webhook(raw, "sig", "evt_1")
            second = self.service.process_webhook(raw, "sig", "evt_1")
        self.assertEqual(first["status"], "license_issued")
        self.assertEqual(second["status"], "duplicate_ignored")
        self.assertEqual(len(self.issued), 1)

if __name__ == "__main__":
    unittest.main(verbosity=2)
