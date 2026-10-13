import { useState } from "react";

export type SceneMode = "transcript" | "visual";
export interface SceneHit { timestampSeconds: number; label: string; evidence: string; }
interface SceneFinderPanelProps {
  onSearch?: (query: string, mode: SceneMode) => Promise<SceneHit[]>;
  onJump: (seconds: number) => void;
}
const timeLabel = (seconds: number) => {
  const safe = Math.max(0, Math.floor(seconds));
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, "0")}`;
};
export function SceneFinderPanel({ onSearch, onJump }: SceneFinderPanelProps) {
  const [mode, setMode] = useState<SceneMode>("transcript");
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<SceneHit[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  async function search() {
    if (!onSearch) { setMessage("Scene search service is not connected in this build."); return; }
    if (query.trim().length < 3) { setMessage("Enter at least 3 characters."); return; }
    setBusy(true); setMessage("");
    try {
      const result = await onSearch(query.trim(), mode);
      setHits(result.filter(hit => Number.isFinite(hit.timestampSeconds) && hit.timestampSeconds >= 0).slice(0, 30));
      if (!result.length) setMessage("No matching moments found.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Search failed."); }
    finally { setBusy(false); }
  }
  return <section className="mt-4 rounded-xl border border-line bg-surface p-4" aria-label="Find a moment in media">
    <h3 className="font-semibold">Find a moment</h3>
    <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Search mode">
      <button type="button" aria-pressed={mode === "transcript"} onClick={() => setMode("transcript")}>Transcript Search</button>
      <button type="button" aria-pressed={mode === "visual"} onClick={() => setMode("visual")}>Scene / Visual Finder</button>
    </div>
    <div className="mt-3 flex gap-2">
      <input value={query} onChange={event => setQuery(event.target.value)} maxLength={240}
        placeholder={mode === "visual" ? "Find where the sports car passes…" : "Find where the speaker explains…"}
        className="min-w-0 flex-1 rounded-lg border border-line bg-background px-3 py-3" />
      <button type="button" onClick={() => void search()} disabled={busy} className="min-h-12 rounded-lg px-4">{busy ? "Searching…" : "Find"}</button>
    </div>
    {message && <p role="status" className="mt-3 text-sm text-muted">{message}</p>}
    <div className="mt-3 grid gap-2">
      {hits.map((hit, index) => <button key={`${hit.timestampSeconds}-${index}`} type="button"
        onClick={() => onJump(hit.timestampSeconds)} className="flex min-h-14 items-center gap-3 rounded-lg border border-line p-3 text-left">
        <span className="rounded-md bg-violet-500/15 px-2 py-1 font-mono text-sm">{timeLabel(hit.timestampSeconds)}</span>
        <span className="min-w-0 flex-1"><span className="block font-medium">{hit.label}</span><span className="block text-xs text-muted">{hit.evidence}</span></span>
        <span aria-hidden="true">↗</span>
      </button>)}
    </div>
  </section>;
}
