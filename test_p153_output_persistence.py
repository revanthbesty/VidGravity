import importlib.util
import json
import tempfile
import time
import urllib.error
import urllib.request
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

SERVER_PATH = Path(__file__).with_name("server.py")
SPEC = importlib.util.spec_from_file_location("vidgravity_p153_server", SERVER_PATH)
server = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(server)

class DownloadOutputPersistenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.old_output_dir = server.DOWNLOAD_OUTPUT_DIR
        cls.old_ttl = server.DOWNLOAD_OUTPUT_TTL_SECONDS
        cls.output_dir = Path(tempfile.mkdtemp(prefix="vidgravity-p153-")).resolve()
        server.DOWNLOAD_OUTPUT_DIR = cls.output_dir
        server.DOWNLOAD_OUTPUT_TTL_SECONDS = 30
        server.DOWNLOAD_JOBS.clear()
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        import threading
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()
        cls.root = "http://127.0.0.1:%d" % cls.httpd.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        cls.thread.join(timeout=2)
        server.DOWNLOAD_JOBS.clear()
        for p in cls.output_dir.glob("*"):
            try: p.unlink()
            except OSError: pass
        try: cls.output_dir.rmdir()
        except OSError: pass
        server.DOWNLOAD_OUTPUT_DIR = cls.old_output_dir
        server.DOWNLOAD_OUTPUT_TTL_SECONDS = cls.old_ttl

    def request(self, path):
        try:
            with urllib.request.urlopen(self.root + path, timeout=3) as resp:
                return resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def test_completed_output_is_persisted_and_downloadable(self):
        job_id = "p153persistedjob"
        output = self.output_dir / (job_id + ".mp4")
        payload = b"controlled test media bytes"
        output.write_bytes(payload)
        server.DOWNLOAD_JOBS[job_id] = {
            "id": job_id, "state": "completed", "progress": 100,
            "message": "Download complete", "filename": output.name,
            "output_path": str(output), "completed_at": time.time()
        }
        code, headers, body = self.request("/api/download-jobs/" + job_id + "/result")
        self.assertEqual(code, 200)
        self.assertEqual(body, payload)
        self.assertEqual(headers.get("Content-Disposition"), 'attachment; filename="%s"' % output.name)
        self.assertTrue(output.exists(), "serving the result must not delete the persisted file")

    def test_public_job_view_does_not_expose_output_path(self):
        job = {"id":"x", "state":"completed", "output_path":"/secret/path", "url":"https://example.com/a"}
        view = server._job_public_view(job)
        self.assertNotIn("output_path", view)
        self.assertNotIn("url", view)

    def test_expiry_cleanup_removes_file_and_marks_job_expired(self):
        job_id = "p153expiredjob"
        output = self.output_dir / (job_id + ".mp4")
        output.write_bytes(b"expired")
        server.DOWNLOAD_JOBS[job_id] = {
            "id":job_id, "state":"completed", "output_path":str(output),
            "filename":output.name, "completed_at":time.time()-100
        }
        server._cleanup_download_outputs(now=time.time())
        self.assertFalse(output.exists())
        self.assertEqual(server.DOWNLOAD_JOBS[job_id]["state"], "expired")
        self.assertNotIn("output_path", server.DOWNLOAD_JOBS[job_id])

    def test_result_after_expiry_returns_gone(self):
        job_id = "p153gonejob"
        server.DOWNLOAD_JOBS[job_id] = {
            "id":job_id, "state":"expired", "message":"Expired"
        }
        code, _, body = self.request("/api/download-jobs/" + job_id + "/result")
        self.assertEqual(code, 410)
        self.assertIn("expired", json.loads(body)["error"].lower())

    def test_output_path_traversal_is_rejected(self):
        outside = self.output_dir.parent / "outside.mp4"
        outside.write_bytes(b"must not be served")
        try:
            self.assertIsNone(server._safe_output_path(str(outside)))
        finally:
            try: outside.unlink()
            except OSError: pass

if __name__ == "__main__":
    unittest.main(verbosity=2)
