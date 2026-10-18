# Sprint 4 — offline Creative Studio

Base: validated draft #63 head `8ca0c7070030bd7ab3949c20d25f72ae70b1722f`.
Phase 0 rechecked the live PR, successful run 39124877667, both 12-test API29/35
jobs, fixed Compose call chains, and unexpired APK artifact 12135777424. Earlier
branches remain unchanged. Android version 182 / 1.3.0-sprint4.

Open a video project → Open Creative Studio. Home's existing Open Studio route
leads to the same project editor; no duplicate navigation destination was added.

## Implemented tools and honest engine boundaries

| Tool | Implementation | Limits |
| --- | --- | --- |
| Scenes/highlights/exposure | Sampled luminance, frame differences and contrast | Rule-based suggestions, not semantic video understanding |
| Silence suggestions/removal | MediaExtractor/MediaCodec PCM RMS, 200 ms bins, 600 ms minimum silence at RMS <0.008 | Low-energy audio is not proof of no speech; always review |
| Subject-aware portrait framing | Android framework `android.media.FaceDetector` inference, then normalized static crop | Frontal faces only; no identity recognition, segmentation, tracking or general object detection |
| Automatic framing | Per-clip crop saved in project and rendered by Media3 | Uses center framing if no face is found; no silent application |
| Exposure/soft focus | Brightness recommendation and Media3 GaussianBlur | Soft focus affects the WHOLE frame; not background-only blur or denoising |
| English caption dictation | Optional `createOnDeviceSpeechRecognizer`, API31+ and device provider required | Microphone dictation, NOT transcription of a video file |
| Model download | Explicit user button, API34+ provider callback progress | Size/storage/scheduling controlled by the device provider; no automatic download |
| Captions | Existing timed editor, styling, SRT, Media3 burn-in reused | Automatic video-file transcription engine is not installed |
| Creative writing | Provider-independent interface with local deterministic rules | Clearly labelled rule-based; no LLM or generative AI inference |
| Templates | Eight original layouts for Reels, Shorts, Status, Stories, Product, Travel, Birthday, Education | User media/music only; no stock downloads or licensed music supplied |
| Thumbnails | Real frame extraction, crop/rotation, title/subtitle, vector badges, color/contrast, PNG export | Not AI image generation; Android Canvas rendering |

Sources: [Android FaceDetector](https://developer.android.com/reference/android/media/FaceDetector),
[on-device speech APIs](https://developer.android.com/reference/android/speech/SpeechRecognizer),
[model download callbacks](https://developer.android.com/reference/android/speech/ModelDownloadListener).
Media3 remains pinned at 1.5.1 (Apache-2.0), using the existing effect/Transformer
dependencies. No new third-party runtime/model dependency or external inference
API was added. Framework model assets are supplied by Android/the speech provider,
not redistributed in the APK. Bundled LLM/STT model bytes: zero. No paid tokens,
subscriptions, model endpoints, analytics or credentials are needed by these tools.

## Non-destructive workflow

Analysis runs on IO, sampling one bounded frame at a time, every two seconds in
the selected clip's first 60 seconds. Low-RAM devices analyze at most 15 seconds
and skip face inference. Face inference runs every third sampled frame. Audio
analysis is bounded to the same window with a 60-second decoder deadline.
Progress reports completed frames and decoded audio time; cancel is honored
between native decode calls. Frame extraction needs API27+. An API26 device gets
an explanation while ordinary editing remains available.

Before/after previews show the sampled source frame and proposed portrait crop /
brightness. Apply is explicit and flows through existing project undo/redo.
Framing is per clip; brightness, soft focus and output aspect are project settings.
No original media is overwritten. Highlights and silence removal create NEW
editable projects with selected source ranges and original audio. Timed captions,
separate music and voiceover stay in the source project instead of being silently
moved out of sync. The UI discloses this before creation. More than 12 audible
segments cannot be applied automatically; it does not discard extra segments.
Only the analyzed prefix is used for these derived cuts.

Templates create separate editable projects, never extend source duration, and
provide title, duration, aspect and optional user-selected music customization.
The static layout preview is not a whole-video playback simulation. Full playback,
media replacement/relinking, caption editing and export use the existing editor.
Portrait 720p exports request aligned 720×1280 (1080p:1080×1920;
2160p:2160×3840), landscape 1280×720 etc., square 720×720 etc. Actual encoders
still determine support; there is no silent preset fallback.

Thumbnail settings autosave atomically in a separate directory and can reopen.
Each image project stores source URI, frame time, crop, text, font, color, badge
and adjustment settings. Rendering and PNG publication run on IO, checksum the
actual output and use existing MediaStore/Library publication. Sharing requires
a matching in-memory export snapshot; edits disable sharing until re-export.
Originals are not copied into project files; revoked URI access requires relinking
in Studio or a new thumbnail source. Uninstall removes private project metadata.

Dictation never falls back to a network recognizer. Microphone permission is
requested on user action and recognition stops when leaving/backgrounding the
workspace. Optional model download size is unknown to the app; the UI says so
and reports provider progress/scheduling/failure without inventing byte counts.
The OS provider owns its download/storage lifecycle. Manual captions and SRT are
fully usable without any speech provider/model. Automatic video transcription,
local LLM generation, background segmentation, background-only blur, denoising,
stabilization and synthetic thumbnail image generation remain unsupported.

## Verification

All Sprint 1–3 tests remain enabled, including the repaired Compose IME calls and
nested-toolbar navigation. New JVM tests cover migration, framing, silence/time
ranges, all templates, derived project safety, offline provider/fallback and
thumbnail serialization. New Android tests analyze generated scenes and silent
PCM, check source SHA preservation and cancellation, render a portrait template
with framing/soft focus/captions, reopen thumbnail settings and publish/verify a
PNG, and exercise Creative Studio fallback/templates/writing/thumbnail UI.

CI retains assembleDebug/testDebugUnitTest/lintDebug, the full regression gates,
and the full API29/API35 instrumentation matrix. APK upload includes checksum and
provenance; CI prints the actual APK SHA-256 and verifies SHA256SUMS with sha256sum.
Android17 remains a separate SDK-image availability gate; API29/35 success is not
Android17 or physical-device certification. Speech accuracy and face-quality
validation are not simulated by fallback/blank-frame tests. No physical camera,
microphone, paid service, production deployment or merge is claimed.

## Suggested Sprint 5 priorities

1. Evaluate an optional licensed video-file STT engine and measured model memory,
   size, languages and cancellation before offering model downloads.
2. Validate face framing on consented diverse clips, add temporal tracking and
   user-adjustable tracking/crop keyframes before general subject claims.
3. Add a real local language model provider only after RAM/storage benchmarks.
4. Expand thumbnail relinking and project portability; retain URI/asset checks.
5. Consider segmentation/blur/stabilization only with reliable processing and
   representative native export tests. Run API37/37.2 when a published image exists.
