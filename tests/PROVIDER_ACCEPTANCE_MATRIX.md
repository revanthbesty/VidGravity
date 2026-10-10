# P135 Real Provider Acceptance Matrix

Use only media URLs you own or are authorized to download. Do not test DRM-protected, paywalled, private, or access-controlled media.

| Scenario | Expected result | Evidence |
|---|---|---|
| Direct MP4 URL | Inspect then browser download if network/CORS permit | URL, browser, outcome |
| Direct MP3 URL | Inspect then download | MIME type, size |
| Configured yt-dlp provider | Formats returned and quality selectable | Provider + formats |
| Unsupported provider | Clear unsupported state; no fake success | Error message |
| Cancel queued item | Queue entry cancelled | State before/after |
| Cancel active download | Abort when supported; disclose limitation otherwise | UI state |
| Retry failed item | Retry with updated status | State and error |
| Reload during queued work | Queue restored or marked interrupted | Before/after |
| Saved media reload | Library item persists | Item ID |
| Private/local target | Rejected before fetch | HTTP response/log |
| Network/rate failure | Actionable error; retry possible | Error + retry result |

Record PASS/FAIL/SKIPPED. A smoke test passing does not prove every provider works.
