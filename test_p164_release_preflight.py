import json, os, pathlib, subprocess, sys, unittest
ROOT=pathlib.Path(__file__).resolve().parent
class P164ReleasePreflightTests(unittest.TestCase):
 def test_missing_external_configuration_is_blocked_without_secret_leak(self):
  env={k:v for k,v in os.environ.items() if k not in {"VIDGRAVITY_PUBLIC_BASE_URL","VIDGRAVITY_LICENSE_KEYS","RAZORPAY_KEY_ID","RAZORPAY_KEY_SECRET","RAZORPAY_WEBHOOK_SECRET","VIDGRAVITY_LIVE_TEST_URL","VIDGRAVITY_PROVIDER_EGRESS_ISOLATED"}}
  p=subprocess.run([sys.executable,str(ROOT/"p164_release_preflight.py")],env=env,capture_output=True,text=True)
  self.assertEqual(p.returncode,2,p.stdout+p.stderr)
  report=json.loads((ROOT/"P164_RELEASE_PREFLIGHT_REPORT.json").read_text())
  self.assertEqual(report["result"],"BLOCKED")
  self.assertGreater(report["checks_blocked"],0)
  self.assertNotIn("RAZORPAY_KEY_SECRET=",p.stdout)
 def test_configured_values_are_never_echoed(self):
  env=os.environ.copy(); env.update({"VIDGRAVITY_PUBLIC_BASE_URL":"https://example.invalid","VIDGRAVITY_LICENSE_KEYS":"SECRET_LICENSE_EXAMPLE","RAZORPAY_KEY_ID":"key_secret_example","RAZORPAY_KEY_SECRET":"secret_value_example","RAZORPAY_WEBHOOK_SECRET":"webhook_secret_example","VIDGRAVITY_LIVE_TEST_URL":"https://media.example.invalid/test.mp4","VIDGRAVITY_PROVIDER_EGRESS_ISOLATED":"true"})
  p=subprocess.run([sys.executable,str(ROOT/"p164_release_preflight.py")],env=env,capture_output=True,text=True)
  self.assertNotIn("SECRET_LICENSE_EXAMPLE",p.stdout); self.assertNotIn("secret_value_example",p.stdout); self.assertNotIn("webhook_secret_example",p.stdout)
 def test_report_has_explicit_blocker_actions(self):
  env={k:v for k,v in os.environ.items() if k not in {"VIDGRAVITY_PUBLIC_BASE_URL","VIDGRAVITY_LICENSE_KEYS","RAZORPAY_KEY_ID","RAZORPAY_KEY_SECRET","RAZORPAY_WEBHOOK_SECRET","VIDGRAVITY_LIVE_TEST_URL","VIDGRAVITY_PROVIDER_EGRESS_ISOLATED"}}
  subprocess.run([sys.executable,str(ROOT/"p164_release_preflight.py")],env=env,capture_output=True,text=True)
  report=json.loads((ROOT/"P164_RELEASE_PREFLIGHT_REPORT.json").read_text())
  self.assertTrue(all(x["release_blocker"] for x in report["checks"] if x["status"]=="BLOCKED"))
if __name__=="__main__": unittest.main(verbosity=2)
