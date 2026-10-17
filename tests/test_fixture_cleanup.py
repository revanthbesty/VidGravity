import subprocess
import sys
import unittest
from unittest.mock import patch

import test_p152_cancel_cleanup as fixture


class CancellationFixtureCleanupTests(unittest.TestCase):
    def test_failed_class_setup_restores_process_and_server_dependencies(self):
        originals = (
            subprocess.Popen, subprocess.call, fixture.server.shutil.which,
            fixture.server.valid_url, fixture.server._run_download_job,
        )

        class FailingSetup(fixture.DownloadCancellationCleanupTests):
            pass

        result = unittest.TestResult()
        with patch.object(fixture, "ThreadingHTTPServer", side_effect=PermissionError("bind denied")):
            unittest.TestSuite([FailingSetup("test_cancel_running_job_terminates_provider")]).run(result)

        self.assertEqual(len(result.errors), 1)
        self.assertIn("bind denied", result.errors[0][1])
        restored = (
            subprocess.Popen, subprocess.call, fixture.server.shutil.which,
            fixture.server.valid_url, fixture.server._run_download_job,
        )
        for original, current in zip(originals, restored):
            self.assertIs(current, original)
        completed = subprocess.run([sys.executable, "-c", "print('restored')"],
                                   check=True, capture_output=True, text=True)
        self.assertEqual(completed.stdout.strip(), "restored")


if __name__ == "__main__":
    unittest.main()
