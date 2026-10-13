import { useEffect } from "react";
import { useShare, type ShareMedia, type ShareTarget } from "../hooks/useShare";
export function ShareModal({ media, onClose }: { media: ShareMedia; onClose: () => void }) {
  const s = useShare();
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);
  const targets: {id:ShareTarget;label:string}[] = [{id:"x",label:"X / Twitter"},{id:"whatsapp",label:"WhatsApp"},{id:"telegram",label:"Telegram"},{id:"reddit",label:"Reddit"},{id:"linkedin",label:"LinkedIn"}];
  return <div className="vg-modal-backdrop" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)onClose();}}>
    <section className="vg-share-modal" role="dialog" aria-modal="true" aria-labelledby="share-title">
      <header className="vg-share-header"><div><h2 id="share-title">Share Media</h2><p className="vg-muted">Choose how you want to share this item.</p></div><button type="button" className="vg-compact-button vg-ghost-button" onClick={onClose} aria-label="Close share dialog">Close</button></header>
      <article className="vg-share-preview">
        {media.thumbnailUrl && <img src={media.thumbnailUrl} alt="" className="vg-share-thumbnail" />}
        <div><h3>{media.title}</h3>{typeof media.durationSeconds==="number" && <p className="vg-muted">Duration: {Math.floor(media.durationSeconds/60)}:{String(Math.floor(media.durationSeconds%60)).padStart(2,"0")}</p>}<p className="vg-muted">{media.url || "Local media file"}</p></div>
      </article>
      <div className="vg-share-actions">
        <button type="button" className="vg-compact-button vg-primary-button" onClick={()=>void s.share(media)} disabled={s.busy}>{s.busy ? "Sharing…" : "Share Using Device"}</button>
        <button type="button" className="vg-compact-button vg-ghost-button" onClick={()=>void s.copyLink(media)} disabled={!media.url}>Copy Link</button>
      </div>
      <h3 className="vg-share-section-title">Share To</h3>
      <div className="vg-share-targets">{targets.map(t=><button type="button" key={t.id} onClick={()=>s.openTarget(t.id,media)} className="vg-compact-button vg-ghost-button">{t.label}</button>)}</div>
      {s.message && <p role="status" aria-live="polite" className="vg-muted">{s.message}</p>}
      <p className="vg-muted vg-share-footnote">Social targets receive a link and text, not an automatic upload of your local file. Use device sharing to share supported files.</p>
    </section>
  </div>;
}
