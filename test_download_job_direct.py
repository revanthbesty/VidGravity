import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import server


class FakeResponse:
    def __init__(self, headers=None, chunks=()):
        self.headers = headers or {}
        self._chunks = iter(chunks)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self, _size=-1):
        return next(self._chunks, b"")


class DirectMediaDownloadTests(unittest.TestCase):
    def test_downloads_direct_mp4_and_completes_job(self):
        with tempfile.TemporaryDirectory() as temp:
            original_dir = server.DOWNLOAD_OUTPUT_DIR
            server.DOWNLOAD_OUTPUT_DIR = Path(temp)
            job = {"id": "unit-test-job", "state": "running", "progress": 0}
            head = FakeResponse({"Content-Type": "video/mp4", "Content-Length": "7"})
            body = FakeResponse({"Content-Type": "video/mp4"}, [b"123", b"4567", b""])
            try:
                with patch.object(server, "_safe_urlopen", side_effect=[head, body]), \
                     patch.object(server, "_job_changed"):
                    handled = server._download_direct_media(
                        "https://media.example/video.mp4", "unit-test-job", job
                    )
                self.assertTrue(handled)
                self.assertEqual(job["state"], "completed")
                self.assertEqual(job["progress"], 100)
                self.assertEqual(Path(job["output_path"]).read_bytes(), b"1234567")
                self.assertEqual(job["filename"], "unit-test-job.mp4")
            finally:
                server.DOWNLOAD_OUTPUT_DIR = original_dir

    def test_non_media_url_falls_back_to_provider_extractor(self):
        head = FakeResponse({"Content-Type": "text/html; charset=utf-8"})
        with patch.object(server, "_safe_urlopen", return_value=head) as opener:
            handled = server._download_direct_media(
                "https://example.com/watch", "unit-test-html", {"id": "unit-test-html"}
            )
        self.assertFalse(handled)
        opener.assert_called_once()


if __name__ == "__main__":
    unittest.main()
