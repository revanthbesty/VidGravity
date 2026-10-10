# P139 — Production Licensing Backend Foundation

## API
- `GET /api/license/health`
- `POST /api/license/issue` — admin-only, requires `VIDGRAVITY_ADMIN_SECRET` and `X-Admin-Secret`
- `POST /api/license/activate` — enforces one active device
- `POST /api/license/deactivate` — permits transfer after deactivation
- `POST /api/license/validate` — checks status, expiry and device activation

## Commercial rule
₹199/year, one licensee, one active device. Transfer requires deactivating the existing device before activation on another device.

## Run locally
`python licensing_server.py --port 5180`

Set `VIDGRAVITY_ADMIN_SECRET` before issuing licenses. SQLite is suitable for local validation/prototype use, not a claim of production deployment readiness.

## Before public launch
Use HTTPS, durable managed database/backups, rate limits, abuse controls, monitoring, admin audit, payment-provider verification/webhooks, secrets manager, migrations, and automated integration tests. License issuance is deliberately not tied to a client-supplied payment-success flag.
