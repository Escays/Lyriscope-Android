#!/usr/bin/env python3
"""Verify the DEX reader in spotify-module/tools/mapping_probe.py against a built module APK.

The probe is what locates Spotify's obfuscated names on the next host release, so a
silently broken parser would cost a whole adaptation round. Its own selftest asserts
class parsing, method signatures, shape matching and code_item string scanning on an
APK this repository produced. Needs an assembled module APK; skipped otherwise.
"""
import subprocess
import sys

from regression_runtime import ROOT, REPORTS

WORK = REPORTS / "mapping-probe"
WORK.mkdir(parents=True, exist_ok=True)
PROBE = ROOT / "spotify-module/tools/mapping_probe.py"


def apk():
    for directory in ("spotify-module/build/outputs/apk/release",
                      "spotify-module/build/outputs/apk/debug"):
        candidates = sorted((ROOT / directory).glob("*.apk"),
                            key=lambda path: path.stat().st_mtime, reverse=True)
        if candidates:
            return candidates[0]
    return None


target = apk()
if target is None:
    text = "SKIP: no assembled module APK; run ./build.sh first.\n"
    (WORK / "result.txt").write_text(text)
    print(text, end="")
    raise SystemExit(0)

result = subprocess.run([sys.executable, str(PROBE), "--apk", str(target), "selftest"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = "DEX probe self-check against an assembled module APK.\n"
report += "%s\n" % target.relative_to(ROOT)
report += result.stdout
(WORK / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
