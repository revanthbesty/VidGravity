import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WebSocialExportSyntaxTests(unittest.TestCase):
    def test_social_export_script_parses_as_javascript(self):
        """The shipped social-export module must parse successfully."""
        script = (ROOT / "site" / "social-export.js").read_text(encoding="utf-8")
        with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8", delete=False) as handle:
            handle.write(script)
            path = handle.name
        try:
            subprocess.run(["node", "--check", path], check=True, capture_output=True, text=True)
        finally:
            Path(path).unlink(missing_ok=True)

    def test_timeupdate_handler_has_real_statement_boundary(self):
        script = (ROOT / "site" / "social-export.js").read_text(encoding="utf-8")
        self.assertIn('$("#vgExportVideo").addEventListener("timeupdate"', script)
        self.assertNotIn(r'\n    $("#vgExportVideo").addEventListener("timeupdate"', script)

    def test_custom_clip_targets_and_start_time_are_wired_to_renderer(self):
        script = (ROOT / "site" / "social-export.js").read_text(encoding="utf-8")
        for seconds in (30, 60, 90, 120):
            self.assertIn(f'data-vg-clip-seconds="{seconds}"', script)
        self.assertIn('id="vgExportStart"', script)
        self.assertIn('headers["X-VG-Start-Seconds"]=String(start)', script)
        self.assertIn('$("#vgExportTrim").value=button.dataset.vgClipSeconds', script)
        self.assertIn('id="vgExportClipTargets"', script)


if __name__ == "__main__":
    unittest.main()
