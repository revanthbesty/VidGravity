# D24/D33 and FR-PLAYER implementation contract

## Status
This change adds typed contracts, a guarded task lifecycle, a React Home screen, a capability-reporting player hook, and native Rust validation primitives. It does not claim the native downloader, FFmpeg runner, GPU shader pipeline, loudness engine, transcript provider, or Ask My Media service is wired or validated. Connect these contracts to the existing runtime before enabling controls.

## Downloader invariants
1. Accept only typed FFmpeg operations. Convert operations to process arguments using a fixed allowlist; never concatenate arbitrary shell strings.
2. Treat source metadata as untrusted. Permit configured HTTP(S) providers only, validate redirects, deny loopback/private/link-local destinations unless explicitly permitted by policy, cap redirects and metadata size, and apply timeouts.
3. Resolve an opaque destination ID to a canonical sandbox directory in native code. Reject traversal and verify the canonical parent immediately before creation.
4. Write to a uniquely named temporary file in the same destination filesystem. Flush and close it, verify expected hash when supplied (always compute SHA-256 for local integrity), then atomically rename. COMPLETED requires successful verification and rename.
5. Throttle reads using a token bucket; schedule windows using explicit timezone and daylight-saving-aware instants. Sequential preview mode writes ordered pieces to a distinct partial artifact and must not report the final task complete.
6. Keep task state transitions guarded by packages/contracts/src/lifecycle.ts and native equivalents. Terminal states cannot be reopened; retry creates a new attempt or an explicit retry transition in a future contract revision.

## Player boundaries
The browser/native player hook reports unavailable capabilities honestly. Frame stepping is a 1/30-second approximation unless the native decoder exposes exact frame timestamps. Pitch correction uses the browser preservesPitch property where available. PiP depends on browser support. Shader upscaling, deinterlacing, EBU R128 processing, audio-track selection, and transcript/chapter/Ask My Media hooks require native/provider implementations and must remain disabled until connected.

## Accessibility and UI
Home copy is in apps/windows/src/locales/en.json. Theme tokens use semantic CSS variables and 4/8/12/16/24/32 px spacing. Keep visible focus, reduced-motion support, accessible labels, live status, and no fake progress indicators. Theme preference supports system, light, and dark.


## D1–D42 Master Specification alignment

The task lifecycle uses the specification's CREATED → QUEUED → ANALYZING → PREPARING → RUNNING/DOWNLOADING/PROCESSING → VERIFYING → ORGANIZING → COMPLETED path, with PAUSED, CANCELLED, FAILED, RETRYING, waiting, unsupported, restricted and upgrade-required states. Internal merge/commit work belongs inside processing/organizing and must not bypass verification.

D24/D33 require source analysis, quality selection, resumable/ranged transfer, verification, atomic completion, cancellation/recovery and safe paths. The TypeScript contracts describe these boundaries; the current PWA still needs native worker integration to provide all of them.

D26 requires intent-driven Home, truthful task states, accessible navigation and media-detail actions. The existing PWA now has a system-aware theme cycle, semantic surface tokens, a share dialog and Share actions on Home/recent downloads, Downloads and Library cards.

D38 requires ffprobe analysis, validated operation graphs, enhancement/normalization, clip creation, social formatting, derivative versioning, output verification, hashes, atomic commit, crash recovery and CPU/GPU capability fallback. Clip presets are represented in the React component and typed contract, but a native export service must be supplied to enable the export action.

## Sharing and platform notes

- PWA/browser sharing uses Web Share API when available, allowing Android browsers to show the system share sheet and supported local files to be shared. Clipboard and direct social share URLs are fallbacks.
- The VidGravityAndroid.shareMedia bridge is an integration point, not an installed Android Intent implementation. Register a native bridge that uses Intent.ACTION_SEND, content URIs/FileProvider and an optional thumbnail before claiming native Android integration.
- Social share targets receive a title/link. They do not upload local media files directly to third-party sites.
- The Windows React AppProviders wrapper must be mounted above the app router for its Context provider to govern every React route and modal. The deployed vanilla PWA theme engine is independent and is already wired to its shared document root.

## Verification status

The JavaScript application and service-worker files passed a syntax-parse check, and root/site/android-web copies were verified to have matching contents. No Windows build, Rust compile, browser E2E, Android native build, accessibility audit, FFmpeg execution or production deployment was run during this change. These are still required before release.
