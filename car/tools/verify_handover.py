"""Verify a handover ZIP and its Git checkout without extracting or modifying either."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import zipfile

FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
PRIVATE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".key", ".pem"}


def safe_member(name):
    path = PurePosixPath(name)
    return bool(path.parts) and not path.is_absolute() and ".." not in path.parts and "\\" not in name and ":" not in name and ".git" not in {part.lower() for part in path.parts} and "\0" not in name


def git(repo, *args):
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, timeout=90, creationflags=FLAGS)
    if result.returncode:
        raise ValueError("Git command failed: " + " ".join(args[:2]))
    return result.stdout


def tree(repo):
    records = {}
    for entry in git(repo, "ls-tree", "-r", "-z", "HEAD").split(b"\0"):
        if entry:
            meta, name = entry.split(b"\t", 1)
            mode, kind, oid = meta.decode().split()
            if kind == "blob":
                records[name.decode("utf-8")] = (mode, oid)
    return records


def sha_stream(stream):
    digest = hashlib.sha256()
    for data in iter(lambda: stream.read(1048576), b""):
        digest.update(data)
    return digest.hexdigest().upper()


def changed_tracked(repo, allowed_deletions):
    changes = []
    for raw in git(repo, "diff", "--name-only", "-z", "HEAD").split(b"\0"):
        if raw:
            name = raw.decode("utf-8")
            if name not in allowed_deletions or (repo / name).exists():
                changes.append(name)
    return changes


def remote_refs(repo, remote, ref):
    rows = git(repo, "ls-remote", remote, "refs/heads/main", "refs/tags/" + ref, "refs/tags/" + ref + "^{}")
    return dict((line.split()[1], line.split()[0]) for line in rows.decode().splitlines())


def verify(bundle, repo, check_remote=False):
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        if len(names) != len({name.casefold() for name in names}) or any(not safe_member(name) for name in names):
            raise ValueError("Duplicate or unsafe ZIP member")
        if archive.testzip() is not None:
            raise ValueError("ZIP CRC failure")
        if any(PurePosixPath(name).suffix.lower() in PRIVATE_SUFFIXES or PurePosixPath(name).name in {"Hawk2.xml", "local.properties"} for name in names):
            raise ValueError("Private material in ZIP")
        state = json.loads(archive.read("SOURCE-STATE.json"))
        if state.get("schema") != 2:
            raise ValueError("Unsupported handover manifest")
        expected_names = {item["path"] for item in state["files"]}
        if len(expected_names) != len(state["files"]) or set(names) != expected_names | {"SOURCE-STATE.json"}:
            raise ValueError("ZIP and inventory disagree")
        entries = tree(repo)
        actual_commit = git(repo, "rev-parse", "HEAD").decode().strip()
        if actual_commit != state["source_commit"]:
            raise ValueError("Checkout commit differs from SOURCE-STATE.json; do not overlay files")
        dirty = changed_tracked(repo, state["local_deletions_not_committed"])
        if dirty:
            raise ValueError("Checkout has modified tracked files: " + ", ".join(dirty[:10]))
        for item in state["files"]:
            info = archive.getinfo(item["path"])
            with archive.open(info) as stream:
                digest = sha_stream(stream)
            if info.file_size != item["bytes"] or digest != item["sha256"]:
                raise ValueError("Payload hash mismatch: " + item["path"])
            if item["kind"] == "git_source":
                if entries.get(item["repo_path"]) != (item["git_mode"], item["git_blob"]):
                    raise ValueError("Git tree mismatch: " + item["repo_path"])
                data = archive.read(item["path"])
                algorithm = hashlib.sha1 if len(item["git_blob"]) == 40 else hashlib.sha256
                blob = algorithm(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
                if blob != item["git_blob"]:
                    raise ValueError("Payload is not the committed Git blob: " + item["repo_path"])
        candidate = state["candidate"]
        with archive.open(candidate["path"]) as stream:
            if sha_stream(stream) != candidate["sha256"]:
                raise ValueError("Candidate APK identity mismatch")
        production = state["production_files"]
        for item in production:
            data = archive.read("tvbox-os-src/" + item["path"]).replace(b"\r\n", b"\n")
            if hashlib.sha256(data).hexdigest().upper() != item["sha256_lf"]:
                raise ValueError("Accepted production fingerprint mismatch: " + item["path"])
        if check_remote:
            refs = remote_refs(repo, state["remote"], state["handoff_ref"])
            ref = "refs/tags/" + state["handoff_ref"]
            if refs.get(ref + "^{}", refs.get(ref)) != actual_commit:
                raise ValueError("Remote handoff ref does not match")
        return {"passed": True, "source_commit": actual_commit, "handoff_ref": state["handoff_ref"],
                "zip_members": len(names), "payload_hashes_checked": len(state["files"]),
                "git_source_files_checked": sum(item["kind"] == "git_source" for item in state["files"]),
                "production_files_checked": len(production), "candidate_sha256": candidate["sha256"],
                "remote_checked": check_remote, "device_tests_run": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--check-remote", action="store_true")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.bundle, args.repo.resolve(), args.check_remote)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        result = {"passed": False, "error": str(error), "device_tests_run": False}
    if args.out:
        destination = args.out.resolve()
        repo = args.repo.resolve()
        if destination == repo or repo in destination.parents:
            parser.error("--out must be outside the source checkout")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
