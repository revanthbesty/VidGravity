export interface PhotoDumpInput { photos: readonly string[]; audioPath:string; outputPath:string; beatTimesSeconds:readonly number[]; durationSeconds:number; }
export interface ArgvPlan { executable:"ffmpeg";args:readonly string[];outputPath:string; }
/** Beat positions should come from an analyzed waveform; this builder validates them and emits an argv plan. */
export function buildPhotoDumpPlan(i:PhotoDumpInput):ArgvPlan {
 if(i.photos.length<5||i.photos.length>12||!i.audioPath||!i.outputPath||i.photos.includes(i.outputPath)||!(i.durationSeconds>0)||i.beatTimesSeconds.some((t,n)=>!Number.isFinite(t)||t<0||t>i.durationSeconds||(n>0&&t<i.beatTimesSeconds[n-1]))) throw new Error("INVALID_PHOTO_DUMP");
 const args:string[]=["-nostdin","-hide_banner"]; for(const p of i.photos) args.push("-loop","1","-t",i.durationSeconds.toFixed(3),"-i",p); args.push("-i",i.audioPath);
 const filters=i.photos.map((_,n)=>`[${n}:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,fps=30,format=yuv420p[v${n}]`).join(";");
 filters && args.push("-filter_complex",filters+";"+i.photos.map((_,n)=>`[v${n}]`).join("")+`concat=n=${i.photos.length}:v=1:a=0[outv]`,"-map","[outv]","-map",`${i.photos.length}:a:0`,"-t",i.durationSeconds.toFixed(3),"-c:v","libx264","-crf","20","-c:a","aac","-b:a","192k","-movflags","+faststart","-y",i.outputPath);
 return {executable:"ffmpeg",args,outputPath:i.outputPath};
}
