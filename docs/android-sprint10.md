# Android Sprint 10: editing layers and timeline integration

Based on unmerged draft PR #70, `codex/android-sprint-9` at
`29a739bbc15111ba83c1c1e74c146d868228b63f`. This branch targets that branch,
not main. Prior integration branches, Firebase and Cloudflare configuration are unchanged.

## Implemented

- Sixteen independent, ordered output-time text layers: editable text, three local
  fonts, color/background presets, size, position, rotation, duration and
  none/fade/pop/slide entrance animations. The same Media3 overlays render preview
  and export; SRT captions and earlier brand overlays remain available.
- Existing sequential video lane and four independently mixed audio lanes gain
  text/caption timing rows, visible playhead, draggable audio/text timing,
  boundary/playhead snapping, drag feedback, zoom and frame-grid seek steps.
  Video reordering uses a long press; audio/text timing uses direct horizontal dragging.
  Up to 100 subtitle cues share one lightweight drawn track rather than expanding
  into 100 vertical lanes. Accessible caption editing remains in the tools panel.
  Logical frame steps default to 30 fps or the chosen export cap. Actual decoded
  seek precision still depends on the source and decoder.
- Selected-clip audio monitoring includes music, voice and independent lanes,
  trimming, offsets, gain and fades. Companion players follow output time and
  pause with the video, including app backgrounding. This monitor resynchronizes
  within a 120 ms drift budget; the exported composition uses the media pipeline.
- Audio properties can decode real PCM RMS waveforms in 200 ms bins, with gain
  and fades visualized. Explicit loading, one concurrent decoder, and a 60-second
  bound keep analysis off the UI thread and cap memory/work.
- Timed effects share an explicit clock: Transformer already supplies composition
  timestamps; clipped ExoPlayer preview starts at item time zero. This avoids
  adding clip offsets twice to text/captions/graphics and fixes per-clip fades.
  Drag callbacks read current project/layer state so later edits are preserved.
- Clip opacity over black; configurable 50–2000 ms fades through black at clip
  boundaries, capped at half the clip duration. Existing color, crop, rotation,
  aspect, speed, splitting, duplicate and undo/redo remain integrated.
- Freeze source frame at playhead into a normal two-second silent MP4, inserted
  after the selected clip. Uses on-device Media3 image encoding, not a screenshot
  or remote service. The source frame is bounded to 1280 pixels, then encoded at
  720 height; project effects remain editable and apply at final export. Trim,
  duplicate or remove the generated clip normally. API27+ frame extraction only.
- Confirmation before recoverable project trash; restore remains available.
- Schema 5 persists layers, opacity and fade duration, reads schemas 1–4 with
  defaults, rejects future versions, validates before atomic writes, and includes
  every new rendering property in export fingerprints. Merge rejects mismatched
  crops or opacity instead of silently discarding those settings.
- Debug identity: versionCode 188, versionName `1.9.0-sprint10`.

## Verification

The PR body is the authoritative final commit, CI conclusions and APK evidence.
Tests are not marked passed merely because they were added.

New unit tests cover migration/roundtrip, export invalidation/layer order, invalid
metadata, merge safety, bounded snapping/frame quantization, preview clock/gain
and adjustable fades. New instrumentation renders real synthetic assets for:

- A: import/trim/opacity/fade, inspect duration and decoded brightness;
- B: split/reorder, inspect black/white scene ordering in actual output;
- C/D: timed rotated/styled text and delayed audio, save/reopen/render, inspect
  decoded color windows and actual AAC PCM silence/audibility;
- freeze insertion and export, inspect duration and image stability;
- actual ExoPlayer trimmed-source frame timestamps before/after seeking;
- overlapping layer order, inspect front/back colors in two rendered outputs;
- Compose text creation, editing, drag timing, undo/redo, reopen, trash confirmation
  and recovery.

The full earlier suite is retained, including E: CameraX emulated-camera capture
through Library, Studio edits, save/reopen, actual export, first-frame playback,
correct Android share intent and invalidation after editing. Intercepting a share
intent is not proof that another app received or uploaded the file.

Local Gradle/Android SDK are absent; the repository intentionally uses CI's
provisioned Gradle 8.10.2. Local Python HTTP tests cannot bind localhost sockets in
this sandbox. Full checks run unmodified in GitHub Actions. Existing workflow runs
compileDebugAndroidTestKotlin, assembleDebug, JVM tests, debug lint, unsigned release
build/lint, API29/API35 instrumentation, native real-media fixtures, web/Python/JS,
Deno/FFmpeg regressions, APK signature/identity/CRC/SHA-256 and optional synthetic
account-config compilation. No live account or production infrastructure changes.

## Partial and unsupported

- Video clips are sequential, not simultaneous composited video tracks. Opacity
  dims over black; it does not expose a second video layer.
- Fades through black and text entrance animations are supported. True overlapping
  dissolve, slide/wipe/zoom video transitions and reverse decoding are not exposed
  as selectable tools. Gaussian soften is an existing global adjustment.
- Audio monitoring is approximate, not sample-accurate multitrack preview; the
  selected video does not automatically play through all timeline clips. DAW-grade scrubbing and full-song sample-resolution waveforms are not implemented.
- Timeline rendering is bounded (12 clips, 4 audio lanes, 16 text layers, 100 cues),
  without per-frame media decoding. It is not an unbounded lazy timeline engine.
- 720p/1080p/4K encoder availability remains device-dependent; unsupported exports
  fail visibly without silently changing quality. Resolution choices are not proof
  of successful encoding on every device. New tests focus on 720-height output.
- Freeze insertion generates a local derivative; asset-aware garbage collection,
  portable project archives and undo history across process restarts remain work.
- API37/37.2/Android17 require an available SDK image; API29/35 results do not
  establish Android17 compatibility. Physical hardware is untested.

## Sprint 11 recommendations

Prioritize continuous composition playback with one synchronized audio/video
clock, supported overlapping video compositing/transitions, long-form waveform caching,
asset-aware project cleanup/portable backup, and real hardware/API37 validation.
Keep unsupported features absent from selectable controls until actual exported
pixels/audio are covered by tests.
