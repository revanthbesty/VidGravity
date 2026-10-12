import React, { useRef, useState } from "react";
export type CaptionStyle="classic"|"heavy"|"neon"|"typewriter"|"comic"|"pill";
export interface CaptionCanvasProps {imageUrl:string;onExport?:(blob:Blob)=>void;onAiVibe?:()=>Promise<string[]>;}
const styles:Record<CaptionStyle,React.CSSProperties>={classic:{background:"rgba(0,0,0,.60)",color:"#fff",fontWeight:500},heavy:{color:"#fff",fontWeight:900,WebkitTextStroke:"1px #111",textShadow:"2px 2px #111"},neon:{color:"#fff",textShadow:"0 0 6px #e879f9,0 0 18px #a855f7"},typewriter:{fontFamily:"monospace",color:"#fff"},comic:{fontWeight:900,color:"#fff",WebkitTextStroke:"1px #111",transform:"rotate(-2deg)"},pill:{background:"#fff",color:"#111",borderRadius:999,padding:"6px 14px"}};
export function CaptionCanvas({imageUrl,onExport,onAiVibe}:CaptionCanvasProps){
 const canvas=useRef<HTMLCanvasElement>(null);const [caption,setCaption]=useState("");const [style,setStyle]=useState<CaptionStyle>("classic");const [color,setColor]=useState("#ffffff");const [x,setX]=useState(.5);const [y,setY]=useState(.2);const [scale,setScale]=useState(1);const [rotation,setRotation]=useState(0);const [vibes,setVibes]=useState<string[]>([]);const [busy,setBusy]=useState(false);const drag=useRef<{x:number;y:number;px:number;py:number}|null>(null);
 const exportPng=()=>{const c=canvas.current;if(!c)return;c.toBlob(b=>{if(b)onExport?.(b);},"image/png");};
 const ai=async()=>{if(!onAiVibe)return;setBusy(true);try{setVibes((await onAiVibe()).slice(0,3));}finally{setBusy(false);}};
 const move=(e:React.PointerEvent<HTMLDivElement>)=>{if(!drag.current)return;const r=e.currentTarget.getBoundingClientRect();setX(Math.max(0,Math.min(1,drag.current.px+(e.clientX-drag.current.x)/r.width)));setY(Math.max(0,Math.min(1,drag.current.py+(e.clientY-drag.current.y)/r.height));};
 return <section className="vg-studio" aria-label="Caption studio">
 <div className="vg-studio-toolbar"><input aria-label="Caption text" value={caption} onChange={e=>setCaption(e.target.value)} placeholder="Add a caption…" /><select aria-label="Typography" value={style} onChange={e=>setStyle(e.target.value as CaptionStyle)}>{Object.keys(styles).map(s=><option key={s}>{s}</option>)}</select><input type="color" aria-label="Caption color" value={color} onChange={e=>setColor(e.target.value)}/><button type="button" disabled={!onAiVibe||busy} onClick={()=>void ai()}>✨ AI Vibe</button><button type="button" onClick={exportPng}>Export PNG</button></div>
 <div className="vg-caption-stage" style={{backgroundImage:`url("${imageUrl.replace(/["\\\\]/g,"")}")`}} onPointerDown={e=>{drag.current={x:e.clientX,y:e.clientY,px:x,py:y};e.currentTarget.setPointerCapture(e.pointerId);}} onPointerMove={move} onPointerUp={()=>{drag.current=null;}}>
 <div className="vg-caption-text" style={{left:`${x*100}%`,top:`${y*100}%`,transform:`translate(-50%,-50%) scale(${scale}) rotate(${rotation}deg)`,...styles[style],color:style==="classic"||style==="heavy"||style==="neon"||style==="typewriter"||style==="comic"?color:styles.pill.color}}>{caption}</div></div>
 <div className="vg-studio-toolbar"><label>Scale <input type="range" min=".5" max="3" step=".1" value={scale} onChange={e=>setScale(+e.target.value)}/></label><label>Tilt <input type="range" min="-30" max="30" value={rotation} onChange={e=>setRotation(+e.target.value)}/></label></div>
 {vibes.length>0&&<ul>{vibes.map(v=><li key={v}><button type="button" onClick={()=>setCaption(v)}>{v}</button></li>)}</ul>}
 <canvas ref={canvas} hidden aria-hidden="true" />
 </section>;
}
