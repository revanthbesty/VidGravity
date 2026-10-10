# P168 — Production Deployment Gate Closure

## Purpose
P168 adds a fail-closed, repeatable deployment gate report. It distinguishes prerequisite/configuration signals from real external acceptance evidence. The gate never prints secret values.

## Run
```bash
python p168_deployment_gate.py
python -m unittest test_p168_deployment_gate.py -v
```

The report is written to `P168_DEPLOYMENT_GATE_REPORT.json`. Exit code `2` means one or more gates remain blocked; this is expected until the relevant deployment/operator prerequisites exist. A `READY` row is not proof of a successful live transaction, deployment, download, or security review.

## Release requirements
1. Deploy the backend behind valid HTTPS, authentication, rate limiting, and managed secrets.
2. Build and run the container on the target host; verify health from outside the deployment network.
3. Use an operator-authorized non-DRM URL for provider acceptance; inspect the produced file.
4. Verify payment order creation, signature validation, webhook handling, and idempotent license issuance in Razorpay test mode.
5. Build the native Android APK and test on an emulator and physical device.
6. Enforce provider-process egress restrictions at the OS/network layer; independently test private, loopback, link-local, redirect, and metadata-address cases.
7. Complete an independent security review before public release.

## Security boundary
Do not put real credentials in `.env` files committed to source control or in this report. Do not use DRM, paywall, login-gated, or otherwise unauthorized URLs for acceptance. CORS is not authentication; in-process throttling is not a substitute for edge controls.

**Status at packaging:** local gate/report logic only. No public deployment, live payment, provider download, APK/device acceptance, or independent security review is claimed.
