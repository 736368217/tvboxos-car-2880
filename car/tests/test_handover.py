"""Read-only handover verifier regression tests using a disposable Git repository."""
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from verify_handover import FLAGS, safe_member, tree, verify


class HandoverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=os.environ.get("PI_SCRATCH_DIR"))
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.command("init", "-q")
        self.command("config", "core.autocrlf", "false")
        (self.repo / "main.java").write_bytes(b"class Main {}\n")
        (self.repo / "legacy.txt").write_bytes(b"historical\n")
        self.command("add", "main.java", "legacy.txt")
        self.command("-c", "user.name=HandoverTest", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false", "commit", "-q", "-m", "fixture")
        mode, oid = tree(self.repo)["main.java"]
        data = (self.repo / "main.java").read_bytes()
        apk = b"local mock APK bytes, not installed"
        self.payload = {"tvbox-os-src/main.java": data, "candidate.apk": apk}
        self.state = {"schema": 2, "source_commit": self.command("rev-parse", "HEAD").strip(), "handoff_ref": "fixture", "remote": "origin",
                      "local_deletions_not_committed": ["legacy.txt"], "candidate": {"path": "candidate.apk", "sha256": self.sha(apk)},
                      "production_files": [{"path": "main.java", "sha256_lf": self.sha(data)}],
                      "files": [{"path": "tvbox-os-src/main.java", "repo_path": "main.java", "git_mode": mode, "git_blob": oid,
                                 "kind": "git_source", "bytes": len(data), "sha256": self.sha(data)},
                                {"path": "candidate.apk", "kind": "artifact", "bytes": len(apk), "sha256": self.sha(apk)}]}

    @staticmethod
    def sha(data):
        return hashlib.sha256(data).hexdigest().upper()

    def command(self, *args):
        result = subprocess.run(["git", "-C", str(self.repo), *args], capture_output=True, timeout=15, creationflags=FLAGS)
        result.check_returncode()
        return result.stdout.decode().strip()

    def bundle(self, state=None, payload=None):
        target = self.root / "bundle.zip"
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, data in (payload if payload is not None else self.payload).items():
                archive.writestr(name, data)
            archive.writestr("SOURCE-STATE.json", json.dumps(state or self.state))
        return target

    def test_correct_bundle_and_commit(self):
        result = verify(self.bundle(), self.repo)
        self.assertTrue(result["passed"])
        self.assertFalse(result["device_tests_run"])

    def test_user_local_deletion_is_not_restored(self):
        (self.repo / "legacy.txt").unlink()
        self.assertTrue(verify(self.bundle(), self.repo)["passed"])
        self.assertFalse((self.repo / "legacy.txt").exists())

    def test_changed_worktree_is_rejected_and_preserved(self):
        data = b"class UserChange {}\n"
        (self.repo / "main.java").write_bytes(data)
        with self.assertRaisesRegex(ValueError, "modified tracked"):
            verify(self.bundle(), self.repo)
        self.assertEqual((self.repo / "main.java").read_bytes(), data)

    def test_wrong_commit_is_rejected(self):
        state = copy.deepcopy(self.state)
        state["source_commit"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "commit differs"):
            verify(self.bundle(state), self.repo)

    def test_payload_tamper_is_rejected(self):
        payload = dict(self.payload, **{"candidate.apk": b"changed"})
        with self.assertRaisesRegex(ValueError, "Payload hash"):
            verify(self.bundle(payload=payload), self.repo)

    def test_candidate_identity_is_separate(self):
        state = copy.deepcopy(self.state)
        state["candidate"]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "APK identity"):
            verify(self.bundle(state), self.repo)

    def test_git_blob_identity_is_checked(self):
        state = copy.deepcopy(self.state)
        state["files"][0]["git_blob"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "Git tree"):
            verify(self.bundle(state), self.repo)

    def test_unlisted_file_is_rejected(self):
        payload = dict(self.payload, extra=b"unlisted")
        with self.assertRaisesRegex(ValueError, "inventory"):
            verify(self.bundle(payload=payload), self.repo)

    def test_private_file_is_rejected_without_reading_it(self):
        payload = dict(self.payload, **{"signing.jks": b"synthetic fixture"})
        with self.assertRaisesRegex(ValueError, "Private material"):
            verify(self.bundle(payload=payload), self.repo)

    def test_case_colliding_paths_are_rejected(self):
        payload = dict(self.payload, **{"CANDIDATE.APK": b"duplicate under Windows"})
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            verify(self.bundle(payload=payload), self.repo)

    def test_unsafe_paths(self):
        for name in ["", ".", "../outside", "/absolute", "C:/drive", "folder\\file", ".GIT/config", "a/../../b", "a\0b"]:
            with self.subTest(name=name):
                self.assertFalse(safe_member(name))
        self.assertTrue(safe_member("car/normal-file.json"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
