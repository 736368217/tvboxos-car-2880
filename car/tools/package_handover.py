"""Package committed car source plus external artifacts, pinned to a verified remote handoff ref."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

from verify_handover import FLAGS, PRIVATE_SUFFIXES, changed_tracked, git, remote_refs, safe_member, sha_stream, tree, verify

REPO = Path(__file__).resolve().parents[2]
ALLOWED_JARS = {"app/libs/commons-lang3-3.12.0.jar", "app/libs/thunder.jar", "gradle/wrapper/gradle-wrapper.jar"}


def excluded_source(name, baseline):
    path = Path(name)
    if path.suffix.lower() in PRIVATE_SUFFIXES or path.suffix.lower() in {".apk", ".log"} or path.name in {"local.properties", ".env"}:
        return "private or generated artifact"
    if path.suffix.lower() == ".jar" and name not in ALLOWED_JARS:
        return "not an allowlisted upstream library"
    if name in baseline["local_deletions_not_committed"]:
        return "user-local deletion retained; historical Git blob is not a test fixture"
    if name.startswith("car/evidence/") and not name.startswith(baseline["evidence"] + "/"):
        return "historical evidence excluded from transfer"
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--delivery-root", type=Path, required=True, help="Parent containing tvbox-os-car")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--python38", type=Path)
    parser.add_argument("--reference-root", type=Path)
    args = parser.parse_args()
    output = args.out.resolve()
    if output == REPO or REPO in output.parents:
        parser.error("--out must be outside the Git checkout")
    baseline = json.loads((REPO / "car/handover/baseline.json").read_text(encoding="utf-8"))
    dirty = changed_tracked(REPO, baseline["local_deletions_not_committed"])
    if dirty:
        parser.error("Commit task changes before packaging: " + ", ".join(dirty[:12]))
    commit = git(REPO, "rev-parse", "HEAD").decode().strip()
    refs = remote_refs(REPO, baseline["remote"], baseline["handoff_ref"])
    ref = "refs/tags/" + baseline["handoff_ref"]
    if refs.get(ref + "^{}", refs.get(ref)) != commit:
        parser.error("Push the exact immutable handoff ref first; do not move an existing ref")
    delivery = args.delivery_root.resolve()
    candidate = baseline["candidate"]
    apk = delivery / candidate["path"]
    with apk.open("rb") as stream:
        if sha_stream(stream) != candidate["sha256"] or apk.stat().st_size != candidate["bytes"]:
            parser.error("Accepted APK differs from baseline")
    evidence = REPO / baseline["evidence"]
    expected_counts = {"playback": (101, 0), "pages": (49, 0), "ui": (58, 0), "quickjs": (14, 0), "searchqueue": (12, 0),
                       "python": (12, 1), "python-chain": (12, 1), "xiaosa": (10, 2), "aidongman-recheck": (4, 1)}
    acceptance = {}
    for name, expected in expected_counts.items():
        result = json.loads((evidence / name / "result.json").read_text(encoding="utf-8"))
        identity = json.loads((evidence / name / "identity.json").read_text(encoding="utf-8"))
        if (result["passed"], result["failed"]) != expected or identity["sourceApkSha256"].upper() != candidate["sha256"]:
            parser.error("Evidence identity/result changed: " + name)
        acceptance[name] = {"passed": result["passed"], "failed": result["failed"], "tag": identity["tag"]}
    state = {"schema": 2, "source_commit": commit, "source_base_commit": baseline["source_base_commit"],
             "repository": baseline["repository"], "remote": baseline["remote"], "handoff_ref": baseline["handoff_ref"],
             "remote_ref_verified_at_pack": True, "remote_main_at_pack": refs.get("refs/heads/main"),
             "candidate": candidate, "production_files": baseline["production_files"], "acceptance": acceptance,
             "local_acceptance_passed": True, "external_acceptance_passed": False, "releaseReady": False,
             "local_deletions_not_committed": baseline["local_deletions_not_committed"],
             "files": [], "excluded_source_files": [], "note": "Inventory excludes SOURCE-STATE.json itself; ZIP SHA is in the external checksum file. Source payload uses canonical Git blobs, not worktree line endings."}
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".building")
    entries = tree(REPO)
    seen = set()

    def add_data(archive, name, data, kind, **metadata):
        if not safe_member(name) or name in seen or Path(name).suffix.lower() in PRIVATE_SUFFIXES:
            raise ValueError("Unsafe, duplicate or private payload: " + name)
        seen.add(name)
        archive.writestr(name, data)
        state["files"].append({"path": name, "kind": kind, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest().upper(), **metadata})

    def add_path(archive, name, path, kind):
        add_data(archive, name, path.read_bytes(), kind)

    process = None
    try:
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=5) as archive:
            process = subprocess.Popen(["git", "-C", str(REPO), "cat-file", "--batch"], stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=FLAGS)
            for name, (mode, oid) in sorted(entries.items()):
                reason = excluded_source(name, baseline)
                if reason:
                    state["excluded_source_files"].append({"path": name, "reason": reason})
                    continue
                process.stdin.write((oid + "\n").encode())
                process.stdin.flush()
                header = process.stdout.readline().decode().split()
                if len(header) != 3 or header[1] != "blob":
                    raise ValueError("Cannot read committed blob: " + name)
                size = int(header[2])
                data = process.stdout.read(size)
                if len(data) != size or process.stdout.read(1) != b"\n":
                    raise ValueError("Incomplete committed blob: " + name)
                add_data(archive, "tvbox-os-src/" + name, data, "git_source", repo_path=name, git_mode=mode, git_blob=oid)
            for item in baseline["production_files"]:
                data = (REPO / item["path"]).read_bytes().replace(b"\r\n", b"\n")
                if hashlib.sha256(data).hexdigest().upper() != item["sha256_lf"]:
                    raise ValueError("Production source changed since accepted APK: " + item["path"])
            add_path(archive, "START-HERE.md", REPO / "START-HERE.md", "entry")
            add_path(archive, "AGENTS.md", REPO / "AGENTS.md", "entry")
            add_path(archive, candidate["path"], apk, "artifact")
            report = delivery / "tvbox-os-car/report-car.7"
            for path in sorted(report.rglob("*")):
                if path.is_file() and not path.name.endswith(".owned-pid"):
                    add_path(archive, path.relative_to(delivery).as_posix(), path, "artifact")
            for name in ["TVBox-car7-report.docx", "TVBox-car7-report.pdf", "TVBox-car7-report.html", "document-validation.json"]:
                if not (report / name).is_file():
                    raise ValueError("Report is incomplete: " + name)
            if args.python38:
                python = args.python38.resolve()
                if not (python / "python.exe").is_file():
                    parser.error("--python38 must contain the already-provisioned interpreter")
                for path in sorted(python.rglob("*")):
                    if path.is_file() and "__pycache__" not in path.parts and path.suffix.lower() not in PRIVATE_SUFFIXES:
                        add_path(archive, "tools/python38-build/" + path.relative_to(python).as_posix(), path, "build_tool")
            if args.reference_root:
                reference = args.reference_root.resolve()
                for relative in ["AGENTS.md", "PACKAGE-REGISTRY.md", "package-registry.json"]:
                    path = reference / relative
                    if path.is_file():
                        add_path(archive, "workspace-reference/" + relative, path, "read_only_reference")
                for name in ["07-lab-environment-mumu.md", "13-lab-pitfalls-and-multi-instance.md", "14-spider-jar-identity-gate-and-whitelist.md", "15-mumu-instance-pool.md"]:
                    path = reference / "tvbox-shell-repair-kit/references" / name
                    if path.is_file():
                        add_path(archive, "tvbox-shell-repair-kit/references/" + name, path, "read_only_reference")
            archive.writestr("SOURCE-STATE.json", json.dumps(state, ensure_ascii=False, indent=2).encode("utf-8"))
        process.stdin.close()
        process.wait(timeout=15)
        if process.returncode:
            raise ValueError("Git blob reader failed")
        validation = verify(temporary, REPO, check_remote=True)
        temporary.replace(output)
        manifest_path = output.parent / "MANIFEST-car.7-r2.json"
        manifest_path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
        with output.open("rb") as stream:
            zip_sha = sha_stream(stream)
        checksums = zip_sha + "  " + output.name + "\n" + candidate["sha256"] + "  " + Path(candidate["path"]).name + "\n"
        output.with_suffix(".sha256.txt").write_text(checksums, encoding="ascii")
        validation.update(zip_sha256=zip_sha, zip_bytes=output.stat().st_size)
        output.with_suffix(".validation.json").write_text(json.dumps(validation, indent=2), encoding="utf-8")
        print(json.dumps(validation, ensure_ascii=True))
    finally:
        if process is not None:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=10)
            for stream in [process.stdin, process.stdout, process.stderr]:
                if stream and not stream.closed:
                    stream.close()
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
