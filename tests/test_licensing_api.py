import json, os, pathlib, subprocess, tempfile, threading, time, urllib.request, urllib.error, unittest
from http.server import ThreadingHTTPServer
import importlib.util
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("licensing_server",ROOT/"licensing_server.py")
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
class LicensingTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory(); cls.db=pathlib.Path(cls.tmp.name)/"test.sqlite3"
  mod.Handler.db_path=cls.db
  cls.http=ThreadingHTTPServer(("127.0.0.1",0),mod.Handler); cls.port=cls.http.server_address[1]
  cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True); cls.thread.start()
  os.environ["VIDGRAVITY_ADMIN_SECRET"]="test-secret"
  cls.base=f"http://127.0.0.1:{cls.port}"
 @classmethod
 def tearDownClass(cls):
  cls.http.shutdown(); cls.http.server_close(); cls.tmp.cleanup(); os.environ.pop("VIDGRAVITY_ADMIN_SECRET",None)
 def req(self,path,data=None,secret=None):
  body=None if data is None else json.dumps(data).encode()
  headers={}
  if body is not None: headers["Content-Type"]="application/json"
  if secret: headers["X-Admin-Secret"]=secret
  r=urllib.request.Request(self.base+path,data=body,headers=headers,method="GET" if data is None else "POST")
  try:
   with urllib.request.urlopen(r,timeout=3) as x:return x.status,json.loads(x.read())
  except urllib.error.HTTPError as e:return e.code,json.loads(e.read())
 def test_health(self):
  code,b=self.req("/api/license/health"); self.assertEqual(code,200); self.assertEqual(b["device_limit"],1)
 def test_issue_requires_admin(self):
  code,_=self.req("/api/license/issue",{"email":"a@example.com"}); self.assertEqual(code,403)
 def test_issue_and_activate(self):
  code,b=self.req("/api/license/issue",{"email":"a@example.com"},"test-secret"); self.assertEqual(code,201); self.key=b["license_key"]
  code,b=self.req("/api/license/activate",{"license_key":self.key,"device_id":"device-A"}); self.assertEqual(code,200)
 def test_second_device_blocked(self):
  if not hasattr(self,"key"): 
   _,b=self.req("/api/license/issue",{"email":"b@example.com"},"test-secret"); self.key=b["license_key"]
   self.req("/api/license/activate",{"license_key":self.key,"device_id":"device-A"})
  code,_=self.req("/api/license/activate",{"license_key":self.key,"device_id":"device-B"}); self.assertEqual(code,409)
 def test_transfer_after_deactivation(self):
  _,b=self.req("/api/license/issue",{"email":"c@example.com"},"test-secret"); key=b["license_key"]
  self.req("/api/license/activate",{"license_key":key,"device_id":"device-A"})
  code,b=self.req("/api/license/deactivate",{"license_key":key,"device_id":"device-A"}); self.assertEqual(code,200); self.assertTrue(b["deactivated"])
  code,b=self.req("/api/license/activate",{"license_key":key,"device_id":"device-B"}); self.assertEqual(code,200)
 def test_validate(self):
  _,b=self.req("/api/license/issue",{"email":"d@example.com"},"test-secret"); key=b["license_key"]
  self.req("/api/license/activate",{"license_key":key,"device_id":"device-X"})
  code,b=self.req("/api/license/validate",{"license_key":key,"device_id":"device-X"}); self.assertEqual(code,200); self.assertTrue(b["ok"])
 def test_invalid_license_denied(self):
  code,b=self.req("/api/license/activate",{"license_key":"bad","device_id":"device"}); self.assertEqual(code,403)
 def test_bad_email_rejected(self):
  code,_=self.req("/api/license/issue",{"email":"bad"},"test-secret"); self.assertEqual(code,400)
if __name__=="__main__": unittest.main(verbosity=2)
