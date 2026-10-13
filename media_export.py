"""Validated FFmpeg graph construction for VidGravity social exports."""
from __future__ import annotations
import shutil, subprocess
from pathlib import Path

PRESETS = {
 "whatsapp-status": (1080,1920), "instagram-story": (1080,1920),
 "instagram-reels": (1080,1920), "youtube-shorts": (1080,1920), "tiktok": (1080,1920),
 "instagram-feed-portrait": (1080,1350), "linkedin-portrait": (1080,1350),
 "square": (1080,1080), "youtube-landscape": (1920,1080), "x-landscape": (1920,1080)
}
MODES = {"smart-reframe","blurred-backdrop","fit-letterbox"}
MAX_DIMENSION=2160
MAX_TRIM_SECONDS=3600

class ExportError(ValueError):
    """Rejected export request or unsuccessful media transcode."""

def validate_export_request(preset, mode, width=None, height=None, trim_seconds=None):
    if preset not in PRESETS and preset != "custom": raise ExportError("Unsupported social export preset.")
    if mode not in MODES: raise ExportError("Unsupported framing mode.")
    if preset == "custom":
        try: w,h=int(width),int(height)
        except (TypeError,ValueError): raise ExportError("Custom width and height must be integers.")
        if not (144<=w<=MAX_DIMENSION and 144<=h<=MAX_DIMENSION): raise ExportError("Custom dimensions must be between 144 and 2160 pixels.")
        w-=w%2; h-=h%2
    else: w,h=PRESETS[preset]
    trim=None
    if trim_seconds not in (None,""):
        try: trim=float(trim_seconds)
        except (TypeError,ValueError): raise ExportError("Trim duration must be a number of seconds.")
        if not (0<trim<=MAX_TRIM_SECONDS): raise ExportError("Trim duration must be between 0 and 3600 seconds.")
    return {"preset":preset,"mode":mode,"width":w,"height":h,"trim_seconds":trim}

def build_filter_graph(width,height,mode):
    try: w,h=int(width),int(height)
    except (TypeError,ValueError): raise ExportError("Invalid output dimensions.")
    if w<144 or h<144 or w>MAX_DIMENSION or h>MAX_DIMENSION or w%2 or h%2: raise ExportError("Output dimensions must be even and within the supported range.")
    if mode not in MODES: raise ExportError("Unsupported framing mode.")
    if mode=="smart-reframe": return f"scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},setsar=1,format=yuv420p"
    if mode=="fit-letterbox": return f"scale={w}:{h}:force_original_aspect_ratio=decrease,pad={w}:{h}:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,format=yuv420p"
    return f"split=2[bg][fg];[bg]scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},boxblur=20:10[blurred];[fg]scale={w}:{h}:force_original_aspect_ratio=decrease,setsar=1[foreground];[blurred][foreground]overlay=(W-w)/2:(H-h)/2,setsar=1,format=yuv420p"

AUDIO_FILTERS = {
 "slowed-reverb": "[0:a]asetrate=44100*0.85,aresample=44100,aecho=0.8:0.88:60:0.4,lowpass=f=3500[outa]",
 "nightcore": "[0:a]asetrate=44100*1.25,aresample=44100[outa]",
 "bass-warmth": "[0:a]bass=g=8:f=110:w=0.6[outa]",
}

def export_audio_file(source, destination, preset):
    """Export an MP3 from a local uploaded media file using a fixed allow-list of graphs."""
    source, destination = Path(source), Path(destination)
    if preset not in AUDIO_FILTERS:
        raise ExportError("Unsupported audio preset.")
    if not source.is_file() or source.stat().st_size <= 0:
        raise ExportError("The source media is empty or unavailable.")
    if source.resolve() == destination.resolve():
        raise ExportError("Source and output paths must be different.")
    if shutil.which("ffmpeg") is None:
        raise ExportError("FFmpeg is not installed on this server.")
    args = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
            "-i", str(source), "-map", "0:a:0", "-vn", "-sn", "-dn",
            "-filter_complex", AUDIO_FILTERS[preset], "-map", "[outa]",
            "-c:a", "libmp3lame", "-b:a", "320k", "-f", "mp3", str(destination)]
    try:
        result = subprocess.run(args, capture_output=True, timeout=900, check=False)
    except subprocess.TimeoutExpired as exc:
        raise ExportError("Audio export timed out. Try a shorter file.") from exc
    if result.returncode != 0 or not destination.is_file() or destination.stat().st_size <= 0:
        destination.unlink(missing_ok=True)
        detail = result.stderr.decode("utf-8", "replace")[-500:] if result.stderr else ""
        raise ExportError("FFmpeg could not export this audio. " + detail)
    return {"size": destination.stat().st_size, "mime_type": "audio/mpeg", "preset": preset}

def export_media_file(source,destination,request):
    """Write a derivative to a distinct path; never overwrite the source."""
    source,destination=Path(source),Path(destination)
    if not source.is_file() or source.stat().st_size<=0: raise ExportError("The uploaded source media is empty or unavailable.")
    if source.resolve()==destination.resolve(): raise ExportError("Source and output paths must be different.")
    if shutil.which("ffmpeg") is None: raise ExportError("FFmpeg is not installed on this server.")
    checked=validate_export_request(request.get("preset",""),request.get("mode",""),request.get("width"),request.get("height"),request.get("trim_seconds"))
    graph=build_filter_graph(checked["width"],checked["height"],checked["mode"])
    args=["ffmpeg","-hide_banner","-loglevel","error","-nostdin","-y","-i",str(source)]
    if checked["trim_seconds"] is not None: args += ["-t",f'{checked["trim_seconds"]:.3f}']
    if checked["mode"]=="blurred-backdrop": args += ["-filter_complex",f"[0:v]{graph}[vout]","-map","[vout]","-map","0:a?"]
    else: args += ["-vf",graph,"-map","0:v:0","-map","0:a?"]
    args += ["-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p","-c:a","aac","-b:a","160k","-movflags","+faststart","-f","mp4",str(destination)]
    try: result=subprocess.run(args,capture_output=True,timeout=1800,check=False)
    except subprocess.TimeoutExpired as exc: raise ExportError("Media export timed out. Try a shorter clip.") from exc
    if result.returncode!=0 or not destination.is_file() or destination.stat().st_size<=0:
        destination.unlink(missing_ok=True)
        detail=result.stderr.decode("utf-8","replace")[-600:] if result.stderr else ""
        raise ExportError("FFmpeg could not export this media. "+detail)
    return {"width":checked["width"],"height":checked["height"],"preset":checked["preset"],"mode":checked["mode"],"size":destination.stat().st_size,"mime_type":"video/mp4"}
