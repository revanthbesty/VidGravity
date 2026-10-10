# P137 — Download Lifecycle API

- `POST /api/download-jobs` JSON `{ "url": "...", "format": "..." }` returns HTTP 202 and a job ID.
- `GET /api/download-jobs/{id}` returns lifecycle state and progress.
- `POST /api/download-jobs/{id}/cancel` requests cancellation.
- `GET /api/download-jobs/{id}/result` returns completed bytes.

Requires yt-dlp and an authorized supported source. Progress is parsed from yt-dlp output and may be approximate. Completed bytes are held in process memory; jobs are lost on restart. This is not production queue/storage infrastructure.
