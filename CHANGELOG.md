# P139 Changelog

- Added SQLite-backed licensing API foundation
- Added admin-only license issuance
- Added one-active-device activation and transfer by deactivation
- Added validation, expiry checks, and audit events
- Added API integration tests


## P168 — Production Deployment Gate Closure
- Added a fail-closed deployment gate report with redacted secret handling.
- Added focused tests and an external acceptance checklist.
- Live production acceptance remains explicitly unverified.


## P169 — External Deployment Verification
- Added HTTPS-only external deployment probe for health and homepage endpoints.
- Added bounded requests, redirect refusal, public-address validation, sanitized JSON evidence, and clear PASS/DEGRADED/BLOCKED outcomes.
- Added offline unit tests and deployment instructions. Live production verification remains operator-triggered.

## P170 — Unified Release Acceptance
- Combined the P168 prerequisite gate and optional P169 HTTPS probe into a single fail-closed report.
- Added explicit NOT_RUN state when external probing is not requested; a successful endpoint probe cannot override other blocked gates.
- Added five offline unit tests and CI report generation. Live deployment remains operator-triggered.
