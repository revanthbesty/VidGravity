import importlib.util, json, os, pathlib, tempfile, threading, unittest, urllib.request, urllib.error
from http.server import ThreadingHTTPServer
ROOT=pathlib.Path(__file__).resolve().parent
SPEC=importlib.util.spec_from_file_location("vg_p163_acceptance_server",ROOT/"server.py")
server=importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(server)

class P163ReleaseAcceptanceTests(unittest.TestCase):
 def test_frontend_android_bundle_is_consistent(self):
  for name in ("index.html","app.js","styles.css","manifest.webmanifest","sw.js"):
   self.assertEqual((ROOT/name).read_bytes(),(ROOT/"android-web"/name).read_bytes(),name)
 def test_pwa_manifest_is_valid_and_names_application(self):
  data=json.loads((ROOT/"manifest.webmanifest").read_text())
  self.assertTrue(data.get("name") or data.get("short_name"))
  self.assertIn(data.get("display"),("standalone","minimal-ui","fullscreen"))
 def test_release_security_docs_disclose_remaining_limits(self):
  doc=(ROOT/"P162_SECURITY_HARDENING.md").read_text()
  self.assertIn("CORS/origin validation is not authentication",doc)
  self.assertIn("provider process",doc.lower())
 def test_http_invalid_format_selector_is_rejected_before_job_creation(self):
  with tempfile.TemporaryDirectory() as d:
   old_db,old_out=server.DOWNLOAD_DB_PATH,server.DOWNLOAD_OUTPUT_DIR
   old_valid=server.valid_url
   server.DOWNLOAD_DB_PATH=pathlib.Path(d)/"jobs.sqlite3"
   server.DOWNLOAD_OUTPUT_DIR=pathlib.Path(d)/"downloads"
   server.valid_url=lambda url:(True,"") if url=="https://acceptance.invalid/clip.mp4" else old_valid(url)
   server.DOWNLOAD_JOBS.clear(); server._init_job_store()
   httpd=ThreadingHTTPServer(("127.0.0.1",0),server.Handler)
   thread=threading.Thread(target=httpd.serve_forever,daemon=True);thread.start()
   try:
    body=json.dumps({"url":"https://acceptance.invalid/clip.mp4","format":"best; touch /tmp/not-allowed"}).encode()
    req=urllib.request.Request("http://127.0.0.1:%d/api/download-jobs"%httpd.server_address[1],data=body,headers={"Content-Type":"application/json"},method="POST")
    with self.assertRaises(urllib.error.HTTPError) as caught: urllib.request.urlopen(req,timeout=3)
    self.assertEqual(caught.exception.code,400)
    self.assertIn("Invalid format selector",caught.exception.read().decode())
    self.assertEqual(server.DOWNLOAD_JOBS,{})
   finally:
    httpd.shutdown();httpd.server_close();thread.join(timeout=2)
    server.valid_url=old_valid
    server.DOWNLOAD_DB_PATH=old_db;server.DOWNLOAD_OUTPUT_DIR=old_out
    server.DOWNLOAD_JOBS.clear();server._init_job_store()
 def test_licensing_routes_require_a_valid_configured_key(self):
  with tempfile.TemporaryDirectory() as d:
   old_db=server.LICENSE_DB_PATH; old_keys=os.environ.get("VIDGRAVITY_LICENSE_KEYS")
   server.LICENSE_DB_PATH=pathlib.Path(d)/"license.sqlite3"
   os.environ["VIDGRAVITY_LICENSE_KEYS"]="P163-ACCEPTANCE-KEY"
   httpd=ThreadingHTTPServer(("127.0.0.1",0),server.Handler)
   thread=threading.Thread(target=httpd.serve_forever,daemon=True);thread.start()
   base="http://127.0.0.1:%d"%httpd.server_address[1]
   try:
    payload=json.dumps({"license_key":"wrong","device_id":"acceptance-device"}).encode()
    req=urllib.request.Request(base+"/api/license/activate",data=payload,headers={"Content-Type":"application/json"},method="POST")
    with self.assertRaises(urllib.error.HTTPError) as caught: urllib.request.urlopen(req,timeout=3)
    self.assertEqual(caught.exception.code,403)
    payload=json.dumps({"license_key":"P163-ACCEPTANCE-KEY","device_id":"acceptance-device"}).encode()
    req=urllib.request.Request(base+"/api/license/activate",data=payload,headers={"Content-Type":"application/json"},method="POST")
    with urllib.request.urlopen(req,timeout=3) as response:
     self.assertEqual(response.status,200);self.assertTrue(json.loads(response.read())["active"])
   finally:
    httpd.shutdown();httpd.server_close();thread.join(timeout=2)
    server.LICENSE_DB_PATH=old_db
    if old_keys is None: os.environ.pop("VIDGRAVITY_LICENSE_KEYS",None)
    else: os.environ["VIDGRAVITY_LICENSE_KEYS"]=old_keys

if __name__=="__main__": unittest.main(verbosity=2)
