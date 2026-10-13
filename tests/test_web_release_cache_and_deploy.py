import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WebReleaseCacheAndDeployTests(unittest.TestCase):
    def test_pwa_asset_refs_are_bumped_for_this_release(self):
        html = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
        self.assertIn('styles.css?v=p175', html)
        self.assertIn('app.js?v=p175', html)
        self.assertIn('social-export.js?v=p175', html)

    def test_service_worker_uses_new_shell_cache_version(self):
        sw = (ROOT / "site" / "sw.js").read_text(encoding="utf-8")
        self.assertIn('const CACHE_NAME = "vidgravity-p175-shell-v1";', sw)

    def test_cloudflare_deploy_workflow_requires_explicit_credentials(self):
        workflow = (ROOT / ".github" / "workflows" / "cloudflare-wpa-deploy.yml").read_text(encoding="utf-8")
        self.assertIn("CLOUDFLARE_API_TOKEN", workflow)
        self.assertIn("CLOUDFLARE_ACCOUNT_ID", workflow)
        self.assertIn("cloudflare/wrangler-action@v3", workflow)
        self.assertIn("deploy --config wrangler.jsonc", workflow)


if __name__ == "__main__":
    unittest.main()
