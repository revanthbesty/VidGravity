# P161 — Android Companion Build Scaffold

## Implemented
- Capacitor 7 configuration with a clean `android-web/` bundle.
- npm scripts and dependency declarations for native Android project generation/build.
- Settings control for a companion API base URL; remote URLs must use HTTPS.
- API calls use the configured base URL when present, while same-origin browser mode remains the default.
- Server CORS allow-list via `VIDGRAVITY_ALLOWED_ORIGINS`; default allowed origin is `https://localhost`, not a wildcard.
- Android build and manual acceptance instructions.

## Automated validation
- 98 unit tests passed, including six P161 scaffold tests.
- Legacy static smoke scripts passed: 10/10, 11/11, and 12/12.
- Node.js syntax checks passed for both source and Android-bundled `app.js`.

## Not yet verified
No Android SDK/device was available for this run. No native Android project was generated and no APK was built or installed. Run `npm install`, `npx cap add android`, then `npm run android:sync` on a machine with Android Studio/SDK. A reachable HTTPS companion API and matching CORS allow-list are required for server-side downloads and licensing.
