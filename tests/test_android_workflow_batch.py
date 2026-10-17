from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "apps/android/app/src/main"
APP = MAIN / "java/com/vidgravity/app"


class AndroidWorkflowBatchTests(unittest.TestCase):
    def test_capture_fallback_is_shareable_and_indexed(self):
        manifest = ET.parse(MAIN / "AndroidManifest.xml")
        ns = "{http://schemas.android.com/apk/res/android}"
        provider = manifest.find("./application/provider")
        self.assertEqual(provider.attrib[ns + "exported"], "false")
        self.assertEqual(provider.attrib[ns + "grantUriPermissions"], "true")
        paths = ET.parse(MAIN / "res/xml/file_paths.xml")
        self.assertTrue(any(p.attrib.get("path") == "Pictures/" for p in paths.getroot()))
        camera = (MAIN / "java/com/vidgravity/camera/DualCameraCaptureActivity.kt").read_text()
        self.assertIn("MediaLibraryIndexer.register", camera)
        self.assertIn("Use capture in Studio", camera)
        self.assertIn("setResult(RESULT_OK", camera)

    def test_image_preview_and_camera_result_reach_workspace(self):
        self.assertIn('ImagePreview(media, onBack)', (APP / "PlayerScreen.kt").read_text())
        source = (APP / "MainActivity.kt").read_text()
        self.assertIn("StartActivityForResult()", source)
        self.assertIn("onBusyChanged", source)
        self.assertIn("rememberSaveable", source)

    def test_changed_story_settings_invalidate_derivative(self):
        source = (APP / "StoryCardScreen.kt").read_text()
        self.assertIn("LaunchedEffect(media.uri, start, length, title, artist, layout)", source)
        self.assertIn("hasDerivative && !isRendering", source)
        self.assertIn("onEdit = { exportedUri = null }", (APP / "MainActivity.kt").read_text())


if __name__ == "__main__":
    unittest.main()
