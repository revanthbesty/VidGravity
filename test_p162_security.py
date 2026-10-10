import importlib.util, ipaddress, json, os, pathlib, tempfile, threading, unittest, urllib.request, urllib.error
from http.server import ThreadingHTTPServer
ROOT=pathlib.Path(__file__).resolve().parent
SPEC=importlib.util.spec_from_file_location("vg_p162_server",ROOT/"server.py")
server=importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(server)

class P162SecurityTests(unittest.TestCase):
 def setUp(self):
  self.old_allow=server.ALLOW_LOCAL
  server.ALLOW_LOCAL=False
  server._RATE_BUCKETS.clear()
 def tearDown(self):
  server.ALLOW_LOCAL=self.old_allow
  server._RATE_BUCKETS.clear()
 def test_json_body_must_be_object(self):
  self.assertEqual(server._parse_json_bytes(b'{"url":"https://example.com"}')["url"],"https://example.com")
  with self.assertRaisesRegex(ValueError,"object"): server._parse_json_bytes(b'[]')
  with self.assertRaisesRegex(ValueError,"Invalid JSON"): server._parse_json_bytes(b'{oops}')
 def test_json_body_size_limit(self):
  with self.assertRaisesRegex(ValueError,"too large"): server._parse_json_bytes(b"x"*30,max_bytes=20)
 def test_url_rejects_credentials_schemes_and_bad_ports(self):
  self.assertFalse(server.valid_url("https://user:pass@example.com/video")[0])
  self.assertFalse(server.valid_url("file:///etc/passwd")[0])
  self.assertFalse(server.valid_url("https://example.com:99999/video")[0])
 def test_url_rejects_control_chars_and_oversize(self):
  self.assertFalse(server.valid_url("https://example.com/a"+chr(10)+"B")[0])
  self.assertFalse(server.valid_url("https://example.com/"+"a"*8200)[0])
 def test_origin_allowlist_is_exact_except_loopback_development(self):
  self.assertTrue(server._safe_origin("https://trusted.example","203.0.113.4",{"https://trusted.example"}))
  self.assertFalse(server._safe_origin("https://evil.example","203.0.113.4",{"https://trusted.example"}))
  self.assertTrue(server._safe_origin("http://localhost:5173","127.0.0.1",set()))
  self.assertFalse(server._safe_origin("http://localhost:5173","203.0.113.4",set()))
 def test_rate_limiter_blocks_after_bucket_budget(self):
  server._RATE_LIMITS["unit-test"]=(2,60)
  self.assertFalse(server._rate_limit_exceeded("198.51.100.1","unit-test",now=100))
  self.assertFalse(server._rate_limit_exceeded("198.51.100.1","unit-test",now=101))
  self.assertTrue(server._rate_limit_exceeded("198.51.100.1","unit-test",now=102))
  self.assertFalse(server._rate_limit_exceeded("198.51.100.2","unit-test",now=102))

 def test_dns_resolution_failure_fails_closed(self):
  from unittest.mock import patch
  with patch.object(server.socket,"getaddrinfo",side_effect=server.socket.gaierror):
   ok,reason=server.valid_url("https://unresolvable.example/media")
  self.assertFalse(ok)
  self.assertIn("resolved",reason)
 def test_redirect_handler_blocks_rejected_redirect_target(self):
  from unittest.mock import patch
  req=server.urllib.request.Request("https://public.example/start")
  handler=server._SafeRedirectHandler()
  with patch.object(server,"valid_url",return_value=(False,"Private/local network targets are blocked")):
   with self.assertRaises(server.urllib.error.HTTPError):
    handler.redirect_request(req,None,302,"Found",{},"http://127.0.0.1/admin")

 def test_http_rejects_untrusted_origin_and_sets_security_headers(self):
  httpd=ThreadingHTTPServer(("127.0.0.1",0),server.Handler)
  thread=threading.Thread(target=httpd.serve_forever,daemon=True); thread.start()
  base="http://127.0.0.1:%s"%httpd.server_address[1]
  try:
   req=urllib.request.Request(base+"/api/health",headers={"Origin":"https://evil.invalid"})
   with self.assertRaises(urllib.error.HTTPError) as caught: urllib.request.urlopen(req,timeout=3)
   self.assertEqual(caught.exception.code,403)
   req=urllib.request.Request(base+"/api/health")
   with urllib.request.urlopen(req,timeout=3) as res:
    self.assertEqual(res.status,200)
    self.assertEqual(res.headers.get("X-Content-Type-Options"),"nosniff")
    self.assertEqual(res.headers.get("X-Frame-Options"),"DENY")
    self.assertEqual(res.headers.get("Referrer-Policy"),"no-referrer")
  finally:
   httpd.shutdown(); httpd.server_close(); thread.join(timeout=2)
 def test_http_bad_json_returns_client_error_not_traceback(self):
  httpd=ThreadingHTTPServer(("127.0.0.1",0),server.Handler)
  thread=threading.Thread(target=httpd.serve_forever,daemon=True); thread.start()
  base="http://127.0.0.1:%s"%httpd.server_address[1]
  try:
   req=urllib.request.Request(base+"/api/download-jobs",data=b"{oops",headers={"Content-Type":"application/json"},method="POST")
   with self.assertRaises(urllib.error.HTTPError) as caught: urllib.request.urlopen(req,timeout=3)
   self.assertEqual(caught.exception.code,400)
   self.assertIn("Invalid JSON",caught.exception.read().decode())
  finally:
   httpd.shutdown(); httpd.server_close(); thread.join(timeout=2)

if __name__=="__main__": unittest.main(verbosity=2)
