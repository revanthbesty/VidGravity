# VidGravity

VidGravity is a browser-first media workspace with a Python companion service, PWA/mobile frontend, provider integration, licensing/payment modules, and deployment tooling.

## Source import status

This repository is being initialized from the P172 Production Deployment Bundle. The complete P172 ZIP and detailed reports are currently retained in the project Library. This Git repository must become the canonical source of truth; do not treat a ZIP-only import as complete. The runtime source files, UI assets, regression tests, Android wrapper, and deployment files should be committed as their original paths from the P172 bundle.

## Local development prerequisites

- Python 3.12
- Node.js 22 (for JavaScript syntax checks)
- Docker and Docker Compose v2 for container validation
- ffmpeg and yt-dlp for media-provider testing

## Security / release status

**Not production-ready yet.** A passing local test suite does not establish that public HTTPS, network egress isolation, authorized provider downloads, payment/webhook verification, Android builds, or independent security review have passed.

Never commit `.env` files, API keys, signing keys, payment secrets, user data, or local databases.

## Deployment

See `P172_DEPLOYMENT_BUNDLE.md`, `compose.production.yml`, and `deploy_p172.sh` once the deployment files are imported. Production must be behind HTTPS and use infrastructure-level outbound network restrictions; CORS and Basic Auth are not substitutes for an account/authorization system.

## YouTube extraction and local companion mode

The hosted service may be blocked by YouTube's IP reputation or bot-verification checks. A cloud-hosted extractor cannot use the cookies or browser session from your PC. The service must report this as a provider restriction rather than inventing formats or marking a failed download complete.

The backend uses yt-dlp's default YouTube client selection and installs the `default` extra (including the matching `yt-dlp-ejs` challenge scripts). Full YouTube extraction also requires a supported JavaScript runtime; the Dockerfile installs Deno. The `/api/health` response reports whether yt-dlp, Deno, and yt-dlp-ejs are available.

For a **local-only** companion server on Windows, install Deno with `winget install --id DenoLand.Deno -e`, open a new PowerShell window, and verify `deno --version`. Then install dependencies and run the service bound to loopback. If YouTube still requires a signed-in session, you can explicitly opt into reading Chrome's local session:

```powershell
python -m pip install -r requirements.txt
$env:VIDGRAVITY_YTDLP_COOKIES_FROM_BROWSER = "chrome"
python server.py --host 127.0.0.1 --port 5173
```

Use this only on your own trusted PC. Close Chrome first if its cookie database is locked. The browser-cookie option is ignored when the service binds to a non-loopback host, and cookies are never forwarded to the hosted Render service by this setting. Do not put cookies, cookie files, or credentials in GitHub, logs, or cloud environment variables. If you do not opt in, the local service makes unauthenticated extraction attempts only.

YouTube can change its access controls at any time; even a correctly configured local extractor cannot guarantee every video or resolution. Test the actual Inspect response and completed media file on the target machine.

## Social export and player refactor

The P172 runtime is a vanilla JavaScript/CSS/HTML PWA with a Python HTTP companion service and Capacitor Android web assets. The social-export/player work in PR #4 targets this runtime directly; it does not introduce disconnected React or Rust/Tauri scaffolding.

- Platform preset and safe-zone preview UI: `site/social-export.js` and `site/social-export.css` (mirrored under `android-web/`).
- FFmpeg filter graph validation and bounded export endpoint: `media_export.py` and `POST /api/media/export` in `server.py`.
- Derivative media is stored as a separate IndexedDB Library item; the original item is not overwritten.
- Run graph/preset tests with `python -m unittest discover -s tests -p "test_media_export.py" -v`.
- PWA/Android UI and native device behavior still require build/device validation before release.
