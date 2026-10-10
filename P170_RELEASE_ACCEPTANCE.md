# P170 — Unified Release Acceptance

P170 combines the P168 prerequisite gate and P169 HTTPS probe into one release-evidence report. It is deliberately fail-closed: a public endpoint passing does not override missing prerequisites, and configuration-only checks do not prove live behavior.

## Local checks (no external network request)

```bash
python -m unittest -v test_p170_release_acceptance.py
python p170_release_acceptance.py
```

The default command writes `P170_RELEASE_ACCEPTANCE_REPORT.json`. The external probe is marked `NOT_RUN`; release remains blocked.

## Explicit live HTTPS probe

Set `VIDGRAVITY_PUBLIC_BASE_URL` to your own deployed public HTTPS base URL, then run:

```bash
python p170_release_acceptance.py --probe
```

On Windows PowerShell:

```powershell
$env:VIDGRAVITY_PUBLIC_BASE_URL = "https://your-domain.example"
python p170_release_acceptance.py --probe
```

The report avoids writing the target URL. The probe only checks `/api/health` and `/`; it does not start a download or payment. Exit code `0` means every check in the combined report passed; exit code `2` means release acceptance is blocked or degraded.

## Release policy

`release_allowed` is true only when all included checks are `PASS`. A probe that passes while Docker, provider setup, payment configuration, Android build prerequisites, or egress-isolation assertion is missing will still be blocked. Operator assertions are not independent evidence.

Remaining real-world evidence includes an authorized non-DRM download, Razorpay test-mode order/signature/webhook/idempotency verification, Android APK installation on emulator and physical device, independent egress-isolation verification, and an independent security review. No live deployment was performed by this milestone.
