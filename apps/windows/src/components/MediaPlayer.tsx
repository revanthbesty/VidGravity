import { useEffect, useRef, useState } from "react";
import { Camera, Share2, Scissors, X, Captions, Volume2, Gauge, PictureInPicture2 } from "lucide-react";
import { useMediaPlayer } from "../hooks/useMediaPlayer";
import { ShareModal } from "./ShareModal";
import { ShareClipModal } from "./ShareClipModal";
import { FrameStudioModal } from "./FrameStudioModal";
import type { ClipExportRequest } from "../../../packages/contracts/src/share";

export interface MediaPlayerProps {
  src: string; title: string; onClose: () => void; mediaId?: string; shareUrl?: string;
  thumbnailUrl?: string; durationSeconds?: number; outputDirectoryId?: string;
  onExportClip?: (request: ClipExportRequest) => Promise<void>;
}
function timeLabel(value:number) { if(!Number.isFinite(value)||value<0)return "00:00";const s=Math.floor(value);return `${Math.floor(s/3600).toString().padStart(2,"0")}:${Math.floor(s%3600/60).toString().padStart(2,"0")}:${(s%60).toString().padStart(2,"0")}`; }
export function MediaPlayer({ src, title, onClose, mediaId, shareUrl, thumbnailUrl, durationSeconds, outputDirectoryId, onExportClip }: MediaPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const p = useMediaPlayer(videoRef);
  const [shareOpen,setShareOpen] = useState(false);
  const [clipOpen,setClipOpen] = useState(false);
  const [frameUrl,setFrameUrl] = useState("");
  const [captureError,setCaptureError] = useState("");
  const [contrast,setContrast] = useState(100);
  const [brightness,setBrightness] = useState(100);
  const [currentTime,setCurrentTime] = useState(0);
  const [actualDuration,setActualDuration] = useState(durationSeconds ?? 0);
  const [silenceSkip,setSilenceSkip] = useState(false);
  const [subtitles,setSubtitles] = useState(true);
  const [audioBoost,setAudioBoost] = useState(false);
  useEffect(()=>()=>{if(frameUrl)URL.revokeObjectURL(frameUrl)},[frameUrl]);
  function snapFrame(){const video=videoRef.current;if(!video||!video.videoWidth||!video.videoHeight)return;try{const canvas=document.createElement("canvas");canvas.width=video.videoWidth;canvas.height=video.videoHeight;const ctx=canvas.getContext("2d");if(!ctx)return;ctx.drawImage(video,0,0,canvas.width,canvas.height);canvas.toBlob(blob=>{if(!blob)return;setFrameUrl(old=>{if(old)URL.revokeObjectURL(old);return URL.createObjectURL(blob)})},"image/png")}catch{setCaptureError("Frame capture was blocked by the media source. Try a locally downloaded file.")}}
  function seek(value:number){const video=videoRef.current;if(!video)return;video.currentTime=Math.max(0,Math.min(actualDuration||video.duration||0,value));setCurrentTime(video.currentTime)}
  return <section className="player-shell" aria-label={`Player: ${title}`}>
    <header className="player-header"><div className="vg-player-brand-heading"><svg width="30" height="30" viewBox="0 0 40 40" role="img" aria-label="VidGravity"><defs><linearGradient id="vg-player-brand-gradient" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#8b5cf6"/><stop offset="1" stopColor="#22d3ee"/></linearGradient></defs><circle cx="20" cy="20" r="17" fill="none" stroke="url(#vg-player-brand-gradient)" strokeWidth="2" opacity=".8"/><path d="M9 13 L17 29 L25 13 L31 13 L20 34 L7 13 Z" fill="url(#vg-player-brand-gradient)"/><circle cx="29" cy="9" r="3" fill="#22d3ee"/></svg><div><p className="vg-player-eyebrow">VIDGRAVITY · STUDIO PLAYER</p><h2>{title}</h2><p className="player-capability-note">Pull. Convert. Create.</p></div></div><div className="vg-player-controls"><button type="button" onClick={()=>setShareOpen(true)} disabled={!shareUrl} aria-label="Share media"><Share2 size={16}/>Share</button><button type="button" onClick={()=>setClipOpen(true)} disabled={!onExportClip || !mediaId || !(actualDuration||durationSeconds) || !outputDirectoryId}><Scissors size={16}/>Share Clip</button><button type="button" onClick={onClose} aria-label="Close player"><X size={16}/></button></div></header>
    <div className="vg-player-video-wrap">
      <video ref={videoRef} src={src} playsInline preload="metadata" aria-label={title} style={{filter:`brightness(${brightness}%) contrast(${contrast}%)`}} onLoadedMetadata={e=>setActualDuration(e.currentTarget.duration||durationSeconds||0)} onDurationChange={e=>setActualDuration(e.currentTarget.duration||durationSeconds||0)} onTimeUpdate={e=>setCurrentTime(e.currentTarget.currentTime)} onError={()=>setCaptureError("This media could not be played by the current browser engine.")}>
        {subtitles&&<track kind="captions" label="Captions" srcLang="en" default={false}/>}
      </video>
      <div className="vg-player-overlay"><span className="vg-player-overlay-title">{title}</span><button type="button" onClick={snapFrame} disabled={!actualDuration} aria-label="Snap this frame"><Camera size={16}/>Snap This Frame</button></div>
    </div>
    <div className="vg-timeline" role="group" aria-label="Video timeline"><span>{timeLabel(currentTime)}</span><input aria-label="Seek timeline" type="range" min="0" max={Math.max(0.1,actualDuration)} step="0.05" value={Math.min(currentTime,actualDuration||currentTime)} onChange={e=>seek(Number(e.target.value))}/><span>{timeLabel(actualDuration)}</span></div>
    <div className="player-toolbar vg-player-primary-tools" role="group" aria-label="Playback utilities">
      <label><Gauge size={15}/>Speed <select value={p.speed} onChange={e => p.applySpeed(Number(e.target.value) as typeof p.speed)}>{p.speeds.map(s => <option key={s} value={s}>{s}×</option>)}</select></label>
      <button type="button" onClick={() => p.stepFrame(-1)} title="Step backward one approximate frame">− Frame</button>
      <button type="button" onClick={() => p.stepFrame(1)} title="Step forward one approximate frame">+ Frame</button>
      <button type="button" onClick={p.setA}>Set Loop A</button><button type="button" onClick={p.setB}>Set Loop B</button>
      <button type="button" onClick={p.clearLoop} disabled={p.loopA === null && p.loopB === null}>Clear A–B</button>
      <button type="button" onClick={p.togglePip} disabled={!p.capabilities.pictureInPicture}><PictureInPicture2 size={15}/>{p.pip ? "Exit PiP" : "PiP"}</button>
    </div>
    <details className="vg-player-settings"><summary><span><Gauge size={16}/>Playback, audio & captions</span><span className="player-capability-note">Enhancements</span></summary><div className="player-toolbar">
      <label>Brightness <input type="range" min="50" max="150" value={brightness} onChange={e=>setBrightness(Number(e.target.value)} /></label>
      <label>Contrast <input type="range" min="50" max="150" value={contrast} onChange={e=>setContrast(Number(e.target.value)} /></label>
      <label className="vg-toggle-row"><input type="checkbox" checked={subtitles} onChange={e=>setSubtitles(e.target.checked)}/><Captions size={15}/> Captions track preference</label>
      <label className="vg-toggle-row"><input type="checkbox" checked={silenceSkip} onChange={e=>setSilenceSkip(e.target.checked)}/> Silence skipper <span className="player-capability-note">2.5× target</span></label>
      <label className="vg-toggle-row"><input type="checkbox" checked={audioBoost} onChange={e=>setAudioBoost(e.target.checked)}/><Volume2 size={15}/> Audio boost preference</label>
      <p className="player-capability-note">Display adjustments are active. Silence detection, audio normalization/boost and external subtitle loading require their processing adapters before they can alter playback.</p>
    </div></details>
    {(p.message||captureError) && <p role={captureError?"alert":"status"} className="vg-player-status">{captureError||p.message}<button type="button" onClick={()=>{setCaptureError("");p.clearMessage()}} aria-label="Dismiss message">Dismiss</button></p>}
    {shareOpen && <ShareModal media={{title,text:`Clip from ${title}`,url:shareUrl,thumbnailUrl,durationSeconds:actualDuration||durationSeconds}} onClose={()=>setShareOpen(false)} />}
    {clipOpen && mediaId && (actualDuration||durationSeconds) && outputDirectoryId && onExportClip && <ShareClipModal mediaId={mediaId} title={title} durationSeconds={actualDuration||durationSeconds||1} outputDirectoryId={outputDirectoryId} onExport={onExportClip} onClose={()=>setClipOpen(false)} />}
    {frameUrl&&<FrameStudioModal imageUrl={frameUrl} onClose={()=>setFrameUrl("")}/>}
  </section>;
}
