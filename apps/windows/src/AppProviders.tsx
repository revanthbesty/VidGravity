import type { ReactNode } from "react";
import { ThemeProvider } from "./theme/ThemeContext";
/** Mount once above all routed screens, dialogs, and player chrome. */
export function AppProviders({ children }: { children: ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}
