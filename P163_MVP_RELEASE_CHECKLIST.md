# P163 — MVP Release Acceptance and Checklist

## Automated checks completed
- 113 unit/integration tests passed across 19 test files; no failures or errors.
- Legacy static checks: `test_p124.py`, `test_p125.py`, `test_p126.py` executed separately.
- Node syntax checks passed for browser and Android-bundled JavaScript.
- HTTP-level tests cover rejection of a malicious format selector and activation behavior for invalid/valid configured license keys.
- Existing controlled-provider integration tests exercise download job creation, polling, result retrieval, cancellation and retry using a fake `yt-dlp` executable.

## Manual / production gates still open
- [ ] Configure and validate a production HTTPS API host, domain and TLS.
- [ ] Add production authentication, authorization, account recovery and abuse monitoring.
- [ ] Apply outbound network restrictions to the provider process/container to block private, loopback, link-local and metadata IP ranges, including redirects.
- [ ] Run a real provider download against permitted sample media and confirm actual file integrity.
- [ ] Generate the Android native project, build a debug APK, and test on emulator plus a physical Android device.
- [ ] Verify offline launch, resume, cancellation and error states on Android.
- [ ] Test real Razorpay order/capture/webhook flows with test credentials; verify idempotency and license issuance.
- [ ] Verify license activation, deactivation, device transfer and server-side entitlement enforcement.
- [ ] Perform a security review / penetration test and review logs, secrets, backups and retention.
- [ ] Tag the release, record build hashes, and prepare rollback/incident procedures.

## Decision
**Pre-release candidate only.** Automated tests are green, but they do not replace live-provider, payment, mobile-device, deployment, or security validation. Do not advertise the package as production-ready until the gates above are completed.
