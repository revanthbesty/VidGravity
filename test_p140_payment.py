import hashlib, hmac, json, os, tempfile, unittest
from payment_verification import (
    verify_checkout_signature, verify_webhook_signature, parse_verified_webhook,
    payment_is_captured, PaymentVerificationError, create_order_configuration
)
from payment_store import PaymentStore

class P140Tests(unittest.TestCase):
    def test_checkout_signature_valid(self):
        msg = b"order_123|pay_456"
        sig = hmac.new(b"test_secret", msg, hashlib.sha256).hexdigest()
        self.assertTrue(verify_checkout_signature("order_123", "pay_456", sig, "test_secret"))

    def test_checkout_signature_invalid(self):
        self.assertFalse(verify_checkout_signature("order_123", "pay_456", "bad", "test_secret"))

    def test_webhook_signature_valid_and_invalid(self):
        body = b'{"event":"payment.captured"}'
        sig = hmac.new(b"hook_secret", body, hashlib.sha256).hexdigest()
        self.assertTrue(verify_webhook_signature(body, sig, "hook_secret"))
        self.assertFalse(verify_webhook_signature(body + b" ", sig, "hook_secret"))

    def test_parse_rejects_bad_signature(self):
        with self.assertRaises(PaymentVerificationError):
            parse_verified_webhook(b'{"event":"payment.captured"}', "bad", "hook_secret")

    def test_only_explicit_captured_events_qualify(self):
        self.assertTrue(payment_is_captured({"event":"payment.captured","payload":{"payment":{"entity":{"status":"captured"}}}}))
        self.assertTrue(payment_is_captured({"event":"order.paid","payload":{"order":{"entity":{"status":"paid"}}}}))
        self.assertFalse(payment_is_captured({"event":"payment.authorized","payload":{"payment":{"entity":{"status":"authorized"}}}}))
        self.assertFalse(payment_is_captured({"event":"payment.captured","payload":{"payment":{"entity":{"status":"authorized"}}}}))

    def test_webhook_event_idempotency(self):
        with tempfile.TemporaryDirectory() as d:
            store = PaymentStore(os.path.join(d, "payments.db"))
            self.assertTrue(store.record_event_once("evt_1", "payment.captured", "pay_1", "ord_1"))
            self.assertFalse(store.record_event_once("evt_1", "payment.captured", "pay_1", "ord_1"))

    def test_license_issuance_gate_is_once_only(self):
        with tempfile.TemporaryDirectory() as d:
            store = PaymentStore(os.path.join(d, "payments.db"))
            self.assertFalse(store.mark_license_issued_once("unknown"))
            store.record_captured_payment("pay_1", "ord_1", 19900)
            self.assertTrue(store.mark_license_issued_once("pay_1"))
            self.assertFalse(store.mark_license_issued_once("pay_1"))

    def test_order_creation_fails_closed_without_credentials(self):
        old_id, old_secret = os.environ.pop("RAZORPAY_KEY_ID", None), os.environ.pop("RAZORPAY_KEY_SECRET", None)
        try:
            with self.assertRaises(PaymentVerificationError):
                create_order_configuration()
        finally:
            if old_id is not None: os.environ["RAZORPAY_KEY_ID"] = old_id
            if old_secret is not None: os.environ["RAZORPAY_KEY_SECRET"] = old_secret

if __name__ == "__main__":
    unittest.main(verbosity=2)
