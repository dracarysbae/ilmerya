"""Install the simulator build on an available iPhone, launch it twice and keep screenshots.

A successful launch only shows the app starts and stays alive; it is not a gameplay test.
"""
import json, subprocess, sys, time, pathlib

derived, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
app = next(derived.glob("Build/Products/Debug-iphonesimulator/*.app"))
bundle = subprocess.check_output(["/usr/libexec/PlistBuddy", "-c", "Print CFBundleIdentifier", str(app / "Info.plist")], text=True).strip()
devices = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "-j"]))["devices"]
phones = [(runtime, d) for runtime, items in devices.items() if "iOS" in runtime for d in items if d["name"].startswith("iPhone")]
runtime, device = sorted(phones, key=lambda item: (item[0], item[1]["name"]))[-1]
udid = device["udid"]
subprocess.run(["xcrun", "simctl", "boot", udid], check=False)
subprocess.check_call(["xcrun", "simctl", "bootstatus", udid, "-b"])
subprocess.check_call(["xcrun", "simctl", "install", udid, str(app)])
result = {"device": device["name"], "runtime": runtime, "udid": udid, "bundle": bundle, "launches": []}
for attempt in range(2):
    pid = subprocess.check_output(["xcrun", "simctl", "launch", udid, bundle], text=True).strip().split(":")[-1].strip()
    time.sleep(25)
    shot = out / f"launch-{attempt + 1}.png"
    subprocess.check_call(["xcrun", "simctl", "io", udid, "screenshot", str(shot)])
    # The simulator has no kill(1); launchd lists running UIKit apps by bundle identifier.
    services = subprocess.run(["xcrun", "simctl", "spawn", udid, "launchctl", "list"], capture_output=True, text=True).stdout
    alive = any(bundle in line and line.split()[0].isdigit() for line in services.splitlines())
    result["launches"].append({"pid": pid, "alive_after_25s": alive, "screenshot": shot.name})
    subprocess.run(["xcrun", "simctl", "terminate", udid, bundle], check=False)
    time.sleep(2)
crashes = sorted(str(p) for p in pathlib.Path.home().glob("Library/Logs/DiagnosticReports/Ilmerya*"))
result["crash_reports"] = crashes
(out / "result.json").write_text(json.dumps(result, indent=2))
print(json.dumps(result, indent=2))
if crashes or not all(l["alive_after_25s"] for l in result["launches"]):
    sys.exit("The app crashed or exited after launch")
