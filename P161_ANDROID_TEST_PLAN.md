# P161 Android Test Plan

## Automated checks
- Validate Capacitor configuration and bundled web assets.
- Confirm API base URL configuration is saved and validates remote URLs as HTTPS.
- Confirm API requests use the configured base URL.
- Validate Node.js syntax for `app.js` if Node is installed.

## Manual device/emulator acceptance
1. Install debug build and launch; verify home, downloads, library, and settings render at portrait and landscape sizes.
2. Configure the HTTPS companion URL; verify license status and media inspection.
3. Test valid/invalid license key; confirm no key is written to local storage (only the existing request flow sends it).
4. Test a permitted sample media URL, progress polling, cancellation, and result retrieval.
5. Toggle airplane mode; verify offline banner and local library availability; verify server actions show a clear failure.
6. Background/resume the app; verify job recovery and UI state.
7. Review Android logs for crashes, cleartext traffic, CORS failures, and permission prompts.

## Server CORS allow-list
Configure `VIDGRAVITY_ALLOWED_ORIGINS` as a comma-separated list of exact trusted origins, for example `https://localhost,https://app.example.com`. Do not use wildcard origins with credentials. Reverse proxy / TLS and origin list must match the deployed app origin. Restart the server after changing the environment variable.
