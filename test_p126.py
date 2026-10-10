from pathlib import Path
app=Path("app.js").read_text(); html=Path("index.html").read_text(); css=Path("styles.css").read_text()
checks=[("answer engine","mediaCopilotAnswer" in app),("copilot renderer","renderMediaCopilot" in app),("mount",'id="mediaCopilot"' in html),("natural input","copilotInput" in app),("suggestions","copilot-suggestions" in app),("grounding","local workspace" in app),("metrics","How many media" in app),("results","copilot-results" in app),("navigation","copilot-open" in app),("activity integration","recordAIActivity" in app),("responsive","@media(max-width:600px)" in css),("private indicator","Private by default" in app)]
failed=[n for n,ok in checks if not ok]
print(f"{len(checks)-len(failed)}/{len(checks)} PASS")
raise SystemExit(1 if failed else 0)
