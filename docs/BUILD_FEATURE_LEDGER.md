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


## CI correction log — 2026-10-15
- Latest confirmed p180 candidate commit before CI correction: `e4dab6683bb87afb123909e44b84beb0cdc28a8f`.
- CI run `38624075700` failed in **Android share pipeline and social export** because the workflow invoked `test_android_build_feature_ledger.py` as a repository-root module, while the file is stored under `tests/`.
- Fix committed to `.github/workflows/vidgravity-ci.yml`: use unittest discovery with `-s tests -p "test_android_build_feature_ledger.py"`. Fix commit: `acb5cdcae6689c1481b9621d7f42d1b7bc7c1043`.
- Validation after the correction: **pending**. Wait for CI on the new exact head; do not mark p180 ready until the Android build, feature tests and artifact upload succeed.
- APK artifact status remains **not verified**. The prior run only produced CI reports, not the APK.


## User-reported p180 regression pass — 2026-10-15

### Latest verified candidate
- p180 debug APK artifact: https://github.com/revanthbesty/VidGravity/actions/runs/38625194536/artifacts/11924346679
- CI run: https://github.com/revanthbesty/VidGravity/actions/runs/38625194536
- Commit built by that artifact: `ff9328a7d1a3820bd8e6e6d6198dd9a648b0c290`
- CI: all four jobs passed and the debug APK upload succeeded.
- Subsequent UI change commit: `1e779174ad72619f283cf6447b9e9cda797774d5` removes the redundant “Grab Best Video” quick action (Inspect Link already exists) and removes StudyTok from the Home quick-action carousel. This later commit has NOT yet been built or tested; do not use the prior artifact as if it contains this change.

### New issues from the user's p180 test — highest priority first
| Priority | Issue / request | Required implementation | Acceptance test | Status |
|---|---|---|---|---|
| P0 | Audio-only / “Sound Only” does not work | Trace source inspection → audio stream selection → job enqueue → worker selector handling; support valid audio-only formats and show actionable failure states. Do not claim fixed until end-to-end test succeeds. | Use a permitted test source with audio-only stream; queue, download, verify nonzero audio file, correct MIME/extension and playable output in Library. | Open |
| P0 | WhatsApp Status split cutter does not work | Reproduce actual failure from source/logs; repair export/splitting flow, job completion handling and output publishing. Preserve 30/60/90/120-second user-selectable targets and 120s compatibility warning. | Test short and multi-segment videos at each target; confirm playable clips, correct duration/count, progress/cancel and Library visibility. | Open |
| P1 | Downloads and Library UI are cluttered / poorly organized | Prioritize one clean information hierarchy, grouped primary actions, compact secondary actions, clear empty/progress/completed/error states, and no duplicated controls. Downloaded video tiles must show thumbnail, title, duration, resolution, size, status, date and clear actions where metadata is available. | Verify queued, downloading, failed, completed and media-library states on a phone; test search/filter/sort and play/share/rename/delete/favorite. | Open |
| P1 | Create menu has too many poorly organized options | Group by workflow (Create/Convert, WhatsApp Status, Stickers, Captions/Share), remove duplicate entry points, make every visible action do something or clearly show why unavailable. | Tap every action; confirm correct picker, processing flow, progress/error, and saved output. | Open |
| P1 | Camera controls are poorly aligned and hard to reach | Move primary capture/mode/flash/quality controls into a reachable lower control zone or right-side thumb zone, maintain status-bar/inset safety and avoid obstructing preview. | One-handed reach check on target phone; photo/video/flash/quality/dual-camera controls and capture outputs verified. | Open |
| P1 | Add VidGravity logo and premium colorful visual system | Use one consistent brand asset in launcher and in-app navigation/header; cohesive accent gradients, consistent icon family, clear contrast, comfortable touch targets, and no duplicated features. | Verify launcher icon, splash/header/navigation and all main tabs in light/dark themes. | Open |
| P2 | Remove StudyTok completely | Home quick-action entry removed in commit `1e779174ad72619f283cf6447b9e9cda797774d5`; screen/navigation implementation cleanup remains to be audited before claiming fully removed. | No StudyTok tile, tab, menu item or reachable route; compile and regression pass. | Partially implemented; CI pending |
| P2 | Remove redundant “Grab Best Video” action | Home quick-action removed in commit `1e779174ad72619f283cf6447b9e9cda797774d5`; keep Inspect Link as the single inspection entry point. | No duplicate action remains in Home or other menus; CI pass. | Partially implemented; CI pending |

### Required workflow for the next candidate
1. Inspect the latest source and reproduce audio-only and Status splitter failures before making assumptions.
2. Fix P0 audio-only and Status split first; add regression tests.
3. Redesign Downloads and Library before adding any more feature tiles.
4. Consolidate Create workflows, then update camera reachability and branding.
5. Run exact-head CI and produce a fresh APK artifact. The earlier p180 APK is not equivalent to the later UI-change commit.
6. Request physical-device verification; never mark device-only behavior as passed from source/CI alone.


## Latest CI status update — 2026-10-15

- Latest full CI run inspected: https://github.com/revanthbesty/VidGravity/actions/runs/38628543175
- Commit tested: `4fdbccd7df8d8351f9f7ad563c03e9d8ccfe9c4c`
- Results: regression, PWA AI Studio and Cloud AI API jobs passed; Android share pipeline/export job failed in the feature-ledger unit tests.
- Failure cause: `test_studytok_quick_action_does_not_require_url` still required the StudyTok Home quick action, contradicting the user's explicit request to remove StudyTok. This is a stale assertion, not proof of an Android compile failure.
- Corrective commit: `d1ffa0569a6bfd5dede7f803173a3c421cbbbb8e` updates the test to assert that StudyTok and Grab Best Video are absent from the quick-action carousel while Sound Only and Status Clip Splitter remain.
- New CI for the corrective commit and a matching APK artifact are not yet verified.
- P0 functional defects reported by the user remain open: Sound Only does not work; Status Splitter does not work. UI reorganization, media detail tiles, branding, camera ergonomics and full StudyTok cleanup remain outstanding.


## Additional user device evidence — Story export HTTP 404 (2026-10-15)

User supplied phone screenshots showing:
- Create Studio's “Render 9:16 Story” action fails with `Story export failed: Export HTTP 404: {"error":"Not found"}`.
- The same Create screen displays “Story render requested” underneath, which is misleading after the server has returned a 404. UI must show a failed state and must not imply a job was accepted.
- The Library reports `0 total` / “Your library is empty” while the Downloads screen shows an Instagram download still running at `0.0 MB`, `0.00 MB/s`, and calculating ETA. This is not proof of a backend bug by itself, but a long-running zero-progress job must have a timeout/stalled state, useful error details, and a retry/cancel path.
- Downloads and Library screens have many full-width secondary controls, horizontally overflowing filter chips, large vertical spacing, and repeated tools; simplify hierarchy and group secondary actions.
- Status splitter UI shows 30/60/90/120 choices and a Split button, but user reports it does not work. Presence of controls is not proof export succeeds.

### Updated priority from screenshot evidence
1. **P0 — Story export 404:** inspect the Android export request URL and deployed server route/version; add a contract test that the client path exists and returns a valid export response. Fix route mismatch or deploy the missing route only after identifying the exact cause. Never mask 404 as a successful queued render.
2. **P0 — Status splitter actual export:** verify the export route used by `DownloadApi.exportMedia`, test a real short video end-to-end, and confirm resulting clip is saved and playable.
3. **P0 — Sound Only:** test audio-only selector through server job creation, worker download, extension/MIME and playback.
4. **P1 — stalled download state:** stop indefinite `0.0 MB / 0.00 MB/s` waiting; surface actionable stalled/error state without discarding the user's job.
5. **P1 — Downloads/Library/Create UX:** reduce repeated full-width tools, use responsive wrapping or vertical filter groups, present clear video/audio tiles with thumbnails and metadata, and group creator actions by workflow.
6. **P1 — camera reachability, branding, and colorful consistent iconography.**
7. Remove StudyTok from navigation and remove duplicate Grab Best Video quick action; latest UI change is commit `1e779174ad72619f283cf6440a0556f8d3f22a6` only if confirmed by GitHub history. The current branch head at the latest CI check is `ff84580c72d2506da39b944179310f459f8f9812`; use that exact SHA as the source of truth for subsequent commits.

### CI / APK state after UI cleanup
- Latest checked head: `ff84580c72d2506da39b944179310f459f8f9812`.
- CI run `38629301419`: regression, PWA AI Studio and Cloud AI API jobs passed; Android share pipeline was still running at check time.
- Artifacts available from that run were CI reports only; no fresh APK artifact was present at that time.
- Do not treat earlier p180 APK as containing the latest UI cleanup or the new screenshot fixes.


## Additional screenshot findings — p180 device feedback (2026-10-15)

Latest successful CI build checked:
- Run: https://github.com/revanthbesty/VidGravity/actions/runs/38629581817
- Candidate commit: `bae16ad723a8246f95cdfcf8b2395349aec59b0d`
- Artifact: https://github.com/revanthbesty/VidGravity/actions/runs/38629581817/artifacts/11925392393
- Artifact SHA-256: `f2f74b05d1ddd57b6bcd545a0ae6ff59d3fab0a4ab1ab300bd921531231e5578`
- All four CI jobs passed. This proves compilation and automated checks only, not physical-device functionality.

### New confirmed-by-screenshot issues
| Priority | Issue | Evidence / likely investigation | Status |
|---|---|---|---|
| P0 | YouTube inspect/download returns generic “cloud service temporarily unavailable”; Instagram works | User screenshot shows app's generic cloud error on a YouTube URL. Check live backend health, formats/inspect endpoint response and logs; distinguish backend outage, provider bot/verification, extractor/JS-runtime issues and rate limits. Do not bypass provider access controls. Show the real actionable provider error rather than generic cloud-unavailable text. | Open; live root cause not yet confirmed |
| P0 | Story export returns HTTP 404 `{"error":"Not found"}` | User screenshot shows Create Studio Story export endpoint not found. Trace the Android export request path and compare it with deployed backend route; align route/payload or deploy the missing supported endpoint, then verify a real export. | Open; endpoint mismatch or missing deployed route suspected |
| P0 | Status splitter appears to accept a selected duration but export fails in user workflow | User screenshot shows split UI, but the previous story export error is distinct; reproduce the Status splitter endpoint and inspect `DownloadApi.exportMedia` response, backend route, output persistence and MIME. Keep the 30/60/90/120-second targets. | Open |
| P1 | Library shows empty even while Downloads has an active transfer | Screenshots show one Instagram job at 0.0 MB / 0% and Library empty. This may be an active/incomplete transfer rather than a completed media indexing bug; verify a successful completed download first, then inspect publication/indexing. | Open |
| P1 | UI still looks dense and secondary actions require too much scrolling | Screenshots show repeated large outlined controls, horizontal chips cut off at screen edges, too much vertical height, and crowded Create Studio. Consolidate duplicate actions, use compact grouped sections, wrap filter chips or provide accessible horizontal scrolling, and keep primary actions visible above bottom navigation. | Open |
| P1 | Download card metadata and thumbnail are not sufficiently clear | Screenshot shows source and title but little completed-file metadata because the item is still running. For completed items show thumbnail/icon, title, source, resolution, duration, size, file type, completion state, and Play/Share/Library actions where data exists. | Open |
| P1 | Camera ergonomics and brand polish remain unverified | User's earlier report: move frequently used camera controls to reachable bottom/right-hand zones, add app branding, consistent colorful theme and modern icons without feature duplication. | Open |

### Current source checks
- The latest candidate source no longer contains the Home “Grab Best Video” tile and keeps “Inspect link” as the single inspection action.
- StudyTok has been removed from the Home quick-action carousel. Full screen/navigation cleanup still needs an explicit audit.
- User's screenshots may be from a build older than the latest candidate; ask them to confirm installed version only if necessary to distinguish stale build behavior. Do not assume screenshot behavior proves the new artifact has the same code.
- Do not call the YouTube problem fixed until a real supported public test URL passes inspect → formats → download → playable file on the deployed service.
- Do not call Story/Status export fixed until the deployed route returns a real playable output that is visible in Library.


## Follow-up implementation — 2026-10-15

- Sound Only: updated Home quick action to prefer a listed audio-only format and fall back to the explicit `bestaudio` selector when the extractor omits separate audio-only rows. User-facing status now distinguishes the fallback path. Regression assertion added in `test_android_ux_regressions.py`.
- Commit sequence: `e6712385d6c3c5cd88b3a3b26758044ea9bb3490` (implementation), `ccaf692e2560be3fd49f7795ac48494a1185f767` (regression test).
- Validation: exact-head CI has been triggered; result and fresh APK artifact must be checked before treating this as verified.
- Status Clip Splitter: Android source already routes exports through `POST /api/media/export`, and `server.py` contains that route. The user's HTTP 404 therefore still needs live companion endpoint/deployment evidence; source presence alone does not prove the deployed service has the route.
- Release blockers remain: live YouTube extraction, deployed Story export endpoint, end-to-end Status clip output, and physical-device acceptance. Do not mark p180 release-ready until these are verified.
