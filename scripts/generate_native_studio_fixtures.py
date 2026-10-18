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
subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "color=c=white:size=320x180:rate=15:duration=2", "-f", "lavfi", "-i",
    "color=c=black:size=320x180:rate=15:duration=2", "-f", "lavfi", "-i",
    "color=c=red:size=320x180:rate=15:duration=1", "-f", "lavfi", "-i",
    "aevalsrc=if(between(t\\,1\\,2.4)\\,0\\,0.2*sin(2*PI*440*t)):s=44100:d=5",
    "-filter_complex", "[0:v][1:v][2:v]concat=n=3:v=1:a=0[v]", "-map", "[v]", "-map", "3:a",
    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-t", "5",
    str(output / "creative-source.mp4")
], check=True)
print("Generated scene-change and silence fixtures")
