import { ChevronRight, Download, Moon, Settings2, Sun } from "lucide-react";
import { useThemeContext } from "../theme/ThemeContext";

export interface DesktopHeaderProps {
  currentSection: string;
  onOpenDownloads?: () => void;
  onOpenSettings?: () => void;
}

export function DesktopHeader({ currentSection, onOpenDownloads, onOpenSettings }: DesktopHeaderProps) {
  const theme = useThemeContext();
  const isDark = theme.resolvedTheme === "dark";
  const ThemeIcon = isDark ? Sun : Moon;
  return <header className="vg-desktop-header sticky top-0 z-30">
    <nav className="vg-breadcrumbs" aria-label="Breadcrumb">
      <span>Workspace</span>
      <ChevronRight className="h-3.5 w-3.5 opacity-60" aria-hidden="true" />
      <strong>{currentSection}</strong>
    </nav>
    <div className="vg-header-actions">
      {onOpenDownloads && <button type="button" onClick={onOpenDownloads} className="vg-compact-button vg-ghost-button" aria-label="Open downloads" title="Downloads"><Download className="h-3.5 w-3.5" aria-hidden="true" /><span className="hidden sm:inline">Downloads</span></button>}
      {onOpenSettings && <button type="button" onClick={onOpenSettings} className="vg-compact-button vg-ghost-button" aria-label="Open settings" title="Settings"><Settings2 className="h-3.5 w-3.5" aria-hidden="true" /><span className="hidden sm:inline">Settings</span></button>}
      <button type="button" onClick={() => theme.setPreference(isDark ? "light" : "dark")} className="vg-compact-button vg-ghost-button" aria-label={isDark ? "Switch to light theme" : "Switch to dark theme"} title={isDark ? "Light theme" : "Dark theme"}><ThemeIcon className="h-4 w-4" aria-hidden="true" /></button>
    </div>
  </header>;
}
