# P166 — Container Deployment Preparation

## What is included
- Container image scaffold using Python 3.12 slim, non-root runtime, health check, and read-only root filesystem example.
- A compose example that does not publish the app port directly to the internet.
- Server bind address is configurable; local use still defaults to `127.0.0.1`. The container command explicitly binds `0.0.0.0` inside its isolated container network.
- `.dockerignore` to keep local secrets, databases, virtual environments, and build output out of the image context.

## Build and smoke-test locally
```bash
docker build -t vidgravity:p166 .
docker run --rm -p 127.0.0.1:5173:5173 vidgravity:p166
```
Then visit `http://127.0.0.1:5173/api/health` and verify JSON indicates the service is up. Publishing to loopback above is for local testing only.

## Production deployment requirements
1. Place the container behind an HTTPS reverse proxy/load balancer; do not expose port 5173 directly.
2. Add real authentication and authorization before allowing public use. CORS/origin checks are not authentication.
3. Use a managed secret store for payment/licensing secrets. Do not bake secrets into image layers or `.env` files.
4. Enforce provider subprocess network egress restrictions outside the Python process/container, blocking loopback, private, link-local, IPv6 local ranges, and cloud metadata IPs, including redirects and DNS rebinding.
5. Set CPU/memory/download time limits, per-user quotas, persistent storage strategy, backups, metrics, logs, alerting, and retention policies.
6. Complete the actual HTTPS deployment, authorized provider download, Razorpay test order + webhook, Android device test, and independent security review.

## Known limits
- This package does not deploy itself and no container build was executed in this environment.
- The included compose file is a hardening-oriented example, not a complete internet-facing stack.
- The app's current throttles are process-local and should not be treated as distributed rate limiting.
- Do not set `VIDGRAVITY_PROVIDER_EGRESS_ISOLATED=true` unless infrastructure-level egress controls have actually been verified.
- Never enable `--allow-local` on a public service.
