# VidGravity P140 — Payment Verification & License Issuance

## Scope
This milestone adds secure Razorpay signature-verification primitives and SQLite idempotency storage. It is **not** a live payment integration: provider credentials, a server-side Razorpay order adapter, webhook registration, deployment, and end-to-end sandbox transactions are still required.

## Security rules
- Never accept a browser-supplied `payment_success` flag as proof of payment.
- Verify checkout signatures on the server with HMAC-SHA256 over `order_id|payment_id` using `RAZORPAY_KEY_SECRET`.
- Verify webhook signatures over the exact raw request bytes using `RAZORPAY_WEBHOOK_SECRET`, before parsing JSON.
- Issue a license only after the provider confirms a captured payment / paid order, the amount and currency match the expected product price (₹199/year = 19900 paise, INR), and the order belongs to the intended account.
- Treat event IDs and payment IDs as idempotency keys. Never issue a second license for a duplicate webhook.
- Keep secrets server-side; do not log secrets or full signature material.

## Environment
Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` in the server's secret manager. Do not commit `.env` files or real credentials.

## Required production flow
1. Authenticated client requests `POST /api/payment/create-order`.
2. Server creates a ₹19900 INR Razorpay order through Razorpay's authenticated HTTPS API and stores order/account/plan/expected-amount mapping.
3. Client opens Razorpay Checkout with the server-created order ID.
4. Client posts checkout identifiers to `POST /api/payment/verify`; server checks HMAC and, preferably, independently fetches payment/order status from Razorpay API.
5. Razorpay webhook endpoint reads raw bytes, verifies `X-Razorpay-Signature`, validates `X-Razorpay-Event-Id`/provider event ID, amount, currency, order association, and captured/paid status.
6. In one transaction, record the event once and mark the payment captured. Only then call the P139 license issuer. Persist the issuance result idempotently.
7. Reconcile pending payments periodically; return generic errors to clients and never expose secrets.

## Included
- `payment_verification.py`: checkout and webhook HMAC checks; strict event parsing; fail-closed order configuration check.
- `payment_store.py`: SQLite event deduplication and one-time license-issuance gate.
- `test_p140_payment.py`: offline tests using synthetic signatures/events only.

## Important integration gap
These primitives are intentionally not wired into a public HTTP route in this milestone. Do not deploy them as a complete checkout system. Next step: wire the handlers into the P139 server, add provider API order creation/status lookup, and run Razorpay test-mode end-to-end tests.
