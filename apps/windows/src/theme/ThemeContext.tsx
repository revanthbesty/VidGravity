import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
export type ThemePreference = "light" | "dark" | "system";
type ThemeContextValue = { preference: ThemePreference; setPreference: (value: ThemePreference) => void; resolvedTheme: "light" | "dark" };
const ThemeContext = createContext<ThemeContextValue | null>(null);
const KEY = "vidgravity-theme";
function initialPreference(): ThemePreference {
  try { const value = localStorage.getItem(KEY); if (value === "light" || value === "dark" || value === "system") return value; } catch {}
  return "system";
}
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreference] = useState<ThemePreference>(initialPreference);
  const [systemDark, setSystemDark] = useState(() => typeof window !== "undefined" && typeof window.matchMedia === "function" && window.matchMedia("(prefers-color-scheme: dark)").matches);
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const update = () => setSystemDark(query.matches);
    if (query.addEventListener) {
      query.addEventListener("change", update);
      return () => query.removeEventListener("change", update);
    }
    query.addListener?.(update);
    return () => query.removeListener?.(update);
  }, []);
  useEffect(() => {
    const syncOtherWindow = (event: StorageEvent) => {
      if (event.key !== KEY) return;
      if (event.newValue === "light" || event.newValue === "dark" || event.newValue === "system") setPreference(event.newValue);\n      else if (event.newValue === null) setPreference("system");
    };
    window.addEventListener("storage", syncOtherWindow);
    return () => window.removeEventListener("storage", syncOtherWindow);
  }, []);
  const resolvedTheme = preference === "system" ? (systemDark ? "dark" : "light") : preference;
  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme;
    document.documentElement.style.colorScheme = resolvedTheme;
    try { localStorage.setItem(KEY, preference); } catch {}
    window.dispatchEvent(new CustomEvent("vidgravity:themechange", { detail: { preference, resolvedTheme } }));
  }, [preference, resolvedTheme]);
  const value = useMemo(() => ({ preference, setPreference, resolvedTheme }), [preference, resolvedTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
export function useThemeContext() {
  const value = useContext(ThemeContext);
  if (!value) throw new Error("useThemeContext must be used inside ThemeProvider.");
  return value;
}
