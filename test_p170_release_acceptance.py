import unittest
import p170_release_acceptance as p

class P170AcceptanceTests(unittest.TestCase):
    def gate(self, env=None, repo=None):
        return {'result':'BLOCKED','checks':[
            {'key':'a','label':'configured prerequisite','status':'READY','kind':'configuration','evidence':'configured'},
            {'key':'b','label':'external prerequisite','status':'BLOCKED','kind':'external','evidence':'missing'}]}
    def probe_pass(self, url):
        return {'result':'PASS','checks':[{'key':'health','status':'PASS','detail':'HTTP 200'}, {'key':'homepage','status':'PASS','detail':'HTTP 200'}]}
    def probe_degraded(self, url):
        return {'result':'DEGRADED','checks':[{'key':'health','status':'DEGRADED','detail':'provider missing'}]}
    def test_missing_external_probe_never_allows_release(self):
        r=p.build_acceptance(env={}, run_external_probe=False, gate_builder=self.gate)
        self.assertEqual(r['result'],'BLOCKED'); self.assertFalse(r['release_allowed'])
        self.assertEqual(r['summary']['not_run'],1)
    def test_missing_url_when_probe_requested_blocks_without_request(self):
        r=p.build_acceptance(env={}, run_external_probe=True, gate_builder=self.gate)
        self.assertEqual(r['source_reports']['p169_probe_result'],'BLOCKED')
        self.assertFalse(r['release_allowed'])
    def test_external_probe_pass_does_not_override_other_blocker(self):
        r=p.build_acceptance(env={'VIDGRAVITY_PUBLIC_BASE_URL':'https://example.com'}, run_external_probe=True, gate_builder=self.gate, probe_runner=self.probe_pass)
        self.assertEqual(r['source_reports']['p169_probe_result'],'PASS')
        self.assertEqual(r['result'],'BLOCKED'); self.assertFalse(r['release_allowed'])
    def test_all_gates_and_probe_pass_can_accept(self):
        def ready_gate(env=None, repo=None):
            return {'result':'READY_FOR_EXTERNAL_ACCEPTANCE','checks':[{'key':'a','label':'prerequisite','status':'READY','evidence':'ok'}]}
        r=p.build_acceptance(env={'VIDGRAVITY_PUBLIC_BASE_URL':'https://example.com'}, run_external_probe=True, gate_builder=ready_gate, probe_runner=self.probe_pass)
        self.assertEqual(r['result'],'ACCEPTED'); self.assertTrue(r['release_allowed'])
    def test_degraded_probe_is_not_release_acceptance(self):
        def ready_gate(env=None, repo=None): return {'result':'READY','checks':[]}
        r=p.build_acceptance(env={'VIDGRAVITY_PUBLIC_BASE_URL':'https://example.com'}, run_external_probe=True, gate_builder=ready_gate, probe_runner=self.probe_degraded)
        self.assertEqual(r['result'],'DEGRADED'); self.assertFalse(r['release_allowed'])

if __name__=='__main__': unittest.main()
