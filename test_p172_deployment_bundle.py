from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent

class P172DeploymentBundleTests(unittest.TestCase):
    def test_proxy_auth_and_tls_template(self):
        caddy = (ROOT / 'Caddyfile.production').read_text()
        self.assertIn('basic_auth', caddy)
        self.assertIn('reverse_proxy vidgravity:5173', caddy)
        self.assertIn('{$VIDGRAVITY_DOMAIN}', caddy)

    def test_backend_not_published(self):
        compose = (ROOT / 'compose.production.yml').read_text()
        app_service = compose.split('  proxy:', 1)[0]
        self.assertIn('expose:', app_service)
        self.assertNotIn('"5173:5173"', app_service)
        self.assertIn('cap_drop:', app_service)
        self.assertIn('read_only: true', app_service)

    def test_template_has_no_live_secret(self):
        env = (ROOT / '.env.production.example').read_text()
        self.assertIn('VIDGRAVITY_BASIC_AUTH_HASH=', env)
        self.assertIn('REPLACE_WITH_REAL_CADDY_BCRYPT_HASH', env)
        self.assertNotIn('RAZORPAY_KEY_SECRET=', env)

    def test_operator_script_fails_closed_without_env(self):
        script = (ROOT / 'deploy_p172.sh').read_text()
        self.assertIn('Missing $ENV_FILE', script)
        self.assertIn('docker compose', script)
        self.assertIn('does not verify external downloads', script)

if __name__ == '__main__':
    unittest.main(verbosity=2)
