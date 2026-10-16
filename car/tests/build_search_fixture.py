"""Build only the local search Spider fixture (never Gradle or a production APK).

Outputs are required to be in the session scratch directory. No device operations.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:9978/file/Car7Search/")
    parser.add_argument("--classes", type=Path, help="Existing app javac classes, not an APK")
    parser.add_argument("--build-tools", default="34.0.0")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    scratch = Path(os.environ["PI_SCRATCH_DIR"]).resolve()
    out = args.out.resolve()
    if out == scratch or scratch not in out.parents:
        parser.error("--out must be a subdirectory of PI_SCRATCH_DIR")
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
               or str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    android = sdk / "platforms/android-33/android.jar"
    java = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
    javac = java / "javac.exe" if java else shutil.which("javac")
    classes = args.classes or repo / "app/build/intermediates/javac/pythonRelease/classes"
    if not (classes / "com/github/catvod/crawler/Spider.class").is_file():
        parser.error("--classes needs existing compiled app classes")
    out.mkdir(parents=True, exist_ok=True)
    compiled, dex = out / "classes", out / "dex"
    for folder in (compiled, dex):
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir()
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0

    def run(label, command):
        result = subprocess.run([str(value) for value in command], capture_output=True,
                                timeout=90, creationflags=flags)
        (out / (label + ".log")).write_bytes(result.stdout + result.stderr)
        print(label, "exit=" + str(result.returncode))
        if result.returncode:
            raise SystemExit(result.returncode)

    run("javac", [javac, "--release", "8", "-proc:none", "-encoding", "UTF-8", "-cp",
                  str(android) + os.pathsep + str(classes), "-d", compiled,
                  repo / "car/tests/fixtures/search_queue/Car7SearchProbe.java",
                  repo / "car/tests/fixtures/search_queue/Init.java"])
    class_files = sorted(compiled.rglob("*.class"))
    run("d8", [sdk / "build-tools" / args.build_tools / "d8.bat", "--min-api", "19",
               "--lib", android, "--classpath", classes, "--output", dex, *class_files])
    jar = out / "car7-search.jar"
    with zipfile.ZipFile(jar, "w", compression=zipfile.ZIP_STORED) as archive:
        archive.write(dex / "classes.dex", "classes.dex")
        # The shell discards JARs below 20 KiB. This is padding, not extra executable code.
        archive.writestr("fixture-padding.bin", bytes(24576))
    base = args.base_url.rstrip("/") + "/"
    for name, ignore in (("queue", False), ("stubborn", True)):
        sites = []
        for index in range(1, 33):
            sites.append({"key": "car7-slow-%02d" % index, "name": "慢站%02d" % index,
                          "type": 3, "api": "csp_Car7SearchProbe", "searchable": 1,
                          "quickSearch": 1, "filterable": 0, "ext": json.dumps({
                              "delay_ms": 22000 if ignore else 45000,
                              "ignore_interrupt": ignore, "source": index})})
        for index, mode in ((33, "result"), (34, "empty"), (35, "invalid")):
            sites.append({"key": "car7-tail-%02d" % index, "name": "尾站%02d" % index,
                          "type": 3, "api": "csp_Car7SearchProbe", "searchable": 1,
                          "quickSearch": 1, "filterable": 0, "ext": json.dumps({
                              "delay_ms": 10, "source": index, "mode": mode})})
        (out / ("api-" + name + ".json")).write_text(json.dumps({"spider": base + jar.name,
                  "sites": sites}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("fixture_sites=35 slow_sites=32 max_search_workers=24 jar_bytes=" + str(jar.stat().st_size))
    print("output=" + str(out))


if __name__ == "__main__":
    main()
