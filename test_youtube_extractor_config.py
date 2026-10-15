import os
import unittest
from unittest.mock import patch
from pathlib import Path

import server


class YouTubeExtractorConfigTests(unittest.TestCase):
    def test_default_extractor_does_not_force_fragile_player_clients(self):
        with patch.object(server, "SERVER_HOST", "127.0.0.1"), patch.dict(os.environ, {}, clear=False):
            os.environ.pop("VIDGRAVITY_YTDLP_COOKIES_FROM_BROWSER", None)
            command = server._yt_dlp_command("--no-playlist", "-J", "https://www.youtube.com/watch?v=test")
        self.assertEqual(command[0], "yt-dlp")
        self.assertNotIn("--extractor-args", command)
        self.assertNotIn("--cookies-from-browser", command)

    def test_android_player_client_is_only_a_youtube_fallback(self):
        self.assertEqual(
            server._youtube_android_fallback_args("https://www.youtube.com/watch?v=test"),
            ["--extractor-args", "youtube:player_client=android"],
        )
        self.assertEqual(
            server._youtube_android_fallback_args("https://youtu.be/test"),
            ["--extractor-args", "youtube:player_client=android"],
        )
        self.assertEqual(server._youtube_android_fallback_args("https://www.instagram.com/reel/test"), [])

    def test_youtube_fallback_only_applies_to_youtube_urls(self):
        self.assertEqual(server._youtube_android_fallback_args("https://youtube.com/watch?v=example"), ["--extractor-args", "youtube:player_client=android"])
        self.assertEqual(server._youtube_android_fallback_args("https://youtu.be/example"), ["--extractor-args", "youtube:player_client=android"])
        self.assertEqual(server._youtube_android_fallback_args("https://www.instagram.com/reel/example"), [])

    def test_browser_cookies_are_opt_in_for_loopback_server_only(self):
        with patch.dict(os.environ, {"VIDGRAVITY_YTDLP_COOKIES_FROM_BROWSER": "chrome"}), patch.object(server, "SERVER_HOST", "127.0.0.1"):
            command = server._yt_dlp_command("--no-playlist", "https://www.youtube.com/watch?v=test")
        self.assertEqual(command[1:3], ["--cookies-from-browser", "chrome"])

    def test_browser_cookies_are_never_added_to_public_server_commands(self):
        with patch.dict(os.environ, {"VIDGRAVITY_YTDLP_COOKIES_FROM_BROWSER": "chrome"}), patch.object(server, "SERVER_HOST", "0.0.0.0"):
            command = server._yt_dlp_command("--no-playlist", "https://www.youtube.com/watch?v=test")
        self.assertNotIn("--cookies-from-browser", command)

    def test_tauri_desktop_origin_is_allowlisted_for_local_companion(self):
        handler = object.__new__(server.Handler)
        with patch.dict(os.environ, {}, clear=True):
            origins = handler._allowed_origins()
        self.assertIn("tauri://localhost", origins)
        self.assertIn("http://tauri.localhost", origins)
        self.assertIn("https://vidgravity.onrender.com", origins)
        self.assertIn("https://vidgravity-web.revanthbesty.workers.dev", origins)

    def test_cookie_browser_setting_is_not_required_for_non_youtube_sources(self):
        with patch.object(server, "SERVER_HOST", "127.0.0.1"), patch.dict(os.environ, {}, clear=False):
            os.environ.pop("VIDGRAVITY_YTDLP_COOKIES_FROM_BROWSER", None)
            command = server._yt_dlp_command("--no-playlist", "https://example.com/video")
        self.assertEqual(command[-1], "https://example.com/video")


if __name__ == "__main__":
    unittest.main(verbosity=2)
