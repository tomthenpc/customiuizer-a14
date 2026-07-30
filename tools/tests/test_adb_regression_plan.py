"""Tests for ADB regression plan validation and command safety policy."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT = REPO_ROOT / "tools" / "adb-regression.py"


_BASE = {
    "schemaVersion": 1,
    "planId": "test-plan",
    "description": "test",
    "supportedApi": [34],
    "supportedRomFamily": ["hyperos1"],
    "steps": []
}


class PlanValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temps: list[tempfile.TemporaryDirectory] = []

    def tearDown(self) -> None:
        for td in self._temps:
            td.cleanup()

    def _write(self, data: dict) -> Path:
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        p = Path(td.name) / "plan.json"
        p.write_text(json.dumps(data, indent=2), encoding="utf-8")
        return p

    def _run(self, plan: Path) -> tuple[int, str, str]:
        proc = subprocess.run(
            [sys.executable, str(SCRIPT), "validate-plan", "--plan", str(plan)],
            capture_output=True,
            text=True,
            timeout=60,
        )
        return proc.returncode, proc.stdout, proc.stderr

    def test_valid_minimal(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "read prop", "command": ["getprop", "ro.product.model"], "timeoutSeconds": 30}
        ]
        rc, out, err = self._run(self._write(data))
        self.assertEqual(rc, 0, err)

    def test_malformed_json(self):
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        p = Path(td.name) / "plan.json"
        p.write_text("{not json", encoding="utf-8")
        rc, _, _ = self._run(p)
        self.assertEqual(rc, 2)

    def test_unsupported_schema_version(self):
        data = dict(_BASE)
        data["schemaVersion"] = 99
        data["steps"] = [{"id": "s1", "type": "shell", "description": "x", "command": ["getprop"], "timeoutSeconds": 30}]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_missing_required_field(self):
        data = {k: v for k, v in _BASE.items() if k != "steps"}
        data["steps"] = []
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_duplicate_step_id(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["getprop"], "timeoutSeconds": 30},
            {"id": "s1", "type": "shell", "description": "y", "command": ["getprop"], "timeoutSeconds": 30},
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_unknown_step_type(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "bad_type", "description": "x", "timeoutSeconds": 30}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_invalid_timeout(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["getprop"], "timeoutSeconds": 99999}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_missing_feature_id(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["getprop"],
             "timeoutSeconds": 30, "linkedFeatureIds": ["definitely-not-a-feature"]}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 1)

    def test_path_traversal(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["getprop"],
             "timeoutSeconds": 30, "evidenceFiles": ["../outside.txt"]}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_allowlist_ok(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["dumpsys", "package", "com.android.systemui"], "timeoutSeconds": 30}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 0)

    def test_denylist_reject(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["rm", "/data/data/x"], "timeoutSeconds": 30}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_metachar_reject(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["getprop", ";reboot"], "timeoutSeconds": 30}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_dangerous_unmarked(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["am", "broadcast", "-a", "x"], "timeoutSeconds": 30}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)

    def test_permanently_forbidden_with_dangerous(self):
        data = dict(_BASE)
        data["steps"] = [
            {"id": "s1", "type": "shell", "description": "x", "command": ["reboot"], "timeoutSeconds": 30, "dangerous": True}
        ]
        rc, _, _ = self._run(self._write(data))
        self.assertEqual(rc, 2)


if __name__ == "__main__":
    unittest.main()
