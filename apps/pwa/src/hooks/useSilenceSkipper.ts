import { useEffect, useRef, useState } from "react";
export interface SilenceSkipperOptions {enabled:boolean;thresholdDb?:number;silentRate?:number;speechRate?:number;}
/** Uses WebAudio RMS metering; caller owns playback element and permission. */
export function useSilenceSkipper(audio:HTMLMediaElement|null,options:SilenceSkipperOptions) {
 const [isSilent,setSilent]=useState(false); const context=useRef<AudioContext|null>(null);
 useEffect(()=>{if(!audio||!options.enabled)return;let source:MediaElementAudioSourceNode|undefined,analyser:AnalyserNode|undefined,frame=0,buffer:Uint8Array;
 try{context.current=new AudioContext();source=context.current.createMediaElementSource(audio);analyser=context.current.createAnalyser();analyser.fftSize=2048;source.connect(analyser);analyser.connect(context.current.destination);buffer=new Uint8Array(analyser.fftSize);}catch{return;}
 let previous=false;const tick=()=>{if(!analyser||!buffer)return;analyser.getByteTimeDomainData(buffer);let sum=0;for(const v of buffer){const n=(v-128)/128;sum+=n*n;}const rms=Math.sqrt(sum/buffer.length);const db=20*Math.log10(Math.max(rms,1e-6));const silent=db<(options.thresholdDb??-40);if(silent!==previous){previous=silent;setSilent(silent);}audio.playbackRate=silent?(options.silentRate??2.5):(options.speechRate??1);frame=requestAnimationFrame(tick);};tick();return()=>{cancelAnimationFrame(frame);source?.disconnect();analyser?.disconnect();void context.current?.close();context.current=null;audio.playbackRate=1;setSilent(false);};},[audio,options.enabled,options.thresholdDb,options.silentRate,options.speechRate]);
 return {isSilent};
}
