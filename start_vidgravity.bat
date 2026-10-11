@echo off
cd /d "%~dp0"
where py >nul 2>nul || (echo Python 3 is required.&pause&exit /b 1)
if not exist .venv py -m venv .venv
call .venv\Scripts\activate.bat
rem Always keep yt-dlp current: site changes break old versions, and most failed downloads are an outdated yt-dlp.
python -m pip install -U -r requirements.txt
where ffmpeg >nul 2>nul || echo [warning] ffmpeg not found. Install it with: winget install --id Gyan.FFmpeg -e   (needed to merge video and audio)
where deno >nul 2>nul || echo [warning] Deno not found. Install it with: winget install --id DenoLand.Deno -e   (needed for full YouTube support)
echo.
echo VidGravity companion starting on http://127.0.0.1:5173  (check http://127.0.0.1:5173/api/health)
python server.py --port 5173
