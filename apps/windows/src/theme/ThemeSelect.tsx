import { useThemeContext } from "./ThemeContext";
export function ThemeSelect() {
  const { preference, setPreference } = useThemeContext();
  return <label className="vg-theme-select"><span>Appearance</span><select className="vg-theme-select-control" value={preference} onChange={e=>setPreference(e.target.value as "light"|"dark"|"system")} aria-label="Appearance">
    <option value="system">System</option><option value="light">Light</option><option value="dark">Dark</option>
  </select></label>;
}
