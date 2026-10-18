# Sprint 8 release preparation — no release or deployment

## Identity and artifact gates

Debug application `com.vidgravity.app`, versionCode186, versionName1.7.0-sprint8; minSDK26, target/compile35, Java17/Gradle8.10.2. Version increment is aligned across Gradle, Python contract and actual-APK verifier. The default APK is guest mode and excludes Firebase runtime SDKs. Optional configured build is compiled separately with synthetic demo identifiers, not distributed as production credentials.

CI assembles, tests and lints; validates APK ZIP CRC and manifest/dex/resources presence; reads actual `aapt dump badging` package/version/debuggable state; verifies signature and Android Debug certificate; independently hashes with Python hashlib and GNU sha256sum; records PR head SHA separately from synthetic merge checkout SHA and tree/run. Artifact contains app-debug.apk, SHA256SUMS.txt, build-provenance.json, signature.txt and badging.txt. GitHub artifact metadata ZIP digest is separate from the APK byte digest. A valid debug signature proves archive signing integrity, not production trust, malware absence or device compatibility. Final artifact is published only after build job success; read both emulator job conclusions separately.

## Signing and distribution preparation

No release keystore, credentials, Play upload, release deployment or production signing task is configured by this sprint. Before any owner-authorized release: establish secure offline signing custody/rotation/recovery, audit final merged manifest and supported hardware, resolve license obligations, run real-device/privacy/provider acceptance and sign/verify release APK/AAB in a separate controlled workflow. Never commit key material or passwords, and do not reuse the ephemeral CI debug key as a release identity. CI debug keys may differ between runners; Android update installation can fail with signature mismatch. Preserve/export valued media and editable metadata before any uninstall; uninstall/clear-data removes private projects. Do not recommend uninstall as a silent upgrade fix.

## Permissions and user data

| Manifest permission | Purpose / boundary |
|---|---|
| INTERNET | User-requested download/source inspection, legacy cloud processing, optional configured Auth/telemetry; native editor/export does not require app network |
| CAMERA | Explicit CameraX capture only; optional hardware |
| RECORD_AUDIO | Video audio, Studio voiceover, optional live dictation; optional microphone hardware |
| POST_NOTIFICATIONS | Android13+ user-controlled progress notifications; no marketing push integration |
| FOREGROUND_SERVICE / DATA_SYNC / MEDIA_PROCESSING | Long downloads/native renders; required progress foreground notification and API-specific service type |
| WAKE_LOCK | WorkManager/framework background task support; no claim of measured battery efficiency |

No broad storage/media-read permission added: use SAF/content grants and app-owned MediaStore. Provider is nonexported, explicit read grants; cached media paths are canonical-contained/read-only/extension-gated; metadata and write access rejected. Original alias retained for saved-project compatibility. Backup disabled, cleartext disabled, AD_ID removed. Exported app components are launcher and normalized text-share receiver; merged SDK/service manifest should be audited again for actual release variants. No project encryption claim beyond Android sandbox.

Data flow: local source URI/read grants, projects/captions/brand/thumbnail/recovery metadata and recorded media stay local for native workflows. MediaStore exports/captures may be visible to other user-authorized apps. Accounts are optional; SDK owns configured Auth session, app does not save passwords/tokens itself. Signout/Auth identity deletion leaves local projects. No Firestore/Storage data path/cloud backup/sync is enabled. Future cloud data requires independent owner-isolated rules, deletion workflow and emulator testing; #64 proposed rules audit does not establish deployed security.

Optional Analytics/Crashlytics disabled by default/manifest; no custom user IDs/events/crash keys added. Consent now persists before enable and attempts withdrawal on failure. Persistently unavailable storage or SDK disable errors cannot guarantee successful withdrawal; UI reports failure rather than claiming saved success. Previously transmitted reports cannot be recalled. Actual provider delivery/session/delete and telemetry propagation are unverified; no live Firebase calls or configuration changes were made. Microphone dictation is Android recognition-provider dependent, not imported-video transcription; disclosure/OS availability apply.

The configured HTTPS API receives user-selected links/media only when downloading/inspecting or requesting legacy cloud tools. Source can include personal URLs/media; owner privacy policy must name the actual controller, hosts, retention, processor terms, support/deletion contact and jurisdiction before distribution. No public service was changed or verified by this sprint. Backend SSRF/authorization are separate server responsibilities, not guaranteed by client URI checks. Logs added here contain aggregate timing/size/hash/lifecycle, not source URI, account email, password or token. Debug test artifacts may contain synthetic fixture paths and emulator system diagnostics; do not upload real personal media in these suites.

## Dependency and license review

Both guest and configured CI run `:app:writeRuntimeCoordinates` over actual debugRuntimeClasspath resolved artifacts (direct and transitive), then `scripts/android_dependency_inventory.py` reads Maven POM license declarations resolved by Gradle into the report directory. Reports live in build/reports/dependencies/runtime-coordinates.txt and licenses.json, uploaded with guest and optional-account check reports. Uncached/inherited/absent declarations are **unknown_or_inherited**, not inferred permission to distribute. POMs are resolved from the existing configured Maven repositories. There is no separate license website crawl, legal approval or vulnerability advisory scan.

Declared baseline families: AndroidX Compose1.7.6/Material3 1.3.1, Activity1.10.0, Lifecycle2.8.7, Navigation2.8.5, WorkManager2.10.0, Media3 1.5.1, CameraX1.4.2, Room2.6.1, DataStore1.1.1, Retrofit2.11/Moshi converter, OkHttp4.12, coroutines1.9, Material1.12; optional FirebaseBoM33.16.0 Auth/Analytics/Crashlytics. Exact transitives come from generated inventory, not this overview. Source-tool/build dependencies and web/backend/FFmpeg licensing are additional distribution obligations outside the Android runtime inventory. Repository app-license ownership and third-party notice/text bundle require owner review. No subscription/billing/ad SDK is added, and no purchase is made.

## Release notes

- Protect current and recovery projects from oversized serialized saves, including JSON-escaped metadata.
- Validate independent audio source URIs consistently with other local media.
- Fail closed when optional consent persistence/provider application fails, with error-path unit coverage.
- Add real1080p/15FPS cadence, encoder cancel/retry and maximum-timeline/60-project checks, emulator launch/memory observations.
- Produce dependency/license evidence and verify APK structure/package/version/debug signature/head SHA/hash before uploading.
- Preserve existing camera, native Studio, Creative/Social, Library, Downloads, privacy and offline guest behavior.

## Known limitations and recovery/rollback

Inherited intermittent Compose teardown remains unproven; a passing run does not prove a fix. API37 unavailable-image checks are not Android17 certification. No physical-camera/microphone/thermal/battery/real-provider authentication or external recipient verification. No simultaneous video tracks, continuous ramps, true generative AI/imported-video STT, cloud project backup/collaboration or full project portability. UHD is encoder-dependent; broad-codec coverage unverified. Interrupted publication lacks exactly-once reconciliation; cancellation releases native encoder but not every process-kill window is fault-injected. Source permissions/cache files may expire; relink readable sources. Recovery/trash/brand assets require future reference-aware quota management. Full runtime control crawl, long-session jank and complete license/legal/data-safety approval remain outstanding.

No schema migration: keep schema1–4 readable. All writes AtomicFile; stale snapshots rejected, invalid/new oversized saves preserve last good state. Recover via Studio trash or Settings Privacy last-good copy, without overwriting corrupt originals. Protect metadata and original documents before OS clear-data/uninstall; account sign-in is not backup. Export cancellation may require retry; never share stale output after edits. A rollback should be a reviewed follow-up reverting Sprint8 delta on the integration stack; avoid downgrading version186 over installed newer data. No merge or rollback deployment is performed here.

Final classification must follow actual final CI and defect evidence: **READY FOR USER DEVICE TESTING** only for a clearly scoped debug candidate with passing required automation; **BLOCKED — FIXES REQUIRED** if required automation or critical stability issues remain. Neither classification authorizes production distribution.
