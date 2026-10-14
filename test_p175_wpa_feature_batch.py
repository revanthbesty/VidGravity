import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent


class P175WpaFeatureBatchTests(unittest.TestCase):
    def test_all_social_destinations_are_present(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        targets = (
            "x", "whatsapp", "telegram", "facebook", "instagram", "threads",
            "bluesky", "reddit", "linkedin", "pinterest", "tumblr", "snapchat", "email",
        )
        for target in targets:
            with self.subTest(target=target):
                self.assertIn(f'data-share-target="{target}"', html)
        for target in ("facebook", "threads", "bluesky", "pinterest", "tumblr", "email"):
            with self.subTest(handler=target):
                self.assertIn(f"case '{target}'", app)
        self.assertIn("shareMedia(shareMediaId);return", app)

    def test_library_selection_and_bulk_copy_controls_are_wired(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        self.assertIn('id="selectVisibleMedia"', html)
        self.assertIn('id="copySelectedNames"', html)
        self.assertIn('class="mediaSelect" type="checkbox"', app)
        self.assertIn("selectVisibleMedia(e.target.checked)", app)
        self.assertIn("document.querySelectorAll('#libraryGrid .mediaSelect:checked')", app)
        self.assertIn("const text=names.join('\\n');", app)
        self.assertIn("if(!confirm(`Delete ${ids.length} selected item(s)?`))return", app)

    def test_player_shortcuts_are_scoped_and_documented(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        app = (ROOT / "site" / "app.js").read_text(encoding="utf-8")
        css = (ROOT / "site" / "styles.css").read_text(encoding="utf-8")
        self.assertIn('id="playerKeyboardHelp"', html)
        self.assertIn("modal.classList.contains('hidden')", app)
        self.assertIn("video.currentTime+(key==='ArrowRight'?5:-5)", app)
        self.assertIn("video.volume+(key==='ArrowUp'?0.05:-0.05)", app)
        self.assertIn("video.muted=!video.muted", app)
        self.assertIn(".playerKeyboardHelp", css)

    def test_release_cache_busting_and_cloudflare_deploy_are_configured(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        sw = (ROOT / "site" / "sw.js").read_text(encoding="utf-8")
        workflow = (ROOT / ".github" / "workflows" / "cloudflare-wpa-deploy.yml").read_text(encoding="utf-8")
        self.assertIn("styles.css?v=p176", html)
        self.assertIn("app.js?v=p176", html)
        self.assertIn('const CACHE_NAME = "vidgravity-p176-shell-v1";', sw)
        self.assertIn("CLOUDFLARE_API_TOKEN", workflow)
        self.assertIn("CLOUDFLARE_ACCOUNT_ID", workflow)
        self.assertIn("cloudflare/wrangler-action@v3", workflow)
        self.assertIn("deploy --config wrangler.jsonc", workflow)
        self.assertIn("Smoke-test deployed WPA assets", workflow)
        self.assertIn("VidGravity-Deploy-Smoke/1.0", workflow)

    def test_web_and_android_runtime_bundles_are_synchronized(self):
        for name in ("index.html", "app.js", "styles.css", "sw.js"):
            with self.subTest(file=name):
                self.assertEqual((ROOT / name).read_bytes(), (ROOT / "android-web" / name).read_bytes())
        self.assertEqual((ROOT / "app.js").read_bytes(), (ROOT / "site" / "app.js").read_bytes())
        self.assertEqual((ROOT / "styles.css").read_bytes(), (ROOT / "site" / "styles.css").read_bytes())


if __name__ == "__main__":
    unittest.main(verbosity=2)
