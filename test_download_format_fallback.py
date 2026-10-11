import unittest

import server


class DownloadFormatFallbackTests(unittest.TestCase):
    def test_requested_format_is_tried_first_then_compatible_fallbacks(self):
        chain = server._download_format_chain("137+bestaudio/best", True)
        self.assertEqual(chain[0], "137+bestaudio/best")
        self.assertEqual(chain[1:], ["bv*+ba/b", "b", "best"])

    def test_default_chain_has_no_duplicates(self):
        chain = server._download_format_chain(None, True)
        self.assertEqual(len(chain), len(set(chain)))
        self.assertEqual(chain[0], "bv*+ba/b")

    def test_single_file_formats_come_first_without_ffmpeg(self):
        chain = server._download_format_chain("bv*+ba/b", False)
        self.assertNotIn("+", chain[0])
        self.assertIn("bv*+ba/b", chain)


if __name__ == "__main__":
    unittest.main(verbosity=2)
