# P143 — Payment Recovery & Reconciliation

## Added
- Durable SQLite recovery state for verified captured payments whose license issuance needs retry.
- Atomic claim operation to reduce concurrent duplicate issuance.
- Bounded reconciliation batches (maximum 500 records per run).
- Redacted failure codes and retry timestamp, with run counters for inspected/repaired/failed records.
- Public status projection that omits account identifiers and internal retry metadata.

## Operating model
1. Only call `register_captured_payment` after the P142 service has verified webhook signature, provider-side capture, order/account mapping, ₹19900 amount and INR currency.
2. Run `PaymentRecoveryService.reconcile()` from a protected scheduled worker or admin-only endpoint.
3. The license issuer must enforce its own idempotency key (`payment_id`). The local claim cannot guarantee exactly-once behavior if the process crashes after external issuance but before local status update.
4. Alert on repeated failures and reconcile against Razorpay records; do not automatically refund or revoke licenses based on a transient network error.
5. Add retention, backups, access controls, monitoring, and migration management before production.

## Important limitation
This recovery component is an integration layer. It is not yet connected automatically to every P142 event path or a deployed worker. End-to-end sandbox and crash-recovery tests remain necessary.
