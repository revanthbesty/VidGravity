/** Validated FFmpeg argument builder for derivative 9:16 story cards. Never invoke through a shell. */
export interface StoryCardRequest {
  inputPath: string;
  outputPath: string;
  title: string;
  artist?: string;
  startSeconds: number;
  durationSeconds: number;
  coverPath?: string;
  subtitlePath?: string;
  waveform?: boolean;
}
export interface StoryCardArgs { args: string[]; durationSeconds: number; }
function validPath(value: string, name: string): string {
  if (!value || value.includes("\0")) throw new TypeError(`Invalid ${name}`);
  return value;
}
function safeText(value: string, name: string): string {
  const text = value.trim().slice(0, 120);
  if (!text || /[\r\n\0]/.test(text)) throw new TypeError(`Invalid ${name}`);
  // FFmpeg drawtext escaping (not shell escaping).
  return text.replace(/\\/g, "\\\\").replace(/:/g, "\\:").replace(/'/g, "\\'").replace(/%/g, "\\%");
}
export function buildStoryCardArgs(request: StoryCardRequest): StoryCardArgs {
  const input = validPath(request.inputPath, "input path");
  const output = validPath(request.outputPath, "output path");
  if (input === output) throw new TypeError("Story derivative must not overwrite the source");
  if (!Number.isFinite(request.startSeconds) || request.startSeconds < 0) throw new TypeError("Invalid clip start");
  if (!Number.isFinite(request.durationSeconds) || request.durationSeconds < 15 || request.durationSeconds > 30) {
    throw new TypeError("Story clip must be between 15 and 30 seconds");
  }
  const title = safeText(request.title, "story title");
  const artist = request.artist?.trim() ? safeText(request.artist, "artist") : "";
  const args = ["-hide_banner", "-nostdin", "-y", "-ss", request.startSeconds.toFixed(3), "-i", input];
  const hasCover = !!request.coverPath;
  const hasSubs = !!request.subtitlePath;
  if (hasCover) args.push("-loop", "1", "-i", validPath(request.coverPath!, "cover path"));
  const filters: string[] = [
    "[0:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,boxblur=20:5[bg]",
  ];
  let base = "[bg]";
  if (hasCover) {
    filters.push("[1:v]scale=720:720:force_original_aspect_ratio=increase,crop=720:720[art]");
    filters.push("[bg][art]overlay=180:360[covermix]");
    base = "[covermix]";
  }
  if (request.waveform) {
    // showwaves consumes the input audio stream; it cannot be applied to the video background.
    filters.push("[0:a]showwaves=s=900x180:mode=line:colors=white@0.9,format=rgba,colorkey=black:0.12:0.0[wave]");
    filters.push(`${base}[wave]overlay=(W-w)/2:1450:shortest=1[wavebase]`);
    base = "[wavebase]";
  }
  const draw = `drawtext=text='${title}':x=(w-text_w)/2:y=1200:fontsize=48:fontcolor=white:box=1:boxcolor=black@0.42:boxborderw=16`;
  filters.push(`${base}${draw}[titlev]`);
  base = "[titlev]";
  if (artist) {
    filters.push(`${base}drawtext=text='${artist}':x=(w-text_w)/2:y=1270:fontsize=30:fontcolor=white[artistv]`);
    base = "[artistv]";
  }
  if (hasSubs) {
    const subPath = validPath(request.subtitlePath!, "subtitle path").replace(/\\/g, "/").replace(/:/g, "\\:");
    filters.push(`${base}subtitles='${subPath}':fontsdir=.` + "[subbed]");
    base = "[subbed]";
  }
  args.push("-filter_complex", filters.join(";"), "-map", base, "-map", "0:a:0?", "-t", request.durationSeconds.toFixed(3),
    "-c:v", "libx264", "-preset", "veryfast", "-crf", "19", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-f", "mp4", output);
  return { args, durationSeconds: request.durationSeconds };
}
