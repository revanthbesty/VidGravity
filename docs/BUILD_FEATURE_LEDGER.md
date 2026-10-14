# VidGravity Build & Feature Ledger

Last updated: 2026-10-15  
Release target: native Android APK first. A feature is **not verified** just because its code exists.

## Build register

| Build | Version | Source / commit | Artifact status | Validation status | Notes |
|---|---|---|---|---|---|
| p178 | 1.0.8-p178 (previous baseline) | Historical build reference; exact commit/artifact mapping not confirmed in this ledger yet | Older debug artifact exists from commit `30c8753bb12af7584a8a090826b5845eb36fb3d6` (CI run `38617206744`) | CI passed for that commit; physical-device status unknown | Do not assume every reported-working feature was verified on this exact build until the user/build mapping is confirmed. |
| p179 | 1.0.9-p179 | Release gate branch head `13e9d1f39a4c1383dcdcb8a86f7bef54df80a934` | Signed production APK not confirmed | Bootstrap validation passed; VidGravity CI was still running at last check | Signing workflow preparation only; not a production release. |
| p180 | 1.1.0-p180 | Fix branch `fix/android-ux-camera-creator-20261015`; candidate head before CI fix `640f5bff70aec53613e298a3e0a0556f8d3f22a6`; CI-test correction commit `d1ec2a7a72cc317aacf5524e16c721f90e8f6558` | Debug APK for corrected head not yet confirmed | CI run `38623533114` failed in `test_android_production_release_gate.py` because a p179-only assertion rejected p180 versionCode 180; the failing assertion was updated to validate explicit versionCode/versionName for later candidate builds. A fresh exact-head CI result is pending. | Regression-fix candidate; do not call fixed until fresh build, automated tests, artifact and device checks are recorded. |

## Latest CI investigation — 2026-10-15

- Run: https://github.com/revanthbesty/VidGravity/actions/runs/38623533114
- Tested head: `640f5bff70aec53613e298a3e0a0556f8d3f22a6`
- Failure isolated: the inherited release-gate test hard-coded `versionCode = 179` and `versionName = "1.0.9-p179"`, causing a false failure after p180 correctly bumped the app to `versionCode = 180`, `versionName = "1.1.0-p180"`.
- Correction committed to the same candidate branch: `d1ec2a7a72cc317aacf5524e16c721f90e8f6558`; test now checks an explicit code of at least 179 and that the version name includes the matching p-number.
- A new workflow result and APK artifact for the corrected exact head are not yet verified. Existing artifact `vidgravity-ci-reports` from run `38623533114` is only a test-report artifact, **not an APK**.
- Other checks observed in that run: PWA AI Studio TypeScript/build passed; Cloud AI API TypeScript/build/task lifecycle passed; Android share pipeline build was still running when inspected. No physical-device tests have been performed by the assistant.

## Feature-to-build matrix

Status meanings: **Present** = implementation found in source; **CI pending** = not validated on this build yet; **Device pending** = needs a real phone test; **Historical unknown** = previous build mapping not proven.

| Feature / reported issue | Previous behavior reported by user | Current candidate | Build where resolved | Verification / acceptance required |
|---|---|---|---|---|
| Screen scrolling across Home, Downloads, Library, Create, StudyTok, Inbox, Settings | Scrolling failed across screens | p180: top-level content host changed to a neutral `Box` so each screen can own its scroll container | Not yet resolved/verified | CI + test each tab with long content, scroll to both ends, keyboard open/closed, portrait and landscape |
| Downloads menu organization | Menus felt cluttered | p180 inherits collapsible secondary tools from earlier source; review for compact menu layout | Earlier introduction build unknown; p180 verification pending | Check filters, sort, queue actions, overflow/secondary tools and touch targets on phone |
| Library menu organization | Menus felt cluttered | p180 inherits collapsible library tools and bulk actions | Earlier introduction build unknown; p180 verification pending | Check search, filters, sort, play/share/rename/delete/favorites and scrolling |
| Camera flash on/off | Flash turned on but could not turn off | p180: explicit ON/tap-off label, async torch state update, force torch off before camera session rebind | Not yet resolved/verified | Repeatedly toggle flash ON→OFF; switch rear/front; enter/exit dual mode; ensure label matches torch state |
| Photo capture controls | Photo/video options were unclear | p180: visible PHOTO/VIDEO mode, Capture/Record shutter and dedicated video-quality selector | Not yet resolved/verified | Take multiple photos; record/stop video; confirm files are valid and visible |
| Video quality | User could not select quality while recording workflow was visible | p180: quality control visible alongside shutter; selection applies to next recording | Not yet resolved/verified | Choose 480p/720p/1080p/highest available and inspect output dimensions/metadata on device |
| Dual-camera | Dual camera and capture did not work | Existing code requests concurrent front/rear only when hardware reports support; video falls back to single camera; p180 retains this hardware-aware behavior | Not yet resolved/verified | Verify front/rear simultaneous preview and photo on a compatible device; on unsupported devices, show clear fallback and keep single-camera photo/video working |
| Create Studio | Some menu actions appeared to do nothing | p180 replaces two dead-end creator cards with video-picker entry points; existing 9:16 story, status split, caption and export actions remain | Not yet resolved/verified | Exercise every visible button; verify picker opens, export errors are surfaced, output is actually written |
| WhatsApp Status split durations | Previously worked; user asks to retain customization | p180 source includes selectable **30s / 60s / 90s / 120s** split targets, saved in draft preferences, progress/cancel, and export calls | Exact previously-working build unknown; p180 regression verification pending | Test all four targets against short/long videos; verify number of clips, duration, playable files, MediaStore/Library presence, cancellation, and retry |
| WhatsApp Status 120-second option | Custom target requested | 120 seconds remains available as a custom segment target; the UI warns that WhatsApp may trim a 120s clip and recommends 90s or less for standard Status compatibility | p180 source present; verification pending | Confirm user can select 120s and each exported segment targets 120s; keep warning visible |
| StudyTok | User says it worked in a previous build but stopped | Existing StudyTok screen has local autosaved transcript, flashcards, practice progress and CSV export; p180 quick action is routed before URL validation so it opens without a download link | Exact previously-working build unknown; p180 regression verification pending | Open from Home with empty URL; create cards, practice, save CSV, restart app and verify draft recovery |
| Share Inbox / WhatsApp sharing handoff | User reports WhatsApp-related features stopped working | Existing share inbox, Android share intents and Status splitter found in source; p180 preserves these routes | Exact previously-working build unknown; p180 regression verification pending | Receive shared URL/media, open chooser, grant URI access, share generated clips into WhatsApp if installed |
| Camera photo/video shared-gallery visibility | Capture results were hard to find | p180 camera code now publishes captures to Pictures/VidGravity and Movies/VidGravity on Android 10+; retains app-storage copy | Not yet resolved/verified | Take photo and video; verify both in Android gallery and VidGravity Library after returning/restarting |
| Download → progress → pause/resume/cancel/retry → checksum → Library | Core release requirement | Existing implementation remains in candidate; do not refactor unless test proves a defect | Baseline build mapping pending | Full end-to-end download on a permitted public test URL; verify final file, checksum and Library item |
| APK signing and distribution | User wants release-ready APK | p179 release workflow preparation inherited; p180 version candidate | No signed production APK confirmed | Configure protected signing secrets outside chat, run signed workflow, verify artifact checksum, install/upgrade test |

## Rules for every future build

1. Before editing, inspect current branch, build number, existing implementation and tests; do not duplicate a feature already present.
2. Keep user-reported regressions in a persistent list and map each to a source commit, build number, test result and device result.
3. Every build entry records: versionCode/versionName, branch, commit SHA, CI run, artifact URL and SHA-256, test outcomes, device-tested model/Android version, known limitations.
4. Separate these states: coded → automated tests passed → APK artifact verified → installed on device → feature manually verified → release signed.
5. Never mark a feature as fixed based on code changes alone. Use “pending” until evidence exists.
6. Do not drop previously working StudyTok, WhatsApp, creator, downloader, library, camera or sharing behavior when changing navigation/layout.
7. Keep WhatsApp Status clip target choices at 30/60/90/120 seconds. Explain that 120 seconds is a custom split target and may not be accepted as one standard WhatsApp Status item.
8. Before adding new features, close P0 regressions. Next-feature work may be prepared behind core stability but must not displace APK validation.
9. Update this ledger after every meaningful code, build, test, artifact, or device-verification change.

## Known evidence gaps to fill

- Identify the exact older APK/build where the user last confirmed StudyTok and WhatsApp features working.
- Record corrected p180 CI run, debug APK artifact URL and SHA-256 when available.
- Record physical-device acceptance results for scroll, flash, photo/video capture, dual-camera support, video quality, Status splitting and StudyTok/Inbox.
- Record the signed production APK build separately from debug builds.
