import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parent
class CreatorDeskBatchTests(unittest.TestCase):
 def test_assets_are_loaded(self):
  for p in ("index.html","site/index.html","android-web/index.html"):
   with self.subTest(p=p): self.assertIn('src="creator-desk.js?v=cd1"',(ROOT/p).read_text(encoding="utf-8"))
 def test_creator_features_and_shortcuts(self):
  js=(ROOT/"site"/"creator-desk.js").read_text(encoding="utf-8")
  for marker in ("select-video","select-audio","invert","favorite","unfavorite","playlist","Exported JSON inventory","vgcdActivity","vgcdSpeed","vgcdSleep","requestPictureInPicture","playbackRate","vgcdSelectedCount","vgcdPlayerTools","vgcdLive"):
   with self.subTest(marker=marker): self.assertIn(marker,js)
 def test_metadata_exports_are_privacy_safe(self):
  js=(ROOT/"site"/"creator-desk.js").read_text(encoding="utf-8")
  self.assertIn("kind:'vidgravity-inventory'",js)
  self.assertIn("items:items.map(({id,title,meta})=>({id,title,meta}))",js)
  self.assertNotIn("sourceUrl",js)
  self.assertNotIn("item.blob",js)
 def test_responsive_accessible_styles(self):
  css=(ROOT/"site"/"creator-desk.css").read_text(encoding="utf-8")
  for marker in (".vgcd-panel",".vgcd-actions",".vgcd-lower","focus-visible","max-width:700px","prefers-reduced-motion"):
   with self.subTest(marker=marker): self.assertIn(marker,css)
  self.assertIn('aria-live=\"polite\"',(ROOT/"site"/"creator-desk.js").read_text(encoding="utf-8"))
 def test_shell_assets_and_cache_are_synchronized(self):
  for p in ("index.html","styles.css","sw.js"):
   with self.subTest(p=p): self.assertEqual((ROOT/p).read_bytes(),(ROOT/"android-web"/p).read_bytes())
  self.assertEqual((ROOT/"index.html").read_bytes(),(ROOT/"site"/"index.html").read_bytes())
  self.assertEqual((ROOT/"styles.css").read_bytes(),(ROOT/"site"/"styles.css").read_bytes())
  self.assertEqual((ROOT/"sw.js").read_bytes(),(ROOT/"site"/"sw.js").read_bytes())
if __name__=="__main__": unittest.main(verbosity=2)
