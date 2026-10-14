# VidGravity FAQ and Troubleshooting

Treat the exact error in your installed build as the source of truth. Causes below are possibilities until confirmed.

## Downloads

### Why is a resolution missing?
The source may not provide it, or the service may not expose a compatible stream. “Best available” means the best compatible option found, not a guarantee of 4K/8K.

### A download stays queued
Check connectivity and whether an unmetered connection is required. A queued job may be waiting for worker capacity or constraints.

### Progress is stuck at 0%
The worker may be analyzing the source or waiting for provider status. Check the job state and error details. Report repeated zero progress with the exact error.

### The download failed
Read the error first. Check URL validity, source availability, internet connection, and network restrictions. Retry if supported; if it repeats, report sanitized details.

### Checksum verification failed / media was not published
The file failed integrity verification or the required checksum receipt was absent. Do not treat an unverified file as successfully published. Retry and report repeated errors.

### Android background download stopped
Check notification permission, connectivity, battery/background restrictions, and Android version. Record foreground-service exceptions verbatim for engineering review.

## Library and playback

### A completed file is missing from Library
Confirm the job says succeeded, reopen Library, and check whether the platform stores media separately. Browser and Android app storage do not automatically synchronize.

### A video appears but will not play
Check completion and non-zero size. Reopen it and retry if corrupted. Container and codec support depends on the player.

### I cannot find the file on another device
Files saved in browser or native app storage are local unless a supported cloud-sync feature is implemented.

## Creator tools

### An effect or audio tool is unavailable
Some functions require a native media engine or platform support. If marked unavailable, the feature is not active in that build.

### Export output is wrong or missing
Confirm source, preset, and completion state. Verify output duration, size, and playback. Record selected settings and errors if it repeats.

## UI and navigation

### A screen does not scroll
Try a vertical swipe over the content area and check whether a modal is open. If scrolling fails across screens, record device/OS, screen name, and a short screen recording. This is a defect to investigate, not a user mistake.

### Menus are crowded or actions are hard to find
Record the screen and the action you expected. Common actions should be prioritized and advanced/reporting actions grouped under clearly labelled secondary controls.

### The app crashes or shows a blank screen
Reopen once. If repeatable, record platform/build, OS version, screen, and last action. Preserve locally stored files you need before uninstalling.

## Network and privacy

### The service is unavailable
Check internet access and retry later. If persistent, record timestamp and exact error. Never send tokens, cookies, credentials, or private signed URLs in bug reports.

### Can I download any media URL?
No. Support depends on source, access permissions, service capability, and applicable terms and laws. Download only content you are authorized to save.

## Bug report checklist
1. Platform and app/build version.
2. Device/OS version if relevant.
3. Reproduction steps.
4. Expected vs actual behavior.
5. Exact error and approximate time.
6. Whether it reproduces consistently.
7. Remove credentials and private data.


## Android in-app Help Center

### Where do I find help in Android?
Open **Settings → Help & troubleshooting → Open Help Center**. Use the quick guides first, then run **Settings → Diagnostics & self-test** if the issue persists.

### The Help Center says to retry, but the download still fails
The guide provides safe first steps, not a guaranteed fix. Check the exact download error, network state, Wi-Fi-only preference, and service availability. If repeated, capture a diagnostics report and reproduction steps. Do not share API tokens, cookies, passwords, or private signed URLs.

### Does the Help Center mean Android behavior has been verified?
No. Automated builds and documentation checks cannot verify scrolling, playback, or background execution on every physical device. Those still need installation and real-device testing.
