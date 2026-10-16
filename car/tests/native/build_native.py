r"""Build the separate car.7 instrumentation APK against the release R8 map.

Usage (from repo root, with JAVA_HOME and Android SDK configured):
  python -B car/tests/native/build_native.py --out C:\path\to\scratch\car7-native
The installed target must use the same developer test certificate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--mapping", type=Path)
    p.add_argument("--build-tools", default="34.0.0")
    a = p.parse_args()
    source = Path(__file__).resolve().parent
    repo = source.parents[2]
    a.out.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
               or str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    java = Path(os.environ["JAVA_HOME"])
    tools = sdk / "build-tools" / a.build_tools
    android = sdk / "platforms/android-33/android.jar"
    mapping_path = a.mapping or repo / "app/build/outputs/mapping/pythonRelease/mapping.txt"
    mapping = {"classes": {}, "members": {}, "constants": {}}
    current = None
    for line in mapping_path.read_text(encoding="utf-8").splitlines():
        cls = re.match(r"^([^ #].*?) -> (.*):$", line)
        if cls:
            original, renamed = cls.groups()
            mapping["classes"][original] = renamed
            current = {"original": original, "fields": {}, "methods": {}}
            mapping["members"][renamed] = current
        elif current is not None:
            member = re.match(r"^\s+(?:\d+:\d+:)?\S+ ([^ (]+)\([^)]*\)(?::[\d:]+)? -> (\S+)$", line)
            if member:
                name, alias = member.groups()
                if "." not in name:
                    aliases = current["methods"].setdefault(name, [])
                    if alias not in aliases:
                        aliases.append(alias)
            else:
                member = re.match(r"^\s+\S+ ([^ ]+) -> (\S+)$", line)
                if member:
                    current["fields"][member[1]] = member[2]
    config = repo / "app/src/main/java/com/github/tvbox/osc/util/HawkConfig.java"
    mapping["constants"] = dict(re.findall(r'public static final String (\w+) = "([^"]*)";', config.read_text(encoding="utf-8")))
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    env = os.environ.copy()
    env["PATH"] = str(java / "bin") + os.pathsep + env.get("PATH", "")
    # Local Android debug certificate only; values are not printed in logs.
    env["CAR7_TEST_STORE_PASSWORD"] = "android"
    env["CAR7_TEST_KEY_PASSWORD"] = "android"

    def run(label, args):
        r = subprocess.run([str(x) for x in args], capture_output=True, env=env,
                           timeout=120, creationflags=flags)
        (a.out / (label + ".log")).write_bytes(r.stdout + r.stderr)
        print(label, r.returncode)
        if r.returncode:
            raise SystemExit(r.returncode)

    classes, dex = a.out / "classes", a.out / "dex"
    for directory in (classes, dex):
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir()
    run("javac", [java / "bin/javac.exe", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                  "-cp", android, "-d", classes, source / "Car7Acceptance.java"])
    run("jar", [java / "bin/jar.exe", "cf", a.out / "test.jar", "-C", classes, "."])
    run("dex", [tools / "d8.bat", "--min-api", "23", "--lib", android, "--output", dex, a.out / "test.jar"])
    run("aapt", [tools / "aapt2.exe", "link", "-I", android, "--manifest", source / "AndroidManifest.xml",
                 "-o", a.out / "unsigned.apk"])
    with zipfile.ZipFile(a.out / "unsigned.apk", "a", compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(dex / "classes.dex", "classes.dex")
        apk.writestr("assets/mapping.json", json.dumps(mapping, separators=(",", ":")))
        apk.write(source / "python_probe.py", "assets/python_probe.py")
    run("align", [tools / "zipalign.exe", "-f", "4", a.out / "unsigned.apk", a.out / "aligned.apk"])
    final = a.out / "car7-native-test.apk"
    run("sign", [tools / "apksigner.bat", "sign", "--ks", Path.home() / ".android/debug.keystore",
                 "--ks-key-alias", "androiddebugkey", "--ks-pass", "env:CAR7_TEST_STORE_PASSWORD",
                 "--key-pass", "env:CAR7_TEST_KEY_PASSWORD", "--out", final, a.out / "aligned.apk"])
    run("verify", [tools / "apksigner.bat", "verify", final])
    print(json.dumps({"bytes": final.stat().st_size, "sha256": hashlib.sha256(final.read_bytes()).hexdigest()}))


if __name__ == "__main__":
    main()
