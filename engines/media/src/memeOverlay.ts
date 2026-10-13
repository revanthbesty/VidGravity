export interface OverlayPlan { executable:"ffmpeg"; args:readonly string[]; outputPath:string; }
export function buildMemeOverlayPlan(basePath:string, cutoutPath:string, outputPath:string, x:number,y:number,width:number,height:number):OverlayPlan {
 const vals=[x,y,width,height]; if(!basePath||!cutoutPath||!outputPath||basePath===outputPath||vals.some(v=>!Number.isFinite(v))||width<1||height<1||Math.abs(x)>10000||Math.abs(y)>10000||width>10000||height>10000) throw new Error("INVALID_OVERLAY_ARGUMENTS");
 const graph=`[1:v]chromakey=0x00FF00:0.12:0.08,scale=${Math.round(width)}:${Math.round(height)}[cutout];[0:v][cutout]overlay=x=${Math.round(x)}:y=${Math.round(y)}[outv]`;
 return {executable:"ffmpeg",outputPath,args:["-nostdin","-hide_banner","-i",basePath,"-i",cutoutPath,"-filter_complex",graph,"-map","[outv]","-map","0:a?","-c:a","copy","-y",outputPath]};
}
