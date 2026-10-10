# P135 — Provider & Download Test Harness

This milestone adds a dependency-free Node.js smoke harness and a real-world acceptance matrix.

Run the companion server, then run `node tests/provider-download-smoke.cjs`; set `VIDGRAVITY_BASE_URL` if needed.

Package scope: health endpoint, JSON health payload, malformed URL rejection, private-target rejection, and a manual matrix for provider inspection, format selection, download lifecycle, retries, and persistence.

Live provider validation remains pending until the harness is executed against the actual configured server. The current build environment does not prove yt-dlp is installed or real providers were exercised. Confirm route contracts before interpreting endpoint failures. Respect provider terms and browser security boundaries.
