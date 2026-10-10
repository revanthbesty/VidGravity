import importlib.util
import json
import threading
import time
import urllib.error
import urllib.request
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

SERVER_PATH = Path(__file__).with_name("server.py")
SPEC = importlib.util.spec_from_file_location("vidgravity_p152_server", SERVER_PATH)
server = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(server)

class BlockingStdout:
    def __init__(self, event):
        self.event = event
    def __iter__(self):
        # Simulate a provider that is running and has not emitted a progress line.
        self.event.wait(timeout=5)
        return iter(())

class FakeProcess:
    def __init__(self):
        self.terminated = threading.Event()
        self.returncode = None
        self.stdout = BlockingStdout(self.terminated)
        self.killed = False
    def poll(self):
        return self.returncode
    def terminate(self):
        self.terminated.set()
        self.returncode = -15
    def kill(self):
        self.killed = True
        self.terminated.set()
        self.returncode = -9
    def wait(self, timeout=None):
        if self.returncode is None and not self.terminated.wait(timeout):
            raise server.subprocess.TimeoutExpired("fake-yt-dlp", timeout)
        return self.returncode

class DownloadCancellationCleanupTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.old_valid_url = server.valid_url
        cls.old_popen = server.subprocess.Popen
        cls.old_call = server.subprocess.call
        cls.old_which = server.shutil.which
        cls.old_runner = server._run_download_job
        cls.fake_processes = []
        server.DOWNLOAD_JOBS.clear()
        server.valid_url = lambda url: (True, "") if url == "https://example.com/cancel-test.mp4" else cls.old_valid_url(url)
        server.subprocess.call = lambda *a, **k: 0
        # The worker checks provider availability before Popen; fake that dependency in this isolated test.
        server.shutil.which = lambda name: "/usr/bin/yt-dlp" if name == "yt-dlp" else cls.old_which(name)
        def fake_popen(*args, **kwargs):
            p = FakeProcess()
            cls.fake_processes.append(p)
            return p
        server.subprocess.Popen = fake_popen
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
        server.subprocess.Popen = cls.old_popen
        server.subprocess.call = cls.old_call
        server.shutil.which = cls.old_which
        server._run_download_job = cls.old_runner
        server.DOWNLOAD_JOBS.clear()

    def request(self, path, method="GET", payload=None):
        data = None if payload is None else json.dumps(payload).encode()
        req = urllib.request.Request(self.root + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=3) as resp:
                return resp.status, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()

    def test_cancel_running_job_terminates_provider(self):
        code, body = self.request("/api/download-jobs", "POST", {"url":"https://example.com/cancel-test.mp4"})
        self.assertEqual(code, 202)
        job_id = json.loads(body)["id"]
        deadline = time.time() + 2
        while time.time() < deadline and not self.fake_processes:
            time.sleep(0.01)
        self.assertTrue(self.fake_processes, "worker did not start provider process")
        process = self.fake_processes[0]
        deadline = time.time() + 2
        while time.time() < deadline:
            with server.DOWNLOAD_JOB_LOCK:
                state = server.DOWNLOAD_JOBS[job_id]["state"]
            if state == "running":
                break
            time.sleep(0.01)
        self.assertEqual(state, "running")
        code, body = self.request("/api/download-jobs/" + job_id + "/cancel", "POST", {})
        self.assertEqual(code, 200)
        self.assertTrue(process.terminated.is_set(), "cancel endpoint did not terminate provider")
        with server.DOWNLOAD_JOB_LOCK:
            job = server.DOWNLOAD_JOBS[job_id]
            self.assertEqual(job["state"], "cancelled")
            self.assertNotIn("process", job, "provider process reference should be cleared by worker")
        deadline = time.time() + 2
        while time.time() < deadline:
            with server.DOWNLOAD_JOB_LOCK:
                if "process" not in server.DOWNLOAD_JOBS[job_id]:
                    break
            time.sleep(0.01)
        self.assertNotIn("process", server.DOWNLOAD_JOBS[job_id])

    def test_stop_helper_escalates_when_process_ignores_terminate(self):
        class IgnoresTerminate(FakeProcess):
            def terminate(self):
                pass
            def wait(self, timeout=None):
                if not self.killed:
                    raise server.subprocess.TimeoutExpired("fake-yt-dlp", timeout)
                return self.returncode
        p = IgnoresTerminate()
        server._stop_download_process(p, timeout=0.01)
        self.assertTrue(p.killed)
        self.assertEqual(p.returncode, -9)

if __name__ == "__main__":
    unittest.main(verbosity=2)
