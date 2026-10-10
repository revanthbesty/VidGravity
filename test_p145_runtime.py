import os, unittest
from unittest.mock import patch
from vidgravity_app import combine_apps, load_integration, RuntimeConfigurationError

class P145Tests(unittest.TestCase):
    def test_missing_integration_module_fails_closed(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(RuntimeConfigurationError):
                load_integration()

    def test_bad_integration_module_fails_closed(self):
        with patch.dict(os.environ, {"VIDGRAVITY_INTEGRATION_MODULE":"json"}, clear=True):
            with self.assertRaises(RuntimeConfigurationError):
                load_integration()

    def test_route_dispatch_payment_and_admin(self):
        seen=[]
        def pay(env, start): seen.append("payment"); return [b"p"]
        def admin(env, start): seen.append("admin"); return [b"a"]
        app=combine_apps(pay,admin)
        def start(status, headers): pass
        self.assertEqual(app({"PATH_INFO":"/api/payment/create-order"},start),[b"p"])
        self.assertEqual(app({"PATH_INFO":"/api/admin/payments/dashboard"},start),[b"a"])
        self.assertEqual(seen,["payment","admin"])

    def test_health_is_available_without_credentials(self):
        app=combine_apps(lambda e,s: [], lambda e,s: [])
        response={}
        def start(status, headers): response["status"]=status
        body=b"".join(app({"PATH_INFO":"/health"},start))
        self.assertEqual(response["status"],"200 OK")
        self.assertIn(b'"status": "ok"',body)

if __name__ == "__main__": unittest.main(verbosity=2)
