from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AndroidBuildFeatureLedgerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (ROOT / "apps/android/app/src/main/java/com/vidgravity/app/MainActivity.kt").read_text()
        cls.camera = (ROOT / "apps/android/app/src/main/java/com/vidgravity/camera/DualCameraCaptureActivity.kt").read_text()
        cls.ledger = (ROOT / "docs/BUILD_FEATURE_LEDGER.md").read_text()
        cls.gradle = (ROOT / "apps/android/app/build.gradle.kts").read_text()

    def test_build_version_matches_ledger(self):
        self.assertIn("versionCode = 180", self.gradle)
        self.assertIn('versionName = "1.1.0-p180"', self.gradle)
        self.assertIn("p180", self.ledger)

    def test_status_split_preserves_all_custom_durations(self):
        for seconds in (30, 60, 90, 120):
            self.assertIn(str(seconds), self.main)
        self.assertIn("statusClipSeconds", self.main)
        self.assertIn("Split into ${statusClipSeconds}-second clips", self.main)
        self.assertIn("120s is a custom split target", self.main)
        self.assertIn("WhatsApp Status", self.ledger)

    def test_removed_studytok_and_duplicate_grab_best_video_quick_actions(self):
        self.assertNotIn('"✦ StudyTok AI"', self.main)
        self.assertNotIn('"⚡ Grab Best Video"', self.main)
        self.assertIn('"♫ Sound Only"', self.main)
        self.assertIn('"✂ Status Clip Splitter"', self.main)

    def test_creator_video_picker_actions_exist(self):
        self.assertIn("Choose video for animated sticker", self.main)
        self.assertIn("Choose video for WhatsApp Status", self.main)
        self.assertIn('picker.launch(arrayOf("video/*"))', self.main)

    def test_camera_has_visible_quality_and_flash_controls(self):
        self.assertIn('controlButton("Quality: 1080p")', self.camera)
        self.assertIn("chooseVideoQuality()", self.camera)
        self.assertIn("Flash ON · tap off", self.camera)
        self.assertIn("Flash OFF · tap on", self.camera)
        self.assertIn("publishCapture(target, \"image/jpeg\")", self.camera)
        self.assertIn("publishCapture(target, \"video/mp4\")", self.camera)

    def test_ledger_requires_evidence_not_code_only(self):
        self.assertIn("not verified", self.ledger.lower())
        self.assertIn("CI run", self.ledger)
        self.assertIn("Device pending", self.ledger)
        self.assertIn("physical-device", self.ledger.lower())


if __name__ == "__main__":
    unittest.main()
