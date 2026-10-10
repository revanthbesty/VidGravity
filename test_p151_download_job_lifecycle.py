import importlib.util
import json
import threading
import time
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

SERVER_PATH = Path(__file__).with_name("server.py")
SPEC = importlib.util.spec_from_file_location("vidgravity_p151_server", SERVER_PATH)
server = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(server)

class DownloadJobLifecycleHTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.old_valid_url = server.valid_url
        cls.old_runner = server._run_download_job
        server.DOWNLOAD_JOBS.clear()

        # Deterministic provider stub: records a failed provider run without
        # claiming that media was downloaded. URL-policy tests use the real validator.
        def test_runner(job_id, url, fmt):
            with server.DOWNLOAD_JOB_LOCK:
                job = server.DOWNLOAD_JOBS.get(job_id)
                if job:
                    job.update({"state": "failed", "progress": 0,
                                "message": "Controlled test provider failure",
                                "error": "Controlled test provider failure"})
        server._run_download_job = test_runner
        server.valid_url = lambda url: (True, "") if url == "https://example.com/video.mp4" else cls.old_valid_url(url)

        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()
        cls.root = "http://127.0.0.1:%d" % cls.httpd.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        cls.thread.join(timeout=2)
        server.valid_url = cls.old_valid_url
        server._run_download_job = cls.old_runner
        server.DOWNLOAD_JOBS.clear()

    def request(self, path, method="GET", payload=None):
        data = None if payload is None else json.dumps(payload).encode()
        req = urllib.request.Request(self.root + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=3) as resp:
                return resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def test_01_rejects_malformed_url(self):
        code, _, body = self.request("/api/download-jobs", "POST", {"url": "not-a-url"})
        self.assertEqual(code, 400)
        self.assertIn("error", json.loads(body))

    def test_02_creates_job_with_accepted_url(self):
        code, _, body = self.request("/api/download/jobs", "POST",
                                     {"url": "https://example.com/video.mp4"})
        self.assertEqual(code, 202)
        data = json.loads(body)
        self.assertTrue(data["id"])
        self.assertEqual(data["state"], "queued")
        self.job_id = data["id"]
        self.__class__.created_job_id = data["id"]

    def test_03_reads_job_status(self):
        job_id = getattr(self.__class__, "created_job_id", None)
        self.assertTrue(job_id, "job creation test should run first")
        code, _, body = self.request("/api/download/jobs/" + job_id)
        self.assertEqual(code, 200)
        data = json.loads(body)
        self.assertEqual(data["id"], job_id)
        self.assertIn(data["state"], ("queued", "running", "failed"))
        self.assertNotIn("url", data)

    def test_04_retry_creates_new_job(self):
        job_id = getattr(self.__class__, "created_job_id", None)
        self.assertTrue(job_id)
        # Wait for controlled stub to set the original job to failed.
        deadline = time.time() + 2
        while time.time() < deadline:
            with server.DOWNLOAD_JOB_LOCK:
                state = server.DOWNLOAD_JOBS[job_id]["state"]
            if state == "failed":
                break
            time.sleep(0.01)
        self.assertEqual(state, "failed")
        code, _, body = self.request("/api/download/jobs/" + job_id + "/retry", "POST", {})
        self.assertEqual(code, 202)
        data = json.loads(body)
        self.assertNotEqual(data["id"], job_id)
        self.assertEqual(data["retry_of"], job_id)
        self.__class__.retry_job_id = data["id"]

    def test_05_retry_job_status_is_readable(self):
        job_id = getattr(self.__class__, "retry_job_id", None)
        self.assertTrue(job_id)
        deadline = time.time() + 2
        while time.time() < deadline:
            with server.DOWNLOAD_JOB_LOCK:
                state = server.DOWNLOAD_JOBS[job_id]["state"]
            if state == "failed":
                break
            time.sleep(0.01)
        code, _, body = self.request("/api/download-jobs/" + job_id)
        self.assertEqual(code, 200)
        self.assertEqual(json.loads(body)["state"], "failed")

    def test_06_result_not_ready_returns_conflict(self):
        job_id = getattr(self.__class__, "created_job_id", None)
        code, _, body = self.request("/api/download-jobs/" + job_id + "/result")
        self.assertEqual(code, 409)
        self.assertIn("not complete", json.loads(body)["error"].lower())

    def test_07_cancel_unknown_job_returns_404(self):
        code, _, body = self.request("/api/download-jobs/not-a-real-job/cancel", "POST", {})
        self.assertEqual(code, 404)
        self.assertIn("not found", json.loads(body)["error"].lower())

    def test_08_retry_unknown_job_returns_404(self):
        code, _, body = self.request("/api/download-jobs/not-a-real-job/retry", "POST", {})
        self.assertEqual(code, 404)
        self.assertIn("not found", json.loads(body)["error"].lower())

    def test_09_terminal_job_cannot_be_cancelled(self):
        job_id = getattr(self.__class__, "created_job_id", None)
        deadline = time.time() + 2
        while time.time() < deadline:
            with server.DOWNLOAD_JOB_LOCK:
                state = server.DOWNLOAD_JOBS[job_id]["state"]
            if state == "failed":
                break
            time.sleep(0.01)
        code, _, body = self.request("/api/download-jobs/" + job_id + "/cancel", "POST", {})
        self.assertEqual(code, 409)
        self.assertIn("terminal", json.loads(body)["error"].lower())

if __name__ == "__main__":
    unittest.main(verbosity=2)
