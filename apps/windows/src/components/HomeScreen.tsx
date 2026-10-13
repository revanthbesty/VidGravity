import { useState } from "react";
import copy from "../locales/en.json";
import { ShareModal } from "./ShareModal";
import { InputHero } from "./InputHero";
import { DesktopHeader } from "./DesktopHeader";

export interface HomeScreenProps {
  onAnalyze: (url: string) => Promise<void>;
  onOpenDownloads: () => void;
  onOpenSettings?: () => void;
  isAnalyzing: boolean;
  statusMessage?: string;
  errorMessage?: string;
  downloadCount: number;
  shareUrl?: string;
}

export function HomeScreen({
  onAnalyze,
  onOpenDownloads,
  onOpenSettings,
  isAnalyzing,
  statusMessage,
  errorMessage,
  downloadCount,
  shareUrl
}: HomeScreenProps) {
  const [shareOpen, setShareOpen] = useState(false);
  return <div className="min-h-screen bg-canvas text-ink">
    <DesktopHeader currentSection="Home" onOpenDownloads={onOpenDownloads} onOpenSettings={onOpenSettings} />
    <main className="mx-auto grid w-full max-w-6xl gap-vg-6 px-4 py-6 sm:px-vg-6 sm:py-vg-8">
      <header className="grid gap-vg-2">
        <p className="text-xs font-semibold uppercase tracking-[.16em] text-violet-600 dark:text-violet-400">{copy["app.name"]}</p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink sm:text-3xl">{copy["home.title"]}</h1>
        <p className="max-w-2xl text-base text-muted">{copy["home.subtitle"]}</p>
      </header>
      <InputHero
        onInspect={onAnalyze}
        isInspecting={isAnalyzing}
        status={statusMessage}
        error={errorMessage}
      />
      <section className="vg-panel flex flex-wrap items-center justify-between gap-vg-4 p-vg-6">
        <div className="grid gap-vg-1">
          <h2 className="text-sm font-semibold text-ink">{copy["home.downloads"]}</h2>
          <p className="text-sm text-muted">{downloadCount === 0 ? copy["home.empty.body"] : downloadCount + " saved item" + (downloadCount === 1 ? "" : "s") + " in your library."}</p>
        </div>
        <div className="flex flex-wrap items-center gap-vg-2">
          <button type="button" onClick={onOpenDownloads} className="vg-compact-button">Open Downloads</button>
          <button type="button" onClick={() => setShareOpen(true)} disabled={!shareUrl} aria-label="Share VidGravity" className="vg-compact-button vg-ghost-button">Share</button>
        </div>
      </section>
      {shareOpen && <ShareModal media={{ title: "VidGravity", text: "Download, organize, and play media in one place.", url: shareUrl }} onClose={() => setShareOpen(false)} />}
    </main>
  </div>;
}
