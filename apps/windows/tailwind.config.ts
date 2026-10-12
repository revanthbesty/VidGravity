import type { Config } from "tailwindcss";
export default {
  content: ["./src/**/*.{ts,tsx,html}"],
  darkMode: ["class", '[data-theme="dark"]'],
  theme: { extend: {
    spacing: { "vg-1":"4px", "vg-2":"8px", "vg-3":"12px", "vg-4":"16px", "vg-6":"24px", "vg-8":"32px" },
    borderRadius: { "vg-sm":"8px", "vg-md":"12px", "vg-lg":"18px" },
    colors: {
      canvas: "var(--vg-bg)", surface: "var(--vg-surface)", elevated: "var(--vg-surface-raised)",
      overlay: "var(--vg-surface-overlay)", ink: "var(--vg-text)", muted: "var(--vg-muted)",
      line: "var(--vg-border)", accent: "var(--vg-accent)", onAccent: "var(--vg-on-accent)", danger: "var(--vg-danger)"
    },
    fontFamily: { sans: ["Inter","ui-sans-serif","system-ui","sans-serif"] },
    boxShadow: { focus: "var(--vg-focus-ring)", premium: "var(--vg-shadow-lg)" }
  }},
  plugins: []
} satisfies Config;
