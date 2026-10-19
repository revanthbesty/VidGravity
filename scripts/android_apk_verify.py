"""Validate the actual APK, then record head/checkout provenance and two independent hashes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def verify(apk, tools):
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError('APK ZIP CRC failure')
        required = {'AndroidManifest.xml', 'classes.dex', 'resources.arsc'}
        if not required.issubset(archive.namelist()):
            raise ValueError('APK is missing Android resources or executable code')
    signature = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    if 'CN=Android Debug' not in signature:
        raise ValueError('Expected the debug signing identity')
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not match or match.groups() != ('com.vidgravity.app', '189', '1.10.0-sprint11'):
        raise ValueError('Unexpected APK identity/version: ' + (match.group(0) if match else 'no package'))
    if 'application-debuggable' not in badging:
        raise ValueError('Expected a debuggable APK')
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    (apk.parent / 'SHA256SUMS.txt').write_text(sha + '  ' + apk.name + '\n')
    subprocess.run(['sha256sum', '--check', 'SHA256SUMS.txt'], cwd=apk.parent, check=True)
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text()) if os.environ.get('GITHUB_EVENT_PATH') else {}
    head = event.get('pull_request', {}).get('head', {}).get('sha') or os.environ.get('GITHUB_SHA')
    provenance = {'checkout_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                  'tree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], text=True).strip(),
                  'pr_head_commit': head, 'run_id': os.environ.get('GITHUB_RUN_ID'),
                  'apk': apk.name, 'bytes': apk.stat().st_size, 'sha256': sha,
                  'build_type': 'debug', 'application_id': match[1], 'version_code': int(match[2]),
                  'version_name': match[3], 'zip_crc': 'passed', 'debug_signature': 'verified',
                  'emulator_validation': 'Read actual API29/35 job conclusions; API37 conditional, physical devices untested'}
    (apk.parent / 'build-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    (apk.parent / 'signature.txt').write_text(signature)
    (apk.parent / 'badging.txt').write_text(badging)
    print(json.dumps(provenance, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, default=Path('apps/android/app/build/outputs/apk/debug/app-debug.apk'))
    parser.add_argument('--tools', type=Path, default=Path(os.environ.get('ANDROID_HOME', '/missing-sdk')) / 'build-tools/35.0.0')
    args = parser.parse_args()
    verify(args.apk, args.tools)
