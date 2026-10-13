import { useMemo, useState } from "react";

export interface StoryCardRequest {
  mediaId: string;
  startSeconds: number;
  durationSeconds: number;
  title: string;
  artist: string;
  layout: "cover-art" | "video-backdrop";
}
interface StoryCardModalProps {
  open: boolean;
  mediaId: string;
  mediaTitle: string;
  durationSeconds: number;
  onClose: () => void;
  onRender: (request: StoryCardRequest) => Promise<{ url?: string }>;
  onShare?: (target: "instagram" | "whatsapp", renderedUrl?: string) => void;
}
export function StoryCardModal({ open, mediaId, mediaTitle, durationSeconds, onClose, onRender, onShare }: StoryCardModalProps) {
  const maxStart = Math.max(0, durationSeconds - 15);
  const [start, setStart] = useState(0);
  const [length, setLength] = useState(15);
  const [title, setTitle] = useState(mediaTitle);
  const [artist, setArtist] = useState("");
  const [layout, setLayout] = useState<"cover-art" | "video-backdrop">("cover-art");
  const [busy, setBusy] = useState(false);
  const [renderedUrl, setRenderedUrl] = useState<string>();
  const [message, setMessage] = useState("");
  const end = useMemo(() => Math.min(durationSeconds, start + length), [durationSeconds, start, length]);
  if (!open) return null;
  async function render() {
    setBusy(true); setMessage("");
    try {
      const result = await onRender({ mediaId, startSeconds: start, durationSeconds: length, title: title.trim(), artist: artist.trim(), layout });
      setRenderedUrl(result.url);
      setMessage("Story derivative rendered.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Story render failed."); }
    finally { setBusy(false); }
  }
  return <div role="dialog" aria-modal="true" aria-labelledby="story-card-title" className="fixed inset-0 z-50 flex items-end justify-center bg-black/70 p-0 sm:items-center sm:p-4">
    <section className="flex max-h-[94dvh] w-full max-w-xl flex-col overflow-y-auto rounded-t-3xl bg-surface p-4 text-foreground sm:rounded-3xl">
      <header className="flex items-center justify-between gap-3"><h2 id="story-card-title" className="text-lg font-bold">Story Card Studio</h2><button type="button" onClick={onClose} className="min-h-12 min-w-12 rounded-xl" aria-label="Close">×</button></header>
      <div className="mx-auto mt-3 flex aspect-[9/16] w-40 flex-col justify-end rounded-2xl bg-gradient-to-br from-violet-700 via-fuchsia-600 to-orange-400 p-3 text-white shadow-xl">
        <div className="mb-auto grid aspect-square place-items-center rounded-xl bg-black/20 text-5xl">{layout === "cover-art" ? "♫" : "▶"}</div>
        <strong className="line-clamp-2">{title || "Your Story"}</strong><span className="text-xs">{artist || "VidGravity"}</span><span className="mt-2 text-sm">〰 ♫ 〰 ♫ 〰</span>
      </div>
      <div className="mt-4 space-y-3">
        <label className="block text-sm font-medium">Snippet duration: {length}s
          <input className="mt-1 min-h-12 w-full accent-violet-500" type="range" min={15} max={30} step={1} value={length} onChange={e => { const next = Number(e.target.value); setLength(next); setStart(s => Math.min(s, Math.max(0, durationSeconds - next))); }} />
        </label>
        <label className="block text-sm font-medium">Start: {start.toFixed(1)}s · End: {end.toFixed(1)}s
          <input className="mt-1 min-h-12 w-full accent-violet-500" type="range" min={0} max={Math.max(0, durationSeconds - length)} step={0.1} value={start} onChange={e => setStart(Number(e.target.value))} />
        </label>
        <label className="block text-sm">Story title<input className="mt-1 min-h-12 w-full rounded-xl border border-line bg-background px-3" value={title} maxLength={100} onChange={e => setTitle(e.target.value)} /></label>
        <label className="block text-sm">Artist / caption<input className="mt-1 min-h-12 w-full rounded-xl border border-line bg-background px-3" value={artist} maxLength={100} onChange={e => setArtist(e.target.value)} /></label>
        <div className="grid grid-cols-2 gap-2">
          <button type="button" aria-pressed={layout === "cover-art"} onClick={() => setLayout("cover-art")} className="min-h-12 rounded-xl border border-line px-2">Cover Art</button>
          <button type="button" aria-pressed={layout === "video-backdrop"} onClick={() => setLayout("video-backdrop")} className="min-h-12 rounded-xl border border-line px-2">Video Backdrop</button>
        </div>
        <button type="button" disabled={busy || !title.trim() || durationSeconds < 15} onClick={() => void render()} className="min-h-14 w-full rounded-2xl bg-violet-600 font-bold text-white disabled:opacity-50">{busy ? "Rendering…" : "Render 9:16 Story"}</button>
        <div className="grid grid-cols-2 gap-2">
          <button type="button" disabled={!renderedUrl} onClick={() => onShare?.("instagram", renderedUrl)} className="min-h-14 rounded-2xl border border-line disabled:opacity-50">Post to Instagram Story</button>
          <button type="button" disabled={!renderedUrl} onClick={() => onShare?.("whatsapp", renderedUrl)} className="min-h-14 rounded-2xl border border-line disabled:opacity-50">Post to WhatsApp Status</button>
        </div>
        {message && <p role="status" className="text-sm text-muted">{message}</p>}
        <p className="text-xs text-muted">Sharing opens the supported system/browser flow; the app cannot silently publish on your behalf.</p>
      </div>
    </section>
  </div>;
}
