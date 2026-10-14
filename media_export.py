"""Validated FFmpeg graph construction for VidGravity social exports."""
from __future__ import annotations
import shutil, subprocess, tempfile
from pathlib import Path

PRESETS = {
 "whatsapp-status": (1080,1920), "instagram-story": (1080,1920),
 "instagram-reels": (1080,1920), "youtube-shorts": (1080,1920), "tiktok": (1080,1920),
 "instagram-feed-portrait": (1080,1350), "linkedin-portrait": (1080,1350),
 "square": (1080,1080), "youtube-landscape": (1920,1080), "x-landscape": (1920,1080), "animated-sticker": (512,512)
}
MODES = {"smart-reframe","blurred-backdrop","fit-letterbox","privacy-blur","audio-story"}
MAX_DIMENSION=2160
MAX_TRIM_SECONDS=3600

class ExportError(ValueError):
    """Rejected export request or unsuccessful media transcode."""

def validate_export_request(preset, mode, width=None, height=None, trim_seconds=None, start_seconds=None, output_format="mp4"):
    if output_format not in ("mp4","webp"): raise ExportError("Unsupported export format.")
    if preset not in PRESETS and preset != "custom": raise ExportError("Unsupported social export preset.")
    if output_format == "webp" and (preset != "animated-sticker" or mode != "fit-letterbox"):
        raise ExportError("Animated WebP requires the animated-sticker preset and fit-letterbox framing.")
    if output_format == "mp4" and preset == "animated-sticker": raise ExportError("The animated-sticker preset requires WebP output.")
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
    start=None
    if start_seconds not in (None,""):
        try: start=float(start_seconds)
        except (TypeError,ValueError): raise ExportError("Start time must be a number of seconds.")
        if not (0<=start<MAX_TRIM_SECONDS): raise ExportError("Start time must be between 0 and 3599.999 seconds.")
        if trim is not None and start+trim>MAX_TRIM_SECONDS: raise ExportError("Start time plus clip length must not exceed 3600 seconds.")
    if output_format == "webp" and (trim is None or trim > 10): raise ExportError("Animated stickers must be trimmed to 10 seconds or less.")
    if mode == "audio-story" and (output_format != "mp4" or preset not in ("whatsapp-status","instagram-story","instagram-reels","youtube-shorts","tiktok")):
        raise ExportError("Audio visualizer stories require a vertical MP4 social preset.")
    return {"preset":preset,"mode":mode,"width":w,"height":h,"trim_seconds":trim,"start_seconds":start,"output_format":output_format}

def build_filter_graph(width,height,mode):
    try: w,h=int(width),int(height)
    except (TypeError,ValueError): raise ExportError("Invalid output dimensions.")
    if w<144 or h<144 or w>MAX_DIMENSION or h>MAX_DIMENSION or w%2 or h%2: raise ExportError("Output dimensions must be even and within the supported range.")
    if mode not in MODES: raise ExportError("Unsupported framing mode.")
    if mode=="smart-reframe": return f"scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},setsar=1,format=yuv420p"
    if mode=="fit-letterbox": return f"scale={w}:{h}:force_original_aspect_ratio=decrease,pad={w}:{h}:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,format=yuv420p"
    if mode=="privacy-blur": return f"scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},boxblur=10:5,setsar=1,format=yuv420p"
    return f"split=2[bg][fg];[bg]scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},boxblur=20:10[blurred];[fg]scale={w}:{h}:force_original_aspect_ratio=decrease,setsar=1[foreground];[blurred][foreground]overlay=(W-w)/2:(H-h)/2,setsar=1,format=yuv420p"

def _clean_overlay_text(value):
    """Bound user-controlled overlay text before passing it to FFmpeg."""
    if not isinstance(value, str):
        return ""
    return "".join(ch for ch in value if ch.isprintable()).strip()[:100]


def _overlay_filter(text_file, width, y_ratio, font_size):
    # The content is read from a UTF-8 text file, not interpolated into the FFmpeg graph.
    path = str(text_file).replace("\\", "\\\\").replace(":", "\\:")
    return (f"drawtext=textfile={path}:expansion=none:fontcolor=white:"
            f"fontsize={font_size}:box=1:boxcolor=black@0.58:boxborderw=16:"
            f"x=(w-text_w)/2:y=h*{y_ratio}:fix_bounds=1")


def export_media_file(source,destination,request):
    """Write a derivative to a distinct path; never overwrite the source."""
    source,destination=Path(source),Path(destination)
    if not source.is_file() or source.stat().st_size<=0: raise ExportError("The uploaded source media is empty or unavailable.")
    if source.resolve()==destination.resolve(): raise ExportError("Source and output paths must be different.")
    if shutil.which("ffmpeg") is None: raise ExportError("FFmpeg is not installed on this server.")
    checked=validate_export_request(request.get("preset",""),request.get("mode",""),request.get("width"),request.get("height"),request.get("trim_seconds"),request.get("start_seconds"),request.get("output_format","mp4"))
    title=_clean_overlay_text(request.get("title",""))
    artist=_clean_overlay_text(request.get("artist",""))
    overlay_files=[]
    overlay_filters=[]
    try:
        for label,value,y_ratio,font_size in (("title",title,"0.72",42),("artist",artist,"0.81",30)):
            if value:
                handle=tempfile.NamedTemporaryFile(mode="w",encoding="utf-8",prefix=f"vg-{label}-",suffix=".txt",dir=str(destination.parent),delete=False)
                try:
                    handle.write(value)
                    handle.flush()
                    overlay_files.append(Path(handle.name))
                finally:
                    handle.close()
                overlay_filters.append(_overlay_filter(overlay_files[-1],checked["width"],y_ratio,font_size))
        graph=None if checked["mode"]=="audio-story" else build_filter_graph(checked["width"],checked["height"],checked["mode"])
        if checked["mode"]=="audio-story":
            args=["ffmpeg","-hide_banner","-loglevel","error","-nostdin","-y","-f","lavfi","-i",f'color=c=0x15151b:s={checked["width"]}x{checked["height"]}:r=30']
            if checked["start_seconds"] is not None and checked["start_seconds"] > 0: args += ["-ss",f'{checked["start_seconds"]:.3f}']
            args += ["-i",str(source)]
        else:
            args=["ffmpeg","-hide_banner","-loglevel","error","-nostdin","-y","-i",str(source)]
            if checked["start_seconds"] is not None and checked["start_seconds"] > 0: args += ["-ss",f'{checked["start_seconds"]:.3f}']
        if checked["trim_seconds"] is not None: args += ["-t",f'{checked["trim_seconds"]:.3f}']
        if checked["output_format"]=="webp":
            sticker_graph="fps=12,scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=0x00000000,format=yuva420p"
            args += ["-vf",sticker_graph,"-an","-c:v","libwebp_anim","-loop","0","-lossless","0","-q:v","70","-f","webp",str(destination)]
        else:
            overlay_suffix=(","+",".join(overlay_filters)) if overlay_filters else ""
            if checked["mode"]=="audio-story":
                visualizer="[1:a]showwaves=s=900x420:mode=line:rate=30:colors=0xB8A5FF[wave];[0:v][wave]overlay=(W-w)/2:(H-h)/2"+overlay_suffix+",format=yuv420p[vout]"
                args += ["-filter_complex",visualizer,"-map","[vout]","-map","1:a:0","-shortest"]
            elif checked["mode"]=="blurred-backdrop":
                args += ["-filter_complex",f"[0:v]{graph}{overlay_suffix}[vout]","-map","[vout]","-map","0:a?"]
            else:
                args += ["-vf",graph+overlay_suffix,"-map","0:v:0","-map","0:a?"]
            args += ["-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p","-c:a","aac","-b:a","160k","-movflags","+faststart","-f","mp4",str(destination)]
        try: result=subprocess.run(args,capture_output=True,timeout=1800,check=False)
        except subprocess.TimeoutExpired as exc: raise ExportError("Media export timed out. Try a shorter clip.") from exc
        if result.returncode!=0 or not destination.is_file() or destination.stat().st_size<=0:
            destination.unlink(missing_ok=True)
            detail=result.stderr.decode("utf-8","replace")[-600:] if result.stderr else ""
            raise ExportError("FFmpeg could not export this media. "+detail)
        return {"width":checked["width"],"height":checked["height"],"preset":checked["preset"],"mode":checked["mode"],"size":destination.stat().st_size,"mime_type":"image/webp" if checked["output_format"]=="webp" else "video/mp4","title_overlay":bool(title),"artist_overlay":bool(artist)}
    finally:
        for overlay_file in overlay_files:
            overlay_file.unlink(missing_ok=True)
