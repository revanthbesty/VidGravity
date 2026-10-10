from pathlib import Path
app=Path("app.js").read_text(); html=Path("index.html").read_text(); css=Path("styles.css").read_text()
checks=[("storage","vg_ai_activity" in app),("recorder","recordAIActivity" in app),("renderer","renderAIActivityCenter" in app),("center","aiActivityCenter" in app),("mount",'id="aiActivityCenter"' in html),("recommendations","Recommended next" in app),("local-only","Local-only intelligence" in app),("responsive","@media(max-width:850px)" in css),("clear","clearAIActivity" in app),("activity-icon","activityIcon" in app)]
failed=[n for n,ok in checks if not ok]
print(f"{len(checks)-len(failed)}/{len(checks)} PASS")
raise SystemExit(1 if failed else 0)
