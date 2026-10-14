# VidGravity User Guide

> This guide describes intended workflows. Verify each workflow against the specific web/PWA, Android, or Windows build before calling it confirmed. Controls vary by platform and version.

## 1. Start here

- **Home:** inspect a supported media URL and see workspace activity.
- **Downloads:** monitor queued, active, completed, and failed jobs.
- **Library:** browse saved media, search, play supported files, and use available file actions.
- **Create / Creator tools:** access editing and export workflows enabled for the current platform.
- **Settings:** adjust supported preferences.
- **Help Center:** search how-to articles, FAQs, and troubleshooting.

## 2. Inspect a link and choose quality

1. Open Home or the URL inspection entry point.
2. Paste a complete URL from a supported source.
3. Select **Inspect** or **Analyze**.
4. Review the title and formats returned by the source.
5. Choose a listed video quality or audio-only format. The picker cannot create a resolution the source does not provide.
6. Start the download and open Downloads to monitor the job.
7. After success, open Library and confirm the item appears and plays.

If inspection fails, check the URL, connection, source accessibility, service support, and displayed error. Some services restrict cloud-hosted extraction.

## 3. Understand Downloads

States may include queued/enqueued, running, paused, blocked, succeeded, failed, or cancelled. Exact labels vary.

- Queued: waiting for a worker or network constraint.
- Running: processing or transferring.
- Progress may not change during analysis or while waiting for provider status.
- Failed: read the error before retrying.
- Pause/resume/cancel actions are build-specific; use controls actually shown.

### A download does not finish
1. Check the job state and error.
2. Confirm the device is online.
3. Check whether an unmetered connection is required.
4. Retry if the action is available.
5. If it repeats, record the exact error, approximate time, platform/build, and reproduction steps.

## 4. Library and playback

1. Open Library after the job succeeds.
2. Search by title if needed.
3. Open or play the item with the available action.
4. Use rename, favorite, share, or delete only if present in your build.
5. Confirm before destructive actions.

**Storage note:** browser/PWA and native Android storage are not automatically the same library unless a documented sync feature is enabled.

If a file is missing, confirm success, reopen Library, check file size, and retry if incomplete. Playback depends on supported container/codec and platform capability.

## 5. Creator and export tools

1. Open Create or the desired creator feature.
2. Select a source if required.
3. Choose an enabled action or preset.
4. Review settings and destination.
5. Run the operation and wait for its reported completion.
6. Verify output playback, duration, dimensions, and filename.

Some advanced functions require a native media engine. A control marked unavailable means that function is not active in that build.

## 6. Settings

Use the controls available in your version. If a setting does not persist, reopen Settings, check it, restart the app, and verify again before reporting a defect.

## 7. Platform notes

### Web / PWA
Browser storage is specific to that browser/profile and device. Private browsing, clearing site data, or storage pressure can remove local data. Processing may depend on the hosted service.

### Android
Permissions, notification settings, network conditions, and battery policies may affect background work. Device vendors can behave differently. Record Android version and exact service/notification errors.

### Windows
Follow controls in the installed build and its release notes. Do not assume Android background behavior or browser storage semantics apply.

## 8. Report a problem

Include platform/build version, device/OS where relevant, numbered reproduction steps, expected vs actual result, exact error, and whether it repeats. Never include passwords, API keys, tokens, cookies, private signed URLs, or unrelated personal information.

## 9. Known limitations

- Available quality depends on source formats and service support.
- Some sources may block cloud-hosted extraction.
- Web and native app libraries may use separate storage.
- Advanced editing functions can require a native media engine.
- CI success does not prove physical-device behavior; scrolling, playback, background work, and OEM behavior need real-device testing.

## Related help

- [Help Center](../site/help.html)
- [FAQ and Troubleshooting](FAQ_TROUBLESHOOTING.md)
- [Feature Documentation Standard](FEATURE_DOCUMENTATION_STANDARD.md)
- [Release Notes](RELEASE_NOTES.md)


## Android in-app Help Center

In the Android app, open **Settings → Help & troubleshooting → Open Help Center**. It includes quick guidance for inspecting links, choosing source-provided quality options, stalled downloads, missing Library items, playback, checksum/service errors, and safely reporting a problem. This built-in guide is available offline once the app screen is loaded.

The Help Center explains troubleshooting steps; it does not guarantee that a source, cloud service, codec, or device will support every workflow. For device-specific issues, use **Settings → Diagnostics & self-test → Run diagnostics**, then copy or share the report after removing any private information.


## Android Downloads screen: reduce clutter

Open **Downloads → Queue controls · cancel, retry & reports** to reveal bulk queue actions such as cancelling queued/active work, copying summaries, and retrying eligible failures. Keep this section collapsed when you only need to monitor individual downloads. The preference is remembered on the device. Individual download actions and progress remain in the download list.
