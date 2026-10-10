# P164 — Release Blocker Closure

## What this milestone adds
- A repeatable release preflight (`python p164_release_preflight.py`) that records required deployment, licensing, Razorpay, provider CLI, live provider, Android, and network-isolation gates.
- A JSON report (`P164_RELEASE_PREFLIGHT_REPORT.json`) that names each blocker and its concrete closure action.
- A privacy check: the preflight never prints configured secret values.
- Automated tests for fail-closed behavior and safe reporting.

## Run locally
```bash
python p164_release_preflight.py
python -m unittest test_p164_release_preflight.py -v
```
The preflight exits `2` while required external gates remain open. This is intentional; do not override the gate to claim production readiness.

## Current boundary
This environment cannot deploy a public HTTPS service, perform an actual Razorpay transaction/webhook, build and install a native APK on an Android device, or prove OS/container egress isolation. Those are real external acceptance gates, not simulated successes. A live provider test also requires an operator-supplied URL they are authorized to download. Never test DRM, paywall, login-gated, or access-controlled media.

## Required to close the remaining blockers
1. Deploy backend with HTTPS, managed secrets, authentication, persistent storage and operational monitoring.
2. Run `provider_live_acceptance.py` with a permitted public test URL and verify the resulting file.
3. Run Razorpay test-mode order → signature verification → webhook → idempotent license issuance; repeat with a controlled live transaction only when ready.
4. Build a native Android package, install it on emulator and physical device, and record acceptance results.
5. Enforce network egress restrictions for the provider subprocess outside this app process; test private/loopback/link-local/cloud metadata targets and redirects.
6. Complete independent security review/penetration test and record results before public launch.

**Status: pre-release candidate; not production-ready.** Presence of configuration variables is not evidence that a live integration works.
