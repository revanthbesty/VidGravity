import json, pathlib, re, unittest
ROOT=pathlib.Path(__file__).resolve().parent
class P160PwaMobileOfflineTests(unittest.TestCase):
 def test_manifest_is_valid_and_scoped(self):
  m=json.loads((ROOT/"manifest.webmanifest").read_text()); self.assertEqual(m["display"],"standalone"); self.assertEqual(m["scope"],"./"); self.assertGreaterEqual(len(m["icons"]),2)
  for icon in m["icons"]: self.assertTrue((ROOT/icon["src"]).is_file())
 def test_service_worker_avoids_sensitive_routes_and_non_get(self):
  sw=(ROOT/"sw.js").read_text(); self.assertIn('request.method !== "GET"',sw); self.assertIn('url.pathname.includes("/api/")',sw); self.assertRegex(sw,r"license\|download\|payment\|checkout"); self.assertIn('request.mode === "navigate"',sw); self.assertIn("response.ok && response.type === \"basic\"",sw)
 def test_service_worker_only_caches_app_shell(self):
  sw=(ROOT/"sw.js").read_text(); self.assertIn("const APP_SHELL",sw); self.assertIn("const knownAsset",sw); self.assertNotIn("cache.put(e.request",sw); self.assertIn("cache.addAll(APP_SHELL)",sw)
 def test_html_has_mobile_and_install_support(self):
  h=(ROOT/"index.html").read_text(); self.assertIn('name="viewport"',h); self.assertIn("viewport-fit=cover",h); self.assertIn('rel="manifest"',h); self.assertIn('id="vgInstallButton"',h); self.assertIn("beforeinstallprompt",h); self.assertIn('navigator.serviceWorker.register("./sw.js")',h); self.assertIn('addEventListener("offline"',h)
 def test_css_has_safe_area_touch_and_mobile_breakpoints(self):
  c=(ROOT/"styles.css").read_text(); self.assertIn("env(safe-area-inset-bottom)",c); self.assertIn("touch-action: manipulation",c); self.assertIn("@media (max-width: 760px)",c); self.assertIn("@media (max-width: 480px)",c); self.assertIn("[hidden] { display: none !important; }",c)
 def test_sensitive_guard_precedes_fetch_strategy(self):
  sw=(ROOT/"sw.js").read_text(); self.assertLess(sw.index("function isSensitiveRequest"),sw.index('self.addEventListener("fetch"')); self.assertIn("if (isSensitiveRequest(url, request)) return;",sw)
if __name__=="__main__": unittest.main()
