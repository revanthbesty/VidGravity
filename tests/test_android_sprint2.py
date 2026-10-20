from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/android/app/src/main/java/com/vidgravity/app"


class Sprint2Contracts(unittest.TestCase):
    def test_native_render_and_request_snapshot_are_wired(self):
        worker = (APP / "NativeStudioExportWorker.kt").read_text()
        self.assertIn("repository.saveRequest", worker)
        self.assertIn("NativeStudioRenderer.render", worker)
        self.assertIn("MediaLibraryIndexer.publish", worker)
        self.assertIn("published.sha256", worker)
        screen = (APP / "NativeStudioScreen.kt").read_text()
        self.assertIn("it.fingerprint == project.fingerprint()", screen)
        self.assertIn("manager.cancelWorkById", screen)

    def test_versions_and_native_encoder_are_declared(self):
        gradle = (ROOT / "apps/android/app/build.gradle.kts").read_text()
        self.assertRegex(gradle, r'versionCode = (18[1-9]|19[0-9]|[2-9][0-9]{2,})')
        self.assertIn('versionName = "1.11.0-sprint12"', gradle)
        self.assertIn('media3-transformer:1.5.1', gradle)
        manifest = ET.parse(ROOT / "apps/android/app/src/main/AndroidManifest.xml")
        ns = '{http://schemas.android.com/apk/res/android}'
        self.assertTrue(any(p.get(ns + 'name') == 'android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING'
                            for p in manifest.getroot().findall('uses-permission')))

    def test_ci_keeps_prior_gates_and_generates_synthetic_media(self):
        workflow = (ROOT / '.github/workflows/vidgravity-ci.yml').read_text()
        self.assertIn('assembleDebug testDebugUnitTest lintDebug', workflow)
        self.assertIn('scripts/generate_native_studio_fixtures.py', workflow)
        self.assertIn('ANDROID17_VALIDATION_BLOCKED', workflow)
        self.assertIn('vidgravity-android-debug-apk', workflow)


if __name__ == '__main__':
    unittest.main()
