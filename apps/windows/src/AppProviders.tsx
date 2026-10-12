import type { ReactNode } from "react";
import { ThemeProvider } from "./theme/ThemeContext";
/** Mount once above the router so cards, dialogs, popovers, and player chrome inherit one theme. */
export function AppProviders({ children }: { children: ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}
