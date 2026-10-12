/** Soundbite/story package builders; always use spawn(executable, args, {shell:false}). */
export interface SoundbiteRequest {
  inputPath: string;
  soundtrackPath: string;
  outputPath: string;
  startSeconds: number;
  durationSeconds: number;
  width?: number;
  height?: number;
  loudnessI?: number;
  loudnessTP?: number;
  loudnessLRA?: number;
}
function pathArg(value: string, label: string): string {
  if (!value || value.includes("\0")) throw new TypeError(`Invalid ${label}`);
  return value;
}
export function buildSoundbiteArgs(request: SoundbiteRequest): string[] {
  const input = pathArg(request.inputPath, "input path");
  const audio = pathArg(request.soundtrackPath, "soundtrack path");
  const output = pathArg(request.outputPath, "output path");
  if (new Set([input, output]).size !== 2) throw new TypeError("Output must be a derivative, not the source");
  const start = request.startSeconds, duration = request.durationSeconds;
  if (!Number.isFinite(start) || start < 0 || !Number.isFinite(duration) || duration < 2 || duration > 60) {
    throw new TypeError("Soundbite must be 2 to 60 seconds with a valid start time");
  }
  const width = request.width ?? 1080, height = request.height ?? 1920;
  if (![width, height].every(Number.isInteger) || width < 144 || height < 144 || width > 2160 || height > 2160 || width % 2 || height % 2) {
    throw new TypeError("Output dimensions must be even integers between 144 and 2160");
  }
  const i = request.loudnessI ?? -16, tp = request.loudnessTP ?? -1.5, lra = request.loudnessLRA ?? 11;
  if (!Number.isFinite(i) || i < -30 || i > -5 || !Number.isFinite(tp) || tp < -9 || tp > 0 || !Number.isFinite(lra) || lra < 1 || lra > 20) {
    throw new TypeError("Invalid loudness normalization settings");
  }
  return [
    "-hide_banner", "-nostdin", "-y", "-ss", start.toFixed(3), "-i", input,
    "-ss", start.toFixed(3), "-i", audio, "-t", duration.toFixed(3),
    "-filter_complex",
    `[0:v]scale=${width}:${height}:force_original_aspect_ratio=increase,crop=${width}:${height},setsar=1,format=yuv420p[v];[1:a]atrim=duration=${duration.toFixed(3)},asetpts=PTS-STARTPTS,loudnorm=I=${i}:TP=${tp}:LRA=${lra},afade=t=in:st=0:d=0.08,afade=t=out:st=${Math.max(0,duration-0.12).toFixed(3)}:d=0.12[a]`,
    "-map", "[v]", "-map", "[a]", "-shortest",
    "-c:v", "libx264", "-preset", "veryfast", "-crf", "19",
    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-f", "mp4", output,
  ];
}
