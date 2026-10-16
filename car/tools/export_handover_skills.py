"""Export selected learned skills as LF-normalized, versioned project references."""
import argparse
import hashlib
import json
from pathlib import Path

SKILLS = ["glsurfaceview-video-lifecycle", "android-native-acceptance-evidence", "git-workspace-handover"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path.home() / ".agents/skills")
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parents[1] / "handover/skills")
    args = parser.parse_args()
    records = []
    for name in SKILLS:
        source = args.source / name
        if not (source / "SKILL.md").is_file():
            parser.error("Missing selected local skill: " + name)
        for path in sorted(source.rglob("*")):
            if not path.is_file() or "__pycache__" in path.parts or path.suffix.lower() not in {".md", ".py", ".ps1", ".sh", ".json", ".txt"}:
                continue
            relative = Path(name) / path.relative_to(source)
            data = path.read_bytes().replace(b"\r\n", b"\n")
            target = args.out / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            records.append({"path": relative.as_posix(), "bytes": len(data), "sha256_lf": hashlib.sha256(data).hexdigest().upper()})
    args.out.mkdir(parents=True, exist_ok=True)
    index = {"schema": 1, "normalization": "CRLF to LF", "auto_install": False,
             "instructions": "Read and compare with local versions; merge deliberately rather than overwriting newer skills.", "files": records}
    (args.out / "index.json").write_text(json.dumps(index, indent=2), encoding="utf-8")
    print(json.dumps({"skills": SKILLS, "files": len(records), "auto_install": False}))


if __name__ == "__main__":
    main()
