"""Render the report in an isolated hidden Word instance with a bounded lifetime."""
import argparse
import json
import os
from pathlib import Path
import subprocess

FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=90)
    args = parser.parse_args()
    report = args.report.resolve()
    result = report / "word-render-validation.json"
    owned = Path(str(result) + ".owned-pid")
    owned.unlink(missing_ok=True)
    args.log.parent.mkdir(parents=True, exist_ok=True)
    command = ["powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File",
               str(Path(__file__).with_suffix(".ps1")), "-InputDocx", str(report / "TVBox-car7-report.docx"),
               "-OutputPdf", str(report / "TVBox-car7-report.pdf"), "-ResultJson", str(result)]
    process = None
    code = 1
    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=FLAGS)
        stdout, stderr = process.communicate(timeout=args.timeout)
        args.log.write_bytes(stdout + stderr)
        code = process.returncode
        print(json.dumps({"renderer_exit": code, "result_present": result.exists()}))
    except subprocess.TimeoutExpired:
        print(json.dumps({"renderer_exit": 124, "error": "Word render deadline exceeded"}))
        code = 124
    finally:
        if process is not None and process.poll() is None:
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, creationflags=FLAGS, timeout=15)
            process.communicate(timeout=10)
        if owned.exists() and owned.read_text().strip().isdigit():
            pid = owned.read_text().strip()
            check = subprocess.run(["tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH"], capture_output=True, creationflags=FLAGS, timeout=15)
            if b"WINWORD.EXE" in check.stdout.upper() or b"WPS.EXE" in check.stdout.upper():
                subprocess.run(["taskkill", "/PID", pid, "/T", "/F"], capture_output=True, creationflags=FLAGS, timeout=15)
            owned.unlink(missing_ok=True)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
