# Sprint 2: on-device Studio

Based on the successful PR #61 content tree, e21686ae7444691a8d0ceb77bed10126b522e12b.
Earlier drafts and the integration branch remain unchanged.

Use Studio → Projects & on-device editor, or select a video in the existing Studio
and choose Edit this video on device. Projects support up to 12 ordered clips and
30 minutes total. Trim with the range slider, split a clip at its midpoint, reorder
or remove clips, add centered text and original/mono/invert color effects, and
optionally fade through black at each clip edge. These are fades, not overlapping
cross-dissolves. The selected clip preview applies trim/text/color/fade and original
volume; the music and voiceover mix is rendered at export.

Project content is versioned JSON stored atomically in app-private storage.
Autosave runs on IO after edits settle; Save project and the Projects back action
also save explicitly. Original videos are referenced through local content URIs
with persistable document grants when supported. Moving/deleting source files or
revoking provider access requires reimport. Project metadata is not a backup of
source media, and uninstalling the app removes projects and private voiceovers.

Media3 1.5.1 Transformer performs local H.264/AAC encoding, clip concatenation,
text/color/fade effects and audio mixing. Music loops to the video timeline;
voiceovers start at the beginning and are bounded to that timeline. Microphone
recording requires consent, stays in the editor, and is limited to five minutes.
Original/music/voiceover volumes are independent. No source is uploaded by this
editor. Existing cloud story/status/sticker tools are retained separately.

720p, 1080p and 2160p output heights are offered; 2160p corresponds to 4K for
landscape 16:9 video. Actual device decoder/encoder capabilities apply. Unsupported
settings fail visibly with no silent resolution fallback. Export runs as a
foreground WorkManager job with real progress when available, indeterminate
progress otherwise, cancellation and explicit retry. A process restart reruns the
immutable request from the beginning; it does not resume a partial encode.

Completed nonempty video is metadata-checked, hashed, saved through the existing
Library publisher, and only then becomes shareable. The full editing fingerprint
must match the export record. A running job reads its own immutable request file,
not later autosaves. Editing a project cannot relabel an old export as current.

Camera additions: capability-backed zoom slider, pause/resume recording, lifecycle
timer cancellation/background recording stop, inset-safe native controls, and the
existing capture → Library → Studio flow. Physical hardware behavior requires
separate validation; CI does not certify camera sensors or microphone quality.

Automated coverage retains Sprint 1 gates and adds project codec/range/split/fade
tests, project save/reopen UI checks and a real native encoder test using generated
video and tone fixtures. CI builds/lints the debug APK, runs API35 instrumentation,
verifies the APK signature, and includes its SHA-256 and source provenance.
An additional job queries the SDK catalog for API37.2/37 images. It runs the same
UI/render checks if a compatible image is published; otherwise it explicitly
records Android 17 validation as blocked. This is not Pixel 10 Pro hardware testing.

Limits: no overlapping cross-dissolve, keyframed transforms, multitrack visual
compositor, arbitrary voiceover placement or waveform editing in this batch.
4K is capability-dependent and is not certified by a 720p emulator render test.
No production deployment, paid service, physical-device test or merge is performed.
