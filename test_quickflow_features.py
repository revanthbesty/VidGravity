import unittest
from pathlib import Path

class QuickFlowFeatureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script = Path("site/quickflow.js").read_text(encoding="utf-8")
        cls.html = Path("site/index.html").read_text(encoding="utf-8")
        cls.css = Path("site/styles.css").read_text(encoding="utf-8")

    def test_quickflow_is_loaded_after_existing_app_scripts(self):
        self.assertIn('src="quickflow.js?v=qf1" defer', self.html)
        self.assertLess(self.html.index('src="player-refactor.js?v=p176"'), self.html.index('src="quickflow.js?v=qf1"'))

    def test_keyboard_and_intake_controls_exist(self):
        for marker in ("vgPasteUrl", "vgClearUrl", "event.key==='Enter'", "event.key.toLowerCase()==='k'"):
            with self.subTest(marker=marker):
                self.assertIn(marker, self.script)

    def test_queue_search_and_status_filters_exist(self):
        for marker in ("vgQueueSearch", 'data-vg-status="queued"', 'data-vg-status="running"', 'data-vg-status="failed"', "vgQueueSummary"):
            with self.subTest(marker=marker):
                self.assertIn(marker, self.script)

    def test_duplicate_active_download_guard_exists(self):
        self.assertIn("Duplicate active download avoided.", self.script)
        self.assertIn("['queued','running'].includes(job.status)", self.script)

    def test_accessibility_and_mobile_styles_exist(self):
        self.assertIn("aria-live", self.script)
        self.assertIn(".vg-queue-tools", self.css)
        self.assertIn("@media(max-width:600px)", self.css)
        self.assertIn("prefers-reduced-motion", self.css)

if __name__ == "__main__":
    unittest.main()
