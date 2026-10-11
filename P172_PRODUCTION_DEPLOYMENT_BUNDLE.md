# P172 — Production Deployment Bundle

This milestone provides an operator-executable container deployment scaffold; it does **not** mean the application has been deployed. No hosting account, DNS, or server credentials are available to this build environment.

## What is included

- `compose.production.yml`: app container remains private to the Docker backend network; Caddy exposes only 80/443, provides automatic HTTPS, and applies HTTP Basic Auth to the site and API.
- `vidgravity_data` is a separate writable persistent volume mounted at `/data`; set `VIDGRAVITY_DATA_DIR` to the mount path so the read-only app filesystem can still persist job and license databases and downloaded outputs.
- `Caddyfile.production`: TLS reverse proxy, authentication, and baseline response headers.
- `.env.production.example`: non-secret template. Replace the example domain and generated hash on the server.
- `deploy_p172.sh`: validates prerequisites and Compose configuration, then builds and starts the stack.
- `test_p172_deployment_bundle.py`: static safety/regression checks.

## Server operator steps (Linux host)

1. Install Docker Engine and Docker Compose v2. Ensure inbound TCP 80/443 (and optionally UDP 443) are allowed. Point the domain's A/AAAA records at the host; do not leave a stale AAAA record.
2. Transfer/extract the release package to the server.
3. Copy `.env.production.example` to `.env.production`; set a domain you control and a non-default username.
4. Generate a strong unique password hash using `docker run --rm caddy:2-alpine caddy hash-password --plaintext 'YOUR-LONG-UNIQUE-PASSWORD'`. Put only the returned hash in `VIDGRAVITY_BASIC_AUTH_HASH`. Store the password in a password manager. Do not commit `.env.production`.
5. Restrict the env file: `chmod 600 .env.production`.
6. Run `./deploy_p172.sh .env.production`.
7. After DNS/TLS are active, open `https://YOUR_DOMAIN/api/health`, authenticate, and confirm the JSON health response. Check `docker compose --env-file .env.production -f compose.production.yml ps` and logs if it fails.
8. Confirm an unauthenticated request returns 401, the HTTP endpoint redirects to HTTPS, and no direct public port exists for 5173.

## Important release caveats

- HTTP Basic Auth is a temporary access gate, not a customer account system. Use only for restricted pilot access; configure account-level authentication before public customer launch.
- The current flag `VIDGRAVITY_PROVIDER_EGRESS_ISOLATED=false` explicitly indicates that container egress is **not** proven isolated. Docker bridge networking does not itself block access to private, link-local, or metadata endpoints. Enforce egress policy at the host/network layer and verify it independently before enabling untrusted URL fetches.
- The app container has a read-only root filesystem. The production Compose file mounts `vidgravity_data` at `/data` and sets `VIDGRAVITY_DATA_DIR=/data` for persistent databases and downloads. Verify volume permissions, capacity, backup, and restore on the actual host before launch.
- This scaffold does not configure Razorpay secrets or prove payment/webhook correctness. Do not add live keys until the payment path has been independently verified.
- A green container health endpoint only proves the process is responding. It does not prove media-provider success, secure egress, payment, licensing, Android, or production readiness.
- Do not use DRM bypass, authenticated private media, or paywalled content in provider acceptance tests.
