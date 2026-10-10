#!/usr/bin/env python3
"""P170 unified release acceptance orchestrator; fails closed on missing external evidence."""
import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

import p168_deployment_gate as p168
import p169_deployment_probe as p169

ROOT = Path(__file__).resolve().parent


def build_acceptance(env=None, run_external_probe=False, gate_builder=p168.build_report, probe_runner=p169.probe):
    env = os.environ if env is None else env
    gate = gate_builder(env=env, repo=ROOT)
    probe_url = (env.get('VIDGRAVITY_PUBLIC_BASE_URL') or '').strip()
    probe_report = None
    if run_external_probe:
        if not probe_url:
            probe_report = {
                'milestone': 'P169', 'name': 'External Deployment Verification',
                'target_configured': False, 'result': 'BLOCKED',
                'checks': [{'key': 'target_url', 'status': 'FAIL', 'detail': 'VIDGRAVITY_PUBLIC_BASE_URL is not configured'}],
                'important_note': 'No external request was made because no target URL was configured.'
            }
        else:
            probe_report = probe_runner(probe_url)

    gates = []
    for item in gate.get('checks', []):
        gates.append({'source': 'P168', 'key': item.get('key'), 'label': item.get('label'),
                      'status': 'PASS' if item.get('status') == 'READY' else 'BLOCKED',
                      'kind': item.get('kind', 'external'), 'evidence': item.get('evidence', '')})
    if probe_report is None:
        gates.append({'source': 'P169', 'key': 'external_probe', 'label': 'External HTTPS deployment probe',
                      'status': 'NOT_RUN', 'kind': 'external',
                      'evidence': 'Run with --probe and configure VIDGRAVITY_PUBLIC_BASE_URL to collect live evidence.'})
    else:
        for item in probe_report.get('checks', []):
            status = item.get('status')
            gates.append({'source': 'P169', 'key': item.get('key'), 'label': item.get('key', 'external check'),
                          'status': 'PASS' if status == 'PASS' else ('DEGRADED' if status == 'DEGRADED' else 'BLOCKED'),
                          'kind': 'external', 'evidence': item.get('detail', '')})

    passed = sum(g['status'] == 'PASS' for g in gates)
    blocked = sum(g['status'] == 'BLOCKED' for g in gates)
    not_run = sum(g['status'] == 'NOT_RUN' for g in gates)
    degraded = sum(g['status'] == 'DEGRADED' for g in gates)
    all_pass = bool(gates) and all(g['status'] == 'PASS' for g in gates)
    result = 'ACCEPTED' if all_pass else ('DEGRADED' if degraded and not blocked and not not_run else 'BLOCKED')
    return {
        'milestone': 'P170', 'name': 'Unified Release Acceptance',
        'generated_at_utc': datetime.now(timezone.utc).isoformat(timespec='seconds'),
        'result': result, 'release_allowed': all_pass,
        'summary': {'total_checks': len(gates), 'passed': passed, 'blocked': blocked,
                    'degraded': degraded, 'not_run': not_run},
        'checks': gates,
        'source_reports': {'p168_gate_result': gate.get('result'),
                           'p169_probe_result': probe_report.get('result') if probe_report else 'NOT_RUN'},
        'important_note': 'Configuration presence is not live acceptance. Release is allowed only when every configured gate and the external probe pass. This report does not replace provider-download, payment/webhook, Android device, egress-isolation, or independent security evidence.',
        'required_external_evidence': [
            'Successful public HTTPS health and homepage probe',
            'Authorized non-DRM provider download with output inspection',
            'Razorpay test-mode order, signature, webhook, and idempotency verification',
            'Native Android APK build and emulator plus physical-device acceptance',
            'Independent verification of provider-process egress restrictions',
            'Independent security review before public launch'
        ]
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probe', action='store_true', help='Run the external HTTPS probe against VIDGRAVITY_PUBLIC_BASE_URL')
    parser.add_argument('--output', default=str(ROOT / 'P170_RELEASE_ACCEPTANCE_REPORT.json'))
    args = parser.parse_args(argv)
    report = build_acceptance(run_external_probe=args.probe)
    Path(args.output).write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    s = report['summary']
    print(f"P170 release acceptance: {report['result']} ({s['passed']} pass, {s['blocked']} blocked, {s['degraded']} degraded, {s['not_run']} not run)")
    for item in report['checks']:
        print(f"[{item['status']}] {item['source']} {item['label']}: {item['evidence']}")
    print(f"Report: {args.output}")
    return 0 if report['release_allowed'] else 2

if __name__ == '__main__':
    sys.exit(main())
