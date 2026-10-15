"""Static regression checks for the additive VidGravity premium UI system.

These checks protect token parity and core accessibility affordances. They do not
replace Android builds, browser rendering, or physical-device acceptance tests.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent
CSS_FILES = (ROOT / "styles.css", ROOT / "site/styles.css", ROOT / "android-web/styles.css")
ANDROID_MAIN = ROOT / "apps/android/app/src/main/java/com/vidgravity/app/MainActivity.kt"


class PremiumUiDesignSystemTests(unittest.TestCase):
    def test_shipped_css_design_systems_stay_synchronized(self):
        css = [path.read_text(encoding="utf-8") for path in CSS_FILES]
        self.assertEqual(css[0], css[1], "root and site CSS drifted")
        self.assertEqual(css[0], css[2], "root and android-web CSS drifted")

    def test_official_light_dark_tokens_and_brand_gradients_exist(self):
        css = CSS_FILES[0].read_text(encoding="utf-8")
        required = (
            "--vg-bg:#171F23",
            "--vg-surface:#202B30",
            "--vg-surface-raised:#29373D",
            "--vg-text:#E6EEF0",
            "--vg-text-muted:#AFBEC3",
            "--vg-teal:#8CC8BC",
            "--vg-teal-hover:#A8D9CE",
            "--vg-teal-soft:#304842",
            "--vg-border:#38484E",
            "--vg-brand-gradient:linear-gradient(110deg,#38B8B2 0%,#35C6B0 48%,#7D5CFF 100%)",
            "--vg-teal-gradient:linear-gradient(110deg,#38B8B2,#35C6B0)",
            "--vg-violet-gradient:linear-gradient(110deg,#967BFF,#6845DB)",
            "--vg-light-surface-gradient:linear-gradient(145deg,#FFFFFF,#E8EEF0)",
            "--vg-dark-surface-gradient:linear-gradient(145deg,#29373D,#202B30)",
            "--vg-bg:#F3F6F7",
            "--vg-text:#26363B",
            "--vg-text-muted:#5C6D73",
            "--vg-teal:#286B66",
            "--vg-teal-hover:#205A56",
            "--vg-teal-soft:#DDEDE9",
            "--vg-border:#D5E0E3",
        )
        for token in required:
            with self.subTest(token=token):
                self.assertIn(token, css)

    def test_accessible_interaction_and_motion_rules_exist(self):
        css = CSS_FILES[0].read_text(encoding="utf-8")
        for rule in (
            ":focus-visible",
            ".primary:hover",
            ".primary:active",
            "button{min-height:48px}",
            "@media(prefers-reduced-motion:reduce)",
            "color-scheme:dark",
            'color-scheme:light',
        ):
            with self.subTest(rule=rule):
                self.assertIn(rule, css)

    def test_native_compose_uses_theme_tokens_and_safe_primary_action(self):
        source = ANDROID_MAIN.read_text(encoding="utf-8")
        for token in (
            "Color(0xFF171F23)",
            "Color(0xFF202B30)",
            "Color(0xFF29373D)",
            "Color(0xFFE6EEF0)",
            "Color(0xFFAFBEC3)",
            "Color(0xFFF3F6F7)",
            "Color(0xFF26363B)",
            "Color(0xFF5C6D73)",
            "private fun VgPrimaryButton(",
            "loading: Boolean = false",
            "androidx.compose.ui.semantics.Role.Button",
            "enabled = !inspecting",
            "loading = inspecting",
        ):
            with self.subTest(token=token):
                self.assertIn(token, source)


if __name__ == "__main__":
    unittest.main()
