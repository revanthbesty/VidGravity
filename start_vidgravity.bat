@echo off
cd /d "%~dp0"
where py >nul 2>nul || (echo Python 3 is required.&pause&exit /b 1)
if not exist .venv py -m venv .venv
call .venv\Scripts\activate.bat
python -m pip install -r requirements.txt
python server.py --port 5173
