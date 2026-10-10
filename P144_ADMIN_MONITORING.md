# P144 — Payment Admin & Monitoring

## Added
- Token-protected admin service and WSGI adapter.
- Dashboard aggregates issuance status, recent redacted audit events, reconciliation run history, and stale pending/retry/issuing records.
- Admin actions: health, dashboard, retry reconciliation, inspect a payment's public status.
- Constant-time bearer token comparison and fail-closed behavior when `VIDGRAVITY_PAYMENT_ADMIN_TOKEN` is unset.
- Bounded reconciliation limits and `Cache-Control: no-store`.

## Routes
- `GET /api/admin/payments/health`
- `GET /api/admin/payments/dashboard`
- `POST /api/admin/payments/reconcile`
- `GET /api/admin/payments/{payment_id}/status`

## Deployment requirements
Configure a high-entropy `VIDGRAVITY_PAYMENT_ADMIN_TOKEN` in a secret manager. Use HTTPS, network-level access restrictions, rate limiting, and preferably integrate with the product's real admin identity system instead of a shared token. Do not expose these endpoints publicly without those controls.

## Limitation
This milestone adds an operational service layer and example WSGI routes. It does not deploy a server, configure a worker, or prove production security. Use a real admin authentication provider and run staging tests before production.
