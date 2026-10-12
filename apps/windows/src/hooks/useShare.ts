import { useCallback, useState } from "react";
export interface ShareMedia { title: string; url?: string; text?: string; file?: File; thumbnailUrl?: string; durationSeconds?: number; }
export type ShareTarget = "x" | "whatsapp" | "telegram" | "reddit" | "linkedin";
declare global { interface Window { VidGravityAndroid?: { shareMedia?: (payload: { title: string; text?: string; url?: string; fileUri?: string; mimeType?: string; thumbnailUri?: string }) => Promise<boolean> }; } }
const targetUrls: Record<ShareTarget, (title: string, url: string, text: string) => string> = {
  x: (title,url,text) => "https://twitter.com/intent/tweet?"+new URLSearchParams({text: [title,text].filter(Boolean).join(" "),url}),
  whatsapp: (title,url,text) => "https://wa.me/?text="+encodeURIComponent([title,text,url].filter(Boolean).join(" ")),
  telegram: (title,url,text) => "https://t.me/share/url?"+new URLSearchParams({url,text:[title,text].filter(Boolean).join(" ")}),
  reddit: (title,url,text) => "https://www.reddit.com/submit?"+new URLSearchParams({url,title}),
  linkedin: (_title,url,text) => "https://www.linkedin.com/sharing/share-offsite/?url="+encodeURIComponent(url || text)
};
export function useShare() {
  const [busy,setBusy] = useState(false);
  const [message,setMessage] = useState<string|null>(null);
  const share = useCallback(async (media: ShareMedia) => {
    setBusy(true); setMessage(null);
    try {
      // Android bridge may implement Intent.ACTION_SEND with content URI and thumbnail.
      if (window.VidGravityAndroid?.shareMedia) {
        const accepted = await window.VidGravityAndroid.shareMedia({ title: media.title, text: media.text, url: media.url,
          fileUri: undefined, mimeType: media.file?.type || "video/mp4", thumbnailUri: media.thumbnailUrl });
        if (accepted) return true;
      }
      if (navigator.share) {
        const data: ShareData = { title: media.title, text: media.text, url: media.url };
        if (media.file && navigator.canShare?.({ files: [media.file] })) data.files = [media.file];
        await navigator.share(data); return true;
      }
      if (media.url && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText([media.title,media.text,media.url].filter(Boolean).join("\n"));
        setMessage("Share details copied to the clipboard."); return true;
      }
      setMessage("Sharing is not available in this environment."); return false;
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return false;
      setMessage(e instanceof Error ? e.message : "Could not share this media."); return false;
    } finally { setBusy(false); }
  },[]);
  const openTarget = useCallback((target: ShareTarget, media: ShareMedia) => {
    const url = targetUrls[target](media.title,media.url || "",media.text || "");
    if (!media.url && target !== "whatsapp" && target !== "x") { setMessage("This target needs a shareable URL."); return; }
    window.open(url,"_blank","noopener,noreferrer");
  },[]);
  const copyLink = useCallback(async (media: ShareMedia) => {
    if (!media.url) { setMessage("There is no shareable link for this item."); return false; }
    try { await navigator.clipboard.writeText(media.url); setMessage("Link copied."); return true; }
    catch { setMessage("Clipboard access is unavailable. Select and copy the link manually."); return false; }
  },[]);
  return { share, openTarget, copyLink, busy, message, clearMessage:()=>setMessage(null) };
}
