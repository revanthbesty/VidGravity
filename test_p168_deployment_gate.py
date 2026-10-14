import tempfile, unittest
from pathlib import Path
from p168_deployment_gate import build_report
class P168GateTests(unittest.TestCase):
    def test_default_env_fails_closed(self):
        r=build_report(env={},which=lambda _:None,repo=Path('/definitely/missing'))
        self.assertEqual(r['result'],'BLOCKED')
        self.assertEqual(r['blocked_count'],7)
    def test_configuration_does_not_claim_live_acceptance(self):
        env={'VIDGRAVITY_PUBLIC_BASE_URL':'https://example.invalid','VIDGRAVITY_LIVE_TEST_URL':'https://example.invalid/test.mp4',
             'RAZORPAY_KEY_ID':'x','RAZORPAY_KEY_SECRET':'y','RAZORPAY_WEBHOOK_SECRET':'z','VIDGRAVITY_PROVIDER_EGRESS_ISOLATED':'true'}
        r=build_report(env=env,which=lambda n: '/bin/'+n if n in ('docker','yt-dlp') else None,repo=Path('/definitely/missing'))
        self.assertIn(r['result'],('BLOCKED','READY_FOR_EXTERNAL_ACCEPTANCE'))
        self.assertIn('does not prove',r['important_note'])
        self.assertNotIn('RAZORPAY_KEY_SECRET',str(r))
        self.assertTrue(any(c['key']=='android_project' and c['status']=='BLOCKED' for c in r['checks']))
    def test_detects_native_gradle_project_under_apps_android(self):
        with tempfile.TemporaryDirectory() as tmp:
            android = Path(tmp) / 'apps' / 'android'
            (android / 'app').mkdir(parents=True)
            (android / 'settings.gradle.kts').write_text('rootProject.name = "VidGravity"')
            (android / 'app' / 'build.gradle.kts').write_text('plugins { id("com.android.application") }')
            env={'VIDGRAVITY_PUBLIC_BASE_URL':'https://example.invalid',
                 'VIDGRAVITY_LIVE_TEST_URL':'https://example.invalid/test.mp4',
                 'RAZORPAY_KEY_ID':'x','RAZORPAY_KEY_SECRET':'y',
                 'RAZORPAY_WEBHOOK_SECRET':'z','VIDGRAVITY_PROVIDER_EGRESS_ISOLATED':'true'}
            report=build_report(env=env,which=lambda n: '/bin/'+n if n in ('docker','yt-dlp') else None,repo=Path(tmp))
            android_check=next(c for c in report['checks'] if c['key']=='android_project')
            self.assertEqual(android_check['status'],'READY')
            self.assertIn('Gradle Android project configuration found',android_check['evidence'])
    def test_secrets_never_echoed(self):
        r=build_report(env={'RAZORPAY_KEY_ID':'secret-value'},which=lambda _:None,repo=Path('/missing'))
        self.assertNotIn('secret-value',str(r))
if __name__=='__main__': unittest.main()
