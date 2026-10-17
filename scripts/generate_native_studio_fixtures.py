"""Generate tiny synthetic fixtures for native Android encoder tests, never fetch user media."""
from pathlib import Path
import subprocess

output = Path("apps/android/app/src/androidTest/assets/generated")
output.mkdir(parents=True, exist_ok=True)
subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "testsrc2=size=160x90:rate=15", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
    "-t", "2", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-movflags", "+faststart",
    str(output / "native-source.mp4")
], check=True)
subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "sine=frequency=220:sample_rate=44100", "-t", "1", str(output / "native-music.wav")
], check=True)
print("Generated synthetic video and music fixtures")
