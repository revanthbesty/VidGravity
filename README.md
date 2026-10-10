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
