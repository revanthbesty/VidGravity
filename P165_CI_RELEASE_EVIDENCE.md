# P165 — Continuous Integration & Release Evidence

## What this milestone adds
- A GitHub Actions workflow for push, pull request, and manual runs.
- One-process Python regression execution, legacy smoke-script checks, JavaScript syntax checks, and uploadable evidence artifacts.
- The release preflight is executed but treated as a separate external-release gate: CI does not falsely mark live deployment, payments, Android devices, or network isolation as completed.

## Use locally
```bash
python run_regression_p165.py
node --check app.js
node --check android-web/app.js
python p164_release_preflight.py
```

The preflight intentionally exits with code `2` when external release blockers remain. CI accepts that specific code for reporting, but does not treat it as proof of release readiness.

## GitHub setup
Commit this package to the repository's default branch. GitHub Actions runs on pushes and pull requests. No secrets are needed for the regression job. Do not put Razorpay keys or license secrets in repository files; configure them only through an appropriate deployment secret manager when performing live validation.

## Limitations
This workflow has not been run on GitHub from this environment. It is locally testable, but a green local run cannot prove a remote workflow executed, a real payment succeeded, a public server is healthy, or an Android APK passed device acceptance.
