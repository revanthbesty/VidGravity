# Sprint 12 cinematic editor

Baseline: unmerged draft PR #73 (`codex/android-smart-cut-continuity`, remote
`9b7a2c2c558dd690c7db82a0428b2e838bef674f`). This branch adds only Sprint 12 changes
on top of that verified source. PR #73 and Sprint 11 PR #72 stay separate.

## Implemented

Open Studio → Open Cinema Studio. Existing camera/library imports, project history,
autosave, recovery, WorkManager export, MediaStore, playback, cancellation, sharing
and stale-export protection are reused.

- One SDR BT.709 GPU pipeline for selected-clip preview and export: thirteen looks,
  adjustable/keyframed look intensity, and eleven manual grade controls. Before/after
  bypass is temporary. Reset, per-clip copy/paste, apply-to-all and twelve local
  atomic custom color presets preserve motion/timing.
- Reusable bounded source-time video motion and scalar curves, local-time title/image/
  shape curves, four interpolation modes, staged add/edit/move/delete, reset and
  timeline markers. Position, scale, rotation and opacity render in exported video.
- Three editable title templates, two shape types and imported image overlays.
  Existing fonts/colors/timing remain editable. Graphics render above video; text
  renders above graphics. No arbitrary interleaved video tracks.
- Existing four-track audio mixer extended with mute/solo, music/voice/FX roles,
  source-time volume automation, split with preserved fade/automation phase,
  normalization and timed Voice-lane ducking. Recorded voiceover can move into an
  independent Voice lane without duplicate playback. Real decoded waveforms include
  gain/automation/ducking. PCM export processing uses the same gain calculation as
  companion-track preview.
- Per-boundary fade through black or white, duration-limited to both neighboring
  clips; no project-duration change. Existing global fades still work at other edges.
- Reviewed three-part stepped retiming (.5×/1×/1.5×/2×) with cancel and one undo step.
  Source keys persist. Existing constant speed and freeze frame are preserved.
- Schema 7 defaults safely read schemas 1–6; existing size limits, atomic writes,
  corruption preservation and fingerprint invalidation apply to new data.
- New tool workspace inherits Follow System/Dark/Light themes, with teal/blue/amber
  styling, scrollable contextual panels, animated cards/panels and compact real preview.
  No unrelated Home redesign. No accessible HTML design reference was found.

## Verification design

Existing suites are retained. New JVM tests cover schema compatibility, validation,
interpolation, fingerprint changes, bounded transitions, gain, split continuity,
cut retiming, safe speed guards and history. Instrumentation adds:

| Workflow | Automated evidence |
| --- | --- |
| Import → look/grade → export | Thirteen real encodes with decoded-pixel checks; warm grade, zero intensity and manual grade checks |
| Two clips → transitions → export | Black/white boundary pixels, source colors and unchanged duration after save/reopen |
| Music → mix → export | Decoded RMS: automation, normalization, timed duck, silent solo and split continuity |
| Title/shape/image → keys → export | Color-masked pixel centroids and disappearance after opacity fade, with saved project |
| Speed → save/reopen → export | Stepped source ranges, expected 3500 ms output, color order and audio-track presence |
| Camera → Library → effects → export/share | Existing actual emulated CameraX capture journey extended with saved vignette; rendered grayscale/duration/playback/share intent/stale invalidation retained |
| Existing project → edit → reopen | Schema-6 file opened via actual UI; speed review cancel/apply/undo/redo/reopen; custom presets, color clipboard, light/dark and mixer/motion/graphics persistence UI tests |

These tests measure the stated properties. They do not prove all codec/device
combinations, preview/export pixel identity, continuous playback synchronization,
all UI interactions for every exported parameter, or long-project performance.
Read actual final GitHub Actions conclusions before claiming a passing result.

## Partial and unsupported

- Sequential video renderer: fade through black/white only. Overlapping crossfade,
  slide/wipe/zoom/blur transitions and multi-video compositing are unsupported.
- Stepped speed is not a smooth speed ramp, reverse, optical flow or interpolated
  time remapping. It safely rejects projects with timed layers/legacy music/voice;
  apply it before adding those layers. Existing constant-speed tools still work.
- Keyframes animate video/overlays, look intensity and audio volume. Manual grade
  parameters and transition parameters are static. Thirty-two keys per property.
- Preview is the selected clipped video, not continuous composition playback.
  Companion-track monitoring polls at 50 ms and resynchronizes beyond 120 ms;
  export is PCM-sample based. No DAW-grade preview synchronization claim.
- Normalization is peak-aware RMS over the first 60 seconds, not LUFS normalization,
  denoising or a limiter. Ducking follows audible/unmuted Voice-lane time intervals,
  not speech/amplitude analysis. Solo excludes original and legacy audio.
- New shader accepts SDR only. Existing export tone mapping may process HDR sources;
  HDR preview/parity and Android 17 compatibility are not verified here.
- Image decode is bounded to 512 px and 100 MP source metadata. Media URI access must
  remain valid. Relink media or remove/reset an unreadable overlay to recover.
- Existing 720p/1080p/4K capability checks remain; no universal 4K/codec claim.
- No new dependencies or model downloads. Media3/Android APIs already used by the
  app (Apache 2.0 / Android platform terms); H.264/AAC support remains device supplied.
  No paid SDK, cloud processing or token usage was added.

## Next scope

Sprint 13: continuous composition preview and measured A/V drift; validated
multi-video overlap transitions; timed-layer retiming for smooth ramps; HDR preview;
long-project frame-time/memory profiling and representative physical devices.
Keep unrelated Cloudflare/Firebase infrastructure changes separate.
