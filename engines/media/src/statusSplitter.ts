export interface SegmentPlan {executable:"ffmpeg";args:readonly string[];outputPattern:string;}

/**
 * Builds a lossless segment plan for a platform's per-clip duration cap.
 * WhatsApp Status now permits videos up to 90 seconds, so 90 seconds is the
 * default. Callers targeting a different platform can pass its own cap.
 */
export function buildStatusSplitPlan(
 inputPath:string,
 outputPattern:string,
 durationSeconds:number,
 segmentSeconds:number=90,
):SegmentPlan {
 if(!inputPath||!outputPattern||inputPath===outputPattern||
    !Number.isFinite(durationSeconds)||!Number.isFinite(segmentSeconds)||
    segmentSeconds<=0||durationSeconds<=segmentSeconds)throw new Error("INVALID_STATUS_SPLIT");
 if(!/\%0[2-4]d/.test(outputPattern))throw new Error("OUTPUT_PATTERN_MUST_BE_NUMBERED");
 return {executable:"ffmpeg",outputPattern,args:["-nostdin","-hide_banner","-i",inputPath,"-map","0","-c","copy","-f","segment","-segment_time",String(segmentSeconds),"-reset_timestamps","1","-y",outputPattern]};
}
