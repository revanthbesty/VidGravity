import unittest

import server


class ProviderErrorClassificationTests(unittest.TestCase):
    def test_youtube_verification_has_clear_safe_guidance(self):
        result = server._classify_extractor_error(
            "ERROR: Sign in to confirm you're not a bot"
        )
        self.assertEqual(result["code"], "provider_verification_required")
        self.assertIn("official download option", result["message"])
        self.assertNotIn("cookies", result["message"].lower())
        self.assertNotIn("bypass", result["message"].lower())

    def test_restricted_media_is_not_mislabelled_as_bot_verification(self):
        result = server._classify_extractor_error("ERROR: This video is private")
        self.assertEqual(result["code"], "provider_access_restricted")
        self.assertIn("permission", result["message"])

    def test_transient_provider_errors_suggest_wait_and_retry(self):
        result = server._classify_extractor_error("HTTP Error 429: Too Many Requests")
        self.assertEqual(result["code"], "provider_temporarily_unavailable")
        self.assertIn("retry", result["message"].lower())

    def test_unknown_errors_get_generic_actionable_message(self):
        result = server._classify_extractor_error("unsupported URL")
        self.assertEqual(result["code"], "provider_extraction_failed")
        self.assertIn("public and supported", result["message"])

    def test_empty_error_has_generic_classification(self):
        result = server._classify_extractor_error("")
        self.assertEqual(result["code"], "provider_extraction_failed")
        self.assertTrue(result["message"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
