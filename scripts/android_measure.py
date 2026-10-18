"""Host-observed emulator launch/memory samples. No device-speed or battery claims."""
import json
import os
from pathlib import Path
import re
import subprocess
import time

PACKAGE = 'com.vidgravity.app'
COMPONENT = PACKAGE + '/.MainActivity'


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=60)


def measure(output):
    apk = 'apps/android/app/build/outputs/apk/debug/app-debug.apk'
    adb('install', '-r', apk)  # UTP may uninstall the target after connected tests.
    samples = []
    for kind in ('cold_process', 'warm_resume'):
        for sample in range(3):
            if kind == 'cold_process':
                adb('shell', 'am', 'force-stop', PACKAGE)
            else:
                adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
                time.sleep(0.5)
            result = adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
            if not re.search(r'^Status: ok$', result, re.M) or 'Error:' in result:
                raise RuntimeError('Launch failed: ' + result)
            times = {key: int(value) for key, value in re.findall(r'^(ThisTime|TotalTime|WaitTime):\s*(\d+)', result, re.M)}
            samples.append({'kind': kind, 'sample': sample + 1, 'am_start_ms': times, 'raw': result})
            time.sleep(1)
    memory = adb('shell', 'dumpsys', 'meminfo', PACKAGE)
    match = re.search(r'TOTAL PSS:\s*(\d+)', memory)
    if not match:
        match = re.search(r'^\s*TOTAL\s+(\d+)', memory, re.M)
    report = {'environment': 'GitHub Actions emulator', 'api': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
              'head': os.environ.get('GITHUB_SHA'), 'samples': samples,
              'pss_kib': int(match[1]) if match else None,
              'limitations': ['am start -W is activity launch time, not full interaction latency',
                              'cold_process does not clear kernel disk caches', 'warm_resume resumes the same Activity',
                              'No comparable pre-change benchmark; no speed improvement claimed',
                              'No physical battery, thermal, hardware-camera or long-session profiling']}
    output.mkdir(parents=True, exist_ok=True)
    (output / 'launch-memory.json').write_text(json.dumps(report, indent=2) + '\n')
    (output / 'meminfo.txt').write_text(memory)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    measure(Path('work/android-smoke/measurements'))
