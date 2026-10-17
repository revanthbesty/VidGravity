# Android workflow batch

Base: integration/android-release-candidate-1, 80a47f4. This isolated draft batch
includes the startup diagnostics from #60 and regression fixes from #59 without
merging either PR or changing #57's branch.

Implemented:
- Native startup recovery and commit/layout/draw logging; theme reads on IO.
- Saveable selected tab and URL, back-to-Home handling, and navigation that clears
  player/inbox overlays. Studio exports block route changes while active.
- Camera completion indexes a content URI on every supported API. The camera's
  Use capture in Studio button returns the saved image/video to the workspace.
- Android 8/9 downloads/exports use app-private files with FileProvider grants,
  avoiding a missing legacy external-storage permission. Legacy captures are
  recovered into Library on refresh. App-private media is removed on uninstall.
- Bounded image decoding and an actual image preview/share route.
- Completed downloads can open Studio; edits invalidate an earlier derivative,
  and story sharing stays disabled until a current render completes.
- Library refresh failures keep the workspace alive and expose retry.
- Native debug version 179 / 1.0.9-workflow-batch.

Automated gates:
- Existing Python/Deno/FFmpeg, web regression and shipped JS syntax coverage.
- Android assembleDebug, testDebugUnitTest, lintDebug, APK signature and SHA-256.
- API 35 emulator launch/navigation and image-preview instrumentation. Launch checks
  verify Home semantics, hidden recovery overlay, and a screenshot that is not
  almost entirely black. Logs, screenshots and test reports are CI artifacts.

The API 35 tests do not establish Pixel 10 Pro / Android 17 API 37.2 compatibility.
No Android 17 root cause has been reproduced here. A draw callback alone is not
proof of GPU presentation; see android-startup.md for diagnostic interpretation.

Limitations: video/audio story rendering, status splitting and animated stickers
still require the configured backend. No production export request or paid service
is used by these tests. Still images support preview/share, not story rendering.
Camera hardware capture and external share targets are not exercised by the UI
smoke tests. CameraX dual camera remains capability-dependent. Licensing activation
is not wired to Android. Existing unrelated functionality is not certified by this
batch. The CI artifact is a debug-signed APK, not a production release.
