# Firebase for the native Android app

This integration targets `apps/android`, not the generated Capacitor wrapper at `android/`. No Firebase project was selected or changed. Existing backend licensing, payments and download authorization are unchanged; Firebase sign-in does not authorize those endpoints.

## Build and compatibility

Use JDK 17, Gradle 8.10.2, SDK 35, existing AGP 8.8.2 and Kotlin 2.0.21. BoM 33.16.0 is a deliberate compatibility baseline, not the newest SDK. Use main modules, with no deprecated `-ktx` artifacts. Google Services 4.4.4 and Crashlytics 3.0.8 meet Crashlytics v3's AGP 8.1+ / Google Services 4.4.1+ requirements. Upgrade SDK/toolchain versions together after compilation and device tests.

```sh
gradle -p apps/android testDebugUnitTest assembleDebug
# With your actual downloaded config in apps/android/app/google-services.json:
gradle -p apps/android -PVG_FIREBASE_ENABLED=true testDebugUnitTest assembleDebug
```

Firebase is disabled by default. An enabled build without the config fails with an actionable message. Initialization is explicit; unconfigured builds do not initialize SDKs. Client identifiers are not secret, but project config files are deliberately ignored. Never commit Admin credentials, server keys, signing keys, tokens or service accounts. The `firebase-tests/google-services.test.json` file contains synthetic build-only identifiers, never usable production credentials.

## Console setup required from the owner

1. Choose separate development/production projects. Register Android package **com.vidgravity.app** and privately download its config to the path above. Add signing SHA-1/SHA-256 fingerprints before adding Google/phone providers. This PR uses email/password only.
2. Enable Authentication Email/Password. Review password policy, enumeration protection, verification/reset templates and support email. Test registration, sign-in, password reset, email verification and sign-out on a test device. No anonymous sign-in occurs automatically.
3. Identify the Firestore edition, database ID and region before deployment. The prototype rules/config target the **Standard edition default database**; no project or edition was identifiable from this repository. Adapt and test first if the selected instance differs. Create the intended database in locked mode.
4. Provision the default Storage bucket in the intended region and review billing requirements. These rules permit only private JPEG/PNG/WebP images up to 10 MiB in `users/{uid}/images/{id}`. They do not enable library backup or public sharing. MIME metadata is not content inspection; inspect/re-encode files on a trusted backend before exposing them to others.
5. Review the rules and existing indexes before deliberately deploying to the selected development project. The empty index file is only a template. No automatic rules deployment or project alias is configured. Example owner-operated command: `firebase deploy --project YOUR_DEV_PROJECT --only firestore:rules,firestore:indexes,storage`.
6. Link Analytics and enable Crashlytics in the console. Both collection flags default off. Settings offers separate opt-ins and revocation. Verify a temporary development-only test crash, restart and confirm receipt, then remove the test crash code. No forced crash code is included.
7. Enable Cloud Messaging HTTP v1 for a trusted sender. Use Settings to grant Android notification permission and retrieve a token. Send **data-only** messages with `type=workspace_update`. Never send notification payloads: Android/FCM can display those in the background outside this service's filtering. Payload URLs/commands are never executed; only generic text is displayed.
8. Production targeted messaging requires a trusted ID-token-verifying backend for token registration, rotation (`onNewToken`), stale-token cleanup and disassociation on sign-out. This PR deliberately does not log tokens, register them to accounts, or include sender credentials. It does not provision a production sender. Re-fetch the current token when enabling; disable asynchronously deletes it and must be retried on failure.
9. Before public release, evaluate App Check with Play Integrity, test monitoring before enforcement, configure quotas/billing alerts, retention and privacy disclosures. App Check does not replace authentication or rules.

## Behavior and limits

Enabled builds show a Firebase account panel in Settings: sign-in/create/reset, verification email, sign-out, private display-name writes and separate telemetry/notification controls. Auth errors are generic; passwords are not saved in UI state. Firestore uses memory cache to avoid persistent disk copies of private profile data. No roles, licenses, payments, device tokens or nested documents are writable through these rules.

`FirebaseServices` also exposes private image upload/download tasks with bounded paths, sizes and MIME types. No media is automatically uploaded; there is no image picker or cloud backup UI. Authenticated reads use the SDK rather than public bearer download links. Callers must handle task failure/cancellation. These are opt-in foundations for future sync features.

Analytics/Crashlytics SDK choices persist across restarts. Revoking analytics resets local analytics data; revoking Crashlytics deletes unsent reports. Notification disable prevents the custom service from displaying data messages and requests token deletion. No analytics events, URLs, credentials or user identifiers are explicitly logged by this integration.

## Tests

With Node 22 and JDK 21 for emulator tooling:

```sh
cd firebase-tests
npm ci
npm test
```

The tests use loopback emulators and `demo-vidgravity`, never a live project. They cover owner CRUD, anonymous/cross-user denial, listing, nested/unknown paths, privilege injection, invalid creates/updates, missing fields, timestamp spoofing and invalid/oversized image overwrites. Android JUnit tests cover path traversal, profile bounds and upload limits. See `FIREBASE_VALIDATION.md` for actual executed results and pending device checks.

References: [Android setup](https://firebase.google.com/docs/android/setup), [SDK versions](https://firebase.google.com/support/release-notes/android), [Crashlytics prerequisites](https://firebase.google.com/docs/crashlytics/android/get-started), [Messaging setup](https://firebase.google.com/docs/cloud-messaging/android/get-started).
