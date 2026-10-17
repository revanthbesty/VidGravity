from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android"


class AndroidStartupContractTests(unittest.TestCase):
    def test_launcher_has_native_recovery_and_draw_milestones(self):
        source = (ANDROID / "app/src/main/java/com/vidgravity/app/StartupHost.kt").read_text()
        for marker in ("compose.committed", "compose.layout", "compose.draw.dispatched",
                       "first-draw.timeout", "Retry startup", "App settings"):
            self.assertIn(marker, source)
        self.assertIn("gate.drawn()", source)
        self.assertIn("removeCallbacks(timeout)", source)

    def test_window_has_explicit_startup_background(self):
        tree = ET.parse(ANDROID / "app/src/main/res/values/styles.xml")
        items = {item.attrib["name"]: item.text for item in tree.findall("./style/item")}
        self.assertEqual(items["android:windowBackground"], "#141A2B")

    def test_ci_builds_and_runs_startup_jvm_tests(self):
        workflow = (ROOT / ".github/workflows/vidgravity-ci.yml").read_text()
        self.assertIn("gradle -p apps/android assembleDebug testDebugUnitTest", workflow)
        self.assertIn('test_android_*.py', workflow)


if __name__ == "__main__":
    unittest.main()
