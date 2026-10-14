import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parent
class FocusStudioBatchTests(unittest.TestCase):
 def test_focus_studio_assets_are_loaded_in_all_shells(self):
  for p in ("index.html","site/index.html","android-web/index.html"):
   text=(ROOT/p).read_text(encoding="utf-8")
   with self.subTest(p=p):
    self.assertIn('href="focus-studio.css?v=fs1"',text)
    self.assertIn('src="focus-studio.js?v=fs1"',text)
 def test_twenty_focus_features_are_implemented(self):
  js=(ROOT/"site"/"focus-studio.js").read_text(encoding="utf-8")
  for marker in ("vgFocusStudio","data-fs=\"preset\"","Custom minutes","paused","Start 5-min break","autoBreak","vgfsGoal","vgfsProgress","Recent focus sessions","data-fs=\"delete\"","data-fs=\"clear\"","Session notes","vidgravity-focus-history.csv","vidgravity-focus-history.json","vgfs-focus-mode","e.code!=='Space'","Notification.permission","Current streak","aria-live=\"polite\"","localStorage"):
   with self.subTest(marker=marker): self.assertIn(marker,js)
 def test_privacy_and_local_persistence(self):
  js=(ROOT/"site"/"focus-studio.js").read_text(encoding="utf-8")
  self.assertIn("vg-focus-studio-v1",js)
  self.assertNotIn("fetch(",js)
  self.assertNotIn("sourceUrl",js)
  self.assertNotIn("item.blob",js)
 def test_responsive_accessible_visual_design(self):
  css=(ROOT/"site"/"focus-studio.css").read_text(encoding="utf-8")
  for marker in (".vgfs-panel",".vgfs-grid",".vgfs-clock","focus-visible","max-width:760px","prefers-reduced-motion","vgfs-focus-mode"):
   with self.subTest(marker=marker): self.assertIn(marker,css)
 def test_cache_and_shell_files_are_synchronized(self):
  for p in ("index.html","styles.css","sw.js"):
   with self.subTest(p=p): self.assertEqual((ROOT/p).read_bytes(),(ROOT/"android-web"/p).read_bytes())
  self.assertEqual((ROOT/"styles.css").read_bytes(),(ROOT/"site"/"styles.css").read_bytes())
  self.assertEqual((ROOT/"sw.js").read_bytes(),(ROOT/"site"/"sw.js").read_bytes())
if __name__=="__main__": unittest.main(verbosity=2)