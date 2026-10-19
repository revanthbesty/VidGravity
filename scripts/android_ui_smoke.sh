#!/usr/bin/env bash
# Run inside the CI emulator action. Preserve the test result while collecting evidence.
set +e
mkdir -p work/android-smoke
# Encoding tests can run without an Activity. Keep the CI display awake so the
# following window-focus assertions are not intercepted by the keyguard.
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
# UTP can uninstall the target APK before the Gradle task returns. Copy evidence while it runs.
while true; do
    adb pull /sdcard/Android/data/com.vidgravity.app/files/smoke-evidence work/android-smoke/ >/dev/null 2>&1
    sleep 1
done &
evidence_pid=$!
# Permission denial must run before tests grant CAMERA. UTP installs/removes each
# phase independently; never revoke this permission inside a running test process.
permission_class=com.vidgravity.app.CameraPermissionRecoveryTest
gradle -p apps/android connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$permission_class" --stacktrace
permission_code=$?
mkdir -p work/android-smoke/permission-phase
cp -R apps/android/app/build/outputs/androidTest-results work/android-smoke/permission-phase/ 2>/dev/null
cp -R apps/android/app/build/reports/androidTests work/android-smoke/permission-phase/ 2>/dev/null
# The class above was executed, not disabled. Keep its JUnit results separately;
# all remaining tests run on a second fresh install with their original assertions.
gradle -p apps/android connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass="$permission_class" --stacktrace
smoke_code=$?
if [ "$permission_code" -ne 0 ]; then smoke_code=$permission_code; fi
kill "$evidence_pid" 2>/dev/null
wait "$evidence_pid" 2>/dev/null
adb logcat -d -v threadtime > work/android-smoke/logcat.txt
adb logcat -d -v threadtime -s VGStartup VGSmoke VGStudio VGCamera VGPerf VGTrace AndroidRuntime
adb shell dumpsys activity lastanr > work/android-smoke/lastanr.txt
adb shell dumpsys window > work/android-smoke/windows.txt
adb shell dumpsys power > work/android-smoke/power.txt
python - <<'PY_REPORT'
from pathlib import Path
import xml.etree.ElementTree as ET
for report in Path('apps/android/app/build/outputs/androidTest-results').rglob('*.xml'):
    try:
        tree = ET.parse(report)
    except ET.ParseError:
        continue
    for case in tree.iter('testcase'):
        for failure in list(case):
            if failure.tag in ('failure', 'error'):
                print(case.get('classname'), case.get('name'), failure.get('message'))
                print((failure.text or '')[:16000])
PY_REPORT
adb pull /sdcard/Android/data/com.vidgravity.app/files/smoke-evidence work/android-smoke/ || true
if [ "$smoke_code" -eq 0 ]; then
    python scripts/android_measure.py
    measurement_code=$?
    if [ "$measurement_code" -ne 0 ]; then exit "$measurement_code"; fi
fi
exit "$smoke_code"
