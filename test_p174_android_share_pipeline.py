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
 def test_invalid_resume_range_discards_partial_and_restarts(self):
  source=(ANDROID/"app/src/main/java/com/vidgravity/app/DownloadApi.kt").read_text()
  self.assertRegex(source, r"code\s*==\s*416")
  self.assertIn("destination.delete()",source)
  self.assertIn("return downloadResult(id, destination, onProgress)",source)
 def test_checksum_mismatch_discards_partial_before_retry(self):
  source=(ANDROID/"app/src/main/java/com/vidgravity/app/DownloadApi.kt").read_text()
  mismatch=source.index("if (expectedHash != null && !calculatedHash.equals(expectedHash, ignoreCase = true))")
  self.assertIn("destination.delete()",source[mismatch:mismatch+500])
 def test_contract_is_exported(self):
  self.assertIn("AndroidShareIntakeRequest",(ROOT/"packages/contracts/src/android-share.ts").read_text());self.assertIn('export * from "./android-share";',(ROOT/"packages/contracts/src/index.ts").read_text())
if __name__=="__main__":unittest.main(verbosity=2)
