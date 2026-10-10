#!/usr/bin/env python3
"""Single-process regression runner for CI; avoids slow per-file subprocess startup."""
import importlib.util
import io
import json
import pathlib
import subprocess
import sys
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parent
EXCLUDED = {"test_p124.py", "test_p125.py", "test_p126.py"}
names = sorted(p.name for p in ROOT.glob("test_p*.py") if p.name not in EXCLUDED)
suite = unittest.TestSuite()
for name in names:
    spec = importlib.util.spec_from_file_location(name[:-3], ROOT / name)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    suite.addTests(unittest.defaultTestLoader.loadTestsFromModule(module))

stream = io.StringIO()
started = time.time()
result = unittest.TextTestRunner(stream=stream, verbosity=2).run(suite)
output = stream.getvalue()
(ROOT / "P165_CI_TEST_OUTPUT.txt").write_text(output, encoding="utf-8")
legacy = {}
for name in ("test_p124.py", "test_p125.py", "test_p126.py"):
    proc = subprocess.run([sys.executable, str(ROOT / name)], cwd=ROOT,
                          capture_output=True, text=True, timeout=30)
    legacy[name] = {"returncode": proc.returncode,
                    "output_tail": (proc.stdout + proc.stderr)[-500:]}
summary = {
    "milestone": "P165",
    "name": "CI Regression Gate",
    "test_files": len(names),
    "tests_run": result.testsRun,
    "failures": len(result.failures),
    "errors": len(result.errors),
    "elapsed_seconds": round(time.time() - started, 2),
    "legacy_smoke_scripts": legacy,
    "result": "PASS" if result.wasSuccessful() and all(x["returncode"] == 0 for x in legacy.values()) else "FAIL",
}
(ROOT / "P165_CI_TEST_REPORT.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
print(output[-3500:])
sys.exit(0 if summary["result"] == "PASS" else 1)
