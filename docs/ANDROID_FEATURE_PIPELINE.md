# VidGravity Android Feature Pipeline

Scope: native Android APK first. Keep the PWA/web scope unchanged unless explicitly requested.
Last updated: 2026-10-15

Build and regression history is tracked in [BUILD_FEATURE_LEDGER.md](BUILD_FEATURE_LEDGER.md). Current candidate: p180 / 1.1.0-p180; its CI, artifact and device validation are pending.

## Priority policy

Work in this order: release-blocking stability and user-reported defects first, core download/library workflows second, creator features third, AI and expansion features after the core acceptance gates pass. Do not mark a feature complete based on a code commit alone: require CI, APK artifact, and device validation where applicable.

## Production APK gate — p179

- Android app version is now `1.0.9-p179` (versionCode 179); this is a release candidate version, not proof of production readiness.
- Standard CI continues to produce a debug APK for quick device testing.
- A separate protected workflow, `.github/workflows/android-signed-release.yml`, builds a signed release APK and a SHA-256 checksum only when the signing secrets are configured.
- Required GitHub Actions repository or environment secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Store the Base64-encoded keystore securely; never commit the keystore or expose the secret values in logs/chat.
- Release workflow failure due to missing signing secrets is an intentional fail-closed gate. Do not distribute an unsigned APK as production.
- The signing keystore must be the stable release key for this application. Back it up securely before first production distribution; losing it can prevent updating installations signed with that key.
- Signing configuration and workflow are code-ready only after their own CI validation. A signed APK still needs installation/upgrade tests and the P0 on-device acceptance checklist.

## P0 — Release blockers / immediate

- [ ] Build and regression-test a fresh APK from the latest feature branch commits.
- [ ] Validate Download → progress → cancel/pause/resume/retry → integrity verification → Library publication.
- [ ] Fix the YouTube inspection/download failure path and ensure provider restrictions/errors are shown honestly.
- [ ] Verify quality picker options are deduplicated and show available resolutions (360p, 480p, 720p, 1080p, 1440p, 4K/8K when actually available), plus audio-only when supported.
- [ ] Verify player fullscreen/landscape playback and Library Play/Share actions.
- [ ] Validate Camera/Snap Studio shutter and settings produce real saved media, not placeholder output.
- [ ] Re-test explicit flash on/off, visible video-quality control, photo capture, video recording, dual-camera fallback and gallery publication.
- [ ] Check Downloads and Library layout on a physical Android device: no cramped horizontal controls, scroll works, actions are understandable.
- [ ] Regression-test StudyTok and Share Inbox/WhatsApp handoff without requiring a media URL on Home.
- Acceptance: CI green, APK artifact available, no crash in the critical path, on-device smoke test recorded.

## Master roadmap scope

The native Android APK is the active release target. Keep PWA/browser and desktop source unchanged unless explicitly requested. Track each feature as discussed, implemented, CI-tested, APK-verified, and device-verified; these are distinct statuses.

- **Downloader:** URL intake, source inspection, available format selection, video/audio transfer, queue, progress/speed/ETA, pause/resume/cancel/retry, Wi-Fi-only option, checksum verification, partial-file recovery, honest provider errors, and completed-media publication.
- **Downloads & Library:** searchable/filterable/sortable collections, playback, rename/delete/share, favorites and bulk actions, storage insights, quick filters, reports, persistent history, and correct Android URI grants.
- **Player:** fullscreen/landscape, seek/resume, frame capture, and safe player-to-creator handoff.
- **Creator Studio:** 9:16 and other social presets, configurable Status segments, captions/title/artist, thumbnails, audio Story Cards, animated WebP stickers, drafts/undo-redo, and verifiable export publication.
- **Camera:** CameraX photo/video capture, settings, permission-aware audio, orientation and saved-media verification; DuoCam/effects after capture is stable.
- **StudyTok & Swipe Feed:** local draft/practice persistence, flashcards, resume state, vertical feed, favorites, and creator shortcuts.
- **Inbox & privacy:** shared-media import, deduplication, Watch/Learn Later, URI permission validation, real privacy blur/redaction, and privacy-safe diagnostic reports.
- **AI:** transcript/scene search, captions, summaries, hooks, storyboards, highlight suggestions and editing plans that are applied by actual media processing.
- **Quality & accessibility:** diagnostics/self-test, recovery engine, readable error states, accessible touch targets/TalkBack, font scaling, optional Tamil localization, security checks and a versioned release checklist.
- **Product operations:** Android APK artifacts and versioning, CI, deployment health checks, licensing/payment security where enabled, and a known-limitations report. Avoid duplicating existing feature implementations; verify them first.

## P1 — Core experience polish

- [x] Preserve the fixed vertical scrolling in Downloads and Library.
- [x] Group secondary Downloads reports/history actions in a collapsible section.
- [x] Group Library report-sharing actions in a collapsible section.
- [x] Add bulk favorite/unfavorite for selected Library items.
- [x] Add expandable Library storage insights (media counts and largest file).
- [ ] Verify all P1 UI changes compile and pass regression tests in the next APK.
- [x] Add Settings diagnostics for connectivity, backend health endpoint, app storage, Library indexing, app version, and a copyable report (source/test committed; APK/device verification pending).
- [ ] Improve Creator Studio draft recovery and undo/redo without duplicating StudyTok draft persistence.
- [ ] Accessibility pass: minimum touch targets, TalkBack descriptions, font scaling, contrast, predictable back/scroll behavior; consider Tamil localization.
- [x] Add one-tap Library quick filters for recent media (last 7 days) and large files (>100 MB), with regression coverage.
- [ ] Improve empty/loading/error states and make horizontal filter/sort controls easier to discover and operate one-handed.
- [ ] Ensure completed downloads appear in Library after returning to the screen and after app restart.

## P2 — Creator media pipeline

- [ ] Real 9:16 Story/Status export from source media.
- [x] Configurable WhatsApp Status splitting at 30/60/90/120 seconds is present in source, with a warning that 120 seconds is a custom target and may not fit one standard WhatsApp Status item.
- [ ] Regression-test all four WhatsApp Status split durations on the p180 APK; code presence is not device verification.
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
