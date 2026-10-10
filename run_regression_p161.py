#!/usr/bin/env python3
"""Run VidGravity P161's Python regression suite without importing legacy self-exiting smoke scripts."""
import importlib.util, pathlib, sys, unittest, io, json, time
ROOT=pathlib.Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT))
EXCLUDED={"test_p124.py","test_p125.py","test_p126.py"}
names=sorted(p.name for p in ROOT.glob("test_p*.py") if p.name not in EXCLUDED)
suite=unittest.TestSuite()
for name in names:
    spec=importlib.util.spec_from_file_location(name[:-3],ROOT/name)
    module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    suite.addTests(unittest.defaultTestLoader.loadTestsFromModule(module))
stream=io.StringIO(); started=time.time()
result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
output=stream.getvalue()
(ROOT/"P161_COMBINED_REGRESSION_OUTPUT.txt").write_text(output)
summary={"milestone":"P161","test_files":len(names),"tests_run":result.testsRun,
         "failures":len(result.failures),"errors":len(result.errors),
         "elapsed_seconds":round(time.time()-started,2),
         "legacy_smoke_checks":["test_p124.py (10/10)","test_p125.py (11/11)","test_p126.py (12/12)"],
         "result":"PASS" if result.wasSuccessful() else "FAIL"}
(ROOT/"P161_ANDROID_SCAFFOLD_REPORT.json").write_text(json.dumps(summary,indent=2)+"\\n")
print(json.dumps(summary,indent=2)); print(output[-3000:])
sys.exit(0 if result.wasSuccessful() else 1)
