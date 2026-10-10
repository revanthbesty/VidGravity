import importlib.util
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
    spec.loader.exec_module(module)
    module.DOWNLOAD_DB_PATH = Path(db_path)
    module.DOWNLOAD_OUTPUT_DIR = Path(output_dir)
    module._init_job_store()
    return module

class JobStoreReliabilityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="vidgravity-p155-"))
        self.db = self.tmp / "jobs.sqlite3"
        self.out = self.tmp / "downloads"
        self.out.mkdir()
        self.s = load_server("vidgravity_p155_" + str(id(self)), self.db, self.out)
        self.s.DOWNLOAD_JOB_RETENTION_SECONDS = 60

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

    def put_job(self, job_id, state, updated_at, output_path=None):
        job = {"id":job_id,"state":state,"progress":0,"message":state,
               "url":"https://example.com/a","format":"best",
               "created_at":updated_at,"updated_at":updated_at}
        if output_path: job.update({"output_path":str(output_path),"filename":Path(output_path).name,"completed_at":updated_at})
        self.s.DOWNLOAD_JOBS[job_id] = job
        self.s._persist_job(job)
        # The persistence function timestamps writes; backdate the database
        # row explicitly to simulate an aged retained record.
        with self.s._db_connect() as db:
            db.execute("UPDATE download_jobs SET updated_at=? WHERE id=?", (updated_at, job_id))
            db.commit()
        return job

    def test_cleanup_prunes_old_terminal_job_and_output(self):
        old = time.time()-200
        path = self.out / "oldjob.mp4"
        path.write_bytes(b"old")
        self.put_job("oldjob","completed",old,path)
        removed = self.s._cleanup_job_store(now=time.time())
        self.assertEqual(removed,1)
        self.assertFalse(path.exists())
        self.assertNotIn("oldjob",self.s.DOWNLOAD_JOBS)

    def test_cleanup_never_prunes_active_job(self):
        old = time.time()-200
        self.put_job("activejob","running",old)
        removed = self.s._cleanup_job_store(now=time.time())
        self.assertEqual(removed,0)
        self.assertIn("activejob",self.s.DOWNLOAD_JOBS)

    def test_orphan_output_is_removed_after_output_ttl(self):
        path = self.out / "orphan.mp4"
        path.write_bytes(b"orphan")
        old = time.time()-self.s.DOWNLOAD_OUTPUT_TTL_SECONDS-10
        os_utime = __import__("os").utime
        os_utime(path, (old,old))
        self.s._cleanup_job_store(now=time.time())
        self.assertFalse(path.exists())

    def test_retained_recent_job_is_not_pruned(self):
        recent = time.time()
        self.put_job("recentjob","failed",recent)
        removed = self.s._cleanup_job_store(now=time.time())
        self.assertEqual(removed,0)
        self.assertIn("recentjob",self.s.DOWNLOAD_JOBS)

    def test_db_job_record_is_transactionally_persisted(self):
        now=time.time()
        self.put_job("transactional","queued",now)
        with self.s._db_connect() as db:
            row=db.execute("SELECT state,progress FROM download_jobs WHERE id='transactional'").fetchone()
        self.assertEqual(row["state"],"queued")
        self.assertEqual(row["progress"],0)

if __name__ == "__main__":
    unittest.main(verbosity=2)
