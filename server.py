#!/usr/bin/env python3
import argparse, ipaddress, json, os, re, shutil, socket, sqlite3, subprocess, tempfile, time, urllib.parse, urllib.request
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
ROOT=Path(__file__).resolve().parent
ALLOW_LOCAL=False
# P162 security controls: bounded request bodies and per-client in-memory throttles.
P162_SECURITY=True
MAX_JSON_BODY_BYTES=1_048_576
_RATE_BUCKETS={}
_RATE_LOCK=__import__('threading').Lock()
_RATE_LIMITS={
    'download-create':(10,60), 'download-retry':(10,60),
    'download-cancel':(20,60), 'inspect':(30,60),
    'formats':(15,60), 'license':(10,60), 'direct-download':(5,60),
}

def _client_ip(handler):
    try: return handler.client_address[0]
    except Exception: return 'unknown'

def _rate_limit_exceeded(client, bucket, now=None):
    # Return True when client exceeds a bounded sliding-window request budget.
    now=time.time() if now is None else now
    limit,window=_RATE_LIMITS.get(bucket,(30,60))
    key=(str(client),str(bucket))
    with _RATE_LOCK:
        values=[stamp for stamp in _RATE_BUCKETS.get(key,[]) if now-stamp < window]
        if len(values)>=limit:
            _RATE_BUCKETS[key]=values
            return True
        values.append(now)
        _RATE_BUCKETS[key]=values
        if len(_RATE_BUCKETS)>4096:
            cutoff=now-max(item[1] for item in _RATE_LIMITS.values())
            for old_key in list(_RATE_BUCKETS):
                _RATE_BUCKETS[old_key]=[stamp for stamp in _RATE_BUCKETS[old_key] if stamp>=cutoff]
                if not _RATE_BUCKETS[old_key]: _RATE_BUCKETS.pop(old_key,None)
        return False

def _safe_origin(origin, client_ip, allowed_origins):
    # Exact allow-list for remote origins; allow loopback browser origins only for loopback clients.
    origin=(origin or '').strip().rstrip('/')
    if not origin: return True
    if origin in allowed_origins: return True
    try:
        parsed=urllib.parse.urlsplit(origin)
        host=(parsed.hostname or '').lower()
        client=ipaddress.ip_address(client_ip)
        if client.is_loopback and parsed.scheme in ('http','https') and host in ('localhost','127.0.0.1','::1'):
            return True
    except Exception:
        pass
    return False

def _parse_json_bytes(raw, max_bytes=MAX_JSON_BODY_BYTES):
    if len(raw)>max_bytes:
        raise ValueError('Request body is too large')
    try:
        value=json.loads(raw or b'{}')
    except (ValueError, UnicodeDecodeError, TypeError):
        raise ValueError('Invalid JSON body')
    if not isinstance(value,dict):
        raise ValueError('JSON body must be an object')
    return value

def valid_url(url):
    try:
        u=urllib.parse.urlsplit(url)
        if u.scheme not in ('http','https') or not u.hostname:return False,'Only http/https URLs are allowed'
        if u.username is not None or u.password is not None:return False,'URLs containing embedded credentials are not allowed'
        if any(ord(ch)<32 for ch in url) or len(url)>8192:return False,'URL contains invalid characters or exceeds the size limit'
        try:
            port=u.port
            if port is not None and not (1 <= port <= 65535):return False,'Invalid port'
        except ValueError:return False,'Invalid port'
        host=u.hostname.lower().rstrip('.')
        if host in ('localhost','localhost.localdomain') and not ALLOW_LOCAL:return False,'Local hosts are blocked by default'
        try:
            ips=socket.getaddrinfo(host,None)
            for item in ips:
                ip=ipaddress.ip_address(item[4][0])
                if (ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved) and not ALLOW_LOCAL:
                    return False,'Private/local network targets are blocked by default'
        except socket.gaierror:
            return False,'Host could not be resolved'
        return True,''
    except Exception:return False,'Invalid URL'


class _SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Revalidate every redirect target to reduce redirect-based SSRF exposure."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        ok, reason = valid_url(urllib.parse.urljoin(req.full_url, newurl))
        if not ok:
            raise urllib.error.HTTPError(newurl, code, 'Redirect blocked: '+reason, headers, fp)
        return super().redirect_request(req, fp, code, msg, headers, newurl)

def _safe_urlopen(request, timeout=8):
    opener=urllib.request.build_opener(_SafeRedirectHandler())
    return opener.open(request,timeout=timeout)

DOWNLOAD_JOBS={}
DOWNLOAD_JOB_LOCK=__import__('threading').RLock()
DOWNLOAD_OUTPUT_DIR=ROOT/'downloads'
DOWNLOAD_OUTPUT_TTL_SECONDS=24*60*60
DOWNLOAD_DB_PATH=ROOT/'download_jobs.sqlite3'

def _db_connect():
    conn=sqlite3.connect(str(DOWNLOAD_DB_PATH), timeout=10)
    conn.row_factory=sqlite3.Row
    return conn

def _cleanup_job_store(now=None):
    """Prune expired jobs and orphaned output files without touching active jobs."""
    now=time.time() if now is None else now
    cutoff=now-DOWNLOAD_JOB_RETENTION_SECONDS
    removed_ids=set()
    with _db_connect() as db:
        db.execute('BEGIN IMMEDIATE')
        rows=db.execute('SELECT id,state,output_path,updated_at FROM download_jobs').fetchall()
        for row in rows:
            if row['state'] in ('queued','running','cancelling'):
                continue
            if row['updated_at'] < cutoff:
                path=_safe_output_path(row['output_path'] or '')
                if path and path.is_file():
                    try: path.unlink()
                    except OSError: pass
                db.execute('DELETE FROM download_jobs WHERE id=?',(row['id'],))
                removed_ids.add(row['id'])
        db.commit()
    with DOWNLOAD_JOB_LOCK:
        for job_id in removed_ids:
            DOWNLOAD_JOBS.pop(job_id,None)
    # Remove orphaned output files that no longer map to a persisted job.
    try:
        DOWNLOAD_OUTPUT_DIR.mkdir(parents=True,exist_ok=True)
        with _db_connect() as db:
            known={r['output_path'] for r in db.execute('SELECT output_path FROM download_jobs WHERE output_path IS NOT NULL').fetchall()}
        for path in DOWNLOAD_OUTPUT_DIR.iterdir():
            if not path.is_file() or str(path.resolve()) in known:
                continue
            try:
                if now-path.stat().st_mtime > DOWNLOAD_OUTPUT_TTL_SECONDS:
                    path.unlink()
            except OSError:
                pass
    except OSError:
        pass
    return len(removed_ids)

def _init_job_store():
    DOWNLOAD_DB_PATH.parent.mkdir(parents=True,exist_ok=True)
    with _db_connect() as db:
        db.execute('''CREATE TABLE IF NOT EXISTS download_jobs (
            id TEXT PRIMARY KEY, state TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0,
            message TEXT NOT NULL DEFAULT '', error TEXT, filename TEXT, output_path TEXT,
            completed_at REAL, created_at REAL NOT NULL, updated_at REAL NOT NULL,
            retry_of TEXT, url TEXT NOT NULL, format TEXT NOT NULL,
            cancel_requested INTEGER NOT NULL DEFAULT 0
        )''')
        db.commit()
    _recover_persisted_jobs()

def _persist_job(job):
    with DOWNLOAD_JOB_LOCK:
        snap=dict(job)
    with _db_connect() as db:
        db.execute('BEGIN IMMEDIATE')
        db.execute('''INSERT INTO download_jobs
            (id,state,progress,message,error,filename,output_path,completed_at,created_at,updated_at,retry_of,url,format,cancel_requested)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              state=excluded.state, progress=excluded.progress, message=excluded.message,
              error=excluded.error, filename=excluded.filename, output_path=excluded.output_path,
              completed_at=excluded.completed_at, updated_at=excluded.updated_at,
              retry_of=excluded.retry_of, url=excluded.url, format=excluded.format,
              cancel_requested=excluded.cancel_requested''',
            (snap['id'],snap.get('state','queued'),int(snap.get('progress',0)),str(snap.get('message','')),
             snap.get('error'),snap.get('filename'),snap.get('output_path'),snap.get('completed_at'),
             snap.get('created_at',time.time()),time.time(),snap.get('retry_of'),snap.get('url',''),
             snap.get('format','bv*+ba/b'),int(bool(snap.get('cancel_requested')))))
        db.commit()

def _load_persisted_jobs():
    with _db_connect() as db:
        rows=db.execute('SELECT * FROM download_jobs ORDER BY created_at').fetchall()
    with DOWNLOAD_JOB_LOCK:
        DOWNLOAD_JOBS.clear()
        for row in rows:
            job=dict(row)
            job['cancel_requested']=bool(job.get('cancel_requested'))
            DOWNLOAD_JOBS[job['id']]=job

def _recover_persisted_jobs():
    now=time.time()
    with _db_connect() as db:
        rows=db.execute("SELECT id,state,output_path,completed_at FROM download_jobs").fetchall()
        for row in rows:
            state=row['state']
            if state in ('queued','running','cancelling'):
                db.execute("UPDATE download_jobs SET state='failed', error=?, message=?, updated_at=? WHERE id=?",
                           ('Server restarted during download','Interrupted by server restart; retry is available',now,row['id']))
            elif state=='completed':
                path=_safe_output_path(row['output_path'] or '')
                if not path or not path.is_file():
                    db.execute("UPDATE download_jobs SET state='expired', output_path=NULL, message=?, updated_at=? WHERE id=?",
                               ('Output unavailable after restart',now,row['id']))
        db.commit()
    _load_persisted_jobs()

def _job_changed(job):
    job['updated_at']=time.time()
    _persist_job(job)

def _safe_output_path(path):
    try:
        candidate=Path(path).resolve()
        root=DOWNLOAD_OUTPUT_DIR.resolve()
        return candidate if candidate.parent==root else None
    except Exception:
        return None

def _cleanup_download_outputs(now=None):
    """Expire completed output files and job records after the configured TTL."""
    now=time.time() if now is None else now
    with DOWNLOAD_JOB_LOCK:
        jobs=list(DOWNLOAD_JOBS.items())
    for job_id,job in jobs:
        completed_at=job.get('completed_at')
        if not completed_at or now-completed_at < DOWNLOAD_OUTPUT_TTL_SECONDS:
            continue
        path=_safe_output_path(job.get('output_path',''))
        if path and path.is_file():
            try: path.unlink()
            except OSError: pass
        with DOWNLOAD_JOB_LOCK:
            current=DOWNLOAD_JOBS.get(job_id)
            if current is job:
                current.pop('output_path',None)
                current.pop('result',None)
                current['state']='expired'
                current['message']='Download output expired and was removed'
                _job_changed(current)

def _job_public_view(job):
    return {k:v for k,v in job.items() if k not in ('process','result','output_path','url','format','cancel_requested')}
def _job_transition(job,state,**fields):
    with DOWNLOAD_JOB_LOCK:
        if job.get('state') in ('completed','failed','cancelled'): return False
        job.update(fields); job['state']=state; return True

def _stop_download_process(process, timeout=2):
    """Stop a provider process, escalating to kill if it ignores terminate."""
    if process is None:
        return
    try:
        if process.poll() is not None:
            return
    except Exception:
        pass
    try:
        process.terminate()
    except Exception:
        pass
    try:
        process.wait(timeout=timeout)
        return
    except subprocess.TimeoutExpired:
        pass
    except Exception:
        pass
    try:
        process.kill()
    except Exception:
        pass
    try:
        process.wait(timeout=timeout)
    except Exception:
        pass

DOWNLOAD_JOB_LIMIT=30
DOWNLOAD_JOB_RETENTION_SECONDS=7*24*60*60
def _media_signature_type(data):
    """Return a MIME type only when a known media container/audio signature is present."""
    if len(data)>=12 and data[4:8]==b'ftyp': return 'video/mp4'
    if data.startswith(b'\\x1a\\x45\\xdf\\xa3'): return 'video/webm'
    if len(data)>=12 and data[:4]==b'RIFF' and data[8:12]==b'AVI ': return 'video/x-msvideo'
    if len(data)>=12 and data[:4]==b'RIFF' and data[8:12]==b'WAVE': return 'audio/wav'
    if data.startswith(b'ID3') or (len(data)>=2 and data[0]==0xff and (data[1]&0xe0)==0xe0): return 'audio/mpeg'
    if data.startswith(b'OggS'): return 'audio/ogg'
    if data.startswith(b'fLaC'): return 'audio/flac'
    if data.startswith(b'\\x00\\x00\\x01\\xba') or data.startswith(b'\\x00\\x00\\x01\\xb3'): return 'video/mpeg'
    return ''


def _download_direct_media(url, job_id, job):
    """Download a direct audio/video resource over HTTP without relying on yt-dlp's generic extractor."""
    import mimetypes
    head_req=urllib.request.Request(url,method='HEAD',headers={'User-Agent':'VidGravity/1.0'})
    try:
        with _safe_urlopen(head_req,timeout=8) as response:
            content_type=(response.headers.get('Content-Type') or '').split(';',1)[0].strip().lower()
            length_raw=response.headers.get('Content-Length')
    except Exception:
        # Some hosts reject HEAD, and tests/provider URLs may not be reachable from
        # the current runtime. Let yt-dlp handle those URLs instead.
        return False
    parsed_path=urllib.parse.urlsplit(url).path
    ext=Path(parsed_path).suffix.lower()
    known_exts={'.mp4','.m4v','.webm','.mov','.mkv','.avi','.mp3','.m4a','.aac','.ogg','.wav','.flac','.mpeg','.mpg'}
    if not (content_type.startswith('video/') or content_type.startswith('audio/') or (content_type in ('application/octet-stream','binary/octet-stream') and ext in known_exts)):
        return False
    if not re.fullmatch(r'\.[a-z0-9]{1,8}',ext or ''):
        ext=mimetypes.guess_extension(content_type) or '.bin'
    target=DOWNLOAD_OUTPUT_DIR/(job_id+ext)
    req=urllib.request.Request(url,headers={'User-Agent':'VidGravity/1.0'})
    total=int(length_raw) if length_raw and length_raw.isdigit() else 0
    received=0
    with _safe_urlopen(req,timeout=30) as response, open(target,'wb') as output:
        actual_type=(response.headers.get('Content-Type') or content_type).split(';',1)[0].strip().lower()
        if not (actual_type.startswith('video/') or actual_type.startswith('audio/') or (actual_type in ('application/octet-stream','binary/octet-stream') and ext in known_exts)):
            raise RuntimeError('The media URL returned a non-media response during download')
        while True:
            if job.get('cancel_requested'):
                try: target.unlink()
                except OSError: pass
                return True
            chunk=response.read(128*1024)
            if not chunk: break
            output.write(chunk)
            received+=len(chunk)
            with DOWNLOAD_JOB_LOCK:
                job['progress']=max(1,min(99,int(received*100/total))) if total else min(99,job.get('progress',0)+1)
                job['message']=f'Downloading direct media ({received} bytes)'
            _job_changed(job)
    if received==0:
        try: target.unlink()
        except OSError: pass
        raise RuntimeError('The media server returned an empty file')
    try:
        with open(target,'rb') as media_file: signature=_media_signature_type(media_file.read(64))
    except OSError:
        signature=''
    if not signature:
        try: target.unlink()
        except OSError: pass
        raise RuntimeError('The server response is not a recognized media file; refusing to mark it complete')
    ext_by_type={'video/mp4':'.mp4','video/webm':'.webm','video/x-msvideo':'.avi','audio/wav':'.wav','audio/mpeg':'.mp3','audio/ogg':'.ogg','audio/flac':'.flac','video/mpeg':'.mpeg'}
    verified_ext=ext_by_type.get(signature,ext)
    if verified_ext!=ext:
        renamed=DOWNLOAD_OUTPUT_DIR/(job_id+verified_ext)
        target.replace(renamed);target=renamed
    _job_transition(job,'completed',progress=100,message='Download verified',filename=target.name,output_path=str(target),completed_at=time.time(),content_type=signature)

    _job_changed(job)
    return True

def _run_download_job(job_id,url,fmt):
    with DOWNLOAD_JOB_LOCK: job=DOWNLOAD_JOBS[job_id]
    if job.get('cancel_requested'): return
    with DOWNLOAD_JOB_LOCK: job.update({"state":"running","progress":0,"message":"Starting provider download"})
    _job_changed(job)
    try:
        ok,err=valid_url(url)
        if not ok:
            job.update({"state":"failed","error":err}); _job_changed(job); return
        _cleanup_download_outputs()
        DOWNLOAD_OUTPUT_DIR.mkdir(parents=True,exist_ok=True)
        # Direct MP4/MP3/WebM links should use a plain HTTP stream first; this avoids
        # depending on extractor heuristics for files that are already directly hosted.
        if _download_direct_media(url,job_id,job):
            return
        if shutil.which('yt-dlp') is None:
            _job_transition(job,"failed",error="yt-dlp is not installed",message="Provider unavailable")
            _job_changed(job); return
        out=str(DOWNLOAD_OUTPUT_DIR/(job_id+'.%(ext)s'))
        p=subprocess.Popen(['yt-dlp','--no-playlist','-f',fmt or 'bv*+ba/b','-o',out,'--restrict-filenames','--newline',url],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        with DOWNLOAD_JOB_LOCK: job['process']=p
        for line in p.stdout:
            line=line.strip()
            m=re.search(r'(\d+(?:\.\d+)?)%',line)
            if m: job["progress"]=max(1,min(99,int(float(m.group(1)))))
            if line: job["message"]=line[-240:]
            _job_changed(job)
            if job.get("cancel_requested"):
                _stop_download_process(p)
                break
        rc=p.wait()
        with DOWNLOAD_JOB_LOCK: job.pop("process",None)
        if job.get("cancel_requested"):
            for leftover in DOWNLOAD_OUTPUT_DIR.glob(job_id+'.*'):
                try: leftover.unlink()
                except OSError: pass
            _job_transition(job,"cancelled",message="Download cancelled"); _job_changed(job); return
        if rc!=0:
            for leftover in DOWNLOAD_OUTPUT_DIR.glob(job_id+'.*'):
                try: leftover.unlink()
                except OSError: pass
            _job_transition(job,"failed",error=job.get("message") or "yt-dlp failed"); _job_changed(job); return
        files=[f for f in DOWNLOAD_OUTPUT_DIR.glob(job_id+'.*') if f.is_file()]
        if not files:
            _job_transition(job,"failed",error="No media file produced"); _job_changed(job); return
        f=files[0]
        _job_transition(job,"completed",progress=100,message="Download complete",filename=f.name,output_path=str(f),completed_at=time.time())
        _job_changed(job)
    except Exception as e:
        _job_transition(job,"failed",error=str(e)[:500]); _job_changed(job)

_init_job_store()


# P159 account/device license contract (local companion-server adapter).
import hashlib as _license_hashlib
LICENSE_DB_PATH=ROOT/'license_devices.sqlite3'

def _license_db():
    conn=sqlite3.connect(str(LICENSE_DB_PATH),timeout=10)
    conn.row_factory=sqlite3.Row
    conn.execute("CREATE TABLE IF NOT EXISTS license_devices (license_hash TEXT PRIMARY KEY, device_id TEXT NOT NULL, activated_at REAL NOT NULL)")
    conn.commit()
    return conn

def _configured_license_hashes():
    raw=os.environ.get('VIDGRAVITY_LICENSE_KEYS','')
    return {_license_hashlib.sha256(k.strip().encode()).hexdigest() for k in raw.split(',') if k.strip()}

def _license_status(device_id):
    if not device_id or len(device_id)>160:return {'active':False,'reason':'missing_device_id'}
    with _license_db() as db:
        row=db.execute('SELECT license_hash,device_id,activated_at FROM license_devices WHERE device_id=?',(device_id,)).fetchone()
    if not row:return {'active':False,'reason':'not_activated'}
    return {'active':True,'device_id':device_id,'activated_at':row['activated_at']}

def _license_activate(key,device_id):
    key=(key or '').strip(); device_id=(device_id or '').strip()
    if not key or not device_id or len(device_id)>160:return 400,{'error':'License key and device ID are required'}
    digest=_license_hashlib.sha256(key.encode()).hexdigest()
    if digest not in _configured_license_hashes():return 403,{'error':'License key is invalid or not configured on this server'}
    with _license_db() as db:
        db.execute('BEGIN IMMEDIATE')
        row=db.execute('SELECT device_id FROM license_devices WHERE license_hash=?',(digest,)).fetchone()
        if row and row['device_id']!=device_id:
            db.rollback();return 409,{'error':'This license is active on another device. Deactivate that device before transferring.'}
        db.execute('INSERT INTO license_devices(license_hash,device_id,activated_at) VALUES(?,?,?) ON CONFLICT(license_hash) DO UPDATE SET device_id=excluded.device_id,activated_at=excluded.activated_at',(digest,device_id,time.time()))
        db.commit()
    return 200,{'active':True,'device_id':device_id,'message':'License activated on this device'}

def _license_deactivate(key,device_id):
    key=(key or '').strip(); device_id=(device_id or '').strip()
    if not key or not device_id:return 400,{'error':'License key and device ID are required'}
    digest=_license_hashlib.sha256(key.encode()).hexdigest()
    with _license_db() as db:
        db.execute('BEGIN IMMEDIATE')
        row=db.execute('SELECT device_id FROM license_devices WHERE license_hash=?',(digest,)).fetchone()
        if not row:db.rollback();return 404,{'error':'No activation found for this license'}
        if row['device_id']!=device_id:db.rollback();return 403,{'error':'This license is not activated on the requesting device'}
        db.execute('DELETE FROM license_devices WHERE license_hash=?',(digest,));db.commit()
    return 200,{'active':False,'device_id':device_id,'message':'Device deactivated; license can be transferred'}

class Handler(SimpleHTTPRequestHandler):
    def _allowed_origins(self):
        configured=os.environ.get('VIDGRAVITY_ALLOWED_ORIGINS','https://localhost').split(',')
        return {origin.strip().rstrip('/') for origin in configured if origin.strip()}

    def end_headers(self):
        origin=self.headers.get('Origin','').rstrip('/')
        if origin and _safe_origin(origin,_client_ip(self),self._allowed_origins()):
            self.send_header('Access-Control-Allow-Origin',origin)
            self.send_header('Vary','Origin')
            self.send_header('Access-Control-Allow-Methods','GET, POST, OPTIONS')
            self.send_header('Access-Control-Allow-Headers','Content-Type, Authorization')
            self.send_header('Access-Control-Max-Age','600')
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('X-Frame-Options','DENY')
        self.send_header('Referrer-Policy','no-referrer')
        self.send_header('Permissions-Policy','camera=(), microphone=(), geolocation=()')
        super().end_headers()

    def _origin_rejected(self):
        origin=self.headers.get('Origin','')
        return bool(origin) and not _safe_origin(origin,_client_ip(self),self._allowed_origins())

    def _limited(self,bucket):
        if _rate_limit_exceeded(_client_ip(self),bucket):
            self._json(429,{'error':'Too many requests. Please wait and retry.'})
            return True
        return False

    def _read_json(self):
        try:
            raw_len=self.headers.get('Content-Length','0')
            if not raw_len.isdigit(): raise ValueError('Invalid Content-Length')
            length=int(raw_len)
            if length>MAX_JSON_BODY_BYTES: raise ValueError('Request body is too large')
            return _parse_json_bytes(self.rfile.read(length))
        except ValueError as exc:
            self._json(413 if 'too large' in str(exc).lower() else 400,{'error':str(exc)})
            return None

    def do_OPTIONS(self):
        origin=self.headers.get('Origin','')
        if origin and not _safe_origin(origin,_client_ip(self),self._allowed_origins()):
            return self._json(403,{'error':'Origin is not allowed'})
        self.send_response(204)
        self.send_header('Content-Length','0')
        self.end_headers()
    def _json(self,code,obj):
        b=json.dumps(obj).encode(); self.send_response(code); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        if self.path.startswith('/api/') and self._origin_rejected():
            return self._json(403,{'error':'Origin is not allowed'})
        if self.path.startswith('/api/formats') and self._limited('formats'): return
        if self.path.startswith('/api/inspect') and self._limited('inspect'): return
        parsed_license=urllib.parse.urlsplit(self.path)
        if parsed_license.path=='/api/license/status':
            q=urllib.parse.parse_qs(parsed_license.query)
            return self._json(200,_license_status(q.get('device_id',[''])[0]))
        # Compatibility aliases: accept both documented lifecycle route spellings.
        parsed_path=urllib.parse.urlsplit(self.path)
        if parsed_path.path.startswith('/api/download/jobs'):
            suffix=parsed_path.path[len('/api/download/jobs'):]
            self.path='/api/download-jobs'+suffix
        if parsed_path.path=='/api/download':
            q=urllib.parse.parse_qs(parsed_path.query); url=q.get('url',[''])[0]
            ok,err=valid_url(url)
            if not ok: return self._json(400,{'error':err})
            self.send_response(405); self.send_header('Allow','POST'); self.send_header('Content-Type','application/json'); self.end_headers()
            self.wfile.write(json.dumps({'error':'Use POST /api/download with a JSON body'}).encode()); return
        if self.path=='/api/health': return self._json(200,{'ok':True,'yt_dlp':shutil.which('yt-dlp') is not None,'version':'1.0.3'})
        if self.path.startswith('/api/formats'):
            q=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query); url=q.get('url',[''])[0]; ok,err=valid_url(url)
            if not ok:return self._json(400,{'error':err})
            if shutil.which('yt-dlp') is None:
                return self._json(200,{'source':'direct','formats':[],'message':'yt-dlp is not installed'})
            try:
                p=subprocess.run(['yt-dlp','--no-playlist','-J','--skip-download','--no-warnings',url],capture_output=True,text=True,timeout=45)
            except subprocess.TimeoutExpired:
                return self._json(504,{'error':'Format inspection timed out while contacting the provider'})
            if p.returncode!=0:return self._json(502,{'error':p.stderr[-1200:] or 'format inspection failed'})
            try:
                d=json.loads(p.stdout); rows=[]
                for f in d.get('formats',[]):
                    fid=str(f.get('format_id') or '')
                    vcodec=f.get('vcodec') or 'none'; acodec=f.get('acodec') or 'none'
                    has_video=vcodec!='none'; has_audio=acodec!='none'
                    if not fid or not f.get('url') or not (has_video or has_audio): continue
                    # Selecting a video-only stream must also fetch audio; otherwise
                    # users get a silent video even though the job reports success.
                    selector=fid if has_audio or not has_video else fid+'+bestaudio/best'
                    rows.append({
                        'id':fid,'selector':selector,'ext':f.get('ext'),
                        'resolution':f.get('resolution') or (f"{f.get('height')}p" if f.get('height') else ('audio' if not has_video else 'unknown')),
                        'fps':f.get('fps'),'vcodec':vcodec,'acodec':acodec,
                        'filesize':f.get('filesize') or f.get('filesize_approx'),
                        'note':f.get('format_note') or '',
                        'has_video':has_video,'has_audio':has_audio
                    })
                rows.sort(key=lambda f: (not f['has_video'], not f['has_audio'], f.get('filesize') or 0), reverse=False)
                return self._json(200,{'source':'yt-dlp','title':d.get('title') or 'Media','duration':d.get('duration'),'thumbnail':d.get('thumbnail'),'format_count':len(rows),'formats_available':bool(rows),'default_format':'bv*+ba/b','formats':rows[:40]})
            except Exception as e:return self._json(502,{'error':f'invalid resolver response: {e}'})
        if self.path.startswith('/api/inspect'):
            q=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
            url=q.get('url',[''])[0]
            ok,err=valid_url(url)
            if not ok:return self._json(400,{'error':err})
            # Prefer real extractor metadata for supported platforms. A webpage HEAD
            # response alone is not proof that a downloadable media item was found.
            extractor_error=''
            if shutil.which('yt-dlp') is not None:
                try:
                    p=subprocess.run(
                        ['yt-dlp','--no-playlist','-J','--skip-download','--no-warnings',url],
                        capture_output=True,text=True,timeout=30
                    )
                    if p.returncode==0:
                        d=json.loads(p.stdout)
                        formats=d.get('formats') or []
                        usable=[f for f in formats if f.get('url') and (f.get('vcodec')!='none' or f.get('acodec')!='none')]
                        size=d.get('filesize') or d.get('filesize_approx')
                        if not size:
                            sizes=[f.get('filesize') or f.get('filesize_approx') for f in usable]
                            sizes=[n for n in sizes if isinstance(n,(int,float))]
                            size=max(sizes) if sizes else 'unknown'
                        format_rows=[]
                        for f in usable:
                            fid=str(f.get('format_id') or '')
                            vcodec=f.get('vcodec') or 'none'; acodec=f.get('acodec') or 'none'
                            has_video=vcodec!='none'; has_audio=acodec!='none'
                            if not fid: continue
                            selector=fid if has_audio or not has_video else fid+'+bestaudio/best'
                            format_rows.append({
                                'id':fid,'selector':selector,
                                'ext':f.get('ext') or d.get('ext') or 'media',
                                'resolution':f.get('resolution') or (
                                    f"{f.get('height')}p" if f.get('height') else
                                    ('audio' if not has_video else 'unknown')
                                ),
                                'fps':f.get('fps'),
                                'filesize':f.get('filesize') or f.get('filesize_approx'),
                                'note':f.get('format_note') or '',
                                'vcodec':vcodec,'acodec':acodec,
                                'has_video':has_video,'has_audio':has_audio
                            })
                        # Some extractors return one combined downloadable result but
                        # no formats array. Preserve a truthful single-format option.
                        if not format_rows and d.get('url'):
                            format_rows=[{
                                'id':str(d.get('format_id') or 'best'),
                                'ext':d.get('ext') or 'media',
                                'resolution':d.get('resolution') or 'best available',
                                'filesize':size if isinstance(size,(int,float)) else None,
                                'note':'Best available'
                            }]
                        return self._json(200,{
                            'title':d.get('title') or 'Media detected',
                            'type':d.get('ext') or 'media',
                            'size':size if size else 'unknown',
                            'duration':d.get('duration'),
                            'thumbnail':d.get('thumbnail'),
                            'source':'yt-dlp',
                            'format_count':len(format_rows),
                            'formats_available':bool(format_rows),
                            'default_format':'bv*+ba/b',
                            'formats':format_rows[:40]
                        })
                    extractor_error=(p.stderr or p.stdout or 'The media provider could not identify this URL')[-1200:]
                except subprocess.TimeoutExpired:
                    extractor_error='Media inspection timed out while contacting the provider'
                except (ValueError, OSError) as exc:
                    extractor_error=str(exc)[:500]
            # Some valid MP4 hosts incorrectly label files as application/octet-stream.
            # Verify the first bytes instead of trusting the header or URL extension alone.
            try:
                req=urllib.request.Request(url,method='HEAD',headers={'User-Agent':'VidGravity/1.0'})
                with _safe_urlopen(req,timeout=8) as response:
                    content_type=(response.headers.get('Content-Type') or '').split(';',1)[0].strip().lower()
                    n=response.headers.get('Content-Length')
                probe_req=urllib.request.Request(url,headers={'User-Agent':'VidGravity/1.0','Range':'bytes=0-63'})
                with _safe_urlopen(probe_req,timeout=8) as probe:
                    signature=_media_signature_type(probe.read(64))
                if signature:
                    ext=Path(urllib.parse.urlsplit(url).path).suffix.lower().lstrip('.') or signature.split('/')[-1]
                    return self._json(200,{
                        'title':Path(urllib.parse.urlsplit(url).path).name or 'Remote media',
                        'type':signature,'size':int(n) if n and n.isdigit() else 'unknown',
                        'source':'direct','format_count':1,'formats_available':True,
                        'default_format':'best',
                        'formats':[{'id':'best','selector':'best','ext':ext,'resolution':'original quality',
                            'filesize':int(n) if n and n.isdigit() else None,'note':'Verified direct media file',
                            'has_video':signature.startswith('video/'),'has_audio':signature.startswith('audio/')}]
                    })
                if content_type in ('text/html','application/xhtml+xml') or content_type.startswith('text/'):
                    message='This URL returned a webpage, not a direct media file.'
                    if extractor_error and ('sign in to confirm' in extractor_error.lower() or 'not a bot' in extractor_error.lower()):
                        message='The provider requires bot verification or sign-in, so the hosted server cannot inspect this URL right now.'
                    elif extractor_error:
                        message='The provider could not extract media metadata from this webpage.'
                    return self._json(422,{'error':message,'source':'inspect','details':extractor_error[-700:]})
                return self._json(415,{'error':'The URL did not return a recognized audio/video file. Content type: '+(content_type or 'unknown')+'. The server could not verify a media-file signature.','source':'inspect','details':extractor_error[-700:]})
            except Exception as exc:
                detail=str(exc)[:400]
                if extractor_error and ('sign in to confirm' in extractor_error.lower() or 'not a bot' in extractor_error.lower()):
                    return self._json(422,{'error':'The provider requires bot verification or sign-in, so the hosted server cannot inspect this URL right now.','source':'inspect','details':extractor_error[-700:]})
                return self._json(502,{'error':'Could not inspect this URL from the companion server. Check that the link is public and reachable.','source':'inspect','details':detail or extractor_error[-700:]})
        if self.path.startswith('/api/download-jobs/'):
            _cleanup_download_outputs()
            parts=urllib.parse.urlsplit(self.path).path.strip('/').split('/')
            if len(parts)<3:return self._json(404,{'error':'Job not found'})
            with DOWNLOAD_JOB_LOCK: job=DOWNLOAD_JOBS.get(parts[2])
            if not job:return self._json(404,{'error':'Job not found'})
            if len(parts)==4 and parts[3]=='result':
                if job.get('state')=='expired':return self._json(410,{'error':'Download output has expired or is unavailable','state':'expired'})
                if job.get('state')!='completed':return self._json(409,{'error':'Download is not complete','state':job.get('state')})
                path=_safe_output_path(job.get('output_path',''))
                if not path or not path.is_file():
                    return self._json(410,{'error':'Download output has expired or is unavailable','state':'expired'})
                b=path.read_bytes()
                filename=Path(job.get('filename','media.bin')).name.replace('"','')
                self.send_response(200); self.send_header('Content-Type','application/octet-stream'); self.send_header('Content-Disposition',f'attachment; filename="{filename}"'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b); return
            with DOWNLOAD_JOB_LOCK: view=_job_public_view(job)
            return self._json(200,view)
        return super().do_GET()
    def do_POST(self):
        if self.path.startswith('/api/') and self._origin_rejected():
            return self._json(403,{'error':'Origin is not allowed'})
        if self.path=='/api/download-jobs' and self._limited('download-create'): return
        if self.path.endswith('/retry') and self._limited('download-retry'): return
        if self.path.endswith('/cancel') and self._limited('download-cancel'): return
        if self.path in ('/api/license/activate','/api/license/deactivate') and self._limited('license'): return
        if self.path=='/api/download' and self._limited('direct-download'): return
        parsed_license=urllib.parse.urlsplit(self.path)
        if parsed_license.path in ('/api/license/activate','/api/license/deactivate'):
            data=self._read_json()
            if data is None:return
            if parsed_license.path.endswith('/activate'):code,result=_license_activate(data.get('license_key'),data.get('device_id'))
            else:code,result=_license_deactivate(data.get('license_key'),data.get('device_id'))
            return self._json(code,result)
        parsed_path=urllib.parse.urlsplit(self.path)
        if parsed_path.path.startswith('/api/download/jobs'):
            suffix=parsed_path.path[len('/api/download/jobs'):]
            self.path='/api/download-jobs'+suffix
        if self.path=='/api/download-jobs':
            _cleanup_download_outputs()
            data=self._read_json()
            if data is None:return
            url=data.get('url',''); ok,err=valid_url(url)
            if not ok:return self._json(400,{'error':err})
            fmt=data.get('format') or 'bv*+ba/b'
            if not isinstance(fmt,str) or len(fmt)>200 or not re.fullmatch(r'[A-Za-z0-9_*+./,\[\]()-]+',fmt):
                return self._json(400,{'error':'Invalid format selector'})
            with DOWNLOAD_JOB_LOCK:
                _cleanup_job_store()
                if len(DOWNLOAD_JOBS)>=DOWNLOAD_JOB_LIMIT:
                    return self._json(429,{'error':'Download job limit reached; active and retained jobs are not removed'})
                job_id=__import__('uuid').uuid4().hex
                DOWNLOAD_JOBS[job_id]={"id":job_id,"state":"queued","progress":0,"message":"Queued","url":url,"format":fmt,"created_at":time.time(),"updated_at":time.time()}
                _persist_job(DOWNLOAD_JOBS[job_id])
            import threading
            threading.Thread(target=_run_download_job,args=(job_id,url,fmt),daemon=True).start()
            return self._json(202,{"id":job_id,"state":"queued","progress":0})
        if self.path.startswith('/api/download-jobs/') and self.path.endswith('/retry'):
            job_id=self.path.split('/')[-2]
            with DOWNLOAD_JOB_LOCK: old=DOWNLOAD_JOBS.get(job_id)
            if not old:return self._json(404,{'error':'Job not found'})
            if old.get('state') not in ('failed','cancelled'):return self._json(409,{'error':'Only failed or cancelled jobs can be retried','state':old.get('state')})
            with DOWNLOAD_JOB_LOCK:
                _cleanup_job_store()
                if len(DOWNLOAD_JOBS)>=DOWNLOAD_JOB_LIMIT:return self._json(429,{'error':'Download job limit reached; active and retained jobs are not removed'})
                new_id=__import__('uuid').uuid4().hex
                new_job={"id":new_id,"state":"queued","progress":0,"message":"Retry queued","url":old.get('url'),"format":old.get('format') or 'bv*+ba/b',"retry_of":job_id,"created_at":time.time(),"updated_at":time.time()}
                DOWNLOAD_JOBS[new_id]=new_job
                _persist_job(new_job)
            import threading
            threading.Thread(target=_run_download_job,args=(new_id,new_job['url'],new_job['format']),daemon=True).start()
            return self._json(202,{"id":new_id,"state":"queued","retry_of":job_id})
        if self.path.startswith('/api/download-jobs/') and self.path.endswith('/cancel'):
            job_id=self.path.split('/')[-2]; job=DOWNLOAD_JOBS.get(job_id)
            if not job:return self._json(404,{'error':'Job not found'})
            with DOWNLOAD_JOB_LOCK:
                if job.get('state') in ('completed','failed','cancelled'):
                    return self._json(409,{"id":job_id,"state":job.get('state'),"error":"Job is already terminal"})
                job['cancel_requested']=True
                p=job.get('process')
                job.update({"state":"cancelled","message":"Cancellation requested"})
                _job_changed(job)
            if p:
                _stop_download_process(p)
            return self._json(200,{"id":job_id,"state":"cancelled"})
        if self.path!='/api/download': return self._json(404,{'error':'Not found'})
        data=self._read_json()
        if data is None:return
        url=data.get('url',''); ok,err=valid_url(url)
        if not ok:return self._json(400,{'error':err})
        if shutil.which('yt-dlp') is None:return self._json(501,{'error':'yt-dlp is not installed. Install it with: python -m pip install -r requirements.txt'})
        with tempfile.TemporaryDirectory() as td:
            out=str(Path(td)/'media.%(ext)s'); fmt=data.get('format') or 'bv*+ba/b'; p=subprocess.run(['yt-dlp','--no-playlist','-f',fmt,'-o',out,'--restrict-filenames',url],capture_output=True,text=True,timeout=600)
            if p.returncode!=0:return self._json(502,{'error':p.stderr[-1600:] or 'yt-dlp failed'})
            files=list(Path(td).glob('media.*'))
            if not files:return self._json(502,{'error':'No media file produced'})
            f=files[0]; b=f.read_bytes(); self.send_response(200); self.send_header('Content-Type','application/octet-stream'); self.send_header('Content-Disposition',f'attachment; filename="{f.name}"'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
    def log_message(self,fmt,*args): print('%s - %s'%(self.address_string(),fmt%args))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description='VidGravity companion server')
    ap.add_argument('--host', default=os.environ.get('HOST', '127.0.0.1'),
                help='bind address; use 0.0.0.0 only behind a trusted reverse proxy/container network')
    ap.add_argument('--port', type=int, default=int(os.environ.get('PORT', '5173')))

    ap.add_argument('--allow-local',action='store_true',help='allow localhost/private network URLs for development ONLY')
    args=ap.parse_args()
    ALLOW_LOCAL=args.allow_local
    os.chdir(ROOT)
    print(f'VidGravity server listening on {args.host}:{args.port}')
    ThreadingHTTPServer((args.host,args.port),Handler).serve_forever()
