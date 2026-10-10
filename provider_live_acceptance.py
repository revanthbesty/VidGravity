#!/usr/bin/env python3
"""Opt-in live-provider acceptance check for a URL the operator is authorized to download.

Usage:
  VIDGRAVITY_LIVE_TEST_URL='https://...' python provider_live_acceptance.py

This script invokes the installed yt-dlp executable and verifies that it produces a
non-empty media file. It does not bypass DRM, paywalls, authentication, or access controls.
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

url = os.environ.get("VIDGRAVITY_LIVE_TEST_URL", "").strip()
binary = shutil.which("yt-dlp")
if not url:
    print("BLOCKED: set VIDGRAVITY_LIVE_TEST_URL to a permitted test URL."); sys.exit(3)
if not binary:
    print("BLOCKED: yt-dlp is not installed; install requirements.txt first."); sys.exit(4)
if not url.startswith(("https://", "http://")):
    print("BLOCKED: only HTTP(S) URLs are accepted."); sys.exit(5)
with tempfile.TemporaryDirectory(prefix="vidgravity-live-provider-") as td:
    out = str(Path(td) / "acceptance.%(ext)s")
    version = subprocess.run([binary, "--version"], capture_output=True, text=True, timeout=10)
    if version.returncode:
        print("FAIL: yt-dlp version probe failed."); sys.exit(6)
    proc = subprocess.run([binary, "--no-playlist", "-f", "bv*+ba/b", "-o", out,
                           "--restrict-filenames", "--newline", url],
                          capture_output=True, text=True, timeout=600)
    files = [p for p in Path(td).glob("acceptance.*") if p.is_file() and p.stat().st_size > 0]
    if proc.returncode or not files:
        print("FAIL: provider download did not produce a non-empty file.")
        print((proc.stderr or proc.stdout)[-2500:])
        sys.exit(7)
    file = files[0]
    print(f"PASS: yt-dlp {version.stdout.strip()} produced {file.suffix} ({file.stat().st_size} bytes).")
    print("Note: this validates one explicitly supplied URL only, not all providers.")
