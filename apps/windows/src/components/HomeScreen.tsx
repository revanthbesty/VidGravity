import { useState } from "react";
import type { FormEvent } from "react";
import copy from "../locales/en.json";

export interface HomeScreenProps {
  onAnalyze: (url: string) => Promise<void>;
  onOpenDownloads: () => void;
  isAnalyzing: boolean;
  statusMessage?: string;
  errorMessage?: string;
  downloadCount: number;
}
export function HomeScreen({ onAnalyze, onOpenDownloads, isAnalyzing, statusMessage, errorMessage, downloadCount }: HomeScreenProps) {
  const [url, setUrl] = useState("");
  const [localError, setLocalError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = url.trim();
    if (!value) { setLocalError("Enter a media URL to continue."); return; }
    let parsed: URL;
    try { parsed = new URL(value); } catch { setLocalError("Enter a valid URL, including https://."); return; }
    if (!["http:", "https:"].includes(parsed.protocol)) { setLocalError("Only HTTP and HTTPS URLs are supported."); return; }
    setLocalError("");
    await onAnalyze(value);
  }
  return <main className="mx-auto grid w-full max-w-6xl gap-vg-8 px-vg-6 py-vg-8">
    <header className="grid gap-vg-2">
      <p className="text-sm font-semibold uppercase tracking-wide text-accent">{copy["app.name"]}</p>
      <h1 className="text-3xl font-semibold tracking-tight">{copy["home.title"]}</h1>
      <p className="max-w-2xl text-base text-muted">{copy["home.subtitle"]}</p>
    </header>
    <section className="grid gap-vg-4 rounded-vg-lg border border-line bg-surface p-vg-6 shadow-sm" aria-labelledby="analyze-title">
      <div><h2 id="analyze-title" className="text-xl font-semibold">Start With a Media Link</h2><p className="mt-vg-1 text-sm text-muted">Use a URL supported by the configured media provider.</p></div>
      <form onSubmit={submit} className="grid gap-vg-3 sm:grid-cols-[minmax(0,1fr)_auto]">
        <label className="grid gap-vg-2 text-sm font-medium" htmlFor="media-url">{copy["home.url.label"]}
          <input id="media-url" type="url" inputMode="url" autoComplete="url" placeholder={copy["home.url.placeholder"]} value={url} onChange={e=>setUrl(e.target.value)} aria-describedby="url-help" className="min-h-11 w-full rounded-vg-sm border border-line bg-canvas px-vg-4 text-base text-ink placeholder:text-muted" />
        </label>
        <button type="submit" disabled={isAnalyzing || !url.trim()} className="self-end bg-accent font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60">{isAnalyzing ? "Analyzing…" : copy["home.analyze"]}</button>
      </form>
      <p id="url-help" className="text-sm text-muted">Only supported sources can be analyzed. Provider availability may vary.</p>
      {(localError || errorMessage) && <p role="alert" className="text-sm font-medium text-danger">{localError || errorMessage}</p>}
      {statusMessage && <p role="status" aria-live="polite" className="text-sm text-muted">{statusMessage}</p>}
    </section>
    <section className="flex flex-wrap items-center justify-between gap-vg-4 rounded-vg-lg border border-line bg-surface p-vg-6">
      <div className="grid gap-vg-1"><h2 className="text-lg font-semibold">{copy["home.downloads"]}</h2><p className="text-sm text-muted">{downloadCount === 0 ? copy["home.empty.body"] : downloadCount + " saved item" + (downloadCount === 1 ? "" : "s") + " in your library."}</p></div>
      <button type="button" onClick={onOpenDownloads}>{copy["home.action.openDownloads"]}</button>
    </section>
  </main>;
}
