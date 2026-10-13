export interface ArgvPlan {executable:"ffmpeg";args:readonly string[];outputPath:string;}
export function buildWallpaperPlan(inputPath:string,outputPath:string,durationSeconds:number):ArgvPlan {
 if(!inputPath||!outputPath||inputPath===outputPath||!Number.isFinite(durationSeconds)||durationSeconds<=0||durationSeconds>60) throw new Error("INVALID_WALLPAPER_REQUEST");
 const filter="[0:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,fps=30,split=2[fwd][rev];[rev]reverse[back];[fwd][back]concat=n=2:v=1:a=0,format=yuv420p[outv]";
 return {executable:"ffmpeg",outputPath,args:["-nostdin","-hide_banner","-i",inputPath,"-filter_complex",filter,"-map","[outv]","-an","-t",(durationSeconds*2).toFixed(3),"-c:v","libx264","-crf","20","-pix_fmt","yuv420p","-y",outputPath]};
}
