import pathlib
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class PwaUiBatch40Tests(unittest.TestCase):
 def test_assets_wired(self):
  h=(ROOT/'site/index.html').read_text()
  self.assertIn('ui-batch-40.css?v=1',h);self.assertIn('ui-batch-40.js?v=1',h)
 def test_library_download_settings_workflows_present(self):
  s=(ROOT/'site/ui-batch-40.js').read_text()
  for x in ['vgClearLibrarySearch','vgLibraryViewToggle','vgLibrarySort','vgDownloadSearch','vgDownloadStatus','vgHideCompleted','vgSettingsSearch','vgResetUiPrefs','vgImportSummary','vgCopyApiAddress']:
   self.assertIn(x,s)
 def test_responsive_and_accessible_styles(self):
  c=(ROOT/'site/ui-batch-40.css').read_text()
  for x in ['@media(max-width:760px)','prefers-reduced-motion','vg-library-list-view','vg-dragover','html.vg-light']:
   self.assertIn(x,c)
if __name__=='__main__': unittest.main()
