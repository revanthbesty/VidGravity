# VidGravity P157 — Media Provider Integration and Download Verification

## Implemented
- Added provider CLI integration tests covering executable discovery, format metadata parsing, download-job invocation, output persistence and retrieval, provider failure handling, and URL validation.
- Added `provider_live_acceptance.py`, an opt-in acceptance test that uses an operator-supplied URL and the installed `yt-dlp` binary to verify a non-empty output file.
- Added a combined regression runner including P157.

## Automated results
The P157 tests use a controlled local `yt-dlp`-compatible executable so that integration behavior is deterministic and network-independent. They validate the app-to-provider command contract, not a real third-party media provider.

## Live-provider status: BLOCKED / NOT RUN
At packaging time, `yt-dlp` was not installed in the execution environment and no live authorized media URL was supplied. Therefore no real provider download is claimed as verified. To run a real check, install dependencies from `requirements.txt` and set `VIDGRAVITY_LIVE_TEST_URL` to a URL you are authorized to download, then run `python provider_live_acceptance.py`.

This does not bypass DRM, paywalls, authentication, or provider security controls.
