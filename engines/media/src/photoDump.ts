export interface PhotoDumpInput { photos: readonly string[]; audioPath:string; outputPath:string; beatTimesSeconds:readonly number[]; durationSeconds:number; }
export interface ArgvPlan { executable:"ffmpeg";args:readonly string[];outputPath:string; }
/** Beat boundaries must be analyzed upstream; one transition boundary is required between each adjacent photo. */
export function buildPhotoDumpPlan(i:PhotoDumpInput):ArgvPlan {
 if(i.photos.length<5||i.photos.length>12||i.photos.some(p=>typeof p!=="string"||!p.trim())||!i.audioPath?.trim()||!i.outputPath?.trim()||i.photos.includes(i.outputPath)||i.photos.includes(i.audioPath)||i.audioPath===i.outputPath||!Number.isFinite(i.durationSeconds)||i.durationSeconds<=0||i.beatTimesSeconds.length!==i.photos.length-1||i.beatTimesSeconds.some((t,n)=>!Number.isFinite(t)||t<=0||t>=i.durationSeconds||(n>0&&t<=i.beatTimesSeconds[n-1])))throw new Error("INVALID_PHOTO_DUMP");
 const boundaries=[0,...i.beatTimesSeconds,i.durationSeconds];
 const durations=boundaries.slice(1).map((t,n)=>t-boundaries[n]);
 if(durations.some(d=>d<0.15))throw new Error("PHOTO_DUMP_BEATS_TOO_CLOSE");
 const args:string[]=["-nostdin","-hide_banner"];
 i.photos.forEach((p,n)=>args.push("-loop","1","-t",durations[n].toFixed(3),"-i",p));
 args.push("-i",i.audioPath);
 const filters=i.photos.map((_,n)=>`[${n}:v]trim=duration=${durations[n].toFixed(3)},setpts=PTS-STARTPTS,scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,fps=30,format=yuv420p[v${n}]`).join(";");
 const concat=i.photos.map((_,n)=>`[v${n}]`).join("")+`concat=n=${i.photos.length}:v=1:a=0[outv]`;
 args.push("-filter_complex",filters+";"+concat,"-map","[outv]","-map",`${i.photos.length}:a:0`,"-t",i.durationSeconds.toFixed(3),"-c:v","libx264","-crf","20","-c:a","aac","-b:a","192k","-movflags","+faststart","-y",i.outputPath);
 return {executable:"ffmpeg",args,outputPath:i.outputPath};
}
