# VidGravity P158 — Frontend-to-Backend Download Lifecycle

## Changes
- Connected the URL inspection / format-selection UI to the persistent server job API (`POST /api/download-jobs`).
- Added polling for queued/running/completed/failed/cancelled/expired states.
- On completion, retrieves the result endpoint and stores the downloaded Blob in IndexedDB.
- Added a two-job client-side concurrency limit, queued job handling, browser direct-fetch path, cancellation, retry, and clear state updates.
- Added frontend lifecycle contract tests and combined regression runner.

## Validation
P158 frontend tests and combined regression results are captured in the output files in this package. Backend job integration remains covered by earlier HTTP lifecycle tests.

## Limitations
- Browser UI behavior has not been manually exercised in a real browser in this environment.
- The provider executable is not installed here, so this does not certify a live third-party download.
- The backend still needs real deployment and real-device acceptance testing.
