# P171 — Deployment Operator Handoff

## Purpose

P171 makes the next deployment actions explicit and adds a local preflight that checks host tooling and configuration without printing secret values. It does **not** deploy the service automatically and does not claim production acceptance.

## Before you start

- Use a dedicated host or VM where you control Docker, DNS, TLS, firewall and outbound network policy.
- Keep the app behind a TLS reverse proxy; do not publish port 5173 directly to the public internet.
- Use a secret manager or protected host environment for Razorpay values. Do not commit `.env.production`, real API keys, webhook secrets, certificates, or customer data.
- Keep `VIDGRAVITY_PROVIDER_EGRESS_ISOLATED` false until the provider process is actually isolated and independently tested. A configuration flag is not proof of isolation.

## Windows (Docker Desktop)

Open PowerShell in the extracted release folder:

```powershell
python --version
python -m unittest -v test_p171_operator_preflight.py
$env:VIDGRAVITY_PUBLIC_BASE_URL = "https://YOUR-REAL-DOMAIN"
python p171_operator_preflight.py
```

Then review `P171_OPERATOR_PREFLIGHT_REPORT.json`. A `BLOCKED` result is expected until Docker is running, required environment variables are safely configured, and the URL is valid. Do not substitute a fake URL to force a pass.

After reviewing the example compose file and configuring the real reverse proxy and network policy, validate and build on the deployment host:

```powershell
docker info
docker compose -f compose.production.example.yml config
docker compose -f compose.production.example.yml build --no-cache
```

These commands validate/build only; they do not create HTTPS, authentication, payment webhooks, or safe egress policy by themselves. The example compose file intentionally does not publish a host port.

## Linux host

```bash
python3 -m unittest -v test_p171_operator_preflight.py
export VIDGRAVITY_PUBLIC_BASE_URL='https://YOUR-REAL-DOMAIN'
python3 p171_operator_preflight.py
```

Configure secrets in the host secret manager, not in shell history or source control. Then run `docker info`, `docker compose -f compose.production.example.yml config`, and `docker compose -f compose.production.example.yml build --no-cache` after reviewing your reverse-proxy and network-policy setup.

## Evidence required before calling the release accepted

1. Public HTTPS health and homepage probe from outside the host network.
2. Authorized non-DRM media download with output integrity checked.
3. Razorpay test-mode order, signature verification, webhook retry/idempotency and license issuance evidence.
4. Android APK build plus emulator and physical-device checks.
5. Independent proof that provider subprocesses cannot reach loopback, private, link-local or cloud metadata endpoints.
6. Independent security review and a fresh unified P170 acceptance report.

## Reports and safe handling

`P171_OPERATOR_PREFLIGHT_REPORT.json` records check status and next actions. It intentionally excludes secret values. Reports may include deployment metadata; review before sharing publicly.
