#!/usr/bin/env python3
"""P171 operator preflight. Checks local tools/configuration without printing secret values."""
import argparse
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REQUIRED_SECRETS = ('RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET', 'RAZORPAY_WEBHOOK_SECRET')


def command_ok(command, runner=subprocess.run):
    try:
        result = runner(command, capture_output=True, text=True, timeout=10, check=False)
        return result.returncode == 0
    except (OSError, subprocess.SubprocessError, TimeoutError):
        return False


def build_report(env=None, which=shutil.which, runner=subprocess.run, repo=ROOT):
    env = os.environ if env is None else env
    docker = which('docker')
    compose_file = repo / 'compose.production.example.yml'
    env_file = repo / '.env.production'
    url = (env.get('VIDGRAVITY_PUBLIC_BASE_URL') or '').strip()
    secrets_present = all((env.get(key) or '').strip() for key in REQUIRED_SECRETS)
    checks = []

    def add(key, status, detail, action):
        checks.append({'key': key, 'status': status, 'detail': detail, 'action': action})

    add('docker_cli', 'PASS' if docker else 'BLOCKED', 'Docker CLI found' if docker else 'Docker CLI is missing', 'Install Docker Desktop on Windows or Docker Engine on Linux.')
    daemon_ok = bool(docker and command_ok([docker, 'info', '--format', '{{.ServerVersion}}'], runner=runner))
    add('docker_daemon', 'PASS' if daemon_ok else 'BLOCKED', 'Docker daemon responds' if daemon_ok else 'Docker daemon unavailable or not running', 'Start Docker Desktop/Engine, then retry this preflight.')
    compose_ok = bool(docker and command_ok([docker, 'compose', 'version'], runner=runner))
    add('compose_plugin', 'PASS' if compose_ok else 'BLOCKED', 'Docker Compose plugin responds' if compose_ok else 'Docker Compose plugin unavailable', 'Install/enable Docker Compose v2.')
    add('compose_file', 'PASS' if compose_file.is_file() else 'BLOCKED', 'Production compose example exists' if compose_file.is_file() else 'Production compose example missing', 'Restore compose.production.example.yml from the release package.')
    add('public_https_url', 'PASS' if url.startswith('https://') else 'BLOCKED', 'Public HTTPS base URL is configured' if url.startswith('https://') else 'VIDGRAVITY_PUBLIC_BASE_URL is missing or not HTTPS', 'Set the public HTTPS URL only after DNS and TLS are configured.')
    add('payment_secrets', 'PASS' if secrets_present else 'BLOCKED', 'Required Razorpay variable names are present (values hidden)' if secrets_present else 'One or more Razorpay environment variables are missing (values hidden)', 'Set test-mode secrets in the host secret manager; do not commit .env.production.')
    add('env_file', 'PASS' if not env_file.exists() or (os.name == 'nt' or (env_file.stat().st_mode & 0o077) == 0) else 'WARN', 'No local .env.production file' if not env_file.exists() else ('Local env file exists; Windows ACLs require manual review' if os.name == 'nt' else 'Local env file permissions are restrictive' if (env_file.stat().st_mode & 0o077) == 0 else 'Local env file permissions are too broad'), 'Prefer host-managed secrets; on Linux restrict env-file permissions to owner only.')
    # Validate compose syntax only when a working Docker daemon and Compose are present.
    if compose_ok and daemon_ok and compose_file.is_file():
        valid = command_ok([docker, 'compose', '-f', str(compose_file), 'config', '--quiet'], runner=runner)
        add('compose_syntax', 'PASS' if valid else 'BLOCKED', 'Compose configuration parsed' if valid else 'Compose configuration validation failed', 'Run docker compose config and resolve the reported configuration issue.')
    else:
        add('compose_syntax', 'NOT_RUN', 'Skipped because Docker/Compose prerequisites are not available', 'Rerun preflight on the deployment host.')

    blocked = sum(c['status'] == 'BLOCKED' for c in checks)
    warnings = sum(c['status'] == 'WARN' for c in checks)
    not_run = sum(c['status'] == 'NOT_RUN' for c in checks)
    passed = sum(c['status'] == 'PASS' for c in checks)
    return {
        'milestone': 'P171', 'name': 'Deployment Operator Preflight',
        'generated_at_utc': datetime.now(timezone.utc).isoformat(timespec='seconds'),
        'result': 'READY_FOR_OPERATOR_DEPLOYMENT' if blocked == 0 and not_run == 0 and warnings == 0 else 'BLOCKED',
        'summary': {'total': len(checks), 'passed': passed, 'blocked': blocked, 'warnings': warnings, 'not_run': not_run},
        'checks': checks,
        'security_note': 'Secret values are never included in this report. Passing this preflight does not prove a live deployment, successful payments/downloads, egress isolation, or security acceptance.'
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default=str(ROOT / 'P171_OPERATOR_PREFLIGHT_REPORT.json'))
    args = parser.parse_args(argv)
    report = build_report()
    Path(args.output).write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    s = report['summary']
    print(f"P171 operator preflight: {report['result']} ({s['passed']} pass, {s['blocked']} blocked, {s['warnings']} warnings, {s['not_run']} not run)")
    for c in report['checks']:
        print(f"[{c['status']}] {c['key']}: {c['detail']}")
    print(f"Report: {args.output}")
    return 0 if report['result'] == 'READY_FOR_OPERATOR_DEPLOYMENT' else 2

if __name__ == '__main__':
    sys.exit(main())
