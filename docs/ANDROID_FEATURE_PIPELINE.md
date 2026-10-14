# VidGravity Android Feature Pipeline

Scope: native Android APK first. Keep the PWA/web scope unchanged unless explicitly requested.
Last updated: 2026-10-14

## Priority policy

Work in this order: release-blocking stability and user-reported defects first, core download/library workflows second, creator features third, AI and expansion features after the core acceptance gates pass. Do not mark a feature complete based on a code commit alone: require CI, APK artifact, and device validation where applicable.

## P0 — Release blockers / immediate

- [ ] Build and regression-test a fresh APK from the latest feature branch commits.
- [ ] Validate Download → progress → cancel/pause/resume/retry → integrity verification → Library publication.
- [ ] Fix the YouTube inspection/download failure path and ensure provider restrictions/errors are shown honestly.
- [ ] Verify quality picker options are deduplicated and show available resolutions (360p, 480p, 720p, 1080p, 1440p, 4K/8K when actually available), plus audio-only when supported.
- [ ] Verify player fullscreen/landscape playback and Library Play/Share actions.
- [ ] Validate Camera/Snap Studio shutter and settings produce real saved media, not placeholder output.
- [ ] Check Downloads and Library layout on a physical Android device: no cramped horizontal controls, scroll works, actions are understandable.
- Acceptance: CI green, APK artifact available, no crash in the critical path, on-device smoke test recorded.

## P1 — Core experience polish

- [x] Preserve the fixed vertical scrolling in Downloads and Library.
- [x] Group secondary Downloads reports/history actions in a collapsible section.
- [x] Group Library report-sharing actions in a collapsible section.
- [x] Add bulk favorite/unfavorite for selected Library items.
- [x] Add expandable Library storage insights (media counts and largest file).
- [ ] Verify all P1 UI changes compile and pass regression tests in the next APK.
- [x] Add one-tap Library quick filters for recent media (last 7 days) and large files (>100 MB), with regression coverage.
- [ ] Improve empty/loading/error states and make horizontal filter/sort controls easier to discover and operate one-handed.
- [ ] Ensure completed downloads appear in Library after returning to the screen and after app restart.

## P2 — Creator media pipeline

- [ ] Real 9:16 Story/Status export from source media.
- [ ] Configurable WhatsApp Status splitting at 30/60/90/120 seconds.
- [ ] Audio-only Story Cards with waveform/cover and reliable audio export.
- [ ] Animated WebP sticker creation and publication to the correct media collection.
- [ ] Story templates, captions, thumbnails, and recoverable editing drafts.
- [ ] CameraX capture with working shutter/settings and saved output.
- Acceptance: verify actual output files, duration, orientation, MIME type, and Library indexing.

## P3 — Learning, privacy, and sharing

- [ ] StudyTok draft/practice persistence and export verification.
- [ ] Privacy blur/redaction controls and confirmation that exported media applies them.
- [ ] Share Inbox import, deduplication, and Watch/Learn Later collections.
- [ ] Validate bulk sharing and Android URI permission grants across target apps.
- Acceptance: persistence across app restart and real import/export tests.

## P4 — AI creator toolkit and expansion

- [ ] AI-assisted captions, summaries, hooks, and storyboard suggestions.
- [ ] AI editing workflow integrated with actual media processing (not UI-only controls).
- [ ] DuoCam and additional effects/templates after core camera and export paths are stable.
- [ ] Accessibility, Tamil/localization, wellbeing controls, and deeper customization.
- Acceptance: feature-level tests, permission/privacy review, and performance checks.

## P5 — Release readiness

- [ ] Run Android build and all relevant regression suites.
- [ ] Publish fresh APK artifact and verify it can be downloaded.
- [ ] Install on physical Android device and test the P0 acceptance checklist.
- [ ] Record known limitations and build commit SHA alongside the artifact.
- [ ] Do not describe debug APK or automated CI as production release certification.

## Current next action

P0 comes before expanding P2/P3/P4. The latest commits for collapsible Downloads/Library tools, bulk favorites, and Library storage insights must be included in one green Android build before new creator features are treated as the next release candidate.
