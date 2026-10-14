import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parent
class WorkspacePlusFeatureTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.js=(ROOT/"site"/"workspace-plus.js").read_text(encoding="utf-8")
  cls.css=(ROOT/"site"/"styles.css").read_text(encoding="utf-8")
  cls.sw=(ROOT/"site"/"sw.js").read_text(encoding="utf-8")
 def test_script_loaded_in_all_app_shells(self):
  for path in ("index.html","site/index.html","android-web/index.html"):
   with self.subTest(path=path):self.assertIn('src="workspace-plus.js?v=wp1"',(ROOT/path).read_text(encoding="utf-8"))
 def test_dock_last_page_and_keyboard_help(self):
  for marker in ("vgpDock","vgpReturnPage","vgpShortcuts","vgpShortcutModal","event.altKey","vgp-last-page"):
   with self.subTest(marker=marker):self.assertIn(marker,self.js)
 def test_queue_health_progress_and_storage_estimate(self):
  for marker in ("vgpQueueHealth","vgpProgressFill","navigator.storage?.estimate","vgpStorageFill"):
   with self.subTest(marker=marker):self.assertIn(marker,self.js)
 def test_library_sort_count_and_clear_filters(self):
  for marker in ("vgpLibrarySort","vgpLibraryCount","vgpClearSearch","vgpCompactToggle"):
   with self.subTest(marker=marker):self.assertIn(marker,self.js)
 def test_saved_filter_presets_persist(self):
  self.assertIn("vgp-library-presets",self.js);self.assertIn("vgpSavePreset",self.js);self.assertIn("vgpPresets",self.js)
 def test_privacy_safe_metadata_backup(self):
  self.assertIn("kind:'library-metadata'",self.js);self.assertNotIn("url:m.url",self.js);self.assertIn("store.get(m.id)",self.js);self.assertIn("unknown IDs will not be imported",self.js)
 def test_responsive_accessible_styles_and_offline_recovery(self):
  for marker in (".vgp-dock",".vgp-pulse","@media(max-width:600px)","prefers-reduced-motion","focus-visible"):
   with self.subTest(marker=marker):self.assertIn(marker,self.css)
  self.assertIn("addEventListener('online'",self.js)
 def test_runtime_assets_and_styles_stay_synchronized(self):
  for name in ("index.html","styles.css","sw.js"):
   with self.subTest(file=name):self.assertEqual((ROOT/name).read_bytes(),(ROOT/"android-web"/name).read_bytes())
  self.assertEqual((ROOT/"styles.css").read_bytes(),(ROOT/"site"/"styles.css").read_bytes())
  self.assertEqual((ROOT/"index.html").read_bytes(),(ROOT/"android-web"/"index.html").read_bytes())
if __name__=="__main__":unittest.main(verbosity=2)
