# P142 — Payment-to-License Route Integration

## Added
- `payment_license_service.py`: coordinates authenticated order creation, checkout signature validation, provider-side capture verification, webhook verification, and one-time license issuance.
- `payment_routes_wsgi.py`: example WSGI routes:
  - `POST /api/payment/create-order`
  - `POST /api/payment/verify`
  - `POST /api/payment/webhook`
- `payment_store.py`: persistent mapping from Razorpay order ID to authenticated account, amount, currency and receipt.

## Required before deployment
- Wire `authenticate(environ)` to the real account/session system.
- Wire the `license_issuer` callback to P139's license creation API with a service credential.
- Configure Razorpay webhook secret and subscribe to relevant events.
- Deploy only behind HTTPS; add CSRF/origin protection for browser routes, rate limiting, request-size limits at proxy, and durable backups.
- Reconcile captured payments whose license issuance fails. For production-grade exactly-once behavior, use a transactional outbox / explicit `pending -> issuing -> issued` state machine rather than relying solely on an in-process callback.
- Confirm payment/order belongs to the account and that amount/currency/plan are exactly as stored. Current service enforces account mapping for checkout verification and uses stored mapping for webhook issuance.
- The webhook event header used by the example is `X-Razorpay-Event-Id`; confirm the exact event identifier/header behavior with the Razorpay webhook configuration/API in the target environment. If unavailable, derive idempotency from a stable provider event ID or payload ID after signature verification.

## Validation
Offline integration tests use fake adapter/issuer and synthetic signatures. They do not prove Razorpay sandbox connectivity, deployed HTTPS behavior, or production licensing service compatibility.
