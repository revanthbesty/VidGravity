# P169 — External Deployment Verification

P169 adds a small external probe that checks a deployed VidGravity instance without starting downloads, triggering payments, or sending secrets. It checks HTTPS target safety, `/api/health`, and the homepage.

## Run it

```bash
# Set this to your own publicly deployed HTTPS base URL
export VIDGRAVITY_PUBLIC_BASE_URL="https://your-domain.example"
python p169_deployment_probe.py
```

Windows PowerShell:

```powershell
$env:VIDGRAVITY_PUBLIC_BASE_URL = "https://your-domain.example"
python p169_deployment_probe.py
```

The probe writes `P169_DEPLOYMENT_PROBE_REPORT.json`. Exit code `0` means both endpoints passed and the health response reports `ok=true` and `yt_dlp=true`. Exit code `2` means blocked or degraded. It deliberately requires HTTPS, resolves hostnames before requests, rejects non-public addresses, disallows redirects, uses bounded timeouts and response sizes, and avoids recording full URLs or response headers in the report.

## Test without network access

```bash
python -m unittest -v test_p169_deployment_probe.py
```

## Interpretation and limits

- `PASS` confirms only that the two tested endpoints were reachable over HTTPS and the health endpoint reported the provider executable present.
- `DEGRADED` means the server responds but an expected dependency or homepage content type is missing.
- `BLOCKED` means the target or an endpoint failed the probe.
- This is not a provider download test, payment/webhook verification, Android install test, egress-isolation proof, or independent security review. Those remain separate release gates.
- The probe makes live requests only when explicitly run with a target. It does not auto-run against any external host in CI.
