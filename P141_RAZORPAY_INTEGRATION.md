# P141 — Razorpay Test-Mode Integration Foundation

## Added
- `razorpay_adapter.py`: standard-library HTTPS adapter for server-side order creation and payment lookup.
- Creates a ₹199 annual INR order (19900 paise) with VidGravity plan metadata.
- Verifies provider-reported order ID, exact amount/currency, and captured status.
- Defaults to test mode. Live mode requires both `RAZORPAY_MODE=live` and explicit `VIDGRAVITY_ALLOW_LIVE_PAYMENTS=1`.
- Avoids logging credentials or returning raw provider response bodies.

## Environment
Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_MODE=test`, and `RAZORPAY_WEBHOOK_SECRET` in a server-side secret manager. Do not place secret values in frontend code or commit them.

## Integration contract
1. Authenticate account and create a server-generated unique receipt.
2. Call `RazorpayAdapter.create_annual_order(account_id, receipt)` and persist `order_id -> account_id, expected amount, currency, plan`.
3. Return only public `key_id`, order ID, amount, and currency to the checkout client.
4. On checkout completion, verify the checkout HMAC from P140, then independently call `fetch_payment` / `verify_captured_payment`.
5. For webhook, verify signature on raw body with P140, validate event and amount, check stored order/account association, and deduplicate event ID.
6. Call the P139 license issuer only once after server-confirmed capture. Store issuance result with payment ID as idempotency key.
7. Test duplicate webhook, late/failed/authorized payments, mismatched amount/order, network timeout, and duplicate license issuance.

## Test limitations
No real Razorpay API calls were made during package validation because credentials and an approved webhook endpoint were not supplied. This is an adapter foundation, not proof of a live payment flow. Before production, run Razorpay test-mode end-to-end tests and wire these methods into authenticated HTTP routes.
