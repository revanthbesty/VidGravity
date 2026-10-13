import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WebSocialSharingDestinationsTests(unittest.TestCase):
    def test_share_dialog_exposes_expected_destinations(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        for target in (
            "x", "whatsapp", "telegram", "facebook", "instagram",
            "threads", "bluesky", "reddit", "linkedin", "pinterest",
            "tumblr", "snapchat", "email",
        ):
            with self.subTest(target=target):
                self.assertIn(f'data-share-target="{target}"', html)

    def test_share_handler_has_intent_or_device_share_fallback(self):
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        for target in (
            "facebook", "threads", "bluesky", "pinterest", "tumblr", "email",
        ):
            with self.subTest(target=target):
                self.assertIn(f"case '{target}'", app)
        self.assertIn("shareMedia(shareMediaId);return", app)

    def test_share_dialog_has_responsive_destination_grid(self):
        css = (ROOT / "site" / "styles.css").read_text(encoding="utf-8")
        self.assertIn(".shareTargets{display:grid", css)
        self.assertIn("@media(max-width:420px){.shareTargets", css)


if __name__ == "__main__":
    unittest.main()
