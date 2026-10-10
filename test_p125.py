from pathlib import Path
app=Path("app.js").read_text(); html=Path("index.html").read_text(); css=Path("styles.css").read_text()
checks=[("command function","mediaCommandCenter" in app),("command renderer","renderMediaCommandCenter" in app),("mount",'id="mediaCommandCenter"' in html),("ask action","Ask My Media" in app),("search action","Knowledge Search" in app),("library action","Media Library" in app),("collections action","Smart Collections" in app),("metrics","cmd-metrics" in app),("health","Transcript coverage" in app),("responsive","@media(max-width:560px)" in css),("focus navigation","cmd-focus" in css)]
failed=[n for n,ok in checks if not ok]
print(f"{len(checks)-len(failed)}/{len(checks)} PASS")
raise SystemExit(1 if failed else 0)
