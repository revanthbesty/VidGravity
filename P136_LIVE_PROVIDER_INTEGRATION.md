# P136 — Live Provider Integration

Aligned the smoke harness to the actual Python companion server routes and executed it against a live local server.

- `GET /api/health`: reports server health and whether yt-dlp is installed.
- `GET /api/inspect?url=...`: direct media metadata inspection.
- `GET /api/formats?url=...`: yt-dlp format discovery when installed.
- `POST /api/download`: companion download endpoint.

Security tests exercise malformed, loopback, localhost, and link-local URLs. Live provider/format success still depends on yt-dlp installation and a supported, authorized media source; this package does not claim every provider has been validated.
