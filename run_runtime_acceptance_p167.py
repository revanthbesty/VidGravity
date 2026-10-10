#!/usr/bin/env python3
"""Local, non-destructive P167 runtime acceptance smoke tests."""
import json, os, socket, subprocess, sys, time, urllib.error, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def free_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1',0)); return s.getsockname()[1]

def request(url, timeout=2):
    with urllib.request.urlopen(url,timeout=timeout) as r:
        return r.status, r.headers.get_content_type(), r.read()

def main():
    port=free_port(); base=f'http://127.0.0.1:{port}'
    proc=subprocess.Popen([sys.executable,str(ROOT/'server.py'),'--host','127.0.0.1','--port',str(port)],cwd=ROOT,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    results=[]
    try:
        last=None
        for _ in range(60):
            if proc.poll() is not None: raise RuntimeError(f'server exited early with code {proc.returncode}')
            try:
                status,ctype,body=request(base+'/api/health'); break
            except Exception as e:
                last=e; time.sleep(.25)
        else: raise RuntimeError(f'health endpoint did not start: {last}')
        payload=json.loads(body)
        results.append({'test':'server_startup','status':'PASS','detail':'server starts on loopback'})
        assert status==200 and payload.get('ok') is True
        results.append({'test':'health_endpoint','status':'PASS','detail':f'HTTP {status}; yt_dlp={payload.get("yt_dlp")}; version={payload.get("version")}'})
        status,ctype,body=request(base+'/')
        assert status==200 and len(body)>0
        results.append({'test':'root_ui','status':'PASS','detail':f'HTTP {status}; content-type={ctype}; bytes={len(body)}'})
        status,ctype,body=request(base+'/api/health')
        assert json.loads(body).get('ok') is True
        results.append({'test':'health_repeatability','status':'PASS','detail':'second health request remains healthy'})
    except Exception as e:
        results.append({'test':'runtime_acceptance','status':'FAIL','detail':repr(e)})
    finally:
        proc.terminate()
        try: proc.wait(timeout=5)
        except subprocess.TimeoutExpired: proc.kill(); proc.wait(timeout=3)
    out={'milestone':'P167','scope':'local loopback runtime smoke tests only','tests':results,'passed':sum(x['status']=='PASS' for x in results),'failed':sum(x['status']=='FAIL' for x in results),'external_gates':['Docker image build/runtime','public HTTPS deployment','authorized live provider download','live payment/webhook verification','native Android APK/device acceptance','network egress isolation verification','independent security review']}
    (ROOT/'P167_RUNTIME_ACCEPTANCE_REPORT.json').write_text(json.dumps(out,indent=2)+'\n')
    print(json.dumps(out,indent=2))
    return 1 if out['failed'] else 0
if __name__=='__main__': raise SystemExit(main())
