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

    def test_youtube_verification_errors_are_actionable_and_do_not_claim_success(self):
        result = server._classify_extractor_error("Sign in to confirm you're not a bot")
        self.assertEqual(result["code"], "provider_verification_required")
        self.assertIn("official download option", result["message"])
        self.assertNotIn("bypass", result["message"].lower())

    def test_restricted_media_and_rate_limits_are_classified(self):
        restricted = server._classify_extractor_error("This video is private")
        temporary = server._classify_extractor_error("HTTP Error 429: Too Many Requests")
        self.assertEqual(restricted["code"], "provider_access_restricted")
        self.assertEqual(temporary["code"], "provider_temporarily_unavailable")

    def test_download_jobs_have_a_bounded_provider_timeout(self):
        source = Path(__file__).with_name("server.py").read_text(encoding="utf-8")
        self.assertIn("threading.Timer(180,stop_stalled_job)", source)
        self.assertIn("Provider download timed out after 180 seconds", source)

    def test_cookie_browser_setting_is_not_required_for_non_youtube_sources(self):
        with patch.object(server, "SERVER_HOST", "127.0.0.1"), patch.dict(os.environ, {}, clear=False):
            os.environ.pop("VIDGRAVITY_YTDLP_COOKIES_FROM_BROWSER", None)
            command = server._yt_dlp_command("--no-playlist", "https://example.com/video")
        self.assertEqual(command[-1], "https://example.com/video")


if __name__ == "__main__":
    unittest.main(verbosity=2)
