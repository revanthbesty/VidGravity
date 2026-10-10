#!/usr/bin/env python3
"""VidGravity licensing API prototype. Production deployment still needs HTTPS, secret management and operational hardening."""
import argparse, hashlib, hmac, json, os, secrets, sqlite3, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

DB_DEFAULT=Path(__file__).resolve().parent/"vidgravity_licenses.sqlite3"
PRICE_INR=199
MAX_BODY=16_384
def digest(s): return hashlib.sha256(s.encode("utf-8")).hexdigest()
def db_connect(path):
    con=sqlite3.connect(path,timeout=10); con.row_factory=sqlite3.Row
    con.execute("PRAGMA foreign_keys=ON")
    con.executescript("""
    CREATE TABLE IF NOT EXISTS licenses(
      license_hash TEXT PRIMARY KEY, email TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'active',
      expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS activations(
      license_hash TEXT NOT NULL REFERENCES licenses(license_hash), device_hash TEXT NOT NULL,
      activated_at INTEGER NOT NULL, last_seen INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1,
      PRIMARY KEY(license_hash,device_hash)
    );
    CREATE INDEX IF NOT EXISTS idx_activations_active ON activations(license_hash,active);
    CREATE TABLE IF NOT EXISTS audit_events(
      id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, event TEXT NOT NULL,
      license_hash TEXT, device_hash TEXT, detail TEXT NOT NULL
    );
    """)
    return con
def event(con,name,lh,dh,detail):
    con.execute("INSERT INTO audit_events(at,event,license_hash,device_hash,detail) VALUES(?,?,?,?,?)",
                (int(time.time()),name,lh,dh,json.dumps(detail,separators=(",",":"))))
class Handler(BaseHTTPRequestHandler):
    db_path=DB_DEFAULT
    def reply(self,status,obj):
        b=json.dumps(obj).encode()
        self.send_response(status); self.send_header("Content-Type","application/json")
        self.send_header("Content-Length",str(len(b))); self.send_header("Cache-Control","no-store")
        self.send_header("X-Content-Type-Options","nosniff"); self.end_headers(); self.wfile.write(b)
    def body(self):
        n=int(self.headers.get("Content-Length","0"))
        if n<0 or n>MAX_BODY: raise ValueError("Request body too large")
        try: return json.loads(self.rfile.read(n) or b"{}")
        except Exception: raise ValueError("Invalid JSON body")
    def do_GET(self):
        if self.path=="/api/license/health":
            return self.reply(200,{"ok":True,"service":"vidgravity-licensing","price_inr_year":PRICE_INR,"device_limit":1})
        return self.reply(404,{"error":"Not found"})
    def do_POST(self):
        try: data=self.body()
        except ValueError as e: return self.reply(400,{"error":str(e)})
        if self.path=="/api/license/issue":
            # Internal/admin operation: disabled unless an operator secret is configured.
            secret=os.environ.get("VIDGRAVITY_ADMIN_SECRET","")
            supplied=self.headers.get("X-Admin-Secret","")
            if not secret or not hmac.compare_digest(secret,supplied): return self.reply(403,{"error":"Admin authorization required"})
            email=str(data.get("email","")).strip().lower()
            if "@" not in email or len(email)>254: return self.reply(400,{"error":"Valid email required"})
            raw="VG-"+secrets.token_urlsafe(24)
            lh=digest(raw); now=int(time.time()); expires=now+365*24*3600
            with db_connect(self.db_path) as con:
                con.execute("INSERT INTO licenses(license_hash,email,status,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,?)",(lh,email,"active",expires,now,now))
                event(con,"license_issued",lh,None,{"expires_at":expires})
            return self.reply(201,{"license_key":raw,"expires_at":expires,"price_inr_year":PRICE_INR})
        if self.path=="/api/license/activate":
            key=str(data.get("license_key","")); device=str(data.get("device_id",""))
            if not key or not device or len(device)>256: return self.reply(400,{"error":"license_key and device_id required"})
            lh=digest(key); dh=digest(device); now=int(time.time())
            with db_connect(self.db_path) as con:
                lic=con.execute("SELECT * FROM licenses WHERE license_hash=?",(lh,)).fetchone()
                if not lic or lic["status"]!="active" or lic["expires_at"]<=now:
                    event(con,"activation_denied",lh,dh,{"reason":"invalid_or_expired"})
                    return self.reply(403,{"ok":False,"error":"Invalid, inactive, or expired license"})
                current=con.execute("SELECT device_hash FROM activations WHERE license_hash=? AND active=1",(lh,)).fetchall()
                if current and not any(r["device_hash"]==dh for r in current):
                    event(con,"activation_denied",lh,dh,{"reason":"device_limit"})
                    return self.reply(409,{"ok":False,"error":"License already active on another device. Deactivate it first."})
                con.execute("INSERT INTO activations(license_hash,device_hash,activated_at,last_seen,active) VALUES(?,?,?,?,1) ON CONFLICT(license_hash,device_hash) DO UPDATE SET last_seen=excluded.last_seen,active=1",(lh,dh,now,now))
                event(con,"activated",lh,dh,{})
            return self.reply(200,{"ok":True,"expires_at":lic["expires_at"],"device_limit":1})
        if self.path=="/api/license/deactivate":
            key=str(data.get("license_key","")); device=str(data.get("device_id",""))
            if not key or not device:return self.reply(400,{"error":"license_key and device_id required"})
            lh=digest(key); dh=digest(device); now=int(time.time())
            with db_connect(self.db_path) as con:
                cur=con.execute("UPDATE activations SET active=0,last_seen=? WHERE license_hash=? AND device_hash=? AND active=1",(now,lh,dh))
                event(con,"deactivated" if cur.rowcount else "deactivation_noop",lh,dh,{})
            return self.reply(200,{"ok":True,"deactivated":cur.rowcount==1})
        if self.path=="/api/license/validate":
            key=str(data.get("license_key","")); device=str(data.get("device_id",""))
            if not key or not device:return self.reply(400,{"error":"license_key and device_id required"})
            lh=digest(key); dh=digest(device); now=int(time.time())
            with db_connect(self.db_path) as con:
                lic=con.execute("SELECT * FROM licenses WHERE license_hash=?",(lh,)).fetchone()
                active=con.execute("SELECT 1 FROM activations WHERE license_hash=? AND device_hash=? AND active=1",(lh,dh)).fetchone()
                ok=bool(lic and lic["status"]=="active" and lic["expires_at"]>now and active)
                if ok: con.execute("UPDATE activations SET last_seen=? WHERE license_hash=? AND device_hash=?",(now,lh,dh))
                event(con,"validated" if ok else "validation_denied",lh,dh,{})
            return self.reply(200,{"ok":ok,"reason":None if ok else "not_active_or_expired"})
        return self.reply(404,{"error":"Not found"})
    def log_message(self,fmt,*args): pass
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--host",default="127.0.0.1"); ap.add_argument("--port",type=int,default=5180); ap.add_argument("--db",default=str(DB_DEFAULT)); args=ap.parse_args()
    Handler.db_path=Path(args.db)
    if args.host not in ("127.0.0.1","localhost") and not os.environ.get("VIDGRAVITY_ALLOW_REMOTE"):
        raise SystemExit("Remote binding requires VIDGRAVITY_ALLOW_REMOTE=1; use HTTPS reverse proxy in production.")
    print(f"VidGravity licensing API on http://{args.host}:{args.port}")
    ThreadingHTTPServer((args.host,args.port),Handler).serve_forever()
if __name__=="__main__": main()
