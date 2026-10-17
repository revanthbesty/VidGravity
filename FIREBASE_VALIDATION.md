# Firebase validation — 2026-10-17

Executed locally against the native `apps/android` app:

- JDK 17 + Gradle 8.10.2 + installed Android SDK 35: `testDebugUnitTest assembleDebug` passed with Firebase disabled.
- The same build/tests passed with `-PVG_FIREBASE_ENABLED=true`, using only synthetic `demo-vidgravity` identifiers. Google Services and Crashlytics plugin tasks executed. No real project configuration or credentials were used.
- Android JUnit: 3 tests, 0 failures, 0 errors (ID/path traversal, display-name bounds, upload size/type boundaries).
- Firestore + Storage emulators: all 4 adversarial rule tests passed. Project: `demo-vidgravity`; services bound to loopback. Rules were parsed and evaluated by emulators, not only inspected as text. Local emulator tooling dependencies were installed in a shorter scratch path after Windows denied nested package directory creation; the committed test code was copied there without changing assertions, only adjusting relative rules-file paths.
- Existing Android-focused Python regression modules (`test_p161_android_scaffold`, `test_p174_android_share_pipeline`, `test_p175_wpa_feature_batch`): 21 passed.
- Broad Python discovery: 172 tests attempted; 1 failure and 33 errors. Errors include legacy modules that raise `SystemExit(0)` during import, Windows SQLite file cleanup locks, provider subprocess failures and HTTP connection errors. This is not a green repository-wide suite. The Firebase changes do not edit Python runtime code.

Initial environment issues included an unavailable default Java launcher, a debug-signing home-directory permission error, and Kotlin daemon temporary-file problems. Workspace-local JDK/Gradle/Android state allowed successful builds; no machine-wide settings or production resources were changed. CI reruns both Android modes and the emulator rule tests on Linux with pinned dependencies.

## Rules review

Prototype assumptions: only a private `users/{uid}` profile containing `displayName` and server `updatedAt`, and private bounded raster images under `users/{uid}/images/{id}`. All other collections, lists, nested paths and roles are denied. The Firestore edition/region still requires owner confirmation before deploying the Standard/default-database template.

Tested attacks denied: anonymous and cross-user CRUD; public and authenticated collection listing; arbitrary subcollections; privilege-field injection; malformed types; missing required fields; oversized strings; timestamp spoofing; empty/oversized/active-content image writes and overwrites. Valid owner profile create/update/delete and private image read/write/delete succeeded. Ownership is encoded in the authenticated path, not a mutable field. Counters, roles, status transitions, public profiles and references are absent; unknown fields/paths are denied rather than introducing those permissions. Content-type validation does not inspect file bytes.

## Still required on a real development project/device

Console provisioning and actual SDK config; auth email delivery; online/offline account switching; real private Storage transfer; consent persistence/revocation; Android 13+ notification permission and data-message delivery in foreground/background; Crashlytics test crash receipt; Analytics reporting. A synthetic-config build does not establish any of those behaviors. No production sender/token registration backend, App Check enforcement, cloud backup or public sharing is included. See `FIREBASE_ANDROID.md` for the owner setup checklist.
