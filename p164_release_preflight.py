#!/usr/bin/env python3
"""VidGravity P164 release preflight. Reports blockers without printing secret values.

Exit codes: 0 all configured gates present; 2 one or more release blockers remain.
This preflight is intentionally conservative: presence of configuration is not proof
of live deployment, real payment, or device acceptance.
"""
import json, os, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
checks = []
def add(key, label, passed, evidence, blocker):
    checks.append({"key": key, "label": label, "status": "PASS" if passed else "BLOCKED", "evidence": evidence, "release_blocker": blocker if not passed else None})

base_url = os.getenv("VIDGRAVITY_PUBLIC_BASE_URL", "").strip()
add("public_https", "Public HTTPS backend", base_url.startswith("https://"), "HTTPS URL configured" if base_url.startswith("https://") else "VIDGRAVITY_PUBLIC_BASE_URL must be configured with an https:// URL", "Deploy backend behind HTTPS and set VIDGRAVITY_PUBLIC_BASE_URL")
keys = os.getenv("VIDGRAVITY_LICENSE_KEYS", "").strip()
add("license_keys", "License issuance configuration", bool(keys), "License key configuration present (value withheld)" if keys else "No license keys configured", "Configure secure license issuance and rotate production keys")
razorpay = bool(os.getenv("RAZORPAY_KEY_ID", "").strip() and os.getenv("RAZORPAY_KEY_SECRET", "").strip() and os.getenv("RAZORPAY_WEBHOOK_SECRET", "").strip())
add("payment_secrets", "Razorpay API and webhook secrets", razorpay, "Required payment secret variables present (values withheld)" if razorpay else "One or more required Razorpay secret variables are missing", "Configure secrets in deployment secret manager and run test/live payment verification")
ytdlp = shutil.which("yt-dlp")
add("provider_cli", "Real provider CLI", bool(ytdlp), f"yt-dlp found at {ytdlp}" if ytdlp else "yt-dlp executable not installed", "Install/pin yt-dlp and run provider_live_acceptance.py with an authorized test URL")
live_url = os.getenv("VIDGRAVITY_LIVE_TEST_URL", "").strip()
add("live_provider_url", "Authorized live-provider acceptance URL", bool(live_url.startswith(("https://", "http://"))), "A test URL was supplied (value withheld)" if live_url.startswith(("https://", "http://")) else "No authorized live test URL configured", "Supply an authorized, non-DRM test URL and run the live provider acceptance check")
android_gradle = (ROOT / "android" / "gradlew").exists() or (ROOT / "android" / "build.gradle").exists() or (ROOT / "android" / "settings.gradle").exists()
add("android_native", "Native Android build project", android_gradle, "Gradle Android project detected" if android_gradle else "Only Android web companion scaffold is present; no native Gradle project detected", "Create/build the native Android wrapper and verify APK on emulator and physical device")
egress = os.getenv("VIDGRAVITY_PROVIDER_EGRESS_ISOLATED", "").strip().lower() == "true"
add("provider_egress", "Provider subprocess egress isolation", egress, "Explicit egress isolation assertion supplied" if egress else "No verified provider-process egress isolation assertion configured", "Run provider downloads in a network-isolated sandbox that blocks private, loopback, link-local and metadata destinations")
source_test = (ROOT / "test_p163_release_acceptance.py").is_file()
add("acceptance_tests", "P163 acceptance suite included", source_test, "P163 acceptance tests found" if source_test else "P163 acceptance tests missing", "Restore release acceptance tests")
report = {
    "milestone": "P164",
    "name": "Release Blocker Closure Preflight",
    "result": "READY_FOR_EXTERNAL_VALIDATION" if all(x["status"] == "PASS" for x in checks) else "BLOCKED",
    "checks_passed": sum(x["status"] == "PASS" for x in checks),
    "checks_blocked": sum(x["status"] == "BLOCKED" for x in checks),
    "checks": checks,
    "note": "Configuration presence is not proof of production readiness. Run live provider, Razorpay webhook/payment, Android device, deployment and security tests before public release."
}
out = ROOT / "P164_RELEASE_PREFLIGHT_REPORT.json"
out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"result": report["result"], "checks_passed": report["checks_passed"], "checks_blocked": report["checks_blocked"], "report": out.name}, indent=2))
for item in checks:
    print(f"[{item['status']}] {item['label']}: {item['evidence']}")
sys.exit(0 if report["result"] == "READY_FOR_EXTERNAL_VALIDATION" else 2)
