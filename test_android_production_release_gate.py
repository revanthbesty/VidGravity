import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parent


class AndroidProductionReleaseGateTests(unittest.TestCase):
    def test_candidate_version_is_explicit_and_incremented(self):
        gradle = (ROOT / "apps" / "android" / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("versionCode = 179", gradle)
        self.assertIn('versionName = "1.0.9-p179"', gradle)
        self.assertIn('signingConfig = signingConfigs.getByName("release")', gradle)

    def test_release_workflow_requires_secrets_and_uploads_checksum(self):
        workflow = (ROOT / ".github" / "workflows" / "android-signed-release.yml").read_text(encoding="utf-8")
        for secret in (
            "ANDROID_KEYSTORE_BASE64",
            "ANDROID_KEYSTORE_PASSWORD",
            "ANDROID_KEY_ALIAS",
            "ANDROID_KEY_PASSWORD",
        ):
            self.assertIn(secret, workflow)
        self.assertIn("assembleRelease", workflow)
        self.assertIn("sha256sum", workflow)
        self.assertIn("vidgravity-android-signed-release-p179", workflow)
        self.assertIn("if-no-files-found: error", workflow)

    def test_release_documentation_does_not_claim_signed_apk_exists(self):
        pipeline = (ROOT / "docs" / "ANDROID_FEATURE_PIPELINE.md").read_text(encoding="utf-8")
        notes = (ROOT / "docs" / "RELEASE_NOTES.md").read_text(encoding="utf-8")
        self.assertIn("ANDROID_KEYSTORE_BASE64", pipeline)
        self.assertIn("on-device acceptance checklist", pipeline)
        self.assertIn("not a claim that a signed production APK", notes)


if __name__ == "__main__":
    unittest.main()
