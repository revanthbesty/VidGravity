#!/usr/bin/env python3
"""P168 deployment gate report. Configuration presence is not live acceptance evidence."""
import json, os, shutil, sys
from pathlib import Path

def check(key, label, ok, evidence, action, kind='external'):
    return {'key':key,'label':label,'status':'READY' if ok else 'BLOCKED','kind':kind,
            'evidence':evidence,'required_action':action}

def build_report(env=None, which=shutil.which, repo=Path(__file__).resolve().parent):
    env = os.environ if env is None else env
    url=(env.get('VIDGRAVITY_PUBLIC_BASE_URL') or '').strip()
    live=(env.get('VIDGRAVITY_LIVE_TEST_URL') or '').strip()
    pay=['RAZORPAY_KEY_ID','RAZORPAY_KEY_SECRET','RAZORPAY_WEBHOOK_SECRET']
    # The native project lives under apps/android. CI invokes Gradle directly, so
    # detect the Gradle project files rather than requiring a checked-in wrapper.
    android_project_paths = (
        'apps/android/settings.gradle.kts',
        'apps/android/build.gradle.kts',
        'apps/android/app/build.gradle.kts',
        'android/settings.gradle', 'android/settings.gradle.kts',
        'android/build.gradle', 'android/build.gradle.kts',
        'settings.gradle', 'settings.gradle.kts',
        'build.gradle', 'build.gradle.kts',
    )
    android=any((repo/p).is_file() for p in android_project_paths)
    checks=[
      check('https_url','Public HTTPS URL',url.startswith('https://'),'HTTPS URL configured' if url.startswith('https://') else 'VIDGRAVITY_PUBLIC_BASE_URL is absent or not HTTPS','Deploy behind a valid TLS reverse proxy and set VIDGRAVITY_PUBLIC_BASE_URL','configuration'),
      check('docker_cli','Docker CLI',bool(which('docker')),'Docker CLI detected' if which('docker') else 'Docker CLI not detected in this environment','Build and start the image on a Docker-capable host; record image and runtime results'),
      check('yt_dlp','Provider engine',bool(which('yt-dlp')),'yt-dlp executable detected' if which('yt-dlp') else 'yt-dlp executable not detected','Install the pinned provider dependency and run the authorized live-provider test','configuration'),
      check('live_url','Authorized live-provider URL',live.startswith(('https://','http://')),'A URL was supplied (value intentionally redacted)' if live.startswith(('https://','http://')) else 'No HTTP(S) test URL supplied','Use an authorized, non-DRM test URL and run provider_live_acceptance.py','operator'),
      check('payment_config','Payment secret configuration',all(env.get(k) for k in pay),'Required payment variable names are configured' if all(env.get(k) for k in pay) else 'One or more required Razorpay variables are missing; values are never displayed','Configure secrets in the deployment secret manager and test order, signature, webhook, and idempotency','configuration'),
      check('android_project','Native Android wrapper',android,'Gradle wrapper found' if android else 'No Gradle wrapper found in expected project locations','Generate native wrapper, build APK, then test emulator and physical device','configuration'),
      check('egress_isolation','Provider egress isolation',env.get('VIDGRAVITY_PROVIDER_EGRESS_ISOLATED','').lower()=='true','Operator assertion is set; it is not independent proof' if env.get('VIDGRAVITY_PROVIDER_EGRESS_ISOLATED','').lower()=='true' else 'No explicit egress-isolation assertion','Enforce and independently test OS/container network policy blocking private, loopback, link-local and metadata endpoints','operator'),
    ]
    ready=sum(c['status']=='READY' for c in checks)
    return {'milestone':'P168','name':'Production Deployment Gate Closure','result':'READY_FOR_EXTERNAL_ACCEPTANCE' if ready==len(checks) else 'BLOCKED',
      'ready_count':ready,'blocked_count':len(checks)-ready,'checks':checks,
      'important_note':'READY means a local prerequisite/configuration signal only. It does not prove a successful deployment, payment, download, Android installation, or security review.',
      'remaining_manual_gates':['Public HTTPS health check from outside deployment network','Authorized provider download and output inspection','Razorpay test-mode order/signature/webhook/idempotency evidence','Native APK install and acceptance on emulator and physical device','Independent verification of provider-process egress restrictions','Independent security review before public launch']}

def main():
    report=build_report()
    out=Path(__file__).resolve().parent/'P168_DEPLOYMENT_GATE_REPORT.json'
    out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(f"P168 release gate: {report['result']} ({report['ready_count']} ready, {report['blocked_count']} blocked)")
    for c in report['checks']:
        print(f"[{c['status']}] {c['label']}: {c['evidence']}")
    print(f'Report: {out.name}')
    return 0 if report['result']=='READY_FOR_EXTERNAL_ACCEPTANCE' else 2
if __name__=='__main__': sys.exit(main())
