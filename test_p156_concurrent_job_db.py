import importlib.util, tempfile, time, threading, unittest, sqlite3
from pathlib import Path

SERVER_PATH = Path(__file__).with_name("server.py")

def load_server(module_name, db_path, output_dir):
    spec = importlib.util.spec_from_file_location(module_name, SERVER_PATH)
    module = importlib.util.module_from_spec(spec)
    module.DOWNLOAD_DB_PATH = Path(db_path)
    module.DOWNLOAD_OUTPUT_DIR = Path(output_dir)
    spec.loader.exec_module(module)
    module.DOWNLOAD_DB_PATH = Path(db_path)
    module.DOWNLOAD_OUTPUT_DIR = Path(output_dir)
    module._init_job_store()
    return module

class ConcurrentJobDatabaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="vidgravity-p156-"))
        self.db = self.tmp / "jobs.sqlite3"
        self.out = self.tmp / "downloads"
        self.out.mkdir()
        self.s = load_server("vidgravity_p156_" + str(id(self)), self.db, self.out)
        self.s.DOWNLOAD_JOB_RETENTION_SECONDS = 60

    def tearDown(self):
        try:
            for p in self.out.glob("*"): p.unlink(missing_ok=True)
            self.out.rmdir()
        except OSError: pass
        try: self.db.unlink(missing_ok=True)
        except OSError: pass
        try: self.tmp.rmdir()
        except OSError: pass

    def job(self, job_id, state="queued"):
        now = time.time()
        return {"id": job_id, "state": state, "progress": 0, "message": state,
                "url": "https://example.com/media", "format": "best",
                "created_at": now, "updated_at": now}

    def test_concurrent_distinct_job_inserts_are_all_persisted(self):
        count, workers = 120, 12
        jobs = [self.job(f"concurrent-{i}") for i in range(count)]
        errors = []
        def worker(batch):
            try:
                for item in batch:
                    with self.s.DOWNLOAD_JOB_LOCK:
                        self.s.DOWNLOAD_JOBS[item["id"]] = item
                    self.s._persist_job(item)
            except Exception as exc: errors.append(repr(exc))
        threads = []
        for i in range(workers):
            t = threading.Thread(target=worker, args=(jobs[i::workers],))
            threads.append(t); t.start()
        for t in threads: t.join()
        self.assertEqual(errors, [])
        with self.s._db_connect() as db:
            self.assertEqual(db.execute("SELECT COUNT(*) FROM download_jobs").fetchone()[0], count)
        self.assertEqual(len(self.s.DOWNLOAD_JOBS), count)

    def test_concurrent_updates_leave_one_consistent_row_per_job(self):
        seed = self.job("shared")
        self.s.DOWNLOAD_JOBS["shared"] = seed
        self.s._persist_job(seed)
        errors = []
        def worker(n):
            try:
                for i in range(20):
                    item = self.job("shared", "running")
                    item["progress"] = (n * 20 + i) % 101
                    item["message"] = f"worker-{n}-{i}"
                    with self.s.DOWNLOAD_JOB_LOCK:
                        self.s.DOWNLOAD_JOBS["shared"] = item
                    self.s._persist_job(item)
            except Exception as exc: errors.append(repr(exc))
        threads = [threading.Thread(target=worker, args=(n,)) for n in range(8)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertEqual(errors, [])
        with self.s._db_connect() as db:
            rows = db.execute("SELECT id,state,progress,message FROM download_jobs WHERE id='shared'").fetchall()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["state"], "running")
        self.assertGreaterEqual(rows[0]["progress"], 0)
        self.assertLessEqual(rows[0]["progress"], 100)

    def test_cleanup_and_persistence_can_run_concurrently(self):
        for i in range(30):
            item = self.job(f"job-{i}", "failed")
            self.s.DOWNLOAD_JOBS[item["id"]] = item
            self.s._persist_job(item)
        errors = []
        def persist():
            try:
                for i in range(30, 70):
                    item = self.job(f"job-{i}", "queued")
                    with self.s.DOWNLOAD_JOB_LOCK: self.s.DOWNLOAD_JOBS[item["id"]] = item
                    self.s._persist_job(item)
            except Exception as exc: errors.append(repr(exc))
        def cleanup():
            try:
                for _ in range(12): self.s._cleanup_job_store(now=time.time())
            except Exception as exc: errors.append(repr(exc))
        threads = [threading.Thread(target=persist), threading.Thread(target=cleanup)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertEqual(errors, [])
        with self.s._db_connect() as db:
            ids = [r["id"] for r in db.execute("SELECT id FROM download_jobs").fetchall()]
        self.assertEqual(len(ids), len(set(ids)))

    def test_database_busy_timeout_is_configured(self):
        conn = self.s._db_connect()
        try:
            self.assertGreaterEqual(conn.execute("PRAGMA busy_timeout").fetchone()[0], 1000)
        finally: conn.close()

    def test_recovery_keeps_completed_output_and_marks_interrupted_work(self):
        output = self.out / "ready.mp4"
        output.write_bytes(b"controlled test output")
        done = self.job("done", "completed")
        done.update({"output_path": str(output), "filename": output.name, "completed_at": time.time()})
        active = self.job("active", "running")
        self.s.DOWNLOAD_JOBS.update({"done": done, "active": active})
        self.s._persist_job(done); self.s._persist_job(active)
        recovered = load_server("vidgravity_p156_recover_" + str(id(self)), self.db, self.out)
        with recovered._db_connect() as db:
            states = {r["id"]: r["state"] for r in db.execute("SELECT id,state FROM download_jobs")}
        self.assertEqual(states["done"], "completed")
        self.assertEqual(states["active"], "failed")
        self.assertTrue(output.exists())

if __name__ == "__main__": unittest.main(verbosity=2)
