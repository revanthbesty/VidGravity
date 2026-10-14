import unittest
import tempfile
from unittest.mock import patch
import subprocess
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
    def test_privacy_blur_graph_blurs_full_frame(self):
        graph=build_filter_graph(1080,1920,"privacy-blur")
        self.assertIn("boxblur=10:5",graph)
        self.assertIn("crop=1080:1920",graph)
        self.assertNotIn("overlay=",graph)
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
    def test_audio_story_requires_vertical_mp4_and_duration(self):
        from media_export import validate_export_request
        result=validate_export_request("whatsapp-status","audio-story",trim_seconds="12",start_seconds="3")
        self.assertEqual(result["mode"],"audio-story")
        self.assertEqual(result["width"],1080)
        with self.assertRaises(ExportError):
            validate_export_request("square","audio-story",trim_seconds="12")
        with self.assertRaises(ExportError):
            validate_export_request("whatsapp-status","audio-story",trim_seconds="12",output_format="webp")

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
    def test_audio_story_uses_waveform_visualizer_and_preserves_source(self):
        from media_export import export_media_file
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/"original.mp3"
            destination=Path(directory)/"story.mp4"
            original=b"immutable-original-audio"
            source.write_bytes(original)
            def fake_ffmpeg(args, **kwargs):
                Path(args[-1]).write_bytes(b"fake-mp4")
                return subprocess.CompletedProcess(args, 0, b"", b"")
            request={"preset":"whatsapp-status","mode":"audio-story","trim_seconds":"12","start_seconds":"3","output_format":"mp4"}
            with patch("media_export.shutil.which", return_value="/usr/bin/ffmpeg"), patch("media_export.subprocess.run", side_effect=fake_ffmpeg) as run:
                result=export_media_file(source,destination,request)
            args=run.call_args.args[0]
            self.assertTrue(any("showwaves=s=900x420" in arg for arg in args))
            self.assertIn("lavfi",args)
            self.assertIn("-ss",args)
            self.assertIn("1:a:0",args)
            self.assertEqual(result["mime_type"],"video/mp4")
            self.assertEqual(source.read_bytes(),original)
            self.assertEqual(destination.read_bytes(),b"fake-mp4")

    def test_webp_export_uses_animated_encoder_and_preserves_source(self):
        from media_export import export_media_file
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/"original.mp4"
            destination=Path(directory)/"sticker.webp"
            original=b"immutable-original-video"
            source.write_bytes(original)
            def fake_ffmpeg(args, **kwargs):
                Path(args[-1]).write_bytes(b"fake-webp")
                return subprocess.CompletedProcess(args, 0, b"", b"")
            with patch("media_export.shutil.which", return_value="/usr/bin/ffmpeg"), patch("media_export.subprocess.run", side_effect=fake_ffmpeg) as run:
                result=export_media_file(source,destination,{"preset":"animated-sticker","mode":"fit-letterbox","trim_seconds":"5","start_seconds":"2","output_format":"webp"})
            args=run.call_args.args[0]
            self.assertIn("libwebp_anim",args)
            self.assertIn("webp",args)
            self.assertIn("-ss",args)
            self.assertEqual(result["mime_type"],"image/webp")
            self.assertEqual(source.read_bytes(),original)
            self.assertEqual(destination.read_bytes(),b"fake-webp")
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

    def test_story_title_and_artist_are_burned_in_and_sanitized(self):
        from media_export import export_media_file, _clean_overlay_text
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/"original.mp4"
            destination=Path(directory)/"story.mp4"
            source.write_bytes(b"immutable-original")
            def fake_ffmpeg(args, **kwargs):
                self.assertIn("drawtext=textfile=", " ".join(args))
                content=" ".join(p.read_text(encoding="utf-8") for p in Path(directory).glob("vg-*.txt"))
                self.assertIn("My Story",content)
                self.assertIn("Artist Name",content)
                Path(args[-1]).write_bytes(b"fake-mp4")
                return subprocess.CompletedProcess(args,0,b"",b"")
            request={"preset":"whatsapp-status","mode":"fit-letterbox","trim_seconds":"8","output_format":"mp4","title":"My Story","artist":"Artist Name"}
            with patch("media_export.shutil.which",return_value="/usr/bin/ffmpeg"), patch("media_export.subprocess.run",side_effect=fake_ffmpeg):
                result=export_media_file(source,destination,request)
            self.assertTrue(result["title_overlay"])
            self.assertTrue(result["artist_overlay"])
            self.assertEqual(_clean_overlay_text("My Story"+chr(10)),"My Story")
            self.assertEqual(source.read_bytes(),b"immutable-original")
            self.assertEqual(list(Path(directory).glob("vg-*.txt")),[])

if __name__=="__main__": unittest.main()
