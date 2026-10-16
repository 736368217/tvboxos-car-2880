"""Measure actual color bars in the car.7 native local-motion screenshots.

A decoder can keep advancing while its output becomes green. This check uses
known fixture colors instead of accepting frame/position counters alone.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageStat


def measure(path, fullscreen):
    image = Image.open(path).convert("RGB")
    if image.size != (2880, 1620):
        raise ValueError("Expected qualified 2880x1620 screenshot: " + str(path))
    x0, y0, width, height = (0, 0, 2880, 1620) if fullscreen else (800, 450, 1280, 720)
    # Five static bars away from the timestamp and floating Back control.
    bars = [(0.24, (0, 1, 0)), (0.41, (1, 1, 0)), (0.58, (0, 0, 1)),
            (0.75, (1, 0, 1)), (0.91, (0, 1, 1))]
    samples = []
    for fraction, expected in bars:
        x = x0 + int(width * fraction)
        y = y0 + int(height * 0.072)
        rgb = ImageStat.Stat(image.crop((x - 8, y - 10, x + 8, y + 10))).median
        matches = all(value >= 170 if high else value <= 90 for value, high in zip(rgb, expected))
        samples.append({"x_fraction": fraction, "rgb_median": rgb, "expected": expected, "pass": matches})
    # The moving diagonal/foreground can cover one patch. A green frame fails
    # yellow, blue, magenta and cyan, well outside this tolerance.
    return {"image": path.name, "pass": sum(s["pass"] for s in samples) >= 4, "samples": samples}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--lifecycle", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    identity = json.loads((args.evidence / "identity.json").read_text(encoding="utf-8"))
    if identity["sourceApkSha256"].lower() != args.sha256.lower():
        raise ValueError("APK identity mismatch")
    stages = ["playing", "fullscreen"] + (["foreground", "reentry"] if args.lifecycle else [])
    results = [measure(args.evidence / (engine + "-" + stage + ".png"), stage == "fullscreen")
               for engine in ["EXO", "IJK"] for stage in stages]
    report = {"candidate_sha256": args.sha256.upper(), "pid": identity["pid"],
              "passed": sum(item["pass"] for item in results),
              "failed": sum(not item["pass"] for item in results), "images": results}
    text = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "images"}))
    return 0 if report["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
