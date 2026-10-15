# VidGravity Build & Feature Ledger

**Snapshot time:** 2026-10-15 20:24 IST  
**Purpose:** Fail-closed release tracking. This is a status ledger, not a claim that untested functionality works.  
**PWA policy:** Keep the PWA unchanged for this Android candidate.  
**YouTube policy:** YouTube testing is paused per the user's instruction. Do not run live YouTube tests until explicitly re-authorized.  
**Release policy:** Do not merge or deploy an unverified product candidate.

## Current Android candidate

- **Candidate PR:** [#40 — Android P0 follow-up: compact filters and explain Story creation](https://github.com/revanthbesty/VidGravity/pull/40)
- **Head branch:** `fix/p0-device-feedback-compact-filters-20261015`
- **Exact head at this snapshot:** `dd4beeac821fbebfa346451a06faa0819ba584cc`
- **Base branch:** `fix/p0-remove-studytok-redundant-action-20261015` at `339c0fb6d24b99b8b3319d53cfa6a2199ada4c74` (stacked PR; not main)
- **Exact-head Android CI:** [Run 38737183579](https://github.com/revanthbesty/VidGravity/actions/runs/38737183579) — completed successfully for `dd4beeac821fbebfa346451a06faa0819ba584cc`. Jobs `regression` and `Android share pipeline and social export` both succeeded; regression steps included real FFmpeg derivative-render integration tests and the P170/P171/P172 unit/preflight tests (not live network acceptance).
- **Bootstrap validation:** [Run 38737183490](https://github.com/revanthbesty/VidGravity/actions/runs/38737183490) — success for the current candidate workflow context.
- **Matching APK artifact:** [Artifact 11973887892](https://github.com/revanthbesty/VidGravity/actions/runs/38737183579/artifacts/11973887892), `vidgravity-android-debug-apk`, 24,303,769 bytes, created 2026-10-15 12:48:19 UTC; expires 2026-10-29 12:48:16 UTC. GitHub artifact metadata maps it to head `dd4beeac821fbebfa346451a06faa0819ba584cc`.
- **Artifact ZIP SHA-256:** `b14d211910698b155086f55dbac410d48331550031ff21c6032658a6d2fcf71d` (matches GitHub artifact metadata and local ZIP hash).
- **Extracted app-debug.apk SHA-256:** `245b51a9a47e5b29a88d6cd80ce2fa8f1e161fcf500f18bd421d3eee53f2ec89` (calculated locally after extracting the artifact).
- **Build type:** Debug APK; no evidence here of a signed production APK.
- **Cloudflare preview deployment:** **NOT VERIFIED for the current head**. Failed preview builds were reported for both `vidgravity` and `vidgravity-web` at earlier head `a4d08f2`; no successful preview/deployment evidence for `dd4beeac821fbebfa346451a06faa0819ba584cc` was found in this check. See [PR #40 deployment comments](https://github.com/revanthbesty/VidGravity/pull/40).
- **Exact-candidate public deployment revision/health:** Not verified for `dd4beeac`. The previously reported Render/main revision is `135ce35de817bdf20b1c83f8eef550db53937cd4`, not the Android PR head; no fresh live probe was run in this check.
- **Live valid Story export with playable output and checksum equality:** Not demonstrated.
- **Physical-device acceptance:** No device evidence attached to this snapshot; do not mark phone-only tests passed.
- **Master ledger history:** This file remains on the documentation-only branch `docs/release-ledger-watch-20261015`; fetch attempts returned 404 on `main` and the Android candidate branch. This update is committed only to the documentation branch; it does not merge or deploy the product candidate.

## Status vocabulary

- **PASS — CI:** automated check passed only.
- **SOURCE:** code/UI element observed in the candidate source; runtime behavior not proven.
- **BLOCKED:** a release-critical condition is failing or required evidence is absent.
- **PENDING DEVICE:** requires emulator/physical-phone evidence not available in this run.
- **PAUSED:** deliberately not tested per user instruction.
- **NOT RUN:** no valid evidence; never infer success from code presence or an HTTP route merely existing.

## Required acceptance matrix

| ID | Requirement | Evidence/status | Exit criteria |
|---|---|---|---|
| REL-01 | Exact-head Android CI/regression | **PASS — CI** for `dd4beeac`; run 38737183579 succeeded and artifact metadata names the same head | Rerun on any changed candidate head; artifact must match that head |
| REL-02 | Fresh downloadable APK and checksums | **PASS for current head**: artifact 11973887892; ZIP and raw APK SHA-256 recorded above | Rebuild after any candidate-source change; record both digests for the new artifact |
| REL-03 | Candidate deployment revision and health | **BLOCKED**: no live deployment evidence for `dd4beeac`; earlier preview failures at `a4d08f2` remain unresolved for this candidate chain | Successful preview/deployment for the exact intended revision, then health checks against that revision |
| REL-04 | Story export endpoint returns playable media | **BLOCKED / NOT RUN end-to-end**. Earlier repository reports explicitly distinguish health checks from real export. | Send valid authorized test input; successful export; non-empty playable file; output saved; server/local SHA-256 match |
| REL-05 | Instagram downloads continue working | **NOT RUN in this snapshot** | Authorized Instagram test URL completes download; file plays; media appears once in Library with correct metadata and checksum |
| REL-06 | YouTube URL handling | **PAUSED** by user instruction; no live test attempted | Do not test until user re-authorizes; then inspect and download a permitted test URL without changing ordinary provider behavior |
| AUD-01 | Sound Only / Audio Only end-to-end | **SOURCE**: Sound Only action and Audio Only picker code exist; exact-head CI passes. Real audio output not verified | Valid audio-only job returns audio media, saves/plays correctly, preserves mode on retry, hashes correctly, and appears once in Library |
| AUD-02 | Remove redundant Grab Best Video; keep Inspect Link | **SOURCE**: current `MainActivity.kt` has no “Grab Best Video” string and retains “Inspect link” references | Verify actual installed candidate on device; scan all user-facing entry points |
| AUD-03 | Remove StudyTok from all user-facing navigation | **SOURCE, partial**: no “StudyTok” string in current `MainActivity.kt`; repo-wide/device acceptance not completed | Search all user-facing surfaces and verify no action, route, menu item, notification or deep link exposes it |
| MED-01 | Status Split Cutter exports playable saved clips for 30/60/90/120 seconds | **SOURCE**: `LocalStatusSplitter` accepts exactly 30/60/90/120-second targets and uses Media3 Transformer; output acceptance not run | For each target, export one or more clips from a known test video; verify expected durations, playable MP4, saved Movies/VidGravity output, Library indexing and temp cleanup |
| MED-02 | Downloads ordering and filtering | **SOURCE**: compact Filters & sorting dialog, search/filter/sort state and Newest default appear in current UI code | Device-test ordering (Newest default), status filtering, search, reset, scrolling, persistence and no duplicate rows |
| MED-03 | Library navigation and media actions | **SOURCE**: compact Library filter dialog and media-management/player code exist | Device-test open/play/rename/favorite/delete/share, metadata, indexing, navigation/back/close, persistence and no duplicates |
| MED-04 | Downloaded media tiles have clear icons/thumbnails and available metadata | **PENDING DEVICE** | Verify supported thumbnails, file type, title, duration/size/quality and useful fallback icons on real files |
| MED-05 | Stalled-download recovery and transfer integrity | **SOURCE / NOT RUN**: bounded result-stream timeout and recovery behavior exist in code history; no interrupted real transfer evidence | Interrupt a controlled transfer; show actionable state; retry/resume without duplicate/corrupt file; verify trusted checksum when supplied |
| MED-06 | Pause/resume/cancel/retry | **NOT RUN end-to-end** | Exercise all controls during an actual authorized test transfer and verify final state and cleanup |
| MED-07 | 360p–8K plus audio format picker | **SOURCE / NOT RUN** | Validate actual available formats per source, deduplication, unavailable-tier handling, correct selector and audio-only mode |
| UI-01 | Downloads and Library are organized, scrollable, non-duplicative | **SOURCE / PENDING DEVICE**: compact filter dialogs added by PR #40 | Scroll up/down on each screen; inspect empty/loading/error/completed states; confirm no duplicated action or media rows |
| UI-02 | Create menu workflows organized and functional | **PENDING DEVICE** | Exercise each displayed Create/Story/Status workflow; each action must finish with a usable saved output or a clear error |
| UI-03 | VidGravity logo, colorful modern navigation/icons/buttons | **PENDING DEVICE** | Inspect installed candidate in light/dark themes, correct logo, visual consistency, contrast and touch affordances |
| UI-04 | Status-bar-safe layout, theme and accessibility | **PENDING DEVICE** | Verify status/navigation insets, light/dark theme, font scaling, contrast, focus/semantics and usable touch targets |
| CAM-01 | Camera controls are one-hand reachable | **SOURCE / PENDING DEVICE**: lower thumb-zone controls described in PR #37 | Reach and operate controls one-handed on target phone, including small screen and landscape |
| CAM-02 | Camera capture, flash and quality | **PENDING DEVICE** | Capture photo/video; flash ON/OFF matches actual torch; quality selection affects output; flip/front/back camera works where hardware supports it |
| CAM-03 | Camera gallery saving | **NOT RUN** | Capture and verify media persists in Android Gallery and VidGravity Library after app restart |
| SHARE-01 | WhatsApp / Android Sharesheet / Share Inbox | **SOURCE / NOT RUN**: sharing implementation and CI checks exist | Share valid URI with permissions to WhatsApp and other targets; verify Share Inbox receives/imports media without exposing private paths |
| AND-01 | Foreground service stability | **PASS — limited CI only**; no device/background-kill evidence | Test long transfer, backgrounding, screen lock, notification action, app process pressure and Android-version behavior |
| AND-02 | Close/back button and all-screen scrolling | **PENDING DEVICE** | Test every screen, dialog, player, camera and export flow; back/close returns to correct prior screen; scrolling works throughout |
| HASH-01 | Checksum handling | **PASS for artifact hashes only**; media checksum parity unverified | Verify downloaded/exported media's server-provided digest against local SHA-256 and prove mismatch is surfaced |
| PWA-01 | Keep PWA unchanged | **SCOPE PRESERVED** in PR #40's observed file diff: changes are Android UI and CI workflow only; no PWA file change in this PR | Do not merge/deploy PWA changes as part of this candidate; re-check diff before any merge |

## Deployment / provider acceptance evidence

- `P170_RELEASE_ACCEPTANCE_REPORT.json` on the current candidate branch is dated 2026-10-10 and says `BLOCKED`, with 0/8 passed, 7 blocked and 1 not run. The current CI run generated preflight/acceptance reports without live network requests; do not treat those jobs as deployment acceptance. It records missing public HTTPS configuration, Docker CLI, yt-dlp executable, authorized live-provider URL, payment configuration, expected Android wrapper prerequisites and egress-isolation assertion; external probe was not run in that report.
- `P163_RELEASE_ACCEPTANCE_REPORT.json` explicitly states its provider tests used a controlled fake CLI and did not test a production HTTPS backend or real third-party download.
- The successful current-head CI run is useful automated evidence but is not a live export, provider-download, camera, or phone-level test.
- PR #40 remains a draft, stacked on the P0 removal branch. Do not merge PR #40 or deploy the candidate until all release-critical gates above pass.

## Release decision

**BLOCKED — not production-ready and not yet declared ready for device acceptance.** The current head has green Android CI and a matching downloadable debug APK with verified ZIP/raw checksums, but candidate Cloudflare preview builds failed, valid live Story export and Instagram download are unverified, the candidate revision is not confirmed deployed/healthy, and device-only navigation/camera/media acceptance remains pending. YouTube remains paused. PWA must remain unchanged.

Last reviewed: 2026-10-15 20:24 IST.


## 2026-10-15 20:24 IST incremental audit

- Current candidate head is `dd4beeac821fbebfa346451a06faa0819ba584cc` (commit: `Remove failure-report action from Downloads`).
- Exact-head PR-triggered workflows fetched from GitHub: run `38737183579` and bootstrap run `38737183490`, both completed successfully. The run's artifact metadata explicitly associates APK artifact `11973887892` with this branch and head.
- The current diff removes the `Copy failure report` action from Android Downloads. This is a source-level finding; runtime layout/order behavior still needs device acceptance.
- `P170_RELEASE_ACCEPTANCE_REPORT.json` is a stale 2026-10-10 report: 0 passed, 7 blocked, 1 not run; its external HTTPS probe was not run. It is not evidence of current live service health.
- No live Story export with a playable file and matching server/local SHA-256, no authorized Instagram file playback/hash acceptance, no current candidate deployment revision/health verification, and no physical-device evidence were found in this run.
- YouTube live tests remain paused. No PWA files were changed by this documentation-only update. No product merge or deployment was performed.
