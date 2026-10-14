# VidGravity Release Notes

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
