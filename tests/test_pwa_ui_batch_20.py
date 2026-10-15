import pathlib
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
class PwaUiBatch20Tests(unittest.TestCase):
    def test_ui_batch_assets_are_wired(self):
        html=(ROOT/'site/index.html').read_text()
        self.assertIn('ui-batch-20.css?v=1', html)
        self.assertIn('ui-batch-20.js?v=1', html)
    def test_command_palette_accessibility_and_shortcuts_exist(self):
        js=(ROOT/'site/ui-batch-20.js').read_text()
        for token in ['role="dialog"','aria-modal="true"','ArrowDown','Escape','vg-ui-theme','vg-scroll-progress','vg-back-top']:
            self.assertIn(token, js)
    def test_mobile_reduced_motion_and_light_theme_styles_exist(self):
        css=(ROOT/'site/ui-batch-20.css').read_text()
        for token in ['@media(max-width:760px)','prefers-reduced-motion','html.vg-light','focus-visible','env(safe-area-inset-bottom)']:
            self.assertIn(token, css)
if __name__ == '__main__':
    unittest.main()
