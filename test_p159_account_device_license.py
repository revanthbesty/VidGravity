import importlib.util, os, pathlib, tempfile, unittest
APP=pathlib.Path(__file__).resolve().parent
SPEC=importlib.util.spec_from_file_location("vg_p159_server",APP/"server.py")
server=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(server)
class LicenseDeviceTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.old_db=server.LICENSE_DB_PATH;server.LICENSE_DB_PATH=pathlib.Path(self.tmp.name)/"license.sqlite3"
  self.old_keys=os.environ.get("VIDGRAVITY_LICENSE_KEYS");os.environ["VIDGRAVITY_LICENSE_KEYS"]="TEST-P159-VALID"
 def tearDown(self):
  server.LICENSE_DB_PATH=self.old_db
  if self.old_keys is None:os.environ.pop("VIDGRAVITY_LICENSE_KEYS",None)
  else:os.environ["VIDGRAVITY_LICENSE_KEYS"]=self.old_keys
  self.tmp.cleanup()
 def test_valid_key_activates_device(self):
  code,d=server._license_activate("TEST-P159-VALID","device-A");self.assertEqual(code,200);self.assertTrue(d["active"]);self.assertTrue(server._license_status("device-A")["active"])
 def test_invalid_key_rejected(self):
  code,d=server._license_activate("WRONG","device-A");self.assertEqual(code,403);self.assertFalse(server._license_status("device-A")["active"])
 def test_one_active_device_enforced(self):
  self.assertEqual(server._license_activate("TEST-P159-VALID","device-A")[0],200);code,d=server._license_activate("TEST-P159-VALID","device-B");self.assertEqual(code,409);self.assertIn("another device",d["error"])
 def test_deactivation_allows_transfer(self):
  self.assertEqual(server._license_activate("TEST-P159-VALID","device-A")[0],200);self.assertEqual(server._license_deactivate("TEST-P159-VALID","device-A")[0],200);self.assertEqual(server._license_activate("TEST-P159-VALID","device-B")[0],200)
 def test_other_device_cannot_deactivate(self):
  server._license_activate("TEST-P159-VALID","device-A");self.assertEqual(server._license_deactivate("TEST-P159-VALID","device-B")[0],403)
 def test_key_is_not_stored_in_plaintext(self):
  server._license_activate("TEST-P159-VALID","device-A");import sqlite3
  with sqlite3.connect(server.LICENSE_DB_PATH) as db:stored=db.execute("SELECT license_hash FROM license_devices").fetchone()[0]
  self.assertNotEqual(stored,"TEST-P159-VALID");self.assertEqual(len(stored),64)
 def test_frontend_has_activation_ui_and_server_routes(self):
  js=(APP/"app.js").read_text();html=(APP/"index.html").read_text()
  for x in ["vgLicenseDeviceId","/api/license/status","/api/license/"]:self.assertIn(x,js)
  for x in ["licenseKeyInput","activateLicenseBtn","deactivateLicenseBtn"]:self.assertIn(x,html)
if __name__=="__main__":unittest.main(verbosity=2)
