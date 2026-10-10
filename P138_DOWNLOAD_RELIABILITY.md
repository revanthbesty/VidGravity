# P138 — Download Reliability & Recovery

- Added retry endpoint for failed/cancelled jobs; retries receive a new job ID and link to the original.
- Added terminal-state protection against cancellation after completion/failure.
- Added synchronized registry creation and public status projection.
- Added cancellation-aware runner transitions and bounded job registry cleanup.
- Preserved the original direct-download endpoint and asynchronous job endpoints.

Limitations: jobs remain in process memory, completed payload bytes are held in RAM, and no persistent worker/queue is configured. Live provider and cancellation race tests still require yt-dlp plus a supported authorized source.
