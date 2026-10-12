import type { Config } from "tailwindcss";
export default {
  content: ["./src/**/*.{ts,tsx,html}"],
  darkMode: ["class", '[data-theme="dark"]'],
  theme: { extend: {
    spacing: { "vg-1":"4px", "vg-2":"8px", "vg-3":"12px", "vg-4":"16px", "vg-6":"24px", "vg-8":"32px" },
    borderRadius: { "vg-sm":"8px", "vg-md":"12px", "vg-lg":"18px" },
    colors: {
      canvas: "var(--bg)", surface: "var(--surface)", elevated: "var(--surface-raised)",
      ink: "var(--text)", muted: "var(--muted)", line: "var(--border)", accent: "var(--accent)",
      danger: "var(--danger)"
    },
    fontFamily: { sans: ["Inter","ui-sans-serif","system-ui","sans-serif"] },
    boxShadow: { focus: "var(--focus-ring)" }
  }},
  plugins: []
} satisfies Config;
