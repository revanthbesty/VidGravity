import importlib.util, json, os, tempfile, threading, time, unittest, urllib.request, urllib.error
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

SERVER_PATH = Path(__file__).with_name("server.py")
SPEC = importlib.util.spec_from_file_location("vidgravity_p157_server", SERVER_PATH)
server = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(server)

class ProviderCliIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        temp_dir = tempfile.TemporaryDirectory(prefix="vidgravity-p157-")
        cls.addClassCleanup(temp_dir.cleanup)
        cls.tmp = Path(temp_dir.name)
        cls.bin_dir = cls.tmp / "bin"; cls.bin_dir.mkdir()
        cls.output_dir = cls.tmp / "downloads"; cls.output_dir.mkdir()
        cls.db_path = cls.tmp / "jobs.sqlite3"
        cls.fake_cli = cls.bin_dir / "yt-dlp"
        cls.fake_cli.write_text("""#!/bin/sh
case " $* " in
  *" --version "*) echo '2026.10.01-test-adapter'; exit 0 ;;
  *" -J "*) printf '%s\\n' '{"title":"Controlled Integration Clip","duration":2,"thumbnail":null,"formats":[{"format_id":"18","url":"https://media.invalid/clip.mp4","ext":"mp4","resolution":"360p","vcodec":"avc1","acodec":"mp4a","filesize":1234}]}'; exit 0 ;;
esac
out=''
prev=''
for arg in "$@"; do
  if [ "$prev" = '-o' ]; then out="$arg"; fi
  prev="$arg"
done
if [ -n "$out" ]; then
  out=$(printf '%s' "$out" | sed 's/%(ext)s/mp4/')
  printf 'VIDGRAVITY-CONTROLLED-MEDIA-OUTPUT' > "$out"
  echo '[download] 100% of 32 bytes'
  exit 0
fi
echo 'unsupported controlled CLI args' >&2
exit 2
""", encoding="utf-8")
        cls.fake_cli.chmod(0o755)
        patcher = patch.dict(os.environ, {"PATH": str(cls.bin_dir) + os.pathsep + os.environ.get("PATH", "")})
        patcher.start()
        cls.addClassCleanup(patcher.stop)
        original_valid_url = server.valid_url
        for name, value in (("DOWNLOAD_DB_PATH", cls.db_path), ("DOWNLOAD_OUTPUT_DIR", cls.output_dir)):
            patcher = patch.object(server, name, value)
            patcher.start()
            cls.addClassCleanup(patcher.stop)
        patcher = patch.object(server, "valid_url", lambda url: (True, "") if url == "https://provider-test.invalid/clip.mp4" else original_valid_url(url))
        patcher.start()
        cls.addClassCleanup(patcher.stop)
        server.DOWNLOAD_JOBS.clear(); server._init_job_store()
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()
        cls.root = "http://127.0.0.1:%d" % cls.httpd.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown(); cls.httpd.server_close(); cls.thread.join(timeout=2)
        server.DOWNLOAD_JOBS.clear()

    def request(self, path, method="GET", payload=None):
        data = None if payload is None else json.dumps(payload).encode()
        req = urllib.request.Request(self.root + path, data=data, method=method,
                                     headers={"Content-Type":"application/json"})
        try:
            with urllib.request.urlopen(req, timeout=3) as r: return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e: return e.code, e.headers, e.read()

    def wait_terminal(self, job_id):
        deadline = time.time() + 4
        while time.time() < deadline:
            code, _, body = self.request("/api/download-jobs/" + job_id)
            if code == 200:
                view = json.loads(body)
                if view.get("state") in ("completed", "failed", "cancelled", "expired"): return view
            time.sleep(.02)
        self.fail("job did not reach a terminal state")

    def test_provider_cli_is_discoverable(self):
        result = subprocess_run(["yt-dlp", "--version"])
        self.assertEqual(result[0], 0); self.assertIn("test-adapter", result[1])

    def test_format_resolution_maps_provider_metadata(self):
        code, _, body = self.request("/api/formats?url=https%3A%2F%2Fprovider-test.invalid%2Fclip.mp4")
        self.assertEqual(code, 200)
        data = json.loads(body)
        self.assertEqual(data["source"], "yt-dlp")
        self.assertEqual(data["title"], "Controlled Integration Clip")
        self.assertEqual(data["formats"][0]["id"], "18")

    def test_download_job_invokes_provider_and_persists_output(self):
        code, _, body = self.request("/api/download-jobs", method="POST",
            payload={"url":"https://provider-test.invalid/clip.mp4", "format":"18"})
        self.assertEqual(code, 202)
        job_id = json.loads(body)["id"]
        final = self.wait_terminal(job_id)
        self.assertEqual(final["state"], "completed", final)
        code, headers, result = self.request("/api/download-jobs/" + job_id + "/result")
        self.assertEqual(code, 200)
        self.assertEqual(result, b"VIDGRAVITY-CONTROLLED-MEDIA-OUTPUT")
        self.assertIn(job_id, headers.get("Content-Disposition", ""))

    def test_provider_nonzero_exit_fails_without_publishing_output(self):
        job_id = "p157controlledfailure"
        server.DOWNLOAD_JOBS[job_id] = {"id":job_id,"state":"queued","progress":0,"message":"queued",
            "url":"https://provider-test.invalid/clip.mp4","format":"fail","created_at":time.time(),"updated_at":time.time()}
        class FailedProcess:
            def __init__(self, *a, **kw): self.stdout = iter(["ERROR: controlled provider failure\n"])
            def wait(self): return 2
        with patch.object(server.subprocess, "Popen", FailedProcess):
            server._run_download_job(job_id, "https://provider-test.invalid/clip.mp4", "fail")
        self.assertEqual(server.DOWNLOAD_JOBS[job_id]["state"], "failed")
        self.assertFalse(list(self.output_dir.glob(job_id + ".*")))

    def test_invalid_url_is_rejected_before_provider_execution(self):
        code, _, body = self.request("/api/formats?url=file%3A%2F%2F%2Fetc%2Fpasswd")
        self.assertEqual(code, 400)
        self.assertIn("http", json.loads(body)["error"].lower())

    def test_live_acceptance_script_is_opt_in(self):
        script = Path(__file__).with_name("provider_live_acceptance.py")
        self.assertTrue(script.is_file())
        contents = script.read_text(encoding="utf-8")
        self.assertIn("VIDGRAVITY_LIVE_TEST_URL", contents)
        self.assertIn("yt-dlp", contents)

def subprocess_run(args):
    import subprocess
    p = subprocess.run(args, capture_output=True, text=True, timeout=3)
    return p.returncode, p.stdout

if __name__ == "__main__": unittest.main(verbosity=2)
