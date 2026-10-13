/**
 * Audio Alchemist: allow-listed FFmpeg audio filter graphs.
 * Callers pass argv to spawn/execFile; never concatenate these values into a shell command.
 */
export type AudioPresetId = "slowed-reverb" | "nightcore" | "bass-warmth";

export interface AudioPreset {
  id: AudioPresetId;
  label: string;
  description: string;
  filterGraph: string;
  outputBitrate: "320k";
}

const PRESETS: Readonly<Record<AudioPresetId, AudioPreset>> = Object.freeze({
  "slowed-reverb": {
    id: "slowed-reverb",
    label: "Slowed + Reverb",
    description: "Warm, slowed playback with a restrained echo tail.",
    filterGraph: "[0:a]asetrate=44100*0.85,aresample=44100,aecho=0.8:0.88:60:0.4,lowpass=f=3500[outa]",
    outputBitrate: "320k",
  },
  nightcore: {
    id: "nightcore",
    label: "Sped Up / Nightcore",
    description: "Bright, faster playback with pitch raised by resampling.",
    filterGraph: "[0:a]asetrate=44100*1.25,aresample=44100[outa]",
    outputBitrate: "320k",
  },
  "bass-warmth": {
    id: "bass-warmth",
    label: "Bass Boost + Warmth",
    description: "A moderate low-frequency lift.",
    filterGraph: "[0:a]bass=g=8:f=110:w=0.6[outa]",
    outputBitrate: "320k",
  },
});

const SAFE_ID = /^[a-z0-9][a-z0-9._-]{0,119}$/i;

/** Returns immutable, allow-listed preset metadata; arbitrary filter text is never accepted. */
export function getAudioPreset(id: AudioPresetId): AudioPreset {
  const preset = PRESETS[id];
  if (!preset) throw new TypeError("Unsupported audio preset");
  return preset;
}

/**
 * Build argv for ffmpeg/ffmpeg-static using spawn(executable, args, {shell:false}).
 * Input/output are separate arguments, not embedded in the filter graph.
 */
export function buildAudioExportArgs(inputPath: string, outputPath: string, id: AudioPresetId): string[] {
  if (!inputPath || !outputPath || inputPath.includes("\0") || outputPath.includes("\0")) {
    throw new TypeError("A valid input and output path are required");
  }
  const preset = getAudioPreset(id);
  const outputName = outputPath.split(/[\\/]/).pop() ?? "";
  if (!SAFE_ID.test(outputName.replace(/\.mp3$/i, "").replace(/\s+/g, "-"))) {
    throw new TypeError("Output filename contains unsupported characters");
  }
  return [
    "-hide_banner", "-nostdin", "-y",
    "-i", inputPath,
    "-map", "0:a:0",
    "-vn", "-sn", "-dn",
    "-filter_complex", preset.filterGraph,
    "-map", "[outa]",
    "-c:a", "libmp3lame",
    "-b:a", preset.outputBitrate,
    "-f", "mp3",
    outputPath,
  ];
}

export function listAudioPresets(): AudioPreset[] {
  return Object.values(PRESETS);
}
