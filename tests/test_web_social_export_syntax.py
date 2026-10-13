import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WebSocialExportSyntaxTests(unittest.TestCase):
    def test_social_export_script_parses_as_javascript(self):
        """Catch escaped-newline text accidentally inserted between JS statements."""
        script = (ROOT / "site" / "social-export.js").read_text(encoding="utf-8")
        with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8", delete=False) as handle:
            handle.write(script)
            path = handle.name
        try:
            subprocess.run(["node", "--check", path], check=True, capture_output=True, text=True)
        finally:
            Path(path).unlink(missing_ok=True)

    def test_social_export_timeupdate_handler_is_separate_statement(self):
        script = (ROOT / "site" / "social-export.js").read_text(encoding="utf-8")
        self.assertIn('$("#vgExportVideo").addEventListener("timeupdate"', script)
        self.assertNotIn('\\\\n    $("#vgExportVideo").addEventListener("timeupdate"', script)


if __name__ == "__main__":
    unittest.main()
