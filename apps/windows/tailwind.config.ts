import type { Config } from "tailwindcss";
export default {
  content: ["./src/**/*.{ts,tsx,html}"],
  darkMode: ["class", '[data-theme="dark"]'],
  theme: { extend: {
    spacing: { "vg-1":"4px", "vg-2":"8px", "vg-3":"12px", "vg-4":"16px", "vg-6":"24px", "vg-8":"32px" },
    borderRadius: { "vg-sm":"6px", "vg-md":"10px", "vg-lg":"14px" },
    colors: {
      canvas:"var(--vg-bg)", surface:"var(--vg-surface)", elevated:"var(--vg-surface-raised)",
      overlay:"var(--vg-surface-overlay)", ink:"var(--vg-text)", muted:"var(--vg-muted)", line:"var(--vg-border)",
      accent:"var(--vg-accent)", onAccent:"var(--vg-on-accent)", danger:"var(--vg-danger)",
      success:"var(--vg-success)", warning:"var(--vg-warning)", info:"var(--vg-info)",
      violet:{50:"#f5f3ff",100:"#ede9fe",200:"#ddd6fe",300:"#c4b5fd",400:"#a78bfa",500:"#8b5cf6",600:"#7c3aed",700:"#6d28d9",800:"#5b21b6",900:"#4c1d95"}
    },
    fontFamily:{sans:["Inter","ui-sans-serif","system-ui","sans-serif"]},
    boxShadow:{focus:"var(--vg-focus-ring)",premium:"var(--vg-shadow-lg)",card:"var(--vg-shadow-sm)"}
  }},
  plugins:[]
} satisfies Config;
