import importlib
import io
import os
import shutil
import subprocess
import sys
import unittest
from unittest.mock import patch


class SubprocessMockCleanupTests(unittest.TestCase):
    def run_fixture(self, module_name, class_name, fail_setup=False):
        module = importlib.import_module(module_name)
        fixture = getattr(module, class_name)
        originals = {
            "call": subprocess.call,
            "Popen": subprocess.Popen,
            "which": shutil.which,
            "valid_url": module.server.valid_url,
            "db": module.server.DOWNLOAD_DB_PATH,
            "output": module.server.DOWNLOAD_OUTPUT_DIR,
            "path": os.environ.get("PATH"),
        }
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(fixture)
        stream = io.StringIO()
        if fail_setup:
            with patch.object(module, "ThreadingHTTPServer", side_effect=RuntimeError("setup failed")):
                result = unittest.TextTestRunner(stream=stream).run(suite)
            self.assertEqual(result.testsRun, 0)
            self.assertEqual(len(result.errors), 1, stream.getvalue())
            self.assertIn("setup failed", result.errors[0][1])
        else:
            result = unittest.TextTestRunner(stream=stream).run(suite)
            self.assertGreater(result.testsRun, 0)
            self.assertTrue(result.wasSuccessful(), stream.getvalue())
        self.assertIs(subprocess.call, originals["call"])
        self.assertIs(subprocess.Popen, originals["Popen"])
        self.assertIs(shutil.which, originals["which"])
        self.assertIs(module.server.valid_url, originals["valid_url"])
        self.assertEqual(module.server.DOWNLOAD_DB_PATH, originals["db"])
        self.assertEqual(module.server.DOWNLOAD_OUTPUT_DIR, originals["output"])
        self.assertEqual(os.environ.get("PATH"), originals["path"])
        self.assertEqual(subprocess.call([sys.executable, "-c", "pass"]), 0)

    def test_fixtures_restore_functions_after_tests(self):
        for module_name, class_name in (
            ("test_p152_cancel_cleanup", "DownloadCancellationCleanupTests"),
            ("test_p157_provider_cli_integration", "ProviderCliIntegrationTests"),
        ):
            with self.subTest(module=module_name):
                self.run_fixture(module_name, class_name)

    def test_fixtures_restore_functions_when_setup_fails(self):
        for module_name, class_name in (
            ("test_p152_cancel_cleanup", "DownloadCancellationCleanupTests"),
            ("test_p157_provider_cli_integration", "ProviderCliIntegrationTests"),
        ):
            with self.subTest(module=module_name):
                self.run_fixture(module_name, class_name, fail_setup=True)
