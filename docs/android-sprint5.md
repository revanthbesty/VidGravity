# Sprint 5 — Social Creator

Baseline verified live: draft #65 head `20deff238ce475c34e6186ab99dbead43257da9b`,
run 39135121666, build/unit/lint/regression success, 17 passing tests each API29/35,
unexpired artifact12139985029, actual APK SHA256
`f12a4846aa47dbfecb6e16046f573ce1b0e7b7be210a4977df11ccc816e344e0`.
New branch codex/android-sprint-5 is stacked on codex/android-sprint-4. Prior PRs
and production infrastructure remain unchanged. Version183 / 1.4.0-sprint5.

## Workflow and reuse
Home → Open Studio → import/open a video project → Open Social Creator.
The editor remains the single place for clip replacement/relinking, music,
manual captions/SRT, timeline/history, full playback, export and sharing. Existing
Creative Studio and thumbnails remain available. No new bottom-nav destination.

Social Creator provides nine guided video workflows: Reels, Shorts, Status,
Stories, landscape YouTube, advertisements, education, product showcase and travel.
Ten original video templates provide timed intro/outro text, fade edges, output
aspect and native caption motion. Before/after uses two timed labels. Users supply
all footage/music; no stock assets, live trend feed or trend ranking is claimed.
Creation makes a separate editable project. Duration fitting truncates without
extending media. New templates/cut projects leave separate timed audio and captions
in the original rather than moving them out of synchronization. User title text,
source media and settings remain editable through the original Studio.

## Rendering, branding and repurposing
Supported output aspects:9:16,1:1,4:5,16:9,4:3. 720/1080/2160 presets, frame-rate
caps15/24/30 and bitrates2/5/10/20Mbps reuse Media3 1.5.1 H264/AAC. Frame dropping
caps input rate; no frame interpolation. Encoders determine actual support and
errors remain visible; no silent quality fallback. 4:5 at720 requests720×900;
4:3 requests960×720. Arbitrary custom dimensions are deferred.
Fit preserves edges of the selected crop and may add bars. Explicit Fill crops
edges. Bounded static frame previews show fit/fill and conservative safe-area
guides; they are NOT whole-video simulations or platform UI specifications.
Existing rotation/global crop/color effects are reviewed in full Studio playback.
No platform upload acceptance or exact current duration limit is certified.

One local atomic brand kit stores name, imported logo, font/color, watermark
position, intro/outro text and export preferences. Logo imports decode to at most
512px and save private immutable PNG assets. Applying snapshots copies branding
into a project; later kit edits do not change existing projects. Watermark removal
is explicit; intro/outro remain ordinary editable/removable caption cues. Logos
are never deleted automatically because older projects may reference them.
Uninstall removes local metadata/assets; portability/asset garbage collection is
future work. No account/subscription is required.

Media3 exports render animated caption fade/pop/slide, lower thirds (first5sec),
brand text/logo and optional timeline progress bars. Preview and export use the
same effects. These overlays are real output, not guide-only decoration. All
settings persist in schema4 projects; schemas1–3 decode with neutral defaults.
Applying branding twice appends intro/outro cues; remove unwanted duplicate cues
with undo or the existing caption controls.

Repurpose selects up to five aspects and saves a separate editable variant for
each before queueing immutable WorkManager snapshots. Jobs are chained serially;
a shared render mutex also protects older jobs/direct calls. Queue state/progress
survives app restart, with cancel/retry and Library results. A failed/cancelled
predecessor may block/cancel descendants via WorkManager dependency semantics;
retry preserved variants explicitly. APPEND_OR_REPLACE allows a new retry chain.
Partial enqueue/storage failure retains saved variant projects; it does not
silently roll them back. Do not repeatedly queue the same batch: each explicit
batch creates new project variants. Within one unchanged project, a readable
successful export is reused, avoiding duplicate publication on retry.

Before rendering, source/audio/logo readability and conservative free storage
(2× estimated output +32MiB) are checked. Batch preflight sums estimates. Estimates
include assumed audio; variable codec behavior is not an exact byte guarantee.
Rendered video dimensions and nonempty output are checked before publication;
actual output hashes are recorded. Failed jobs preserve projects and never report
success. Later project edits invalidate sharing using the existing fingerprint.
Cancellation around publication has the existing MediaStore boundary limitation;
check Library for a completed file before retrying if cancellation coincides with
final publication. Process-death exactly-once publication is not guaranteed.

## Honest scope
Beat-grid assistance uses user-entered BPM and a start at the clip in-point, not
beat detection or automatic phase synchronization. Up to12 clip segments are
supported; larger cuts are rejected without discarding segments. Jump cuts keep
alternating beat ranges. Stepped speed uses0.5×/1×/2× thirds, not a continuous ramp.

Freeze frames, PiP, split-screen, photo montages, word-level timed highlighting,
arbitrary sticker layers, custom dimensions, and real local LLM/video-STT remain
deferred. Emoji text depends on platform glyphs. Existing captions/SRT and optional
offline microphone dictation continue. The writing assistant adds call-to-action
and repurposing suggestions with transparent local rules; no AI inference is
claimed for these suggestions. No new model, dependency, paid API or cloud call.
Thumbnail creator reuses frame extraction/persistence/PNG publication and adds
cover templates, text-panel backgrounds, brand colors/fonts and safe-area guides.
Guides are preview-only and never baked into exported PNGs.

## Validation
All17 existing instrumentation tests remain enabled. New tests exercise brand
persistence/project snapshots, actual4:5/4:3 native renders, serial queue completion
and checksum verification, unchanged-export reuse, missing-media failure and
Social Creator UI/template/brand persistence. JVM tests cover schema migration,
all recipes, independent variants, cut/speed timing, size/storage guards and cover
codec migration. Original contract checks remain enabled. Final CI links/results
and APK provenance belong in the draft PR and completion report.
Android17 validation depends on published SDK images. No physical-device tests,
platform certification, deployment, merge or paid service is claimed.

Sprint6 priorities: photo-to-video timeline support; safe compositing for PiP and
split-screen; export exactly-once publication/recovery; automatic beat detection;
brand asset portability/cleanup; measured local speech inference; API37 when available.
