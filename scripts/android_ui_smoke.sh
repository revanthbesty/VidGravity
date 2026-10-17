#!/usr/bin/env bash
# Run inside the CI emulator action. Preserve the test result while collecting evidence.
set +e
gradle -p apps/android connectedDebugAndroidTest --stacktrace
smoke_code=$?
mkdir -p work/android-smoke
adb logcat -d -v threadtime > work/android-smoke/logcat.txt
adb pull /sdcard/Android/data/com.vidgravity.app/files/smoke-evidence work/android-smoke/ || true
exit "$smoke_code"
