# Sprint 9 — integrated native creator workflow

Base: draft #69, `codex/android-sprint-8`, head `3170e070e7663b1582429501c7556ed7e6c966d9`. Dedicated branch: `codex/android-sprint-9`. PRs #57 and #61–69 remain unchanged/unmerged. The independent Firebase PR #64 is not the starting point.

## Audit and scope

The Sprint 8 audit (`android-sprint8-feature-audit.json`, 35 groups: 16 implemented, 18 partial, 1 missing) remains the feature inventory, not proof that every path works on every device. Its CI baseline passed 44 instrumentation tests on each API 29/35, 41 JVM tests, debug build/lint and the optional-account synthetic configuration build. This sprint adds focused runtime checks instead of reclassifying untested advanced features as complete.

Architecture remains Kotlin 2.0.21 / Compose 1.7.6 / Material3 1.3.1, CameraX 1.4.2, Media3 1.5.1 and WorkManager, compile/target 35, min 26. Sequential video editing, offset audio lanes, on-device effects, atomic project/recovery saves, stale-export fingerprints, provider restrictions and the API29 decoder backpressure fix are preserved. No new runtime library or service was introduced; espresso-intents is test-only.

## Implemented repairs

* Five core bottom destinations in order: Home, Camera, Library, Studio, Settings. Downloads remains available from the header and Home. Camera has a usable destination before opening the dedicated capture Activity; cancellation returns to that destination. Native project save-before-navigation remains enforced.
* Saveable per-destination state retains Library search, filters, sorting, grid and scroll across tab changes and Activity recreation. A new capture clears excluding filters once and brings its Library review card into view. Privacy and Swipe Feed Back close their nested screen.
* Camera captures return to Library after an IO readability/type/size/metadata check and index repair. Latest capture offers Preview and Open capture in Studio. Malformed, cancelled, empty, missing and MIME-mismatched results are rejected without deleting the saved source.
* Camera photo timers/saves block camera rebinding. Back during recording requests stop and waits for indexing before returning; Back during a timer cancels it. Completed result and camera mode survive recreation. Save/start errors have retry guidance, permission errors offer Android app settings and initialize the camera after a returning permission grant, logs record event/type/code without media URI or account details.
* Mode-specific CameraX binding avoids unnecessarily requiring Preview + ImageCapture + VideoCapture concurrently. Quality selection can fall back to a supported capture resolution. Unsupported video explicitly falls back to photo mode; export resolution still fails visibly without a silent quality change. Concurrent preview is photo-only and capability-gated.
* Camera control panels follow the chosen dark/light appearance, keep readable contrast and minimum 48dp controls. The preview itself remains camera imagery; native camera dialogs follow the selected appearance.
* Camera journey exposed real source-aspect portrait export failures: Media3 reports encoding-format/encoding errors on API35/API29. Its height-only presentation rounds 9:16 720p output to405×720; strict AVC encoding cannot use that odd width reliably. A final GL canvas aligns odd dimensions upward by one pixel, preserving the image proportions with padding and keeping the requested height. The encoder's quality fallback remains disabled. A silent9:16 synthetic source and the real CameraX recording journey cover the repair.
* Current fingerprint-matching export gains Play current export. Player shows buffering/error/retry, pauses when backgrounded, and can prepare an Android read-granted share intent after rendering a frame. Edited projects disable both export sharing and this preview shortcut until a fresh export succeeds.

## Verification plan and evidence

New instrumentation cases cover five destinations, nested Back, search across navigation/recreation, unsupported playback recovery, capture-result rejection/readability/metadata, real Android system permission denial and Settings round-trip recovery, and an actual **emulated CameraX recording** through Library → split/duplicate/undo/redo/text/mono → save/reopen → WorkManager/Media3 export → decoded output → first-frame playback → intercepted Android share intent. The share chooser is intercepted; no recipient transmission occurs. Existing trim, timeline, captions, effects, audio, save/recovery and export tests stay enabled. The permission-denial class runs first on a fresh UTP install and its JUnit/HTML reports are retained separately; a second fresh install runs all other classes. No in-process permission revocation or assertion weakening is used.

CI explicitly compiles instrumentation sources, builds/test/lints debug, builds and lints **unsigned release**, runs all API29/35 instrumentation and regression suites, records dependency inventory and verifies debug APK signature/ZIP/identity/SHA-256/provenance. Release signing/distribution is blocked without the owner's signing prerequisites. API37/37.2 still depends on SDK catalog availability. Results and artifact links will be recorded after actual CI completion.

## Remaining partial/blocked work

Physical cameras/microphones, recipient share delivery, battery/thermal behavior, every codec/4K device and Android 17 are not established by API29/35 emulation. Recording/process death at every lifecycle boundary still needs additional device-level coverage. System denial is tested in emulation; physical-device permission behavior remains unverified. Process death may prevent returning the capture Activity result; completed MediaStore captures are reconciled when Library opens. Camera quality fallback is capture-only and hardware-dependent.

The inherited rare Compose teardown SlotTable crash has no proven universal root-cause fix; passing stress tests are bounded evidence. Multiple simultaneous video/PiP tracks, continuous speed ramps, portable backup/collaboration, generative AI and video speech transcription remain unsupported. Native tools remain free/on-device. URL downloads and legacy cloud tools still require the configured external VidGravity API; optional accounts require an owner-configured Firebase build. No live provider, Firebase or production infrastructure changes are part of this sprint.

Sprint 10 priorities: broader lifecycle/camera interruption tests, device/codec matrix and Android17 when published, migration of the large legacy Activity into feature modules, and asset-reference-aware queue cleanup/portable project backup before expanding advanced tools.
