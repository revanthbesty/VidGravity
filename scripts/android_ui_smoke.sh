#!/usr/bin/env bash
# Run inside the CI emulator action. Preserve the test result while collecting evidence.
set +e
mkdir -p work/android-smoke
# UTP can uninstall the target APK before the Gradle task returns. Copy evidence while it runs.
while true; do
    adb pull /sdcard/Android/data/com.vidgravity.app/files/smoke-evidence work/android-smoke/ >/dev/null 2>&1
    sleep 1
done &
evidence_pid=$!
gradle -p apps/android connectedDebugAndroidTest --stacktrace
smoke_code=$?
kill "$evidence_pid" 2>/dev/null
wait "$evidence_pid" 2>/dev/null
adb logcat -d -v threadtime > work/android-smoke/logcat.txt
adb logcat -d -v threadtime -s VGStartup VGSmoke AndroidRuntime
adb shell dumpsys activity lastanr > work/android-smoke/lastanr.txt
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
exit "$smoke_code"
