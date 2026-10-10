# P161 — Android Companion Build Scaffold

## What is included
- Capacitor 7 project configuration (`capacitor.config.json`) and npm scripts.
- Minimal `android-web/` bundle containing only the browser runtime assets (not the Python server or secrets).
- In-app companion API URL setting for the Android wrapper.
- Companion server CORS allow-list support is required before connecting a different Android origin (see below).

## Build on a machine with Node.js LTS, Android Studio, and Android SDK
1. Install Node.js LTS and Android Studio with Android SDK/platform tools.
2. From this folder run `npm install`.
3. Run `npx cap add android` once to generate the native Android project.
4. Run `npm run android:sync`.
5. Run `npm run android:open`, choose an emulator/device, then build/run from Android Studio.
6. For a debug APK, run `cd android` then `gradlew.bat assembleDebug` on Windows (or `./gradlew assembleDebug` on macOS/Linux).

## Backend connection
The wrapper loads a bundled local UI. In Settings, configure the Companion server address to a reachable **HTTPS** base URL. The server must permit the Android origin and should be exposed only through HTTPS. Do not use `0.0.0.0`, a private server URL over public Wi-Fi, or an unauthenticated public endpoint. Local HTTP is accepted by the UI only for localhost development.

## Important limits
This is a build scaffold, not a compiled APK. This environment does not have a verified Android SDK/device and did not run `npm install`, generate the native project, or execute an emulator/device test. Live downloads and license calls require a reachable companion server. Use the server CORS allow-list described in `P161_ANDROID_TEST_PLAN.md` before cross-origin API use.
