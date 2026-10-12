import type { DownloadProgress, MediaContainer, TaskId } from "./downloader";
export type SharedUrlSource = "android-share-sheet" | "paste" | "browser-share";
export type AndroidPreset = "best-mp4" | "1080p" | "audio-only" | "custom";
export type AndroidNetworkConstraint = "connected" | "unmetered";
export interface AndroidShareIntakeRequest { text:string; source:SharedUrlSource; receivedAt:string; }
export interface NormalizedSharedUrl { url:string; host:string; source:SharedUrlSource; }
export interface AndroidDownloadPreferences { autoDownloadWithPreferredPreset:boolean; preferredFormatSelector:string; preferredPreset:AndroidPreset; networkConstraint:AndroidNetworkConstraint; }
export interface AndroidFormatOption { selector:string; formatId:string; resolution:string; container:MediaContainer; videoCodec:string; audioCodec:string; sizeBytes?:number; }
export interface AndroidSourceAnalysis { title:string; durationSeconds?:number; formats:AndroidFormatOption[]; providerId?:string; }
export interface AndroidEnqueueRequest { url:string; selector:string; title?:string; networkConstraint:AndroidNetworkConstraint; expedited:boolean; }
export interface AndroidDownloadProgressEvent { taskId:TaskId; state:DownloadProgress["state"]; bytesDownloaded:number; totalBytes?:number; bytesPerSecond?:number; etaSeconds?:number; percent?:number; }
export interface AndroidDownloadReceipt { contentUri:string; displayName:string; mimeType:"video/mp4"; bytesDownloaded:number; sha256:string; indexed:boolean; }
export function extractSharedUrl(text:string):string|undefined{
 if(!text||text.length>8192||/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(text))return undefined;
 const m=text.match(/https?:\/\/[^\s<>"']+|(?:www\.)?[a-z0-9][a-z0-9.-]+\.[a-z]{2,}(?:\/[^\s<>"']*)?/i);if(!m)return undefined;
 let c=m[0].replace(/[),.;!?}\]]+$/,"");if(!/^https?:\/\//i.test(c))c="https://"+c;
 try{const u=new URL(c);if(!["http:","https:"].includes(u.protocol)||!u.hostname||u.username||u.password)return undefined;if(/^(localhost|.*\.localhost|.*\.local|.*\.internal)$/i.test(u.hostname))return undefined;u.hash="";u.protocol="https:";return u.toString()}catch{return undefined}
}
export function preferredNetworkConstraint(p:AndroidDownloadPreferences):AndroidNetworkConstraint{return p.networkConstraint==="unmetered"?"unmetered":"connected"}
export function validateAndroidDownloadRequest(r:AndroidEnqueueRequest):void{const u=new URL(r.url);if(u.protocol!=="https:"||!u.hostname||u.username||u.password)throw new Error("A public HTTPS URL is required.");if(!r.selector.trim()||r.selector.length>200||!/^[A-Za-z0-9_*+./,\[\]()-]+$/.test(r.selector))throw new Error("Invalid format selector.")}
