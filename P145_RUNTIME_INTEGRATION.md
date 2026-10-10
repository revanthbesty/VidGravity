# P145 — Unified Runtime Integration

## Added
- `vidgravity_app.py`: one WSGI application routes `/api/payment/*` and `/api/admin/payments/*`, plus `/health`.
- `run_payment_runtime.py`: startup entry point.
- `.env.runtime.example`: documented environment variable names without real credentials.
- Fail-fast startup if the admin token, Razorpay credentials, or real integration module are missing.

## Required integration module
Provide a Python module configured by `VIDGRAVITY_INTEGRATION_MODULE` with:
- `authenticate(environ) -> account_id | None`
- `issue_license(account_id, payment_id, order_id, plan, duration_days, idempotency_key=None) -> license_result`

This deliberately does not fabricate accounts or licenses. Connect it to the actual account/session and P139 licensing backend.

## Run locally
1. Use Python 3.11+.
2. Set environment variables from `.env.runtime.example` using your shell or secret manager.
3. Configure Razorpay test credentials and the real integration module.
4. Run `python run_payment_runtime.py`.
5. Check `GET /health`; use authenticated test account and Razorpay test checkout to exercise payment routes.

## Production caution
The built-in WSGI server is for local smoke testing only, not production. Use a production WSGI server behind HTTPS, real admin authentication, rate limiting, CSRF/origin protection, database backups, webhook signature validation, and monitoring. The runtime has not been deployed or tested against a live Razorpay account.
