# Provider/download smoke tests

Requires Python 3 and Node.js 18+. Start the companion server with `python server.py --port 5173`, then run `node tests/provider-download-smoke.cjs`. Set `VIDGRAVITY_BASE_URL` if using a different port.

Tests are aligned to the actual `server.py` API: `GET /api/health`, `GET /api/inspect?url=...`, `GET /api/formats?url=...`, and `POST /api/download`. Format discovery can only be fully validated when yt-dlp is installed and a supported, authorized media URL is supplied.
