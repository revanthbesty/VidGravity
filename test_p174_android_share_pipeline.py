from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent
ANDROID=ROOT/"apps"/"android"
class P174AndroidSharePipelineTests(unittest.TestCase):
 def test_manifest_share_filter(self):
  root=ET.parse(ANDROID/"app/src/main/AndroidManifest.xml").getroot();ns="{http://schemas.android.com/apk/res/android}";app=root.find("application")
  a=next(x for x in app.findall("activity") if x.attrib.get(ns+"name")==".ShareReceiverActivity")
  self.assertTrue(any(any(q.attrib.get(ns+"name")=="android.intent.action.SEND" for q in f.findall("action")) and any(d.attrib.get(ns+"mimeType")=="text/plain" for d in f.findall("data")) for f in a.findall("intent-filter")))
 def test_checksum_before_media_store(self):
  p=ANDROID/"app/src/main/java/com/vidgravity/app";self.assertIn("X-Content-SHA256",(p/"DownloadApi.kt").read_text());self.assertIn("MediaLibraryIndexer.publish",(p/"DownloadWorker.kt").read_text())
 def test_contract_is_exported(self):
  self.assertIn("AndroidShareIntakeRequest",(ROOT/"packages/contracts/src/android-share.ts").read_text());self.assertIn('export * from "./android-share";',(ROOT/"packages/contracts/src/index.ts").read_text())
 def test_all_screen_quality_regressions(self):
  app=ANDROID/"app/src/main/java/com/vidgravity/app"
  main=(app/"MainActivity.kt").read_text()
  player=(app/"PlayerScreen.kt").read_text()
  swipe=(app/"SwipeFeedScreen.kt").read_text()
  study=(app/"StudyTokScreen.kt").read_text()
  picker=(app/"FormatPickerBottomSheet.kt").read_text()
  manifest=ET.parse(ANDROID/"app/src/main/AndroidManifest.xml").getroot()
  ns="{http://schemas.android.com/apk/res/android}"
  activity=next(x for x in manifest.find("application").findall("activity") if x.attrib.get(ns+"name")==".MainActivity")
  self.assertIn("fun DownloadsContent",main);self.assertIn("fun LibraryContent",main)
  self.assertGreaterEqual(main.count("verticalScroll(rememberScrollState())"),4)
  self.assertIn("onCreateWithMedia",main)
  self.assertNotIn("Sticker renderer integration is not available yet",main)
  self.assertNotIn("30-second status splitting is not available yet",main)
  self.assertIn("vg_player_progress",player)
  self.assertIn("STATE_ENDED",player)
  self.assertIn("clips.filter { it.isFavorite }",swipe)
  self.assertIn("CreateDocument(\"text/csv\")",study)
  self.assertIn("verticalScroll(rememberScrollState())",study)
  self.assertIn("return ScrollView(context)",picker)
  self.assertIn("orientation|screenSize|keyboardHidden",activity.attrib.get(ns+"configChanges",""))
 def test_creator_stickers_are_reconciled_and_published_atomically(self):
  app=ANDROID/"app/src/main/java/com/vidgravity/app"
  indexer=(app/"MediaLibraryIndexer.kt").read_text()
  api=(app/"DownloadApi.kt").read_text()
  self.assertIn("MediaStore.Images.Media.EXTERNAL_CONTENT_URI to \"Pictures/VidGravity/Stickers/\"",indexer)
  self.assertIn('MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> "image/*"',indexer)
  self.assertIn("MediaStore.MediaColumns.IS_PENDING, 1",api)
  self.assertIn("MediaStore.MediaColumns.IS_PENDING, 0",api)
  self.assertIn("context.contentResolver.notifyChange(publishedUri, null)",api)
if __name__=="__main__":unittest.main(verbosity=2)
