import pathlib, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class PwaUiBatch50Tests(unittest.TestCase):
 def test_assets_are_wired_and_cached(self):
  html=(ROOT/'site/index.html').read_text()
  sw=(ROOT/'site/sw.js').read_text()
  self.assertIn('ui-batch-50.css?v=1',html)
  self.assertIn('ui-batch-50.js?v=1',html)
  self.assertIn('./ui-batch-50.css',sw)
  self.assertIn('./ui-batch-50.js',sw)
 def test_fifty_numbered_enhancements_are_documented(self):
  js=(ROOT/'site/ui-batch-50.js').read_text()
  for n in range(41,91):
   self.assertIn('// '+str(n)+'.',js)
 def test_accessibility_responsive_preferences(self):
  js=(ROOT/'site/ui-batch-50.js').read_text()
  css=(ROOT/'site/ui-batch-50.css').read_text()
  for s in ['aria-live','beforeunload','vg90Contrast','vg90TextSize','prefers-reduced-motion','max-width:650px','vg90-skip']:
   self.assertIn(s,js+'\n'+css)
if __name__=='__main__': unittest.main()
