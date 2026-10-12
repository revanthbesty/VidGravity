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
