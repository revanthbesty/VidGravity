import type { Metadata, Viewport } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: "VidGravity · AI Video Studio",
  description: "AI-assisted object removal, face swap, and motion transfer for video creators.",
  applicationName: "VidGravity",
};
export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#0f172a" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
