import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseArtifactQa(unittest.TestCase):
    def test_apk_validation_rejects_wrong_identity_and_corrupt_archive(self):
        module = load('android_apk_verify')
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'app-debug.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                for name in ('AndroidManifest.xml', 'classes.dex', 'resources.arsc'):
                    archive.writestr(name, 'fixture')
            with patch.object(module.subprocess, 'check_output', side_effect=[
                'Signer #1 certificate DN: CN=Android Debug',
                "package: name='other.app' versionCode='186' versionName='1.7.0-sprint8'\napplication-debuggable"]):
                with self.assertRaisesRegex(ValueError, 'identity'):
                    module.verify(apk, Path(tmp))
            apk.write_bytes(b'not an APK')
            with self.assertRaises(zipfile.BadZipFile):
                module.verify(apk, Path(tmp))

    def test_apk_provenance_records_real_head_instead_of_merge_sha(self):
        module = load('android_apk_verify')
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'app-debug.apk'
            event = Path(tmp) / 'event.json'
            event.write_text(json.dumps({'pull_request': {'head': {'sha': 'reviewed-head'}}}))
            with zipfile.ZipFile(apk, 'w') as archive:
                for name in ('AndroidManifest.xml', 'classes.dex', 'resources.arsc'):
                    archive.writestr(name, 'fixture')
            outputs = ['Signer #1 certificate DN: CN=Android Debug',
                       "package: name='com.vidgravity.app' versionCode='186' versionName='1.7.0-sprint8'\napplication-debuggable", 'merge-checkout\n', 'checkout-tree\n']
            with patch.dict(module.os.environ, {'GITHUB_EVENT_PATH': str(event)}), patch.object(module.subprocess, 'check_output', side_effect=outputs):
                module.verify(apk, Path(tmp))  # GNU sha256sum is deliberately real.
            provenance = json.loads((apk.parent / 'build-provenance.json').read_text())
            self.assertEqual('reviewed-head', provenance['pr_head_commit'])
            self.assertEqual('merge-checkout', provenance['checkout_commit'])
            self.assertEqual(module.hashlib.sha256(apk.read_bytes()).hexdigest(), provenance['sha256'])

    def test_resolved_inventory_keeps_unknown_licenses_explicit(self):
        module = load('android_dependency_inventory')
        with tempfile.TemporaryDirectory() as tmp:
            pom = Path(tmp) / 'sample.group/tool/1.0/hash/tool.pom'
            pom.parent.mkdir(parents=True)
            pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><licenses><license><name>Apache-2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0</url></license></licenses></project>')
            report = module.inventory('sample.group:tool:1.0\nsample.group:unknown:2.0\n', Path(tmp))
            self.assertEqual('declared_in_cached_pom', report['dependencies'][0]['license_status'])
            self.assertEqual('unknown_or_inherited', report['dependencies'][1]['license_status'])
            pom.write_text('<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///private">]><project/>')
            self.assertEqual([], module.inventory('sample.group:tool:1.0', Path(tmp))['dependencies'][0]['licenses'])
