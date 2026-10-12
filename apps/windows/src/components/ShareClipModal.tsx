import { useState } from "react";
import type { FormEvent } from "react";
import type { ClipAspectRatio, ClipExportRequest, ClipFit, ClipExportMode } from "../../../packages/contracts/src/share";
import { validateClipRequest } from "../../../packages/contracts/src/share";
const presets: {value:ClipAspectRatio;label:string;width:number;height:number}[]=[
 {value:"9:16",label:"Vertical · 9:16",width:1080,height:1920},
 {value:"4:5",label:"Portrait feed · 4:5",width:1080,height:1350},
 {value:"1:1",label:"Square · 1:1",width:1080,height:1080},
 {value:"16:9",label:"Landscape · 16:9",width:1920,height:1080}
];
export function ShareClipModal({mediaId,title,durationSeconds,outputDirectoryId,onClose,onExport}:{mediaId:string;title:string;durationSeconds:number;outputDirectoryId:string;onClose:()=>void;onExport:(request:ClipExportRequest)=>Promise<void>}) {
 const [start,setStart]=useState("0"); const [end,setEnd]=useState(String(Math.max(1,Math.floor(durationSeconds)))); const [aspect,setAspect]=useState<ClipAspectRatio>("9:16"); const [fit,setFit]=useState<ClipFit>("cover"); const [mode,setMode]=useState<ClipExportMode>("platform-optimized"); const [busy,setBusy]=useState(false); const [error,setError]=useState("");
 async function submit(event:FormEvent<HTMLFormElement>) {
  event.preventDefault(); setError("");
  const preset=presets.find(p=>p.value===aspect)!;
  const request:ClipExportRequest={mediaId,range:{startSeconds:Number(start),endSeconds:Number(end)},aspectRatio:aspect,outputContainer:"mp4",exportMode:mode,outputDirectoryId,includeThumbnail:true,includeMetadata:true,operations:[
   {op:"trim",startSeconds:Number(start),endSeconds:Number(end)},
   {op:"scale-crop",width:preset.width,height:preset.height,fit:"cover",algorithm:"lanczos"},
   {op:"encode-video",codec:"h264",crf:20},
   {op:"encode-audio",codec:"aac",bitrateKbps:192},
   {op:"mux",container:"mp4"}
  ]};
  try { validateClipRequest(request); if(request.range.endSeconds>durationSeconds) throw new Error("The end time cannot exceed the media duration."); setBusy(true); await onExport(request); onClose(); }
  catch(e) { setError(e instanceof Error?e.message:"Could not export this clip."); }
  finally { setBusy(false); }
 }
 return <div className="vg-modal-backdrop" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget&&!busy)onClose();}}>
  <section className="vg-share-modal" role="dialog" aria-modal="true" aria-labelledby="clip-title">
   <header className="vg-share-header"><div><h2 id="clip-title">Create a Shareable Clip</h2><p className="vg-muted">{title}</p></div><button type="button" onClick={onClose} disabled={busy} aria-label="Close clip export">Close</button></header>
   <form className="grid gap-vg-4" onSubmit={submit}>
    <div className="grid grid-cols-2 gap-vg-3"><label className="grid gap-vg-1 text-sm">Start time (seconds)<input type="number" min="0" step="0.1" value={start} onChange={e=>setStart(e.target.value)} required /></label><label className="grid gap-vg-1 text-sm">End time (seconds)<input type="number" min="0.1" max={durationSeconds} step="0.1" value={end} onChange={e=>setEnd(e.target.value)} required /></label></div>
    <fieldset className="grid gap-vg-2"><legend className="text-sm font-semibold">Export format</legend>{([{value:"platform-optimized",label:"Platform optimized",description:"Re-encode for social platforms"},{value:"original-format",label:"Original format",description:"Preserve source codec/container where supported"}] as const).map(o=><label key={o.value} className="flex items-start gap-vg-2"><input type="radio" name="export-mode" checked={mode===o.value} onChange={()=>setMode(o.value)} /><span><strong>{o.label}</strong><span className="block text-xs text-muted">{o.description}</span></span></label>)}</fieldset>\n    <fieldset className="grid gap-vg-2"><legend className="text-sm font-semibold">Aspect Ratio</legend>{presets.map(p=><label key={p.value} className="flex items-center gap-vg-2"><input type="radio" name="aspect" value={p.value} checked={aspect===p.value} onChange={()=>setAspect(p.value)} />{p.label} ({p.width} × {p.height})</label>)}</fieldset>
    <p className="vg-muted text-sm">The native export service must validate the operation graph, sandbox the output, verify integrity, and commit atomically.</p>
    {error&&<p role="alert" className="text-sm" style={{color:"var(--vg-danger)"}}>{error}</p>}
    <div className="vg-share-actions"><button type="submit" disabled={busy}>{busy?"Exporting…":"Export Clip"}</button><button type="button" onClick={onClose} disabled={busy}>Cancel</button></div>
   </form>
  </section>
 </div>;
}
