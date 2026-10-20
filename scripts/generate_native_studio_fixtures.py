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
subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "testsrc2=size=160x90:rate=30", "-t", "2", "-c:v", "libx264", "-pix_fmt", "yuv420p",
    str(output / "native-fast.mp4")
], check=True)
print("Generated 30 FPS source for frame-cap regression")

subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "testsrc2=size=90x160:rate=15", "-t", "2", "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p",
    str(output / "native-portrait-silent.mp4")
], check=True)
print("Generated silent 9:16 source for odd-width AVC canvas regression")

subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "color=c=blue:size=320x180:rate=15:duration=2,drawbox=x=160:y=0:w=160:h=180:color=red:t=fill",
    "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p", str(output / "smart-pan.mp4")
], check=True)
subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "color=c=gray:size=160x90:rate=15:duration=2", "-f", "lavfi", "-i",
    "sine=frequency=440:sample_rate=44100:duration=2", "-af", "volume=0.25",
    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-t", "2", str(output / "smart-quiet.mp4")
], check=True)
print("Generated two-color pan and quiet PCM/AAC normalization fixtures")

# A known SDR chart and three one-second colors make rendered effects measurable.
subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
    "color=c=0x808080:size=320x180:rate=20:duration=3,drawbox=x=40:y=45:w=60:h=70:color=red:t=fill,drawbox=x=220:y=45:w=60:h=70:color=blue:t=fill,drawbox=x=20:y=140:w=80:h=30:color=0x404040:t=fill,drawbox=x=220:y=140:w=80:h=30:color=0xC0C0C0:t=fill",
    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=3",
    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-t", "3", str(output / "cinema-chart.mp4")
], check=True)
subprocess.run([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=red:size=320x180:rate=20:duration=1",
    "-f", "lavfi", "-i", "color=c=green:size=320x180:rate=20:duration=1",
    "-f", "lavfi", "-i", "color=c=blue:size=320x180:rate=20:duration=1",
    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=3",
    "-filter_complex", "[0:v][1:v][2:v]concat=n=3:v=1:a=0[v]", "-map", "[v]", "-map", "3:a",
    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-t", "3", str(output / "cinema-steps.mp4")
], check=True)
print("Generated measured cinematic chart and stepped-speed fixtures")
