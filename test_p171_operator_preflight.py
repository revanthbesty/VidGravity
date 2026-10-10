import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import p171_operator_preflight as p171

class P171OperatorPreflightTests(unittest.TestCase):
    def test_missing_docker_and_secrets_blocks_without_leaking_values(self):
        report = p171.build_report(env={'RAZORPAY_KEY_SECRET':'very-secret-value'}, which=lambda name: None, repo=Path('/nonexistent'))
        self.assertEqual(report['result'], 'BLOCKED')
        serialized = str(report)
        self.assertNotIn('very-secret-value', serialized)
        self.assertTrue(any(c['key'] == 'docker_daemon' and c['status'] == 'BLOCKED' for c in report['checks']))

    def test_https_and_all_secrets_are_detected(self):
        env = {'VIDGRAVITY_PUBLIC_BASE_URL':'https://example.invalid', 'RAZORPAY_KEY_ID':'id', 'RAZORPAY_KEY_SECRET':'secret', 'RAZORPAY_WEBHOOK_SECRET':'hook'}
        report = p171.build_report(env=env, which=lambda name: None, repo=Path('/nonexistent'))
        by_key = {c['key']: c for c in report['checks']}
        self.assertEqual(by_key['public_https_url']['status'], 'PASS')
        self.assertEqual(by_key['payment_secrets']['status'], 'PASS')

    def test_compose_command_failure_is_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            repo = Path(td)
            (repo / 'compose.production.example.yml').write_text('services: {}')
            def fake_run(command, **kwargs):
                class R: returncode = 1
                return R()
            report = p171.build_report(env={}, which=lambda name: '/usr/bin/docker', runner=fake_run, repo=repo)
            self.assertEqual(report['result'], 'BLOCKED')
            self.assertTrue(any(c['key'] == 'docker_daemon' and c['status'] == 'BLOCKED' for c in report['checks']))

    def test_secret_values_never_appear_in_report(self):
        secret = 'RAZORPAY-VERY-SECRET-123'
        report = p171.build_report(env={'RAZORPAY_KEY_ID':secret, 'RAZORPAY_KEY_SECRET':secret, 'RAZORPAY_WEBHOOK_SECRET':secret}, which=lambda name: None, repo=Path('/nonexistent'))
        self.assertNotIn(secret, str(report))

if __name__ == '__main__':
    unittest.main()
