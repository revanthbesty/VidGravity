# Sprint 11: reviewed offline smart editing

Baseline: unmerged draft PR71, `codex/android-sprint-10` at
`a478d323e4631149f7b99f2872f06e5c69fad9e2`. This branch targets that branch;
main and earlier Sprint branches are untouched. Final CI results and APK
provenance are recorded in the Sprint 11 draft PR after checks actually finish.

## Implemented

The existing Creative Studio route now opens an AI Tools dashboard. Eight tool
cards report available, processing, completed or unsupported capabilities and
reuse the existing caption and thumbnail workspaces. Material colors follow the
current dark/light theme; lightweight card size animation and bounded drawn
tracks avoid thumbnail decoding while scrolling.

- Local analysis reuses `CreativeAnalyzer`: one visual sample per two seconds,
  at most 60 seconds (15 on low-RAM devices), 320-pixel sampling, 200 ms PCM
  energy/peak windows, coroutine cancellation and progress. `SmartAnalyzer`
  serializes smart analyses. No source media is modified or uploaded.
- Silence suggestions use measured low energy, not speech classification.
  Individual range selection and a confirmation dialog are mandatory before
  applying cuts. Unanalyzed tails and other video clips are retained. Captions,
  text and independent audio lanes are intersected/retimed, with undo/redo through
  Studio history. Unsupported attached looping music/voice or lane-limit overflow
  is rejected visibly; data is not silently dropped. Remaining fragments must be
  at least 100 ms. Text entrance animations restart on resulting layer fragments.
- Sampled scene changes become saved source-time markers, shown with silence
  bands on the timeline. Users can navigate to a scene or split explicitly.
- Ranked highlight ranges combine visual change, contrast, exposure and nearby
  audio activity. Users adjust start/end and confirm the portions to remove.
  Rankings are deterministic suggestions, not semantic understanding.
- Thumbnail ranking uses exposure, contrast, temporal separation and optional
  face observations. Preview decodes the real source frame; choosing it opens the
  existing editable thumbnail creator with that time, including crop, title,
  badge, local save and actual PNG export to Android Library.
- Reframe supports 16:9, 9:16, 1:1 and 4:5 static presets and up to 32 manual pan
  keys per clip. Keys use absolute source time and linearly interpolate crop
  centers. Shared Media3 transforms are used for preview/export. Optional sparse
  frontal-face observations can generate pan keys when at least two were found;
  this is not identity tracking or segmentation. Outside the first/last key the
  nearest center holds. Crop dimensions are fixed through a pan.
- Audio normalization measures RMS and peak, proposes a bounded 0.25–4x source
  gain and applies it through PCM processors in preview/export. A three-second
  source-only audition supports before/after listening, with lifecycle cleanup.
  A one-second energy overview shows proposed gain. Existing detailed waveforms,
  audio fades and independent music/voice balance remain in Studio tools. The
  gain target is not LUFS normalization or noise reduction; verify unanalyzed
  portions, and mixed tracks can still clip.
- Project schema6 reads schemas1–5. It saves analysis, scene markers, silence,
  ranked ranges, sparse face samples, gain and pan keys; checks bounded metadata
  before atomic writes. Analysis-only changes do not invalidate exports, while
  gain/framing changes do. Relinking clears source analysis; incompatible merges
  are rejected. Analysis acts as a local project cache keyed by URI, source
  duration and available file metadata. Same-size content replacement may evade
  metadata detection, so reanalyze after replacing source files.

## Models, compatibility and free operation

No dependency or model download was added. Existing Media3 1.5.1 (Apache-2.0),
Android media APIs and the existing Android framework frontal-face detector are
reused. ONNX Runtime was considered unnecessary for the implemented deterministic
tools; adding it without tested model weights, redistribution terms and APK/RAM
measurements would not establish functioning transcription or tracking.

No offline video-file ASR model is bundled. Automatic video transcription and
multilingual recognition are unsupported. Complete manual caption editing,
styles, timeline cues, SRT import/export and burned-in rendering remain available.
Optional API31+ platform **microphone** dictation depends on the device's installed
provider/model; provider availability does not prove a language model is ready.
CI does not establish speech recognition or face-detection quality. API26 users
retain manual tools; frame analysis needs API27+.

No noise-reduction action, cloud AI API, paid tokens, server processing service,
Firebase/Cloudflare production changes or recurring paid dependency was added.

## Verification plan and honest limits

New JVM tests cover schema migration/roundtrip, export fingerprints, cut retiming
and rejection guards, unanalysed tails, speed, history, key interpolation, aspect
presets, rankers and peak-aware gain. New instrumentation uses synthetic assets
to analyze/cut/save/reopen/render actual video; split at detected scenes; inspect
blue/red pan output; decode before/after AAC gain; publish ranked PNG frames; and
exercise analysis caching/cancellation. Compose tests require review before cuts,
then undo/redo/save/reopen. The existing Camera→Library→Studio→export→play/share
journey now performs a real local AI Tools analysis before editing/export.
All earlier tests remain enabled. Added tests are not a claim they passed until
their workflow jobs succeed. No physical device or Android17 compatibility is
claimed from API29/35 results.

Project limits remain 12 video clips, 4 independent audio lanes, 16 text layers,
100 caption cues and 256k project characters. Overlapping video, true crossfade
transitions, reverse video, sample-accurate continuous composition preview,
cross-session history and portable project archives remain outside this batch.
Source-time smart markers remain annotations after editing; reanalyze the
selected range for refreshed rankings. Analysis is sampled and may miss short
scene changes or quiet speech. Model inference quality and tracking stability
require representative footage/hardware, not synthetic motion tests.

Sprint 12 should prioritize continuous composition preview, richer cached analysis
for long footage, supported video compositing/transitions, asset-aware backup,
and a separately audited offline speech model with measured size/RAM/accuracy.
