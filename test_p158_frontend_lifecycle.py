import pathlib, re, shutil, subprocess, unittest
ROOT=pathlib.Path(__file__).resolve().parent
APP=ROOT
class FrontendLifecycleContractTests(unittest.TestCase):
 def setUp(self):
  self.js=(APP/"app.js").read_text(encoding="utf-8")
 def test_frontend_has_all_download_lifecycle_handlers(self):
  for name in ["serverDownload","startBrowserDownload","pumpQueue","runDownloadLifecycle","cancelJob","retryJob","startBrowserDownloadNow"]:
   self.assertRegex(self.js, r"function\s+"+name+r"\s*\(")
 def test_server_job_create_poll_and_result_routes_are_used(self):
  for route in ["/api/download-jobs","/result","/cancel"]:
   self.assertIn(route,self.js)
  self.assertIn("state===\"completed\"",self.js)
  self.assertIn("state===\"failed\"",self.js)
 def test_concurrency_limit_and_persistent_ui_state_are_used(self):
  self.assertIn("ACTIVE_LIMIT",self.js)
  self.assertIn("saveJobs()",self.js)
  self.assertIn("renderJobs()",self.js)
 def test_cancellation_aborts_browser_and_requests_server_cancel(self):
  self.assertIn("controller.abort()",self.js)
  self.assertIn('method:"POST"',self.js)
  self.assertIn("job.cancelRequested=true",self.js)
 def test_deployed_site_bundle_matches_root_runtime(self):
  self.assertEqual((APP/"app.js").read_bytes(),(APP/"site"/"app.js").read_bytes(),"site app.js drifted from root")
  self.assertEqual((APP/"styles.css").read_bytes(),(APP/"site"/"styles.css").read_bytes(),"site styles.css drifted from root")
 def test_inspect_formats_progress_verification_and_device_save_are_present(self):
  for token in ["renderAvailableFormats", "data.formats||[]", "detectMediaSignature", "downloadBlobToDevice", "Transferring file to your device", "Verified and saved to device and Library"]:
   self.assertIn(token,self.js)
 def test_javascript_syntax_check_when_node_is_available(self):
  node=shutil.which("node")
  if not node:self.skipTest("Node.js not installed in validation environment")
  p=subprocess.run([node,"--check",str(APP/"app.js")],capture_output=True,text=True,timeout=10)
  self.assertEqual(p.returncode,0,p.stderr)
if __name__=="__main__":unittest.main(verbosity=2)
