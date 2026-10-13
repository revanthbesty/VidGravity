import type { FrameCaptureRequest } from "../../../packages/contracts/src/studio";
export interface ArgvPlan { executable: "ffmpeg"; args: readonly string[]; outputPath: string; }
/** Returns argv, never a shell command. outputPath must be a newly allocated derivative path. */
export function buildFrameGrabPlan(inputPath: string, outputPath: string, timestampMs: number): ArgvPlan {
  if (!inputPath || !outputPath || inputPath === outputPath || !Number.isFinite(timestampMs) || timestampMs < 0) throw new Error("INVALID_FRAME_CAPTURE");
  return { executable: "ffmpeg", args: ["-nostdin","-hide_banner","-ss",(timestampMs/1000).toFixed(3),"-i",inputPath,"-frames:v","1","-vsync","0","-y",outputPath], outputPath };
}
export function validateFrameRequest(r: FrameCaptureRequest): void {
  if (!r.sourceId || !r.outputId || r.sourceId === r.outputId || !Number.isFinite(r.timestampMs) || r.timestampMs < 0) throw new Error("INVALID_FRAME_REQUEST");
}
export const BLURRED_BACKDROP_FILTER = "[0:v]split=2[orig][bg];[bg]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,boxblur=20:5[blurred];[orig]scale=1080:1920:force_original_aspect_ratio=decrease[scaled];[blurred][scaled]overlay=(W-w)/2:(H-h)/2[outv]";
