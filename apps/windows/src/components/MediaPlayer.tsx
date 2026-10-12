import { useRef } from "react";
import { useMediaPlayer } from "../hooks/useMediaPlayer";

export interface MediaPlayerProps { src: string; title: string; onClose: () => void; }
export function MediaPlayer({ src, title, onClose }: MediaPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const p = useMediaPlayer(videoRef);
  return <section className="player-shell" aria-label={`Player: ${title}`}>
    <header className="player-header"><h2>{title}</h2><button type="button" onClick={onClose} aria-label="Close player">Close</button></header>
    <video ref={videoRef} src={src} controls playsInline preload="metadata" aria-label={title} />
    <div className="player-toolbar" role="group" aria-label="Playback settings">
      <label>Speed <select value={p.speed} onChange={e => p.applySpeed(Number(e.target.value) as typeof p.speed)}>{p.speeds.map(s => <option key={s} value={s}>{s}×</option>)}</select></label>
      <button type="button" onClick={() => p.stepFrame(-1)} title="Step backward one frame">Previous Frame</button>
      <button type="button" onClick={() => p.stepFrame(1)} title="Step forward one frame">Next Frame</button>
      <button type="button" onClick={p.setA}>Set Loop A</button>
      <button type="button" onClick={p.setB}>Set Loop B</button>
      <button type="button" onClick={p.clearLoop} disabled={p.loopA === null && p.loopB === null}>Clear A–B Loop</button>
      <button type="button" onClick={p.togglePip} disabled={!p.capabilities.pictureInPicture}>{p.pip ? "Exit Picture-in-Picture" : "Picture-in-Picture"}</button>
    </div>
    {p.message && <p role="status">{p.message}<button type="button" onClick={p.clearMessage} aria-label="Dismiss message">Dismiss</button></p>}
    <p className="player-capability-note">Enhancement shaders, loudness normalization, and multi-track selection are shown as unavailable until the native media pipeline exposes them.</p>
  </section>;
}
