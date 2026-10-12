import React,{useEffect,useRef,useState} from "react";
import {CaptionCanvas} from "./CaptionCanvas";
export function SnapStudioModal({onClose}:{onClose:()=>void}){
 const video=useRef<HTMLVideoElement>(null);const [stream,setStream]=useState<MediaStream|null>(null);const [frame,setFrame]=useState<string>("");
 useEffect(()=>{let live:MediaStream|null=null;void navigator.mediaDevices?.getUserMedia({video:{facingMode:"user"},audio:false}).then(s=>{live=s;setStream(s);if(video.current)video.current.srcObject=s;}).catch(()=>setFrame(""));return()=>live?.getTracks().forEach(t=>t.stop());},[]);\n useEffect(()=>()=>{if(frame.startsWith("blob:"))URL.revokeObjectURL(frame);},[frame]);
 const snap=()=>{const v=video.current;if(!v||!v.videoWidth)return;const c=document.createElement("canvas");c.width=v.videoWidth;c.height=v.videoHeight;c.getContext("2d")?.drawImage(v,0,0);c.toBlob(blob=>{if(blob)setFrame(URL.createObjectURL(blob));},"image/png");stream?.getTracks().forEach(t=>t.stop());};\n const exportFrame=(blob:Blob)=>{const url=URL.createObjectURL(blob);const a=document.createElement("a");a.href=url;a.download="vidgravity-snap.png";a.click();window.setTimeout(()=>URL.revokeObjectURL(url),1000);};
 return <div role="dialog" aria-modal="true" aria-label="Snap Studio" className="vg-modal"><button onClick={onClose} type="button">Close</button>{frame?<CaptionCanvas imageUrl={frame} onExport={exportFrame}/>:<><video ref={video} autoPlay playsInline muted/><button type="button" onClick={snap} disabled={!stream}>📷 Capture frame</button></>}</div>;
}
