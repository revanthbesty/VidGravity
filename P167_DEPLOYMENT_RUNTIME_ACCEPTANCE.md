# P167 — Deployment and Runtime Acceptance

## Scope
P167 adds a repeatable, local runtime smoke test and removes the server's dependency on a Bash shell for detecting `yt-dlp`. The health endpoint and provider-availability checks now use Python's `shutil.which`, which works on Windows and Unix-like systems.

## Run locally
```bash
python run_runtime_acceptance_p167.py
```
The script starts the server on an ephemeral loopback port, checks startup, calls `/api/health`, checks the root UI response, repeats the health check, and shuts down the process. It writes `P167_RUNTIME_ACCEPTANCE_REPORT.json`.

## Acceptance boundary
A passing local smoke test does **not** establish production readiness. Docker CLI/image execution, HTTPS ingress, live provider downloads, payment/webhook verification, native Android build/device checks, network egress isolation, and independent security review remain external gates. Do not expose the service publicly without HTTPS, authentication/authorization, quotas, monitoring, and infrastructure-level egress restrictions. CORS is not authentication.

## Deployment sequence
1. Build the image in a Docker-capable environment and inspect the build output.
2. Run the container with the provided hardened Compose example and verify its health check and writable download/state paths.
3. Configure a trusted HTTPS reverse proxy and authentication before allowing non-local users.
4. Verify egress policy blocks loopback, private, link-local, and cloud metadata destinations from the provider subprocess/container.
5. Perform an authorized test download using a non-DRM public test asset; record provider and output validation.
6. Verify Razorpay test-mode payment and webhook signature handling, then separately verify live-mode configuration.
7. Build and test the native Android package on an emulator and a physical device.
8. Re-run release preflight and close every external gate before launch.
