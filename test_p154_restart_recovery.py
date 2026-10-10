import importlib.util
import json
import os
import tempfile
import time
import unittest
from pathlib import Path

SERVER_PATH = Path(__file__).with_name("server.py")

def load_server(module_name, db_path, output_dir):
    spec = importlib.util.spec_from_file_location(module_name, SERVER_PATH)
    module = importlib.util.module_from_spec(spec)
    module.DOWNLOAD_DB_PATH = Path(db_path)
    module.DOWNLOAD_OUTPUT_DIR = Path(output_dir)
    # Execute module after setting overrides; _init_job_store uses globals at runtime.
    spec.loader.exec_module(module)
    module.DOWNLOAD_DB_PATH = Path(db_path)
    module.DOWNLOAD_OUTPUT_DIR = Path(output_dir)
    module._init_job_store()
    return module

class RestartSafeJobMetadataTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="vidgravity-p154-"))
        self.db = self.tmp / "jobs.sqlite3"
        self.out = self.tmp / "downloads"
        self.out.mkdir()
        self.server = load_server("vidgravity_p154_" + str(id(self)), self.db, self.out)

    def tearDown(self):
        for p in self.out.glob("*"):
            try: p.unlink()
            except OSError: pass
        try: self.out.rmdir()
        except OSError: pass
        try: self.db.unlink()
        except OSError: pass
        try: self.tmp.rmdir()
        except OSError: pass

    def test_completed_job_survives_store_reload(self):
        job_id = "completed-job"
        output = self.out / (job_id + ".mp4")
        output.write_bytes(b"persistent bytes")
        self.server.DOWNLOAD_JOBS[job_id] = {
            "id":job_id, "state":"completed", "progress":100, "message":"Done",
            "url":"https://example.com/media.mp4", "format":"best",
            "filename":output.name, "output_path":str(output), "completed_at":time.time(),
            "created_at":time.time(), "updated_at":time.time()
        }
        self.server._persist_job(self.server.DOWNLOAD_JOBS[job_id])
        self.server.DOWNLOAD_JOBS.clear()
        self.server._load_persisted_jobs()
        job = self.server.DOWNLOAD_JOBS[job_id]
        self.assertEqual(job["state"], "completed")
        self.assertEqual(Path(job["output_path"]).read_bytes(), b"persistent bytes")

    def test_running_job_recovers_as_failed_and_retryable(self):
        job_id = "interrupted-job"
        self.server.DOWNLOAD_JOBS[job_id] = {
            "id":job_id, "state":"running", "progress":30, "message":"Downloading",
            "url":"https://example.com/media.mp4", "format":"best",
            "created_at":time.time(), "updated_at":time.time()
        }
        self.server._persist_job(self.server.DOWNLOAD_JOBS[job_id])
        self.server.DOWNLOAD_JOBS.clear()
        self.server._recover_persisted_jobs()
        job = self.server.DOWNLOAD_JOBS[job_id]
        self.assertEqual(job["state"], "failed")
        self.assertIn("restart", job["message"].lower())

    def test_missing_output_marks_completed_job_expired(self):
        job_id = "missing-output"
        missing = self.out / "missing-output.mp4"
        self.server.DOWNLOAD_JOBS[job_id] = {
            "id":job_id, "state":"completed", "progress":100, "message":"Done",
            "url":"https://example.com/media.mp4", "format":"best",
            "filename":missing.name, "output_path":str(missing), "completed_at":time.time(),
            "created_at":time.time(), "updated_at":time.time()
        }
        self.server._persist_job(self.server.DOWNLOAD_JOBS[job_id])
        self.server._recover_persisted_jobs()
        job = self.server.DOWNLOAD_JOBS[job_id]
        self.assertEqual(job["state"], "expired")
        self.assertIsNone(job.get("output_path"))

    def test_public_view_never_exposes_private_metadata(self):
        job = {"id":"x", "state":"completed", "url":"https://example.com/x",
               "format":"best", "output_path":"/private/file", "result":b"bytes"}
        view = self.server._job_public_view(job)
        for key in ("url","format","output_path","result"):
            self.assertNotIn(key, view)

if __name__ == "__main__":
    unittest.main(verbosity=2)
