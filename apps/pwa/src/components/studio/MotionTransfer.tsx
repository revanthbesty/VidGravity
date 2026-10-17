import React, { useState } from "react";
import "./ai-studio.css";
export interface MotionTransferProps { onTaskStarted: (path: string, data: FormData, fileName: string) => Promise<void>; }
export function MotionTransfer({ onTaskStarted }: MotionTransferProps) {
  const [character,setCharacter]=useState<File|null>(null),[dance,setDance]=useState<File|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(""),[status,setStatus]=useState("Upload a full-body character image and a choreography video.");
  const submit=async()=>{if(!character||!dance)return;setBusy(true);setError("");try{const data=new FormData();data.append("character",character);data.append("choreography",dance);data.append("options",JSON.stringify({preserveTempo:true,outputFormat:"mp4",motionMode:"full-body-transfer"}));setStatus("Queuing motion transfer with the configured provider…");await onTaskStarted("/api/ai/motion",data,"vidgravity-motion-transfer.mp4");setStatus("Motion transfer queued. Watch progress in AI Studio tasks.");}catch(e){setError(e instanceof Error?e.message:"Could not start motion transfer.");setStatus("Ready when you are.");}finally{setBusy(false);}};
  return <section className="vg-ai-upload" aria-labelledby="vg-motion-title"><header><h2 id="vg-motion-title">Motion Transfer · dance puppetry</h2><p>Animate a full-body character image using the timing and pose sequence from a reference choreography clip.</p></header>
    <label>Static full-body character image<input type="file" accept="image/*" onChange={e=>{const f=e.target.files?.[0];if(f&&f.size>15*1024*1024){setError("Character image must be 15 MiB or smaller.");return;}setCharacter(f??null);setError("");}}/></label>
    <label>Reference choreography video<input type="file" accept="video/*" onChange={e=>{const f=e.target.files?.[0];if(f&&f.size>200*1024*1024){setError("Choreography video exceeds 200 MiB.");return;}setDance(f??null);setError("");}}/></label>
    <p className="vg-ai-field-note">The backend uses a provider adapter. Kling/Viggle-compatible credentials and an API adapter must be configured before generative renders can run.</p>
    <button type="button" disabled={busy||!character||!dance} onClick={()=>void submit()}>{busy?"Queuing…":"Generate dance video"}</button><p role="status">{status}</p>{error&&<p role="alert" className="vg-ai-error">{error}</p>}
  </section>;
}
