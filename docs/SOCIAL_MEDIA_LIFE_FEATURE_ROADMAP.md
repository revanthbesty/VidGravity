# VidGravity — Social Media Everyday-Life Feature Roadmap

**Status:** Pipeline / planned only — not implemented or verified by this document.  
**Date:** 2026-10-15  
**Delivery:** Android APK first; ₹0 additional-cost target; P0 reliability before enhancements; small tested batches; preserve existing SRS and platform scope. Keep PWA unchanged unless separately approved.

## Product goal

Serve two audiences through one local-first media utility:
- **Simple Mode:** save supported content for later, organize files, view, and share.
- **Creator Mode:** edit original or otherwise authorized media and prepare social-ready exports.

Target compatibility includes YouTube, Snapchat, Instagram, Facebook, WhatsApp and other social platforms, but support is capability-specific and must be tested. VidGravity is a companion utility, not a replacement for platform feeds, chat systems, comments, accounts, or official playback.

## Pipeline entries

All items below are **planned/backlog**. Reconcile IDs with the SRS and existing feature ledger before implementation; reuse existing modules rather than duplicating them.

| ID | Feature | Priority after P0 | Scope and acceptance evidence |
|---|---|---|---|
| VG-SM-001 | Universal Share Inbox | P1 after P0 | Receive supported text, URLs, images, video, audio and multiple selected files via Android Sharesheet/intents. Preview and confirm before processing. Test ACTION_SEND/ACTION_SEND_MULTIPLE, MIME types, malformed and oversized input. |
| VG-SM-002 | Save for Later / Link Shelf | P1 after P0 | Save URL, title when available, user note, tags, date and Watch Later state locally. Test restart persistence, invalid links, search/filter and backup/export. |
| VG-SM-003 | Smart Media Vault | P1 after P0 | Unified local library with thumbnails, duration, size, format, resolution, tags, search, albums, duplicate hints and safe temporary-file cleanup. Test indexing, restart, permissions, false-positive duplicates and deletion confirmation. |
| VG-SM-004 | Create Once, Share Anywhere | P2 | Hand off user-owned/authorized exports via Android's native Sharesheet; do not claim a post succeeded just because the chooser opened. Test common MIME types and target-app behavior on-device. |
| VG-SM-005 | Social Export Presets | P2 | Configurable presets for YouTube Shorts, Instagram Reels/Stories, Snapchat, Facebook Reels, WhatsApp Status and generic vertical/square/landscape sharing. Validate dimensions, codec, duration, size and real target-app playback/upload before claiming compatibility. |
| VG-SM-006 | Quick Photo Studio | P2 | Local crop, rotate, resize, simple filters, collage, text/stickers, thumbnails and optional watermark. Test quality, metadata policy, large-image memory, cancel/undo and output integrity. |
| VG-SM-007 | Quick Video Studio | P2 | Local trim/split/merge where supported, speed, mute/audio selection, simple text/title cards, preview and draft recovery. Preserve originals. Test duration, codec, audio sync, cancellation and Library output. |
| VG-SM-008 | Captions & Post Pack | P2 | Editable caption/title/hook/description/hashtag suggestions and one-tap copy. AI optional. Test Unicode/languages and copy/paste; no automatic posting. |
| VG-SM-009 | Chat & Comment Draft Helper | P2 | User manually shares/pastes selected text to rewrite, shorten, translate or change tone, then copies the draft back. No auto-send, background DM reading, contact scraping or account login. |
| VG-SM-010 | Privacy Shield | P2 | Optional location-metadata removal, crop, manual blur/redaction and pre-share review. Test output metadata, original preservation, undo and redaction workflow. Detection models require a verified local model or explicit online consent. |
| VG-SM-011 | Audio & Voice Note Tools | P2 | Trim/convert supported audio, normalize where practical and optional user-initiated transcription with a verified local/free engine. Test codecs, playback, transcript quality before claiming language support, and no silent upload. |
| VG-SM-012 | Watch Later & Universal Player | P2 | Local playlists, resume, speed, subtitles when available, favorites, open/share and missing-file recovery. Open external social links in official apps/sites rather than bypassing platform playback. |
| VG-SM-013 | Story/Status Planner | P2 | Create and reorder clips/cards, preview, save drafts and export. Preserve existing 30/60/90/120-second split options. User posts manually. Test durations, order, audio sync, cancellation and output files. |
| VG-SM-014 | Multilingual & Accessibility Studio | P2/P3 | English and selected Indian languages, caption editing, screen-reader labels, contrast and one-handed controls. Add languages only after font/rendering and quality tests. |
| VG-SM-015 | Smart Batch Actions | P3 | Batch rename/tag/convert/export, queue presets, storage estimates and recoverable cleanup. Test partial failures, cancellation and non-destructive behavior. |
| VG-SM-016 | Optional AI Creator Copilot | P3 | Local/rule-based assistance first; optional online AI for caption/storyboard ideas, translation and edit suggestions. Confirm genuinely free availability, quotas, data disclosure, opt-in consent and offline/manual fallback. |
| VG-SM-017 | Creator Checklist & Reminders | P3 | Local checklist for caption, cover, aspect ratio, audio and privacy; optional local reminder. No unsupported auto-posting or scheduler claims. |
| VG-SM-018 | Platform Capability Registry | P1/P2 foundation | Tested matrix by platform and input type: shared URL/file, deep link, import/export support, restrictions and last-tested app/version. Unsupported actions must be disabled or labelled clearly. |

## Recommended batch order after P0

1. **Batch A — Share Inbox foundation:** VG-SM-001 + minimal VG-SM-018 registry.
2. **Batch B — Save and find:** VG-SM-002 + basic VG-SM-003 local search/indexing.
3. **Batch C — Share out:** VG-SM-004 + VG-SM-005; start with generic exports and validate WhatsApp Status / Instagram-style vertical outputs on-device.
4. **Batch D — Local creation:** VG-SM-006 + VG-SM-007 + VG-SM-013; reuse Create Studio and Status Splitter.
5. **Batch E — Text and privacy:** VG-SM-008 + VG-SM-009 + VG-SM-010, useful without online AI.
6. **Batch F — Audio, viewing and localization:** VG-SM-011 + VG-SM-012 + VG-SM-014.
7. **Batch G — Automation and optional AI:** VG-SM-015 + VG-SM-016 + VG-SM-017 only after free-service and privacy feasibility is demonstrated.

## Platform, privacy and budget guardrails

- Prefer official Android sharing intents and the native Sharesheet; let users review incoming content before action.
- Platform names are compatibility targets, not permission to bypass access controls. Process/download only content the user owns, is authorized to use, or the platform explicitly permits.
- Do not use YouTube integration/API paths to enable unauthorized offline downloads, audio separation, ad blocking or background playback. If a download is not authorized, offer a bookmark or open the official app/site.
- Do not scrape private chats/comments/contacts, capture credentials/cookies, automate likes/views/comments, or auto-send/post without an officially supported, explicitly authorized integration.
- Local-first by default. Online AI or transcription must be opt-in, disclose data leaving the device, and preserve a non-AI/manual path. No paid APIs, mandatory accounts or paid infrastructure under the ₹0 target.
- Originals remain unchanged by default. Deletion/overwrite requires explicit confirmation; never upload private media silently.

## Definition of done per batch

- Requirement ID, scope, platform, dependencies and tests recorded before implementation.
- Isolated branch and small change set; do not add these enhancements to the P0 bug-fix PR.
- Targeted tests and exact-head CI results attached.
- For APK changes: version/versionCode, exact commit, artifact URL, size and SHA-256 recorded; install and test on a physical Android device.
- Platform compatibility claims backed by platform/app-version-specific device smoke tests.
- Master tracker and feature ledger updated; Planned, Coded, CI passed, APK artifact verified, Device verified and Released remain distinct states.
