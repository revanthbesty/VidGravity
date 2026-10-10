import os, unittest
from unittest.mock import patch
from razorpay_adapter import (
    RazorpayConfig, RazorpayAdapter, RazorpayConfigurationError,
    RazorpayAPIError
)

class P141Tests(unittest.TestCase):
    def test_config_requires_credentials(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(RazorpayConfigurationError):
                RazorpayConfig.from_env()

    def test_mode_defaults_to_test(self):
        cfg = RazorpayConfig("rzp_test_x", "secret")
        self.assertEqual(cfg.mode, "test")

    def test_live_mode_locked_by_default(self):
        env = {"RAZORPAY_KEY_ID":"rzp_live_x", "RAZORPAY_KEY_SECRET":"secret", "RAZORPAY_MODE":"live"}
        with patch.dict(os.environ, env, clear=True):
            with self.assertRaises(RazorpayConfigurationError):
                RazorpayConfig.from_env()

    def test_live_mode_needs_explicit_enable(self):
        env = {"RAZORPAY_KEY_ID":"rzp_live_x", "RAZORPAY_KEY_SECRET":"secret", "RAZORPAY_MODE":"live", "VIDGRAVITY_ALLOW_LIVE_PAYMENTS":"1"}
        with patch.dict(os.environ, env, clear=True):
            self.assertEqual(RazorpayConfig.from_env().mode, "live")

    def test_order_validation(self):
        adapter = RazorpayAdapter(RazorpayConfig("rzp_test_x", "secret"))
        adapter._request = lambda method, path, payload=None: {"id":"order_1","amount":19900,"currency":"INR","status":"created"}
        result = adapter.create_annual_order("acct_1", "receipt_1")
        self.assertEqual(result["id"], "order_1")
        self.assertEqual(result["amount"], 19900)

    def test_reject_wrong_amount(self):
        adapter = RazorpayAdapter(RazorpayConfig("rzp_test_x", "secret"))
        adapter._request = lambda method, path, payload=None: {"id":"order_1","amount":9900,"currency":"INR"}
        with self.assertRaises(RazorpayAPIError):
            adapter.create_annual_order("acct_1", "receipt_1")

    def test_provider_capture_and_order_match(self):
        adapter = RazorpayAdapter(RazorpayConfig("rzp_test_x", "secret"))
        adapter.fetch_payment = lambda pid: {"id":pid,"order_id":"order_1","amount":19900,"currency":"INR","status":"captured","captured":True}
        self.assertEqual(adapter.verify_captured_payment("pay_1", "order_1")["status"], "captured")
        with self.assertRaises(RazorpayAPIError):
            adapter.verify_captured_payment("pay_1", "order_wrong")

    def test_reject_authorized_payment(self):
        adapter = RazorpayAdapter(RazorpayConfig("rzp_test_x", "secret"))
        adapter.fetch_payment = lambda pid: {"id":pid,"order_id":"order_1","amount":19900,"currency":"INR","status":"authorized","captured":False}
        with self.assertRaises(RazorpayAPIError):
            adapter.verify_captured_payment("pay_1", "order_1")

if __name__ == "__main__":
    unittest.main(verbosity=2)
