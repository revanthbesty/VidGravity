export interface SegmentPlan {executable:"ffmpeg";args:readonly string[];outputPattern:string;}

/**
 * Builds an FFmpeg plan for platform-limited clips.
 * Re-encoding forces keyframes at segment boundaries; stream-copy splitting can
 * exceed the requested cap when the source has sparse keyframes. A small
 * segment_time_delta absorbs timestamp rounding so boundary keyframes are not
 * skipped by the segment muxer.
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
 return {
  executable:"ffmpeg",
  outputPattern,
  args:[
   "-nostdin","-hide_banner","-i",inputPath,
   "-map","0:v:0","-map","0:a?",
   "-c:v","libx264","-preset","veryfast","-crf","20",
   "-force_key_frames","expr:gte(t,n_forced*" + String(segmentSeconds) + ")",
   "-c:a","aac","-b:a","192k",
   "-f","segment","-segment_time",String(segmentSeconds),
   "-segment_time_delta","0.1",
   "-reset_timestamps","1","-y",outputPattern
  ]
 };
}
