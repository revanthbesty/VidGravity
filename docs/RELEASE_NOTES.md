# VidGravity Release Notes

## Android regression-fix candidate — 1.1.0-p180 (versionCode 180)

Status: **candidate only — build/CI/device verification pending**.

- Added a persistent build/feature ledger at `docs/BUILD_FEATURE_LEDGER.md`, with separate code, CI, APK, and physical-device verification states.
- Addressed the reported scroll regression by making the top-level screen host a neutral container and keeping scroll ownership in individual screens.
- Restored Home quick-action routing so StudyTok opens even when no media URL is pasted.
- Replaced dead-end creator cards with explicit video-picker entry points for sticker and WhatsApp Status workflows.
- Added visible video-quality selection beside camera capture controls and clearer flash on/off labels/state handling.
- Camera captures are published to the shared Android gallery on supported Android versions, with a clear fallback message if publication fails.
- Preserved configurable WhatsApp Status split targets of 30, 60, 90 and 120 seconds. The 120-second option remains a custom target and carries a compatibility warning.
- Added automated regression checks for build version, duration choices, creator entry points, camera controls, and ledger evidence policy.

This entry records intended code changes, not proof that the APK builds, features work on a physical phone, or a signed production APK exists. Follow CI and the feature ledger before calling any issue resolved.

## Android release candidate — 1.0.9-p179 (versionCode 179)

- Added a protected GitHub Actions workflow for producing a signed Android release APK and SHA-256 checksum when release-signing secrets are configured.
- Added fail-closed checks for missing signing configuration; no keystore or signing secrets are stored in the repository.
- This is release-pipeline preparation, not a claim that a signed production APK or physical-device certification is complete.

## Unreleased — Help Center foundation

- Added a searchable Help Center page for web/PWA with category filters and how-to, FAQ, and troubleshooting articles.
- Added a cross-platform User Guide for downloads, Library, playback, creator workflows, settings, and bug reports.
- Added FAQ/troubleshooting references for download failures, checksum issues, Android background work, playback, storage, and navigation.
- Established a mandatory documentation definition of done for every new feature.
- Content distinguishes intended workflows and possible causes from verified build behavior.
- Added an in-app Help & troubleshooting screen to Android Settings with offline guidance for common download, Library, playback, checksum, and background-service problems.

## Release-note policy

For each release, add date/build identifier, features, UI changes, bug fixes, compatibility notes, known issues, and links to updated guides. Do not claim a bug is fixed until the code change and relevant verification are complete.
