# P162 — Security, Error Handling, and Abuse Prevention

## Implemented
- Request bodies are capped at 1 MiB and must be valid JSON objects; malformed, oversized, or non-object JSON returns a client error rather than escaping as an unhandled exception.
- URL validation rejects non-HTTP(S) schemes, embedded username/password credentials, invalid ports, control characters, overlong URLs, localhost/private/reserved IP destinations, and DNS failures.
- Redirect targets followed by the metadata inspection HTTP client are revalidated.
- Added exact-origin allow-list enforcement for API requests, with loopback-only browser development origins accepted only from loopback clients.
- Added response security headers: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, and `Permissions-Policy`.
- Added per-client sliding-window throttles for job creation/retry/cancel, inspect/formats, license operations, and direct download.
- Added focused automated tests for malformed payloads, URL checks, origin restrictions, redirect checks, throttling, and response headers.

## Current throttle budgets
These are in-memory per-process budgets and reset when the server restarts:
- Download creation/retry: 10 requests per 60 seconds per client IP
- Download cancellation: 20 per 60 seconds
- Inspect: 30 per 60 seconds
- Format inspection: 15 per 60 seconds
- License operations: 10 per 60 seconds
- Direct downloads: 5 per 60 seconds

## Security limits still requiring production infrastructure
- CORS/origin validation is not authentication. Do not expose the companion server publicly without authentication, HTTPS termination, authorization, and operational monitoring.
- The download provider (`yt-dlp`) may make its own network requests beyond the original URL. Redirect checks are applied to the metadata inspection client, but they do not sandbox the provider process. Enforce outbound network restrictions at the host/container/firewall layer to block loopback, private, link-local, and cloud metadata ranges.
- Rate limits are per-process and in-memory; a multi-worker deployment needs shared rate limiting.
- No penetration test, cloud deployment test, or live third-party provider abuse test was performed.
