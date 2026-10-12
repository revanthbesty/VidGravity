import React,{useState} from "react";
/** Safe UI shell only: browser pages cannot bind arbitrary local HTTP servers without a companion service. */
export function LocalDropModal({createSession,onClose}:{createSession:()=>Promise<{url:string;expiresAt:string}>;onClose:()=>void}){
 const [url,setUrl]=useState("");const [error,setError]=useState("");const [busy,setBusy]=useState(false);
 const start=async()=>{setBusy(true);setError("");try{const s=await createSession();const u=new URL(s.url);if(u.protocol!=="https:"&&u.hostname!=="localhost"&&!/^192\.168\.|^10\.|^172\.(1[6-9]|2\d|3[01])\./.test(u.hostname))throw new Error("Drop endpoint must be HTTPS or a private LAN address.");setUrl(u.toString());}catch(e){setError(e instanceof Error?e.message:"Could not create local drop session.");}finally{setBusy(false);}};
 return <div role="dialog" aria-modal="true" aria-label="Local DropZone" className="vg-modal"><button type="button" onClick={onClose}>Close</button><h2>Local DropZone</h2><p>Creates an expiring transfer session through a trusted companion service. This web UI does not open a server port itself.</p><button type="button" onClick={()=>void start()} disabled={busy}>{busy?"Starting…":"Create secure session"}</button>{url&&<p><a href={url} rel="noreferrer">{url}</a></p>}{error&&<p role="alert">{error}</p>}</div>;
}
