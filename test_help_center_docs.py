import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parent

class HelpCenterDocumentationTests(unittest.TestCase):
    def test_help_center_is_linked_from_app_navigation(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        self.assertIn('href="help.html"', html)
        self.assertIn("Help Center", html)

    def test_help_center_has_search_and_topic_filters(self):
        html = (ROOT / "site" / "help.html").read_text(encoding="utf-8")
        self.assertIn('id="helpSearch"', html)
        for category in ("getting-started", "downloads", "library", "creator", "troubleshooting"):
            self.assertIn(f'data-filter="{category}"', html)
        self.assertIn("Frequently asked questions", html)
        self.assertIn("How-to guides", html)

    def test_documentation_references_exist(self):
        for path in (
            "docs/USER_GUIDE.md",
            "docs/FAQ_TROUBLESHOOTING.md",
            "docs/FEATURE_DOCUMENTATION_STANDARD.md",
            "docs/RELEASE_NOTES.md",
        ):
            self.assertTrue((ROOT / path).is_file(), path)
        html = (ROOT / "site" / "help.html").read_text(encoding="utf-8")
        for path in ("USER_GUIDE.md", "FAQ_TROUBLESHOOTING.md",
                     "FEATURE_DOCUMENTATION_STANDARD.md", "RELEASE_NOTES.md"):
            self.assertIn(path, html)

    def test_android_has_in_app_help_center_and_docs(self):
        source = (ROOT / "apps" / "android" / "app" / "src" / "main" / "java" /
                  "com" / "vidgravity" / "app" / "MainActivity.kt").read_text(encoding="utf-8")
        for phrase in ("Help & troubleshooting", "Open Help Center", "VidGravity Help Center",
                       "DOWNLOAD STUCK OR AT 0%", "FILE MISSING FROM LIBRARY",
                       "CHECKSUM OR SERVICE ERROR"):
            self.assertIn(phrase, source)
        guide = (ROOT / "docs" / "USER_GUIDE.md").read_text(encoding="utf-8")
        faq = (ROOT / "docs" / "FAQ_TROUBLESHOOTING.md").read_text(encoding="utf-8")
        release = (ROOT / "docs" / "RELEASE_NOTES.md").read_text(encoding="utf-8")
        self.assertIn("Android in-app Help Center", guide)
        self.assertIn("Android in-app Help Center", faq)
        self.assertIn("in-app Help & troubleshooting", release)

    def test_feature_definition_of_done_includes_help_and_release_notes(self):
        standard = (ROOT / "docs" / "FEATURE_DOCUMENTATION_STANDARD.md").read_text(encoding="utf-8")
        for requirement in ("Feature guide", "FAQ", "Troubleshooting", "Release notes",
                            "In-product help", "Tests", "physical-device behavior"):
            self.assertIn(requirement, standard)

if __name__ == "__main__":
    unittest.main()
