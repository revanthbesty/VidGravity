#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
python3 -m venv .venv 2>/dev/null || true
. .venv/bin/activate
# Always keep yt-dlp current: site changes break old versions, and most failed downloads are an outdated yt-dlp.
python -m pip install -U -r requirements.txt
command -v ffmpeg >/dev/null 2>&1 || echo "[warning] ffmpeg not found; install it to merge video and audio."
command -v deno >/dev/null 2>&1 || echo "[warning] Deno not found; install it for full YouTube support."
echo "VidGravity companion starting on http://127.0.0.1:5173 (check /api/health)"
python server.py --port 5173
