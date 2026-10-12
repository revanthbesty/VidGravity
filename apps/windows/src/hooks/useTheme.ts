import { useEffect, useState } from "react";
export type ThemePreference = "system" | "light" | "dark";
const STORAGE_KEY = "vidgravity-theme";
function readPreference(): ThemePreference {
  try { const value = localStorage.getItem(STORAGE_KEY); if (value === "light" || value === "dark" || value === "system") return value; } catch {}
  return "system";
}
export function useTheme() {
  const [preference, setPreference] = useState<ThemePreference>(readPreference);
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = preference;
    try { localStorage.setItem(STORAGE_KEY, preference); } catch {}
    root.style.colorScheme = preference === "system" ? "light dark" : preference;
  }, [preference]);
  return { preference, setPreference };
}
