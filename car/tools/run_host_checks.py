"""Run car host checks from any checkout without starting an emulator or Gradle."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

REPO = Path(__file__).resolve().parents[2]
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def cache_jar(roots, group, artifact, version):
    for root in roots:
        folder = root / "caches/modules-2/files-2.1" / group / artifact / version
        matches = sorted(folder.glob("*/%s-%s.jar" % (artifact, version)))
        if matches:
            return matches[0]
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--core-only", action="store_true", help="Skip checks needing cached JVM HTTP/QuickJS libraries")
    parser.add_argument("--gradle-cache", action="append", type=Path, default=[])
    parser.add_argument("--quickjs-classes", type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    if out == REPO or REPO in out.parents:
        parser.error("--out must be outside the source checkout (prefer PI_SCRATCH_DIR)")
    out.mkdir(parents=True, exist_ok=True)
    java_home = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
    java = java_home / "java.exe" if java_home and os.name == "nt" else java_home / "java" if java_home else shutil.which("java")
    javac = java_home / "javac.exe" if java_home and os.name == "nt" else java_home / "javac" if java_home else shutil.which("javac")
    if not java or not javac or not Path(javac).is_file():
        parser.error("A JDK is required; set JAVA_HOME to JDK 17")
    checks = []
    missing = []

    def run(name, command, timeout=90):
        try:
            result = subprocess.run([str(value) for value in command], cwd=REPO, capture_output=True,
                                    timeout=timeout, creationflags=FLAGS)
            (out / (name + ".log")).write_bytes(result.stdout + result.stderr)
            item = {"name": name, "exit": result.returncode,
                    "stdout": result.stdout.decode("utf-8", errors="replace").strip()[-2500:]}
        except subprocess.TimeoutExpired:
            item = {"name": name, "exit": 124, "error": "deadline exceeded"}
        checks.append(item)
        print(json.dumps(item, ensure_ascii=True))
        return item["exit"] == 0

    xmls = list((REPO / "app/src/main/res").rglob("*.xml"))
    for path in xmls:
        ET.parse(path)
    checks.append({"name": "resource_xml", "exit": 0, "files": len(xmls)})
    run("diff_whitespace", ["git", "-c", "core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol", "diff", "--check"])
    render = REPO / "player/src/main/java/xyz/doikki/videoplayer/render"
    sources = [render / name for name in ["VideoEnhancePreset.java", "VideoPostProcessPolicy.java", "VideoEnhanceConfig.java"]]
    app = REPO / "app/src/main/java/com/github/tvbox/osc"
    sources += [app / "car/AudioEffectPreset.java", app / "car/BackNavPolicy.java", app / "util/SearchSession.java"]
    tests = ["VideoEnhancePresetTest", "Car6PostProcessTest", "Car7RuntimeConfigTest", "Car7SearchSessionTest"]
    sources += [REPO / "car/tests" / (name + ".java") for name in tests]
    sources.append(REPO / "app/src/test/java/com/github/tvbox/osc/car/BackNavPolicyHostTest.java")
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    if run("compile_core", [javac, "--release", "8", "-encoding", "UTF-8", "-d", classes, *sources]):
        for name in tests + ["com.github.tvbox.osc.car.BackNavPolicyHostTest"]:
            run(name, [java, "-cp", classes, name])
    run("python_http", [os.sys.executable, "-B", REPO / "car/tests/test_python_http.py"])
    if not args.core_only:
        caches = args.gradle_cache + ([Path(os.environ["GRADLE_USER_HOME"])] if os.environ.get("GRADLE_USER_HOME") else []) + [Path.home() / ".gradle"]
        libraries = [cache_jar(caches, *coordinate) for coordinate in [
            ("wang.harlon.quickjs", "wrapper-java", "2.1.0"),
            ("com.squareup.okhttp3", "okhttp", "3.12.11"),
            ("com.squareup.okio", "okio", "1.15.0")]]
        quickjs = args.quickjs_classes or REPO / "quickjs/build/intermediates/compile_library_classes_jar/release/classes.jar"
        for name, path in zip(["wrapper-java 2.1.0", "okhttp 3.12.11", "okio 1.15.0"], libraries):
            if path is None:
                missing.append(name + ": build dependencies first or pass --gradle-cache")
        if not quickjs.is_file():
            missing.append("QuickJS Android bridge classes: build once or pass --quickjs-classes")
        if not missing:
            cp = os.pathsep.join(str(path) for path in [classes, quickjs, *libraries])
            sources = [REPO / "app/src/main/java/com/github/catvod/crawler/js/Async.java",
                       REPO / "car/tests/QuickJsResultTest.java", REPO / "car/tests/Car7SearchHttpScopeTest.java"]
            if run("compile_runtime_contracts", [javac, "--release", "8", "-encoding", "UTF-8", "-proc:none", "-cp", cp, "-d", classes, *sources]):
                for name in ["com.github.catvod.crawler.js.QuickJsResultTest", "Car7SearchHttpScopeTest"]:
                    run(name, [java, "-cp", cp, name])
    code = 1 if any(item["exit"] for item in checks) else 2 if missing else 0
    summary = {"exit": code, "scope": "core" if args.core_only else "full-host", "repo": str(REPO),
               "checks": checks, "missing_prerequisites": missing, "device_tests_run": False}
    (out / "results.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in summary.items() if key != "checks"}, ensure_ascii=True))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
