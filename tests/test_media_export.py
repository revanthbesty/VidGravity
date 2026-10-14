import unittest
import tempfile
from pathlib import Path
from media_export import ExportError, PRESETS, build_filter_graph, validate_export_request

class SocialExportPresetTests(unittest.TestCase):
    def test_platform_dimensions(self):
        self.assertEqual(PRESETS["whatsapp-status"],(1080,1920))
        self.assertEqual(PRESETS["instagram-story"],(1080,1920))
        self.assertEqual(PRESETS["youtube-shorts"],(1080,1920))
        self.assertEqual(PRESETS["instagram-feed-portrait"],(1080,1350))
        self.assertEqual(PRESETS["square"],(1080,1080))
        self.assertEqual(PRESETS["youtube-landscape"],(1920,1080))
    def test_crop_graph(self):
        graph=build_filter_graph(1080,1920,"smart-reframe")
        self.assertIn("scale=1080:1920",graph); self.assertIn("crop=1080:1920",graph)
    def test_blur_graph(self):
        graph=build_filter_graph(1080,1920,"blurred-backdrop")
        self.assertIn("split=2",graph); self.assertIn("boxblur=20:10",graph); self.assertIn("overlay=",graph)
    def test_fit_graph(self):
        graph=build_filter_graph(1080,1350,"fit-letterbox")
        self.assertIn("force_original_aspect_ratio=decrease",graph); self.assertIn("pad=1080:1350",graph)
    def test_rejects_bad_inputs(self):
        with self.assertRaises(ExportError): build_filter_graph(1080,1920,"unknown")
        with self.assertRaises(ExportError): build_filter_graph(1081,1920,"smart-reframe")
        with self.assertRaises(ExportError): validate_export_request("custom","smart-reframe",99999,1920)
    def test_trim_window_supports_start_and_duration(self):
        from media_export import validate_export_request
        result=validate_export_request("whatsapp-status","smart-reframe",trim_seconds="15",start_seconds="30")
        self.assertEqual(result["start_seconds"],30.0)
        self.assertEqual(result["trim_seconds"],15.0)
    def test_trim_window_rejects_invalid_or_excessive_ranges(self):
        from media_export import validate_export_request
        for start in ("-1","3600","not-a-number"):
            with self.subTest(start=start), self.assertRaises(ExportError):
                validate_export_request("square","smart-reframe",start_seconds=start)
        with self.assertRaises(ExportError):
            validate_export_request("square","smart-reframe",trim_seconds="20",start_seconds="3590")
    def test_animated_sticker_requires_short_webp_export(self):
        from media_export import validate_export_request
        result=validate_export_request("animated-sticker","fit-letterbox",trim_seconds="8",start_seconds="2",output_format="webp")
        self.assertEqual((result["width"],result["height"]),(512,512))
        self.assertEqual(result["output_format"],"webp")
        for duration in (None,"10.1"):
            with self.subTest(duration=duration), self.assertRaises(ExportError):
                validate_export_request("animated-sticker","fit-letterbox",trim_seconds=duration,output_format="webp")
        with self.assertRaises(ExportError):
            validate_export_request("square","fit-letterbox",trim_seconds="5",output_format="webp")
    def test_custom_dimensions_are_even(self):
        result=validate_export_request("custom","smart-reframe",1081,1351)
        self.assertEqual((result["width"],result["height"]),(1080,1350))
    def test_original_protection_rejects_same_source_and_destination(self):
        from media_export import export_media_file
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/"original.mp4"
            source.write_bytes(b"immutable-original")
            with self.assertRaises(ExportError):
                export_media_file(source,source,{"preset":"square","mode":"smart-reframe"})
            self.assertEqual(source.read_bytes(),b"immutable-original")

if __name__=="__main__": unittest.main()
