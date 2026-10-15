# VidGravity Build & Feature Ledger

Last audited: 2026-10-15 06:03 Asia/Calcutta

## Current candidate
- Branch: `fix/audio-only-extraction-20261015`
- Exact HEAD at latest successful CI: `45c1434743855cffe2b224442e828244e471a04d` (commit message: docs: track current candidate readiness and blockers).
- Android CI/regression: passed at run 38652807973; Android share pipeline and regression jobs both succeeded.
- Matching APK artifact: [vidgravity-android-debug-apk](https://github.com/revanthbesty/VidGravity/actions/runs/38652807973/artifacts/11935062991), 23,992,962 bytes, expires 2026-10-28.
- Artifact ZIP SHA-256: `e0a3cfc672e52d4ac058ea4f5a4d505155301ae502679cc8136f5a33983bd3fc`. Raw APK SHA-256 is still not recorded.
- PR mismatch: open PR #33 head is `ad9a57f5ef34755ce083534041df99aae3b126ce`, not the exact current candidate; no open PR points to HEAD `45c1434743855cffe2b224442e828244e471a04d`.
- Latest live probe run 38652807872 failed: `https://vidgravity.onrender.com` health check timed out and homepage returned HTTP 503. The Story export endpoint previously returned HTTP 404 in request logs and has not been shown passing since.
- Readiness: NOT READY. The matching debug APK and CI pass exist, but live deployment/export, raw APK hash, and feature/device acceptance gates remain outstanding.

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


## Fresh readiness audit — 2026-10-15 06:03 Asia/Calcutta

Evidence refreshed from GitHub Actions:
- CI run [38652807973](https://github.com/revanthbesty/VidGravity/actions/runs/38652807973) completed successfully at exact HEAD `45c1434743855cffe2b224442e828244e471a04d`. Jobs `Android share pipeline and social export` and `regression` passed.
- Fresh matching debug APK artifact is [11935062991](https://github.com/revanthbesty/VidGravity/actions/runs/38652807973/artifacts/11935062991); ZIP SHA-256 is recorded above. This ZIP digest must not be presented as the raw APK digest.
- Live probe [38652807872](https://github.com/revanthbesty/VidGravity/actions/runs/38652807872) remains failed: `P169 deployment probe: BLOCKED`, `health: TimeoutError`, `homepage: HTTP 503`. PWA-origin probe was skipped after the backend probe failed.
- Current open PR #33 is still on head `ad9a57f5ef34755ce083534041df99aae3b126ce`; it does not match the latest candidate commit. Candidate/PR alignment remains a release blocker.
- The latest ledger commit itself is the candidate HEAD, so the ledger is present on that branch but its recorded exact-head details were stale until this audit update.

### Release gate decision
**NOT READY.** Do not notify as ready until (1) live health/homepage and media export pass, (2) exact-head CI has a downloadable matching APK and a raw APK SHA-256 checksum file, (3) explicit user issues are verified with evidence, and (4) phone-only acceptance checks are either completed or clearly left pending for user testing.

No new pass is inferred for Sound Only, YouTube live downloads, Status Split Cutter output, StudyTok removal, Grab Best Video removal, media tile metadata, Downloads/Library/Create UX, branding/accessibility, camera reachability, download recovery, Library playback/indexing, close/back behavior, checksum matching/mismatch, WhatsApp/Share Inbox, or camera gallery saving. Existing issue matrix remains the source of truth for each outstanding acceptance test.
