import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WebPlayerKeyboardShortcutTests(unittest.TestCase):
    def test_player_advertises_supported_shortcuts(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        for label in ("Space", "M", "playerKeyboardHelp"):
            with self.subTest(label=label):
                self.assertIn(label, html)

    def test_keyboard_shortcuts_only_run_when_player_is_open(self):
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        self.assertIn("modal.classList.contains('hidden')", app)
        self.assertIn("target?.closest?.('input,textarea,select,button,[contenteditable=\"true\"]')", app)
        self.assertIn("video.currentTime+(key==='ArrowRight'?5:-5)", app)
        self.assertIn("video.volume+(key==='ArrowUp'?0.05:-0.05)", app)
        self.assertIn("video.muted=!video.muted", app)

    def test_shortcut_help_has_mobile_styling(self):
        css = (ROOT / "site" / "styles.css").read_text(encoding="utf-8")
        self.assertIn(".playerKeyboardHelp", css)
        self.assertIn("@media(max-width:620px){.playerKeyboardHelp", css)


if __name__ == "__main__":
    unittest.main()
