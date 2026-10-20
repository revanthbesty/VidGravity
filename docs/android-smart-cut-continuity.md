# Sprint 11 follow-up: continuous timed layers after reviewed cuts

Baseline: unmerged draft PR #72, `67526524966e9e112e6bf58feb4b471ca4106461`.
This isolated follow-up preserves that branch and targets `codex/android-sprint-11`.
APK identity: versionCode 190, versionName `1.10.1-sprint11`.

## Verified defect and repair

Smart Cuts previously intersected every audio, caption and text layer with every
retained **video clip**. A five-clip timeline with a single 200 ms removal could
produce six music fragments and fail the four-audio-lane limit. A music fade or
animated title could also restart at an unchanged video boundary.

The repair coalesces adjacent old-to-new composition-time windows when both their
source and destination times are contiguous and their lengths match exactly.
Timed layers are intersected with those continuous windows. Actual removed time
still creates a boundary. Clip ordering, media, trims, speeds, analysis notes,
project format, existing lane limits and review/approval requirements are retained.
The operation remains a normal undoable Studio edit.

Example: five 1-second clips, remove 200–400 ms from the first clip. A music lane
spanning all five seconds becomes two fragments, not six. A separate music lane
starting at 3000 ms keeps its complete source range and fades, moving to 2800 ms.

## Automated coverage

Four JVM regression tests cover:

- One genuine cut across a five-clip project, correct audio/caption/text retiming,
  stable first-fragment IDs, unique subsequent IDs, fades, codec round trip and undo/redo.
- Four audio lanes, sixteen animated titles and one hundred captions entirely
  after a cut: same IDs, source ranges, fades and animation settings; translated times.
- Entire middle-clip deletion, retaining the real time gap without splitting at
  other video boundaries.
- 1.5x video speeds, composition-time retiming and preservation of the uncut tail.

An Android integration test saves/reopens a five-clip edit, encodes the reference
and edited projects, decodes actual AAC energy in 200 ms windows and compares the
music fade across an unchanged video boundary. It also verifies duration, delayed
music start and unchanged hashes of both source fixtures. All previous tests remain.
No new models, libraries, paid APIs or infrastructure changes are introduced.

CI must run build, instrumentation compilation, unit tests, lint, API 29/35 suites
and the existing APK signature/identity/CRC/SHA-256/provenance verifier. Consult the
pull request for actual run results and the final artifact; this document is not
a claim that pending checks have passed.

## Limits

- A genuine cut can still split a timed layer. Existing fragment fade/animation
  behavior at that actual discontinuity is retained; this is not a new envelope editor.
- Sub-millisecond speed rounding can leave non-identical integer window lengths.
  Those windows are conservatively kept separate instead of changing timing.
- Genuine cuts that exceed existing lane limits still fail safely without saving
  a partial project. Attached legacy looping music/voice remain unsupported by Smart Cuts.
- This does not establish the cause of the previously observed intermittent Compose
  teardown crash. Lifecycle reliability remains a separate investigation.
- Video ASR, noise reduction, continuous composition preview, physical-device and
  Android 17 validation remain outside this repair. No hardware test is claimed.
