import React, { useRef, useState } from "react";
import { FaceDetector, FilesetResolver } from "@mediapipe/tasks-vision";
import "./ai-studio.css";

export interface FaceSwapProps { onTaskStarted: (path: string, data: FormData, fileName: string) => Promise<void>; }
interface FaceBox { time: number; x: number; y: number; width: number; height: number; frameWidth: number; frameHeight: number; }
const MODEL = "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite";

export function FaceSwap({ onTaskStarted }: FaceSwapProps) {
  const videoRef=useRef<HTMLVideoElement>(null), [video,setVideo]=useState<File|null>(null),[selfie,setSelfie]=useState<File|null>(null),[selfieUrl,setSelfieUrl]=useState(""),[busy,setBusy]=useState(false),[progress,setProgress]=useState(0),[error,setError]=useState(""),[status,setStatus]=useState("Choose a target video and a reference selfie.");
  const detectTimeline = async (file: File): Promise<FaceBox[]> => {
    const url=URL.createObjectURL(file), videoEl=document.createElement("video");videoEl.src=url;videoEl.muted=true;videoEl.playsInline=true;
    try {
      await new Promise<void>((resolve,reject)=>{videoEl.onloadedmetadata=()=>resolve();videoEl.onerror=()=>reject(new Error("Could not read the target video."));});
      const vision=await FilesetResolver.forVisionTasks("https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.22/wasm");
      const detector=await FaceDetector.createFromOptions(vision,{baseOptions:{modelAssetPath:MODEL},runningMode:"IMAGE",minDetectionConfidence:0.45});
      const canvas=document.createElement("canvas"),ctx=canvas.getContext("2d");if(!ctx)throw new Error("Canvas is unavailable.");
      canvas.width=videoEl.videoWidth;canvas.height=videoEl.videoHeight;const boxes:FaceBox[]=[];
      const duration=Math.max(0,videoEl.duration||0), sampleCount=Math.min(180,Math.max(1,Math.ceil(duration)));
      for(let i=0;i<sampleCount;i++){const time=Math.min(Math.max(0,duration-.05),i);videoEl.currentTime=time;await new Promise<void>(resolve=>{videoEl.onseeked=()=>resolve();});ctx.drawImage(videoEl,0,0,canvas.width,canvas.height);const result=detector.detect(canvas);for(const detection of result.detections){const box=detection.boundingBox;if(box)boxes.push({time,x:box.originX,y:box.originY,width:box.width,height:box.height,frameWidth:canvas.width,frameHeight:canvas.height});}setProgress(Math.round((i+1)/sampleCount*100));}
      detector.close();if(!boxes.length)throw new Error("No face was detected. Try a clearer, front-facing clip.");return boxes;
    } finally { URL.revokeObjectURL(url);videoEl.removeAttribute("src");videoEl.load(); }
  };
  const pickSelfie=(file:File|null)=>{if(!file)return;if(!file.type.startsWith("image/")){setError("Choose an image for the reference selfie.");return;}if(file.size>15*1024*1024){setError("Reference image must be 15 MiB or smaller.");return;}if(selfieUrl)URL.revokeObjectURL(selfieUrl);setSelfie(file);setSelfieUrl(URL.createObjectURL(file));setError("");};
  const submit=async()=>{if(!video||!selfie)return;setBusy(true);setError("");setProgress(0);try{setStatus("Detecting faces locally with MediaPipe…");const timeline=await detectTimeline(video);const data=new FormData();data.append("video",video);data.append("reference",selfie);data.append("timeline",JSON.stringify({boxes:timeline,coordinateSpace:"source-pixels",sampleIntervalSeconds:1}));setStatus(`Detected ${timeline.length} face samples. Queuing the model task…`);await onTaskStarted("/api/ai/faceswap",data,"vidgravity-face-off.mp4");setStatus("Face-Off queued. Progress will appear in AI Studio tasks.");}catch(e){setError(e instanceof Error?e.message:"Could not start Face-Off.");setStatus("Ready when you are.");}finally{setBusy(false);}};
  return <section className="vg-ai-upload" aria-labelledby="vg-face-title"><header><h2 id="vg-face-title">Face-Off · video face swap</h2><p>Face boxes are detected on your device. The configured provider performs the actual identity swap and temporal blending.</p></header>
    <label>Target video<input type="file" accept="video/*" onChange={e=>{const f=e.target.files?.[0];if(f&&f.size>200*1024*1024){setError("Target video exceeds 200 MiB.");return;}setVideo(f??null);setError("");}}/></label>
    <label>Reference selfie<input type="file" accept="image/*" onChange={e=>pickSelfie(e.target.files?.[0]??null)}/></label>{selfieUrl&&<img className="vg-ai-preview" src={selfieUrl} alt="Reference selfie preview"/>}
    <p className="vg-ai-field-note">Timeline sampling is capped at 180 frames (approximately three minutes at one sample per second). Longer clips are sampled up to the cap.</p>
    <button type="button" disabled={busy||!video||!selfie} onClick={()=>void submit()}>{busy?"Detecting…":"Detect faces and queue swap"}</button>{busy&&<progress max={100} value={progress}/>}<p role="status">{status}</p>{error&&<p role="alert" className="vg-ai-error">{error}</p>}
  </section>;
}
