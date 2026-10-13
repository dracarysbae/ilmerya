"""Capture App Store screenshots of the debug demo scenes on the largest available iPhone simulator.

Usage: python3 iosApp/Scripts/store_screenshots.py <derived-data> <out-dir>
Writes <out-dir>/<lang>/<scene>.png for tr and en, plus manifest.json. The status bar is the
standard 9:41 / full battery override so captures are clean.
"""
import json, subprocess, sys, time, pathlib

derived, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
app = next(derived.glob("Build/Products/Debug-iphonesimulator/*.app"))
bundle = subprocess.check_output(["/usr/libexec/PlistBuddy", "-c", "Print CFBundleIdentifier", str(app / "Info.plist")], text=True).strip()
devices = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "-j"]))["devices"]
phones = [(runtime, d) for runtime, items in devices.items() if "iOS" in runtime for d in items if "Pro Max" in d["name"]]
runtime, device = sorted(phones, key=lambda item: (item[0], item[1]["name"]))[-1]
udid = device["udid"]
subprocess.run(["xcrun", "simctl", "boot", udid], check=False)
subprocess.check_call(["xcrun", "simctl", "bootstatus", udid, "-b"])
subprocess.run(["xcrun", "simctl", "status_bar", udid, "override", "--time", "9:41", "--dataNetwork", "wifi",
                "--wifiMode", "active", "--wifiBars", "3", "--cellularMode", "active", "--cellularBars", "4",
                "--batteryState", "charged", "--batteryLevel", "100"], check=False)
manifest = {"device": device["name"], "runtime": runtime, "shots": []}
for lang, locale in (("tr", "tr_TR"), ("en", "en_US")):
    # A fresh install per language: the app stores the language chosen on first launch.
    subprocess.run(["xcrun", "simctl", "uninstall", udid, bundle], check=False)
    subprocess.check_call(["xcrun", "simctl", "install", udid, str(app)])
    (out / lang).mkdir(exist_ok=True)
    for scene in ("menu", "board", "tutorial", "result", "settings"):
        subprocess.check_output(["xcrun", "simctl", "launch", "--terminate-running-process", udid, bundle,
                                 "-AppleLanguages", f"({lang})", "-AppleLocale", locale, "-ilmeryaScene", scene], text=True)
        time.sleep(9)
        shot = out / lang / f"{scene}.png"
        subprocess.check_call(["xcrun", "simctl", "io", udid, "screenshot", str(shot)])
        manifest["shots"].append(str(shot.relative_to(out)))
    subprocess.run(["xcrun", "simctl", "terminate", udid, bundle], check=False)
(out / "manifest.json").write_text(json.dumps(manifest, indent=2))
print(json.dumps(manifest, indent=2))
