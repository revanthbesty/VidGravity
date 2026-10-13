from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent

class ResponsiveWorkspaceTests(unittest.TestCase):
    def test_both_mobile_shells_have_five_primary_destinations(self):
        for rel in ("index.html", "site/index.html", "android-web/index.html"):
            with self.subTest(page=rel):
                html = (ROOT / rel).read_text(encoding="utf-8")
                self.assertIn('class="vg-mobile-nav"', html)
                for page in ("home", "downloads", "library", "ask", "settings"):
                    self.assertIn(f'data-page="{page}"', html)
                self.assertIn('id="mobileQueueBadge"', html)

    def test_mobile_navigation_is_wired_to_page_state(self):
        for rel in ("app.js", "site/app.js", "android-web/app.js"):
            with self.subTest(script=rel):
                js = (ROOT / rel).read_text(encoding="utf-8")
                self.assertIn("vg-mobile-nav-item", js)
                self.assertIn("setAttribute('aria-current','page')", js)
                self.assertIn("mobileBadge.textContent=queued", js)

    def test_mobile_navigation_styles_and_cache_versions_exist(self):
        for rel in ("styles.css", "site/styles.css", "android-web/styles.css"):
            with self.subTest(style=rel):
                css = (ROOT / rel).read_text(encoding="utf-8")
                self.assertIn("P193 responsive workspace polish", css)
                self.assertIn("@media(max-width:760px)", css)
                self.assertIn(":focus-visible", css)
        for rel in ("site/index.html", "android-web/index.html"):
            with self.subTest(page=rel):
                html = (ROOT / rel).read_text(encoding="utf-8")
                self.assertIn("styles.css?v=p193", html)
                self.assertIn("app.js?v=p193", html)

if __name__ == "__main__":
    unittest.main()
