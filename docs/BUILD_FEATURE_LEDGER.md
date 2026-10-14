# VidGravity Build & Feature Ledger

Last audited: 2026-10-15 04:41 Asia/Calcutta

## Current candidate
- Branch: `fix/audio-only-extraction-20261015`
- Exact HEAD: `135ce35de817bdf20b1c83f8eef550db53937cd4`
- Android CI/regression: passed at run 38643775578.
- APK artifact: 11932045219, approximately 23.99 MB, expires 2026-10-28.
- Artifact ZIP SHA-256: `0b122f5520fcc0560245fc9fc95c4e2eb107f49f3d8682e519a703630dfcf63d`. Raw APK SHA-256 is not yet published.
- PR mismatch: open PR #32/#33 target a different branch/commit; no open PR points to this exact candidate.
- Live probe run 38643775836 failed: homepage and /api/health both returned HTTP 502.
- Render request logs recorded POST /api/media/export HTTP 404 twice. The phone screenshot also shows Story export HTTP 404.
- Readiness: NOT READY. Debug APK exists and CI passed, but live backend/export and feature acceptance gates have not passed.

## Explicit issue matrix

| Priority | Issue | Current state / acceptance |
|---|---|---|
| P0 | Sound Only | Source selects audio-only format and queues it; end-to-end playable audio file not verified. |
| P0 | YouTube URL download | Extractor configuration regression passed; live backend 502 and user saw cloud-service unavailable. Real URL download unverified. |
| P0 | Story export endpoint | Live POST /api/media/export returned 404; latest probe returns 502. Deploy and verify playable export. |
| P0 | Status Split Cutter 30/60/90/120 | Options and export call exist; user reports failure. Verify playable clips, count/duration/progress/cancel and saved output. |
| P1 | Remove Grab Best Video, retain Inspect Link | Still present in this exact candidate; duplicate removal on another branch is not included. |
| P1 | Remove StudyTok from all navigation | Current source still has route and Home quick action; remove all visible entries/routes. |
| P1 | Downloaded media tiles | Thumbnail/type icon, title, duration, resolution, size, status/date not verified on a completed download. |
| P1 | Downloads / Library layout and scrolling | Screenshots show stacked tools and clipped horizontal chips; simplify and test all-screen scrolling. |
| P1 | Create menu organization | Many stacked workflows; group Create/Convert, Status, Stickers, Captions/Share and verify every action. |
| P1 | Logo, modern colorful UI, icons/buttons, accessibility | Branding/theme and WCAG/TalkBack/touch-target audit not verified. |
| P1 | Camera one-hand controls | Photo/video/flash/quality/dual-camera and gallery saving require physical-device verification. |
| P1 | Foreground-service stability | Source/CI only; test Android 14+ background, lock-screen and process recovery. |
| P1 | 360p–8K/audio picker | Source-discovered formats exist; test available tiers, no duplicates, labels and audio-only selection. |
| P1 | Progress/size/speed/ETA and stalled recovery | Source/CI paths exist; user's screenshot showed 0.0 MB and 0.00 MB/s. Test timeout/retry/pause/resume/relaunch. |
| P1 | Play/Rename/Favorite/Delete/Share | Must be exercised on completed local media; not device-verified. |
| P1 | SHA-256 handling | Source calculates local hash and compares trusted companion checksum; live matching/mismatch tests pending. Publish raw APK SHA-256. |
| P1 | WhatsApp / Share Inbox | Receive/share URI and WhatsApp handoff not device-verified. |
| P1 | Camera gallery saving | Must confirm photo/video in Android Gallery and VidGravity Library after restart. |
| P1 | Close/back button and all-screen navigation | Modal close/back and return paths not acceptance-tested on device. |
| P1 | Premium theme/accessibility | Light/dark, status-bar insets, contrast, TalkBack, font scaling and touch targets unverified. |

## Required release gates
1. One unambiguous candidate branch/PR and exact-head CI pass.
2. Fresh matching APK artifact, artifact ZIP digest and raw APK SHA-256.
3. Live homepage, /api/health, /api/formats, download-job lifecycle and /api/media/export all pass.
4. Audio-only, YouTube, Status split and creator export produce playable saved files.
5. Duplicate actions removed and navigation/UI changes are in the same candidate.
6. Keep source/CI evidence separate from physical-device acceptance; do not claim device checks passed without evidence.
