import { useRef, useState } from "react";
import { useMediaPlayer } from "../hooks/useMediaPlayer";
import { ShareModal } from "./ShareModal";

export interface MediaPlayerProps { src: string; title: string; onClose: () => void; mediaId?: string; shareUrl?: string; thumbnailUrl?: string; durationSeconds?: number; }
export function MediaPlayer({ src, title, onClose, mediaId, shareUrl, thumbnailUrl, durationSeconds }: MediaPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const p = useMediaPlayer(videoRef);
  const [shareOpen,setShareOpen] = useState(false);
  const [contrast,setContrast] = useState(100);
  const [brightness,setBrightness] = useState(100);
  const [sharpen,setSharpen] = useState(false);
  return <section className="player-shell" aria-label={`Player: ${title}`}>
    <header className="player-header"><div><h2>{title}</h2><p className="player-capability-note">Playback settings and supported sharing actions</p></div><div className="vg-player-controls"><button type="button" onClick={()=>setShareOpen(true)} disabled={!shareUrl && !src}>Share Clip</button><button type="button" onClick={onClose} aria-label="Close player">Close</button></div></header>
    <div className="vg-player-video-wrap">
      <video ref={videoRef} src={src} controls playsInline preload="metadata" aria-label={title} style={{filter:`brightness(${brightness}%) contrast(${contrast}%)`}} />
      <div className="vg-player-overlay"><button type="button" onClick={()=>setShareOpen(true)}>Share Clip</button><span>{title}</span></div>
    </div>
    <div className="player-toolbar" role="group" aria-label="Playback settings">
      <label>Speed <select value={p.speed} onChange={e => p.applySpeed(Number(e.target.value) as typeof p.speed)}>{p.speeds.map(s => <option key={s} value={s}>{s}×</option>)}</select></label>
      <button type="button" onClick={() => p.stepFrame(-1)} title="Step backward one approximate frame">Previous Frame</button>
      <button type="button" onClick={() => p.stepFrame(1)} title="Step forward one approximate frame">Next Frame</button>
      <button type="button" onClick={p.setA}>Set Loop A</button>
      <button type="button" onClick={p.setB}>Set Loop B</button>
      <button type="button" onClick={p.clearLoop} disabled={p.loopA === null && p.loopB === null}>Clear A–B Loop</button>
      <button type="button" onClick={p.togglePip} disabled={!p.capabilities.pictureInPicture}>{p.pip ? "Exit Picture-in-Picture" : "Picture-in-Picture"}</button>
    </div>
    <details><summary>Video Adjustments</summary><div className="player-toolbar">
      <label>Brightness <input type="range" min="50" max="150" value={brightness} onChange={e=>setBrightness(Number(e.target.value))} /></label>
      <label>Contrast <input type="range" min="50" max="150" value={contrast} onChange={e=>setContrast(Number(e.target.value))} /></label>
      <label><input type="checkbox" checked={sharpen} onChange={e=>setSharpen(e.target.checked)} disabled={!p.capabilities.shaderEnhancements.sharpening} /> GPU Sharpening</label>
      <p className="player-capability-note">{sharpen ? "GPU sharpening enabled." : "Shader-based sharpening and super-resolution require the native GPU pipeline."}</p>
    </div></details>
    {p.message && <p role="status">{p.message}<button type="button" onClick={p.clearMessage} aria-label="Dismiss message">Dismiss</button></p>}
    <p className="player-capability-note">GPU super-resolution, EBU R128 normalization, multi-track audio, dynamic silence skipping, and frame-accurate stepping are unavailable until their native services are connected.</p>
    {shareOpen && <ShareModal media={{title,text:`Clip from ${title}`,url:shareUrl,thumbnailUrl,durationSeconds}} onClose={()=>setShareOpen(false)} />}
  </section>;
}
