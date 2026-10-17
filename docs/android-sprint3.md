# Sprint 3: precise native editing

Stacked on draft #62 (`f8969c9e144cd4e81fae9fe70b0fe6225cc8b481`).
Sprint 1/2 routes, camera, downloads, startup watchdog and sharing gates remain.
Android version: 181 / 1.2.0-sprint3.

## Editing workflow

Open Studio → Projects & on-device editor. Import a video or continue a project.
The horizontally scrollable timeline supports zoom, long-press/drag reordering,
clip selection, a playhead slider and millisecond seek input. Each source range
also has millisecond in/out fields. Split at midpoint or playhead, duplicate,
reorder, remove, and merge consecutive ranges from the same source and speed.
Undo/redo keeps up to 50 snapshots in the current editor session; continuous
changes coalesce. History itself is not persisted, but the resulting project is.

The main video track remains sequential. Four additional independently timed
audio lanes support import, source in/out, timeline offset, volume and fade-in/out.
These mix with the original track and the existing music/voiceover controls.
Audio beyond the video end is cropped. Music and additional lanes are mixed at
export, not in the selected-clip preview. Existing microphone voiceover recording
remains available. No waveform, sample-level trim or live multitrack audio monitor
is claimed.

Per-clip speed supports 0.5×, 1×, 1.5× and 2×. Media3 speed effects and audio speed
processing change both video and original audio duration. Slow motion repeats
source frames; it does not synthesize interpolated frames. Color controls apply
brightness, contrast and saturation; Neutral, Vivid and Soft are reusable presets.
Center crop, quarter-turn rotation and Source/16:9/9:16/1:1 fit framing are applied
to preview and export. Aspect fit may introduce black bars. Existing monochrome,
invert, full-duration text and fade-through-black remain available. Cross-dissolve
and overlapping video layers are not implemented in this batch.

Timed title/subtitle cues use output timeline milliseconds, Unicode multiline
text, three Android font families, three colors, size and vertical position.
SRT import replaces cues atomically after validation; malformed/out-of-range input
preserves existing cues. SRT export writes editable subtitle text via Android's
system document picker. SRT cannot carry styling, which stays in the project.
Cues overlapping in time are displayed together. Shortening a project below a
caption or audio offset is rejected until those timings are adjusted or removed.
Stickers/custom image layers and external font imports are not implemented.

## Persistence and export

Schema 2 writes all editing settings; schema 1 projects migrate on save with
neutral defaults. Unknown schemas remain rejected. Relink selected video keeps
its clip identity, speed and trim bounds and checks replacement duration. Choose
a matching source: the app cannot infer whether a different file has the same
content. Audio sources can be removed/reimported. Duplicate project makes a new
editable project identity without copying originals. Move to trash preserves a
recoverable project file; restore is available in the hub. Original media is
never deleted by these project actions. Uninstall still deletes private projects.

Exports retain foreground execution, progress/cancel/retry, immutable requests,
metadata/hash validation, Library publication and edit-fingerprint sharing checks.
The FPS control is a maximum frame-rate cap (15/24/30), using actual frame drops;
it never upscales a lower source rate. Bitrate requests are Auto/2/5/10/20 Mbps;
actual average bitrate is encoder/content dependent. 720p/1080p/2160p presets are
retained subject to encoder capabilities; no silent resolution fallback. Encoding
restarts after interruption instead of resuming partial output.

## Verification boundaries

CI runs the complete instrumentation suite on API29 and API35 and keeps Gradle assembleDebug/testDebugUnitTest/lintDebug and every existing
regression gate. New JVM tests cover schema migration, effect/audio/caption
roundtrip, editing/history, SRT and relinking. Android tests cover project
trash/duplicate/restore, UI undo/redo and save/reopen, and real encoding of speed,
rotation/crop/aspect, timed captions and delayed mixed audio. AAC is decoded to
verify the silent lead-in and audible lane, rather than checking only metadata.
Existing startup nonblack capture, navigation, legacy export, native render,
project recovery and foreground export tests remain.

API29/API35 emulator checks do not certify Android 17, camera sensors, microphone quality
or 4K hardware. The Android17 CI discovery job reports unavailable API37/37.2
Google APIs x86_64 images as BLOCKED and runs the suite when an image is published.
No physical-device tests, merges, infrastructure changes or deployment are part
of this sprint.
