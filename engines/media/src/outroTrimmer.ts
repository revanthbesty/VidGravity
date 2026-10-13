/**
 * Conservative media packaging helpers.
 *
 * Outro detection is intentionally based on injected frame metrics so the app can use
 * its chosen decoder/frame sampler without coupling this module to a native FFmpeg binding.
 * Unknown or ambiguous samples must return no trim decision.
 */
export interface FrameMetric {
  timestampSeconds: number;
  meanLuma: number;       // normalized 0..1
  lumaVariance: number;   // normalized 0..0.25
  edgeDensity: number;    // normalized 0..1
  perceptualHash: string;
}

export interface OutroDecision {
  shouldTrim: boolean;
  originalDurationSeconds: number;
  trimEndSeconds: number;
  reason: "static-dark-card" | "static-light-card" | "no-confident-outro";
}

const clamp = (n: number, min: number, max: number) => Math.min(max, Math.max(min, n));

function hammingDistance(a: string, b: string): number | null {
  if (!a || a.length !== b.length || !/^[0-9a-f]+$/i.test(a) || !/^[0-9a-f]+$/i.test(b)) return null;
  let count = 0;
  for (let i = 0; i < a.length; i++) {
    let x = parseInt(a[i], 16) ^ parseInt(b[i], 16);
    while (x) { count += x & 1; x >>>= 1; }
  }
  return count;
}

function isUniformCard(frame: FrameMetric, kind: "dark" | "light"): boolean {
  const lumaOk = kind === "dark" ? frame.meanLuma <= 0.12 : frame.meanLuma >= 0.88;
  return lumaOk && frame.lumaVariance <= 0.018 && frame.edgeDensity <= 0.12;
}

/** Call with samples from the last <=2 seconds; never trims based on one frame alone. */
export function detectPlatformOutro(
  durationSeconds: number,
  samples: readonly FrameMetric[],
): OutroDecision {
  const safeDuration = Number.isFinite(durationSeconds) && durationSeconds > 0 ? durationSeconds : 0;
  const no = (reason: OutroDecision["reason"] = "no-confident-outro"): OutroDecision => ({
    shouldTrim: false, originalDurationSeconds: safeDuration, trimEndSeconds: safeDuration, reason,
  });
  if (safeDuration < 3 || samples.length < 3) return no();
  const ordered = [...samples].filter(s =>
    Number.isFinite(s.timestampSeconds) && Number.isFinite(s.meanLuma) &&
    Number.isFinite(s.lumaVariance) && Number.isFinite(s.edgeDensity) &&
    s.timestampSeconds >= Math.max(0, safeDuration - 2.05) && s.timestampSeconds <= safeDuration
  ).sort((a, b) => a.timestampSeconds - b.timestampSeconds);
  if (ordered.length < 3) return no();

  for (const kind of ["dark", "light"] as const) {
    const tail = ordered.filter(frame => isUniformCard(frame, kind));
    if (tail.length < 3) continue;
    const first = tail[0];
    const last = tail[tail.length - 1];
    const span = last.timestampSeconds - first.timestampSeconds;
    if (span < 0.9 || safeDuration - last.timestampSeconds > 0.35) continue;
    const hashes = tail.map(frame => hammingDistance(first.perceptualHash, frame.perceptualHash));
    if (hashes.some(distance => distance == null || distance > 5)) continue;
    // Leave a small safety margin before the first stable outro frame.
    const trimEnd = clamp(first.timestampSeconds - 0.08, 0.5, safeDuration);
    if (trimEnd >= safeDuration - 0.2) continue;
    return {
      shouldTrim: true,
      originalDurationSeconds: safeDuration,
      trimEndSeconds: Number(trimEnd.toFixed(3)),
      reason: kind === "dark" ? "static-dark-card" : "static-light-card",
    };
  }
  return no();
}

/** Use spawn/execFile with this argv array; do not use a shell string. */
export function buildLosslessOutroTrimArgs(inputPath: string, outputPath: string, decision: OutroDecision): string[] {
  if (!inputPath || !outputPath || inputPath.includes("\0") || outputPath.includes("\0")) {
    throw new TypeError("Valid input and output paths are required");
  }
  if (!decision.shouldTrim || !Number.isFinite(decision.trimEndSeconds) ||
      decision.trimEndSeconds <= 0 || decision.trimEndSeconds >= decision.originalDurationSeconds) {
    throw new TypeError("A confident, valid outro trim decision is required");
  }
  return [
    "-hide_banner", "-nostdin", "-y",
    "-i", inputPath,
    "-to", decision.trimEndSeconds.toFixed(3),
    "-map", "0", "-c", "copy",
    "-avoid_negative_ts", "make_zero",
    outputPath,
  ];
}

/**
 * Clean Crop is a known, fixed filter, not user-provided filter text.
 * Cropping requires video re-encoding; audio may be copied. Source files stay immutable.
 */
export function buildCleanCropArgs(inputPath: string, outputPath: string): string[] {
  if (!inputPath || !outputPath || inputPath.includes("\0") || outputPath.includes("\0")) {
    throw new TypeError("Valid input and output paths are required");
  }
  return [
    "-hide_banner", "-nostdin", "-y",
    "-i", inputPath,
    "-vf", "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920",
    "-c:v", "libx264", "-preset", "medium", "-crf", "18",
    "-c:a", "copy", "-movflags", "+faststart",
    outputPath,
  ];
}
