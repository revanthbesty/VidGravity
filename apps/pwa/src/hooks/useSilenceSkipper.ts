import { useEffect, useRef, useState } from "react";
export interface SilenceSkipperOptions {enabled:boolean;thresholdDb?:number;silentRate?:number;speechRate?:number;}
interface Meter {context:AudioContext;source:MediaElementAudioSourceNode;analyser:AnalyserNode;buffer:Uint8Array<ArrayBuffer>;}
/** Reuses one WebAudio source per mounted media element; avoids createMediaElementSource() reuse failures. */
export function useSilenceSkipper(audio:HTMLMediaElement|null,options:SilenceSkipperOptions) {
 const [isSilent,setSilent]=useState(false);const meter=useRef<Meter|null>(null);const latest=useRef(options);latest.current=options;
 useEffect(()=>{
  if(!audio)return;
  let created:Meter;
  try{
   const context=new AudioContext();const source=context.createMediaElementSource(audio);const analyser=context.createAnalyser();analyser.fftSize=2048;source.connect(analyser);analyser.connect(context.destination);
   created={context,source,analyser,buffer:new Uint8Array(new ArrayBuffer(analyser.fftSize))};meter.current=created;
  }catch{return;}
  return()=>{created.source.disconnect();created.analyser.disconnect();void created.context.close();if(meter.current===created)meter.current=null;audio.playbackRate=1;setSilent(false);};
 },[audio]);
 useEffect(()=>{
  const current=meter.current;
  if(!audio||!options.enabled||!current){if(audio)audio.playbackRate=options.speechRate??1;setSilent(false);return;}
  let frame=0;let previous=false;let alive=true;
  const tick=()=>{if(!alive)return;const m=meter.current;if(!m)return;m.analyser.getByteTimeDomainData(m.buffer);let sum=0;for(const v of m.buffer){const n=(v-128)/128;sum+=n*n;}const rms=Math.sqrt(sum/m.buffer.length);const db=20*Math.log10(Math.max(rms,1e-6));const opts=latest.current;const silent=db<(opts.thresholdDb??-40);if(silent!==previous){previous=silent;setSilent(silent);}audio.playbackRate=silent?(opts.silentRate??2.5):(opts.speechRate??1);frame=requestAnimationFrame(tick);};
  void current.context.resume().catch(()=>undefined);tick();
  return()=>{alive=false;cancelAnimationFrame(frame);audio.playbackRate=latest.current.speechRate??1;setSilent(false);};
 },[audio,options.enabled,options.thresholdDb,options.silentRate,options.speechRate]);
 return {isSilent};
}
