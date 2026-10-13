import { useState } from "react";

export type AudioPresetId = "slowed-reverb" | "nightcore" | "bass-warmth";
export interface AudioPresetRequest { preset: AudioPresetId; bitrate: "320k"; outputFormat: "mp3"; }
interface AudioPresetPanelProps {
  onExport?: (request: AudioPresetRequest) => Promise<void>;
}
const presets: { id: AudioPresetId; title: string; detail: string }[] = [
  { id: "slowed-reverb", title: "Slowed + Reverb", detail: "Warm slowdown with echo" },
  { id: "nightcore", title: "Sped Up / Nightcore", detail: "Faster, brighter sound" },
  { id: "bass-warmth", title: "Bass Boost + Warmth", detail: "Add a low-frequency lift" },
];
export function AudioPresetPanel({ onExport }: AudioPresetPanelProps) {
  const [busy, setBusy] = useState<AudioPresetId | null>(null);
  const [message, setMessage] = useState("");
  async function exportPreset(id: AudioPresetId) {
    if (!onExport || busy) return;
    setBusy(id); setMessage("");
    try {
      await onExport({ preset: id, bitrate: "320k", outputFormat: "mp3" });
      setMessage("Audio export queued.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Audio export could not be started.");
    } finally { setBusy(null); }
  }
  return <section className="mt-4 rounded-xl border border-line bg-surface p-4" aria-label="Audio presets">
    <h3 className="text-base font-semibold">Audio Alchemist</h3>
    <p className="mt-1 text-sm text-muted">Export an MP3 at 320 kbps with a sound profile.</p>
    <div className="mt-3 grid gap-2">
      {presets.map(preset => <button key={preset.id} type="button"
        onClick={() => void exportPreset(preset.id)} disabled={!onExport || busy !== null}
        className="flex min-h-14 items-center justify-between rounded-lg border border-line px-3 py-3 text-left disabled:cursor-not-allowed disabled:opacity-50"
        title={!onExport ? "Connect the media export service to enable this preset" : undefined}>
        <span><span className="block font-medium">{preset.title}</span><span className="block text-xs text-muted">{preset.detail}</span></span>
        <span className="text-sm">{busy === preset.id ? "Exporting…" : "MP3 · 320k"}</span>
      </button>)}
    </div>
    {!onExport && <p className="mt-3 text-xs text-muted">Export service integration is not configured in this build.</p>}
    {message && <p role="status" className="mt-3 text-sm">{message}</p>}
  </section>;
}
