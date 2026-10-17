import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WebPlayerRefactorSyntaxTests(unittest.TestCase):
    def test_every_shipped_player_refactor_parses(self):
        for directory in (ROOT, ROOT / "site", ROOT / "android-web"):
            with self.subTest(directory=directory.name):
                subprocess.run(
                    ["node", "--check", str(directory / "player-refactor.js")],
                    check=True, capture_output=True, text=True,
                )

    def test_shipped_player_refactors_are_synchronized(self):
        source = (ROOT / "player-refactor.js").read_bytes()
        for directory in ("site", "android-web"):
            with self.subTest(directory=directory):
                self.assertEqual(source, (ROOT / directory / "player-refactor.js").read_bytes())
