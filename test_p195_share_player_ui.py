from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent
SHELLS = ("", "site/", "android-web/")

class ShareAndPlayerUiTests(unittest.TestCase):
    def test_share_targets_are_available_once_in_each_shell(self):
        expected = ("x", "whatsapp", "telegram", "facebook", "instagram", "threads",
                    "bluesky", "reddit", "linkedin", "pinterest", "tumblr", "snapchat", "email")
        for prefix in SHELLS:
            with self.subTest(shell=prefix or "root"):
                html = (ROOT / f"{prefix}index.html").read_text(encoding="utf-8")
                for target in expected:
                    self.assertEqual(html.count(f'data-share-target="{target}"'), 1, target)
                self.assertIn('id="shareDeviceButton"', html)
                self.assertIn('id="shareCopyButton"', html)

    def test_share_destinations_have_handlers_and_copy_fallback(self):
        for prefix in SHELLS:
            with self.subTest(shell=prefix or "root"):
                js = (ROOT / f"{prefix}app.js").read_text(encoding="utf-8")
                for target in ("facebook", "instagram", "threads", "bluesky", "pinterest", "tumblr", "snapchat", "email"):
                    self.assertIn(target, js)
                self.assertIn("copyShareLink(url)", js)
                self.assertIn("document.execCommand('copy')", js)
                self.assertIn("Your browser blocked the share window", js)

    def test_player_layout_and_error_toast_are_actionable(self):
        for prefix in SHELLS:
            with self.subTest(shell=prefix or "root"):
                css = (ROOT / f"{prefix}styles.css").read_text(encoding="utf-8")
                self.assertIn("P195: mobile-safe player chrome", css)
                self.assertIn(".vg-player-quick-utilities", css)
                self.assertIn("grid-template-columns:repeat(2,minmax(0,1fr))", css)
                js = (ROOT / f"{prefix}app.js").read_text(encoding="utf-8")
                self.assertIn("A page action failed.", js)
                self.assertNotIn('vgUXNotify("Something went wrong. Your media is safe.","error")', js)

    def test_cache_busting_matches_p195_assets(self):
        for prefix in SHELLS:
            with self.subTest(shell=prefix or "root"):
                html = (ROOT / f"{prefix}index.html").read_text(encoding="utf-8")
                worker = (ROOT / f"{prefix}sw.js").read_text(encoding="utf-8")
                self.assertIn("styles.css?v=p195", html)
                self.assertIn("app.js?v=p195", html)
                self.assertIn('CACHE_NAME = "vidgravity-p195-shell-v1"', worker)

if __name__ == "__main__":
    unittest.main()
