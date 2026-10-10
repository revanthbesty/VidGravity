import io, json, unittest
from unittest.mock import patch
import p169_deployment_probe as p

class Headers(dict):
    pass
class Response:
    def __init__(self, body, status=200, headers=None):
        self.body=body; self.status=status; self.headers=Headers(headers or {'Content-Type':'application/json'})
    def read(self, n=-1): return self.body[:n]
    def getcode(self): return self.status
    def __enter__(self): return self
    def __exit__(self,*args): return False
class Opener:
    def __init__(self, responses): self.responses=list(responses)
    def open(self, req, timeout=0):
        result=self.responses.pop(0)
        if isinstance(result,Exception): raise result
        return result

def public_resolver(host, *args, **kwargs):
    return [(None,None,None,None,('93.184.216.34',443))]

class P169ProbeTests(unittest.TestCase):
    def test_rejects_http(self):
        self.assertFalse(p.validate_public_https('http://example.com', public_resolver)[0])
    def test_rejects_private_dns(self):
        self.assertFalse(p.validate_public_https('https://example.test', lambda *a,**k:[(None,None,None,None,('127.0.0.1',443))])[0])
    def test_healthy_endpoints_pass(self):
        op=Opener([Response(b'{"ok":true,"yt_dlp":true}',headers={'Content-Type':'application/json'}),Response(b'<html/>',headers={'Content-Type':'text/html; charset=utf-8'})])
        result=p.probe('https://example.com', lambda:op, public_resolver)
        self.assertEqual(result['result'],'PASS')
    def test_missing_provider_degrades(self):
        op=Opener([Response(b'{"ok":true,"yt_dlp":false}',headers={'Content-Type':'application/json'}),Response(b'<html/>',headers={'Content-Type':'text/html'})])
        result=p.probe('https://example.com', lambda:op, public_resolver)
        self.assertEqual(result['result'],'DEGRADED')
    def test_health_failure_blocks(self):
        op=Opener([Response(b'{"ok":false,"yt_dlp":true}'),Response(b'<html/>',headers={'Content-Type':'text/html'})])
        result=p.probe('https://example.com', lambda:op, public_resolver)
        self.assertEqual(result['result'],'BLOCKED')

if __name__=='__main__': unittest.main()
