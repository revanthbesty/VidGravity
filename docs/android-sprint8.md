# Sprint 8 stabilization and integration audit

## Baseline and scope

One draft PR, #69, based on #68 `67510758194edfb02b3edd06678b5c2860bcd95d` (tree `3f87acb73422e7bc8ff93308c9ddf568e967685d`). Open/unmerged stack: #61 → #62 → #63 → #65 → #66 → #67 → #68. #64 is independently based on main, not integrated wholesale. All previous PRs remain unchanged. Baseline run [39153216578](https://github.com/revanthbesty/VidGravity/actions/runs/39153216578) passed build/unit/lint/regression, API29/35 each 38 tests. Configured compile run 39153216409 passed. Baseline APK artifact 12145714244, SHA256 `2cce9984f1c5b73f1ab6ad494cff13781ca89528ef41833e7c3246be278ddc48`, version185 / 1.6.0-sprint7. These are baseline results, not Sprint8 results.

Scope is repair, evidence and release preparation, not feature expansion. Local cloud workspace has no Android SDK, Gradle binary or wrapper; Android compilation, lint and emulator execution use GitHub Actions. No physical-device testing, Android17 certification, production-service calls, merge, deployment or purchase is implied.

## Audited functionality and controls

`android-sprint8-feature-audit.json` is the machine-readable inventory: 35 feature groups, routes, actual behavior, persistence, source files, automated cases, known defects/limits and recommended follow-up. Static callback references include exact source lines; positional/dynamic controls are covered by route groups, not a complete AST or runtime crawl. A callback reference is not proof of a passing click test. All entrypoint groups were reviewed; not every control/permission/network/recipient combination was exercised.

| Entry | Destination / return | Persistence and automated scope |
|---|---|---|
| Home Create, Import, Continue, templates | Native Studio or typed media tools; Back/Projects | Active ID and tab saveable; existing native save/reopen + integrated Home navigation cases |
| Camera tab/Home/Studio camera | Dedicated CameraX Activity → Studio, captured media index | Real camera code retained; synthetic return routing only; sensor capture unverified |
| Studio projects → editor | Clip tools, Creative, Social, export → Projects | Atomic project, recovery/trash and immutable worker request; edit/history/render cases |
| Creative analysis/templates/assistant/dictation/thumbnail | Back to editor; derived project or applied edit | Applied model/project and thumbnail JSON; local analysis/native PNG/render tested; recognizer external |
| Social templates/brand/variants/cuts | Back to editor; derived projects/WorkManager queue | Shared native project/renderer; branded/feed/classic/queue cases |
| Library list/grid/filters/cards | Player/image preview, Studio, Android share | MediaStore/index/preferences; populated image/theme regression and provider security checks |
| Downloads URL/quality/batch/jobs | Local Library/Studio; Inbox and StudyTok | Remote-service dependent; existing mocked/server regression, not live endpoint verification |
| Settings | Account, Privacy, export/download/theme/system links/help | Guest/fake-provider UI and storage/consent unit/integration checks; no live Auth test |
| Text-share intent | Inbox → URL inspection or external browser | Normalized URL store; no arbitrary external media deep-link support |

No new duplicate editor, template model, settings screen or renderer was introduced. Native video, Creative and Social use StudioProject/StudioTools/StudioRepository/NativeStudioRenderer. Creative and Social recipe catalogs are different supported operations. Legacy cloud story/status/sticker tools remain explicitly separate because they have capabilities without complete local replacements. Removing them or changing project schemas would risk working behavior. Unused GhostPlayer/old Camera/collection/collaboration scaffolding is excluded from functional claims; cleanup is deferred rather than inventing replacements.

## Demonstrated defects repaired

1. **Unreadable successful saves:** encode accepted serialized JSON beyond decode's 256,000-character limit. Unbounded provider media names or JSON escaping could produce files that cannot reopen. Encode and decode now share the limit; repository serializes/validates before touching the current or recovery file. Regression proves rejection leaves both byte-identical and reopenable; ordinary Unicode round trips and escaped-character expansion are covered. Schema1–4 compatibility and normal project behavior remain.
2. **Inconsistent audio URI validation:** independent lanes checked only `content://` prefix/length. They now use the same authority/userinfo/port/fragment policy as video/music/voice. Negative tests retain valid custom provider authorities, including underscores. Resolver permissions/actual decoding remain required.
3. **Consent persistence ordering:** optional SDK collection was applied before the preference commit. Persist now precedes enabling; disk/provider exceptions attempt to disable both collectors and store withdrawal. UI resets requested choices on failure. Unit tests inject disk and provider failures; permanent disk failure or an SDK refusing disable cannot guarantee persistent/live collection state, and is reported as failure. No live telemetry delivery claim.

4. **Redundant FPS effect truncates the video tail:** first Sprint8 QA produced13 samples at correct15FPS cadence but only866,666us video duration for a one-second trim. The source fixture is verified by ffprobe as15FPS. Media3 1.5.1's default dropper buffers a next frame and discards the pending tail on EOS. Renderer preparation now probes each distinct source's intrinsic rate; with playback speed applied, no drop effect is used when already at/below the requested cap. Unknown/higher-rate sources retain the existing dropper. Assertions are retained (14–17 samples,66.7ms median) and strengthened with900–1100ms track duration. A separate real30FPS fixture verifies higher-rate downsampling remains active. This is a cap, not frame interpolation/CFR certification. Final CI must establish the correction.
5. **Navigation test observes the wrong completion signal:** API29 could see the autosaved title before the navigation save returned to Main, then assert Library selection too early. It now also waits for selected Library semantics; all saved-title/selected-tab/reopen assertions remain. Separate API29 Home-loading/Social-create timeouts were observed in an earlier run and must be checked in final CI rather than assumed fixed.

6. **API29 surface handoff loses early decoded frames:** diagnostic run39205934438 at779a38a3 showed14 video decoder outputs but10 SurfaceTexture inputs/queued/encoded frames in the Full HD case;30FPS decode had28 outputs but24 surface inputs. Loss occurs before processing/encoding. Both exports then waited approximately20seconds for EOS because registered surface frames were missing. Media3 1.5.1 normally permits five pending frames on API29, assuming the decoder honors KEY_ALLOW_FRAME_DROP=0; its existing older-device workaround limits pending frames to one. Native exports now apply that backpressure limit on API29 through a delegated decoder factory, preserving default audio decoding and other APIs. No sleep, frame duplication, timestamp rewriting or relaxed assertion is used. Hardware throughput on API29 is not separately profiled; correctness is prioritized. Pipeline tracing is now opt-in with `-Pandroid.testInstrumentationRunnerArguments.vgPipelineTrace=true`; final required tests run without it. Final CI must validate the correction and broader suite.

## Workflow acceptance matrix

| Workflow | Automated evidence | Remaining limitation |
|---|---|---|
| A Import → trim → text → save/reopen → export | NativeStudioProjectUiTest + NativeStudioIntegrationTest + OfflineGuestExportTest | Combined coverage across UI/repository/renderer; not one universal picker-to-sharesheet recording |
| B Camera → record → preview → Studio → export | WorkspaceReliabilityTest synthetic result; native real render | Actual camera/microphone/sensor capture and permission denial need device testing |
| C Template → replace media → customize → save → export | CreativeWorkspaceUiTest, CreativeIntegrationTest, StudioEditingTest relink | OS picker/provider replacement combinations not exhaustively automated |
| D Caption/timing → export | StudioAdvancedTest real yellow-pixel timing; SRT JVM cases | Dictation/transcription is provider-dependent; imported-video STT absent |
| E Social platform/aspect → export | SocialCreatorIntegrationTest feed/classic, Creative portrait, StudioAdvanced square | Not every resolution/codec/platform-recipient combination |
| F Thumbnail/text → image | CreativeIntegrationTest save/reopen, real PNG dimensions/hash/MediaStore | Manual recipient rendering unverified |
| G Offline save/close/reopen/continue | OfflineGuestExportTest explicitly disconnects networking; native UI reopen | Not OS kill-at-every-instruction fault injection |
| H Guest edit/export → optional signin → local preservation | OfflineGuestExportTest + fake AccountPrivacyUiTest | Live Firebase auth/session/deletion not configured or contacted |
| I Export cancel/interrupt/retry | ReleaseQaTest actual Transformer cancellation → next render; queue missing-media failure/reuse | No durable exactly-once publication guarantee across process death; queued cancellation cleanup needs further fault injection |
| J Android Sharesheet | Source uses content URI/MIME/ClipData/read grants; provider and stale-output tests | OS chooser interaction and receipt by external apps not fully automated |

These distinctions remain even if every emulator test passes. No screenshots are required from the user to run automated checks.

## Export QA and performance

Existing real encoders test 720p landscape, portrait720×1280, square720×720, feed720×900, classic960×720, speed/audio track synchronization, yellow-caption timing via decoded frames, mixed audio onset via AAC PCM RMS, MONO output, source hashes, durations and published file hashes. Sprint8 adds actual 1920×1080 AVC, requested15FPS sample cadence/count/positive sizes/strictly increasing PTS, explicit track duration and30→15FPS downsampling, source preservation, cancelled encoder teardown then successful retry. Unsupported4K encoders/other codecs are not declared verified. Edge fade gain has JVM coverage; all filter/transition visual combinations do not have pixel oracle coverage.

Per-API artifacts include `smoke-evidence/export-fullhd.json` (render duration, bytes, samples, hash) and `project-scan.json` (60 projects ×12 clips ×100 cues, write/scan observations). Existing VGPerf logs measure bounded thumbnail reuse/decode. Post-suite host sampling installs the built APK, performs3 process-cold and3 warm-resume `am start -W` launches and dumpsys memory, writes `measurements/launch-memory.json` and meminfo.txt. Launch failure fails the measurement job; timing/PSS are observations without arbitrary cross-host performance thresholds. `am start -W` is not full interactive readiness; VGStartup draw logs provide separate lifecycle evidence. No same-host pre-change benchmark exists: no speedup claim. UI jank/timeline frame pacing, sustained thermal/memory, battery, long-background operation and physical camera performance remain unmeasured. The 60-project scan is not a million-media library benchmark.

## Reliability finding retained

An inherited Compose1.7.6 SlotTable teardown crash appeared intermittently on S6 API35 and S7 API29 (S7 failed after27/38, unchanged rerun38/38 passed). Stack is in composition disposal without an app frame; root cause is unproven. Sprint8 adds four Social-preview/Back/editor-disposal cycles in StudioLifecycleStressTest. Tests remain enabled and no speculative framework upgrade is presented as a fix. An unrelated external Google launcher ANR has a narrowly gated test-only focus recovery helper; VidGravity ANRs still fail. A clean Sprint8 run reduces observed regression risk but does not establish that intermittency is repaired. Any recurrent app/Compose failure must be investigated using the retained logs, not hidden with skips or automatic retries.

API37/37.2 image discovery is conditional. A successful discovery job with skipped emulator steps is **blocked compatibility testing**, not an Android17 pass. StartupHost cannot recover a blocked UI thread/GPU or every asynchronous Compose exception.

## Final evidence

Use PR #69's latest head/run links for final build/unit/lint/API29/API35 conclusions, artifact digests, counts and measurements. The first implementation run is 39156117763 at `ca284cf045b075ae0fbb2955a1c6470ac3c071d6`; it predates the final audit/consent changes and is not the final deliverable. Local Android contract/artifact-tool suite: 12 passing. A broader local Python discovery ran46 cases but its licensing HTTP fixture could not bind127.0.0.1 because the sandbox denies sockets (PermissionError); the unchanged fixture runs in the Actions regression job. No tests are removed, disabled or weakened. Release preparation and privacy/security details are in `android-sprint8-release.md`.
