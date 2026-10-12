export interface SegmentPlan {executable:"ffmpeg";args:readonly string[];outputPattern:string;}
export function buildStatusSplitPlan(inputPath:string,outputPattern:string,durationSeconds:number):SegmentPlan {
 if(!inputPath||!outputPattern||inputPath===outputPattern||!Number.isFinite(durationSeconds)||durationSeconds<=30)throw new Error("INVALID_STATUS_SPLIT");
 if(!/\%0[2-4]d/.test(outputPattern))throw new Error("OUTPUT_PATTERN_MUST_BE_NUMBERED");
 return {executable:"ffmpeg",outputPattern,args:["-nostdin","-hide_banner","-i",inputPath,"-map","0","-c","copy","-f","segment","-segment_time","30","-reset_timestamps","1","-y",outputPattern]};
}
