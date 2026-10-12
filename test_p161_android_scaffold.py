import json, pathlib, re, unittest
ROOT=pathlib.Path(__file__).resolve().parent
class P161AndroidScaffoldTests(unittest.TestCase):
 def test_capacitor_config_points_to_clean_web_bundle(self):
  c=json.loads((ROOT/"capacitor.config.json").read_text())
  self.assertEqual(c["appId"],"com.vidgravity.app")
  self.assertEqual(c["webDir"],"android-web")
  self.assertTrue((ROOT/c["webDir"]/"index.html").is_file())
  self.assertFalse((ROOT/c["webDir"]/"server.py").exists())
 def test_android_bundle_matches_source_runtime(self):
  for name in ["index.html","app.js","styles.css","manifest.webmanifest","sw.js"]:
   self.assertEqual((ROOT/name).read_bytes(),(ROOT/"android-web"/name).read_bytes(),name)
 def test_api_base_url_helper_used_by_api_calls(self):
  app=(ROOT/"app.js").read_text()
  self.assertIn("function vgApiUrl(path)",app)
  self.assertIn("vg-api-base-url",app)
  self.assertGreaterEqual(app.count("vgApiUrl("),8)
  html=(ROOT/"index.html").read_text()
  self.assertIn('id="vgApiBaseInput"',html)
  self.assertIn('Enter a valid HTTPS cloud API address',html)
  self.assertIn('https://vidgravity.onrender.com',html)
  self.assertNotIn('http://127.0.0.1:5173',app)
  self.assertNotIn('local companion server',app)
 def test_server_cors_uses_explicit_allowlist(self):
  server=(ROOT/"server.py").read_text()
  self.assertIn("VIDGRAVITY_ALLOWED_ORIGINS",server)
  self.assertIn("self._allowed_origins()",server); self.assertIn("_safe_origin(origin",server)
  self.assertIn("def do_OPTIONS(self)",server)
  self.assertNotIn("Access-Control-Allow-Origin','*'",server)
 def test_build_docs_explain_no_apk_and_prerequisites(self):
  doc=(ROOT/"ANDROID_BUILD.md").read_text()
  self.assertIn("not a compiled APK",doc)
  self.assertIn("Android Studio",doc)
  self.assertIn("HTTPS",doc)
 def test_npm_scripts_and_capacitor_dependencies(self):
  pkg=json.loads((ROOT/"package.json").read_text())
  self.assertIn("@capacitor/core",pkg["dependencies"])
  self.assertIn("@capacitor/android",pkg["devDependencies"])
  self.assertIn("android:sync",pkg["scripts"])
if __name__=="__main__": unittest.main()
