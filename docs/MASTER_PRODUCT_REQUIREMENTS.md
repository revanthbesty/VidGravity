# VidGravity Master Product Requirements
Last reviewed: 2026-10-13
Status: living implementation specification; requirements are not considered shipped until code, tests, and release validation prove them.

## Product goal
Build VidGravity as a useful, privacy-respecting, cross-platform media companion for downloads the user is authorized to save, offline organization, accessibility, safe creative editing, learning, and social-ready exports. Prioritize stable daily workflows before novelty features. Android, web/PWA, browser companion, and desktop must share typed contracts and domain behavior where appropriate.

## Engineering non-negotiables
- Never execute arbitrary shell strings. Use typed argument arrays, allow-listed operations, bounded inputs, and safe temporary paths.
- Never mutate original media. Every edit/export is a new derivative with source provenance and cleanup policy.
- Server-authoritative licensing/entitlements; local UI flags never grant premium rights. Current beta/testing experience should not force a purchase flow unless explicitly approved.
- Atomic task lifecycle: queued, preparing, running, paused, retry-wait, completed, failed, cancelled; cancellation/retry/pause must not corrupt published files.
- Stream to temporary .part files, calculate SHA-256 locally while streaming, and persist checksum only after successful verification/publish. Do not demand a server checksum from public streaming sources that do not provide a trusted receipt.
- Honest progress/speed/ETA based on actual bytes and elapsed time; never fabricate progress.
- Platform permission minimization, secure defaults, WCAG 2.2 AA intent, adaptive dark/light/system themes, readable contrast, 48–56dp minimum touch targets, haptics only when useful and user-controllable.
- No claims of guaranteed screenshot detection, private cross-app gesture detection, unlimited cloud/AI, or successful deployment without evidence.
- Respect platform terms, copyright, DRM, authentication and access controls. Do not bypass paywalls, DRM, or private access.

## Architecture and shared contracts
Use UI -> domain/application -> services -> repositories/API; no direct HTTP calls from UI. Keep Android-native CameraX/Compose/Media3/WorkManager code under apps/android; reusable media processors and typed FFmpeg argument construction under engines/media; download/source analysis under engines/download; platform-independent DTOs, validation and capability contracts under packages/contracts. Avoid duplicated root/site frontend logic where feasible.

## User journeys and features
### A. Download core
- Public/authorized URL inspection; source/platform identification and capability-aware format list.
- Resolutions from 360p through available 8K, exact available streams, codec/container/FPS/dimensions/known size; do not invent unavailable variants.
- Audio-only extraction where supported (MP3 128/320 kbps and AAC as actual conversion targets, not mislabeled streams).
- Highest available, user-selected quality, saved default quality; video+audio merge when source provides separate streams.
- Multi-URL/batch queue, configurable concurrency, duplicate protection, retry, cancel, pause/resume where backend supports true resumable transfer, .part recovery and safe filenames.
- WorkManager foreground-service handling for Android 14+, permission-aware notifications, non-expedited fallback and lifecycle-safe cleanup.
- Real task cards: thumbnail when available, source, progress, bytes, speed, ETA, pause/resume/cancel, expandable useful errors and retry.
- Completed media: play, favorite, rename, delete confirmation, share sheet, open file, remix/export actions.
- Keep server-reported integrity receipts distinct from locally calculated hashes.

### B. Android shell and daily ergonomics
- Five bottom tabs: Home, Downloads, Library, Create, Settings; state/back-stack restoration, system bar insets, accessible touch targets, adaptive theme.
- Collapsible visual header, content-safe status/navigation bar padding, natural-reach primary controls, adjacent-screen swipe only when it does not conflict with child gestures.
- Clipboard link suggestion on resume, shown transparently and never auto-download; user explicitly taps to inspect/paste.
- Quick actions: best available video, audio-only, WhatsApp/status splitter, StudyTok. Unavailable actions must be disabled or clearly marked rather than pretending to work.
- Now Grabbing task carousel with genuine live status and pause/cancel; Recently Stashed carousel from real local library records.

### C. Format picker
- Reachable Material 3 bottom sheet; video/audio and audio-only grouping; include only streams actually returned by extractor.
- Show resolution, dimensions, FPS, codec/container, file size only if known; estimate label when estimated.
- Saved automatic quality selection, safe dismissal and accessible controls.

### D. Library and privacy
- Search, categories: all media, video, audio, moodboards, GIF/stickers, secure vault; duration/spec overlays, playlists, multi-select, batch favorite/re-categorize/delete.
- Helpful empty state and route back to Home.
- Biometric vault using Android BiometricPrompt, encrypted metadata/key handling, lock on background, threat-modelled recovery; no fake FaceID on Android.
- Three-finger masquerade is optional and must not replace real vault locking; clear safety/false-trigger handling.
- Local DropZone/P2P with explicit consent, QR/invite, peer verification, local-network permission disclosure, encrypted transfer, checksums, expiry and no accidental public exposure.
- “Unlimited memories” means user-controlled local/LAN/P2P storage, not unlimited free cloud storage.

### E. Create / Snap Studio
- CameraX photo capture and vertical recording up to platform/device capability; dual front/rear concurrent camera only when hardware supports it. Provide fallback PiP/split layouts.
- **DualView / DuoCam fun capture mode:** record front and rear cameras in one session when the device supports concurrent camera streams; offer side-by-side, picture-in-picture, and front-camera reaction overlay layouts. Probe CameraX concurrent-camera capabilities first and gracefully fall back to front-only/rear-only capture or a clearly labeled sequential two-take mode; never promise simultaneous capture on unsupported devices.
- **Lens Lab:** live front/rear preview effects such as color looks, soft glow, retro/VHS, comic, mirror, background blur where supported, face-safe AR stickers/masks, and adjustable intensity. Separate real-time camera effects from post-capture effects; use GPU/device capability checks and provide a no-effects fallback to avoid overheating or dropped frames.
- **Reaction Remix:** combine the two recorded angles, choose which view is primary, resize/reposition the reaction window, switch layouts at edit points, trim each track, and export a new vertical or landscape derivative. Preserve originals and let users preview before export.
- **Fun capture controls:** timer, countdown, gesture/voice trigger only when explicitly enabled, flash/torch control where supported, exposure/zoom controls, grid, stabilization where available, and optional short intro/outro templates.
- **Privacy and safety:** camera/microphone permissions only when the user starts capture; visible recording state and stop control; no hidden/background recording; no face identification or biometric identity inference. Keep effects processed on-device where feasible, disclose any remote processing, and default to local drafts until the user chooses Share.
- **Acceptance tests:** test concurrent-camera capability detection across supported/unsupported devices, audio sync, rotation and lifecycle interruptions, thermal/performance fallback, permission denial, save/cancel cleanup, original-file preservation, accessibility, and export metadata.
- Caption canvas: draggable text, styles/outlines, timestamp/battery/velocity badges with opt-in; transcript-based prompts only when transcript exists.
- Frame grab from player scrubber; 9:16 exports with blurred background fill for landscape source; original immutable.
- Story Card exports with chosen snippet, artwork, waveforms/subtitles and safe subtitle formats; export presets validated against current platform rules.
- Snap & Forget 24-hour derivative-cache expiry is opt-in, visible, and does not delete original/source without confirmation.
- First Draft silence trim, audio replacement, split clips, sticker/GIF/WebP output, Vibe-Sync beat cuts/crossfades, Cine-Vibe transitions, soundboard/soundbite overlays, hardware-aware compression and voice-dub subtitles are separate processors with previews, progress, cancellation and bounded resource use.
- Never promise “85% smaller” or “almost no loss” for all inputs; report measured output size and quality settings.

### F. Social export presets and platform compatibility
- Presets for WhatsApp Status, Instagram Reels/Stories, TikTok, YouTube Shorts, Discord/other share targets; preserve a platform-spec registry with checked date, official source URL, constraints, and distinction between native-app and publishing-API limits.
- Validate duration, aspect ratio, resolution, codecs, frame rate, file size and audio. Provide warnings/alternate export rather than guaranteeing acceptance.
- Use Android Sharesheet/deep links where official integrations are unavailable. Do not scrape private feeds or automate uploads without approved APIs and user consent.
- Copyright warning for licensed music and claimed content; allow users to use their own/licensed audio.

### G. StudyTok and AI assistance
- Transcription and timestamped structured flashcards {front, back, timestamp}; export CSV/Markdown/Anki-compatible files.
- Silence skipper with user-adjustable threshold, safe playback-speed ramp and manual override.
- 60-second recap/lecture highlights only when content/transcript supports it; no fabricated quotes.
- Capture frame + associated transcript quote; export notes.
- Vibe Companion modes/templates (Sarcastic, Aesthetic, Focus, Brain-Rot), summaries, bio drafts and captions; on-device/free/local model option where feasible, disclose cost/network/privacy for remote inference, quotas and consent. No claim of unmetered high-end remote AI.

### H. Group stash and collaboration
- Invite links/QR for shared folders/playlists; explicit consent and authorization before retrieving media.
- Queue shared public/authorized links for Wi-Fi/off-hours; configurable Wi-Fi-only, charging, time window and data caps; respect source terms and rate limits.
- Series/seasons/episodes organization, local-first collaboration and conflict-safe sync.
- “Ghost P2P Map” should be proximity-only and consent-based; never track precise locations without explicit permission. Prefer peer discovery over map when it adds no utility.

### I. Ephemeral sharing / Peek / replay
- Peek gesture detection only inside VidGravity-owned UI. Never claim to observe Snapchat/WhatsApp private gestures or alert a sender about another app's private interaction.
- View-once flow: recipient/session authorization, one-time random key, authenticated encryption, expiry, replay attempt journal, bounded telemetry, sender notification only if backed by a server-delivered receipt.
- Android FLAG_SECURE is a mitigation, not guaranteed capture detection. Do not claim screenshots or external recordings were detected unless platform supplies a reliable event.
- Validate all paths/inputs before consuming session allowances; session counters need server/transactional authority for multi-device enforcement. Best-effort file deletion is not secure erase on flash storage.

### J. Settings and retention
- 15 categories: Account, Security, Privacy, Downloads, Playback, Library, AI, Automation, Devices, Storage, Network, Notifications, Appearance, Advanced, About.
- License key paste with validation and server-authoritative entitlements; copyable device ID; do not expose secrets in logs.
- Overnight scheduler (e.g. 2–5 AM), Wi-Fi-only, charging constraints, storage caps and explicit notification controls.
- Compression policies, older-than-14-day review, optional auto-cleanup of generated status derivatives after 24 hours, no silent deletion of originals.
- System/dark/light themes, text scaling, backup/restore directory routes and accessible contrast.

### K. Additional creative / lifestyle utilities
- Sound Snatcher to extract a short user-selected clip as ringtone only with Android user-mediated ringtone/contact permissions.
- Aura/streak/trivia and weekly recap are optional, privacy-first, local-first and opt-in; no manipulative dark patterns or surveillance of “doomscrolling”.
- Receipts/argument finder, if implemented, must search only user-selected local media/transcripts and present timestamped evidence with uncertainty, never infer facts unsupported by source.
- Hover PiP only through supported Android PiP or explicit overlay permission; no hidden overlays. Message Scissors works only on user-provided/authorized media and shares via standard intents.

### L. Gen Z, students, and younger users (new priority)
Design this as an age-aware experience within VidGravity, not a separate product. Make the core downloader reliable first, then add small, independently testable learning and creativity workflows.
- **Age-appropriate onboarding:** offer simple experience choices such as Creator, Student, or Everyday use; do not require date of birth or collect a child’s identity just to personalize the UI. Any age gate required by law or distribution policy must be handled transparently and minimally.
- **Study mode:** turn user-selected, authorized lecture recordings or notes into timestamped summaries, flashcards, revision questions, vocabulary lists, and exportable study notes. Always link generated notes back to source timestamps; flag uncertain transcription and never invent facts.
- **Micro-learning:** optional 5–15 minute sessions, spaced-repetition review, progress indicators, and locally stored goals. No streak-loss punishment, shame messages, or pressure to keep using the app.
- **Creator starter kit:** beginner-friendly templates for captions, vertical edits, thumbnails/story cards, subtitles, sticker/GIF creation, and audio cleanup. Provide previews, undo, clear export settings, and original-media preservation.
- **Safe social sharing:** default to private/local drafts; explicit preview and confirmation before sharing; show destination and included media; use the OS share sheet; no public profile, contact upload, location sharing, or auto-posting by default.
- **Younger-user privacy and safety:** collect the minimum data; keep learning history and recommendations local by default where practical; no targeted ads, behavioral profiling, hidden tracking, or precise-location features aimed at children. Remote AI must be optional, clearly disclosed, consent-based, and governed by age-appropriate policy and applicable law. Provide easy clear-history/delete controls.
- **Healthy-use tools:** optional focus timer, study breaks, bedtime quiet hours, and user-controlled reminders. No invasive usage monitoring or claims to detect activity in other apps.
- **Accessibility and inclusion:** plain-language labels, optional Tamil and other localized UI/content when supported, adjustable text size, captions, reduced-motion option, keyboard/screen-reader support, and calm visual mode alongside expressive themes.
- **Trustworthy AI:** label generated text/media suggestions as AI-assisted; allow edit/reject; never fabricate quotes, answer keys, citations, or a person's identity. Explain when content is processed on-device versus sent to a remote service.
- **Child-safety release gate:** before marketing or enabling child-directed experiences, review applicable child privacy, consent, advertising, age-rating, and app-store requirements for target markets. Until that review is complete, do not claim the app is certified or compliant for children.
- **Acceptance tests:** test age-aware onboarding without unnecessary personal data, default-private exports, cancel/delete history, no auto-share, offline study basics, accessibility settings, and AI failure/uncertainty states. Mark each capability as implemented only after real behavior and tests exist.

## Delivery phases / acceptance gates
1. Build health and contract audit: inspect actual repo state, branches, workflows, duplicate frontend logic, tests and release blockers.
2. Download reliability: atomic state machine, worker lifecycle, integrity tests, progress metrics, pause/resume semantics and regression tests.
3. Android navigation and Home: insets, clipboard prompt, format picker, live queue and accessibility tests.
4. Library and completed actions: durable metadata, safe rename/delete, player, share, favorites and search.
5. Media export engine: typed FFmpeg args, immutable source, presets/spec validator, cancellation and fixtures.
6. StudyTok and creative tools: small independently tested processors, capability detection, graceful unsupported states.
7. Vault, ephemeral shares and P2P: threat model, security tests, key/session lifecycle and opt-in permissions.
8. Settings, automation, accessibility and retention.
9. Gen Z/student/youth UX: privacy-first profiles, StudyTok review flows, creator templates, localization and healthy-use controls; complete child-safety/legal review before any child-directed marketing.
10. Dual-camera fun capture: capability detection, Lens Lab, Reaction Remix, sync/performance and device-matrix validation.
11. Release validation: unit/integration/UI tests, Android build, install/device smoke test, APK artifact, web/Worker deployment checks and rollback notes.

## Definition of done for every feature
- User story and capability constraints documented.
- UI state is connected to real domain/service behavior; no fake/demo success messages.
- Inputs validated; errors are actionable; cancellation and cleanup tested.
- Original media preserved.
- Unit/integration tests added and CI passes; platform-specific manual/device tests identified.
- Accessibility and dark/light behavior checked.
- Security/privacy implications and required permissions disclosed.
- Current social platform constraints checked against official documentation and date-stamped.
- Status accurately reported as implemented, partial, blocked, or not started.
