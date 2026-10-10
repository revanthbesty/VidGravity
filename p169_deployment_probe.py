#!/usr/bin/env python3
"""P169 external deployment probe. HTTPS-only; rejects private targets and redirects."""
import argparse, ipaddress, json, socket, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

TIMEOUT_SECONDS = 8

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def validate_public_https(url, resolver=socket.getaddrinfo):
    try:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != 'https' or not parsed.hostname:
            return False, 'A public HTTPS URL is required'
        if parsed.username is not None or parsed.password is not None:
            return False, 'URL credentials are not allowed'
        if parsed.fragment:
            return False, 'URL fragments are not allowed'
        host = parsed.hostname.rstrip('.').lower()
        if host in {'localhost', 'localhost.localdomain'}:
            return False, 'Local hosts are not allowed'
        try:
            ip = ipaddress.ip_address(host)
            addresses = [ip]
        except ValueError:
            try:
                records = resolver(host, None, type=socket.SOCK_STREAM)
                addresses = [ipaddress.ip_address(record[4][0]) for record in records]
            except (OSError, ValueError, IndexError):
                return False, 'Hostname did not resolve to a public address'
        if not addresses:
            return False, 'Hostname did not resolve to a public address'
        if any(not addr.is_global for addr in addresses):
            return False, 'Private, reserved, or non-public network targets are blocked'
        return True, ''
    except Exception:
        return False, 'Invalid URL'

def probe(base_url, opener_factory=None, resolver=socket.getaddrinfo):
    base_url = base_url.strip().rstrip('/')
    ok, reason = validate_public_https(base_url, resolver=resolver)
    report = {'milestone':'P169','name':'External Deployment Verification','target_configured':bool(base_url),
              'result':'BLOCKED','checks':[],
              'important_note':'A successful probe verifies only the reachable HTTPS endpoint and reported health. It is not proof of provider-download, payment, Android, or security acceptance.'}
    if not ok:
        report['checks'].append({'key':'target_url','status':'FAIL','detail':reason})
        return report
    report['checks'].append({'key':'target_url','status':'PASS','detail':'Target is HTTPS and resolves only to public addresses'})
    opener = (opener_factory or (lambda: urllib.request.build_opener(NoRedirect())))()
    for label, url in [('health', base_url + '/api/health'), ('homepage', base_url + '/')]:
        started = time.monotonic()
        try:
            req = urllib.request.Request(url, headers={'User-Agent':'VidGravity-P169-Deployment-Probe/1.0','Accept':'application/json' if label=='health' else 'text/html,*/*'})
            with opener.open(req, timeout=TIMEOUT_SECONDS) as response:
                status = getattr(response, 'status', response.getcode())
                body = response.read(1024 * 1024 + 1)
                headers = response.headers
            elapsed_ms = round((time.monotonic()-started)*1000)
            if len(body) > 1024*1024:
                report['checks'].append({'key':label,'status':'FAIL','detail':'Response exceeded 1 MiB limit','elapsed_ms':elapsed_ms})
                continue
            passed = 200 <= status < 300
            detail = f'HTTP {status}'
            item = {'key':label,'status':'PASS' if passed else 'FAIL','detail':detail,'http_status':status,'elapsed_ms':elapsed_ms}
            if label == 'health' and passed:
                try:
                    data = json.loads(body.decode('utf-8'))
                    healthy = data.get('ok') is True
                    provider = data.get('yt_dlp') is True
                    item['application_health'] = 'PASS' if healthy else 'FAIL'
                    item['provider_engine'] = 'PRESENT' if provider else 'MISSING'
                    if not healthy:
                        item['status']='FAIL'; item['detail']='Health endpoint did not report ok=true'
                    elif not provider:
                        item['status']='DEGRADED'; item['detail']='Application responds, but yt-dlp is not available'
                except (ValueError, UnicodeDecodeError, AttributeError):
                    item['status']='FAIL'; item['detail']='Health endpoint did not return valid JSON'
            if label == 'homepage' and passed:
                ctype = headers.get('Content-Type','')
                item['content_type_ok'] = 'text/html' in ctype.lower()
                if not item['content_type_ok']:
                    item['status']='DEGRADED'; item['detail']='Homepage responded, but Content-Type is not text/html'
            report['checks'].append(item)
        except urllib.error.HTTPError as exc:
            report['checks'].append({'key':label,'status':'FAIL','detail':f'HTTP {exc.code}','http_status':exc.code,'elapsed_ms':round((time.monotonic()-started)*1000)})
        except Exception as exc:
            # Keep reports useful without leaking URLs, headers, credentials, or environment values.
            report['checks'].append({'key':label,'status':'FAIL','detail':type(exc).__name__,'elapsed_ms':round((time.monotonic()-started)*1000)})
    statuses = [item['status'] for item in report['checks']]
    report['result'] = 'PASS' if statuses and all(s=='PASS' for s in statuses) else ('DEGRADED' if 'DEGRADED' in statuses and 'FAIL' not in statuses else 'BLOCKED')
    return report

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default=None, help='Public HTTPS base URL; defaults to VIDGRAVITY_PUBLIC_BASE_URL')
    parser.add_argument('--output', default='P169_DEPLOYMENT_PROBE_REPORT.json')
    args=parser.parse_args(argv)
    import os
    target=args.url if args.url is not None else os.environ.get('VIDGRAVITY_PUBLIC_BASE_URL','')
    report=probe(target)
    Path(args.output).write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(f"P169 deployment probe: {report['result']}")
    for check in report['checks']:
        print(f"[{check['status']}] {check['key']}: {check['detail']}")
    print(f"Report: {args.output}")
    return 0 if report['result']=='PASS' else 2

if __name__=='__main__':
    sys.exit(main())
