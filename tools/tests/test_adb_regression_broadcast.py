"""Tests for the ADB broadcast probe executor."""

import json
import os
import platform
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT = REPO_ROOT / "tools" / "adb-regression.py"
FIXTURES = REPO_ROOT / "tools" / "tests" / "fixtures"
FAKE_ADB = FIXTURES / "fake_adb.py"


class BroadcastTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temps: list[tempfile.TemporaryDirectory] = []

    def tearDown(self) -> None:
        for td in self._temps:
            td.cleanup()

    def _make_wrapper(self, tmp: Path) -> Path:
        if platform.system() == "Windows":
            wrapper = tmp / "fake-adb.cmd"
            wrapper.write_text(
                f'@echo off\n"{sys.executable}" "{FAKE_ADB}" %*\n',
                encoding="utf-8",
            )
        else:
            wrapper = tmp / "fake-adb"
            wrapper.write_text(
                f'#!/bin/sh\nexec "{sys.executable}" "{FAKE_ADB}" "$@"\n',
                encoding="utf-8",
            )
            os.chmod(wrapper, 0o755)
        return wrapper

    def _write_plan(self, data: dict) -> Path:
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        p = Path(td.name) / "plan.json"
        p.write_text(json.dumps(data, indent=2), encoding="utf-8")
        return p

    def _run(
        self,
        plan: Path,
        state: str = "ok",
        extra: list[str] | None = None,
        serial: str = "FAKE001",
    ) -> tuple[int, str, str, Path]:
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        tmp_path = Path(td.name)
        wrapper = self._make_wrapper(tmp_path)
        out_dir = tmp_path / "out"
        env = os.environ.copy()
        env["ADB_FAKE_STATE"] = state
        env["FAKE_ADB_SCENARIO"] = "ok"
        args = [
            sys.executable,
            str(SCRIPT),
            "run",
            "--adb",
            str(wrapper),
            "--serial",
            serial,
            "--plan",
            str(plan),
            "--output",
            str(out_dir),
            "--timeout",
            "10",
        ]
        if extra:
            args += extra
        proc = subprocess.run(args, capture_output=True, text=True, env=env, timeout=120)
        actual_out = out_dir
        if out_dir.is_dir():
            run_dirs = [d for d in out_dir.iterdir() if d.is_dir()]
            if run_dirs:
                actual_out = run_dirs[0]
        return proc.returncode, proc.stdout, proc.stderr, actual_out

    def _broadcast_plan(self, kind: str, result: str | None = None) -> Path:
        expected: dict[str, object] = {"broadcastKind": kind}
        if result:
            expected["result"] = result
        return self._write_plan({
            "schemaVersion": 1,
            "planId": "broadcast-test",
            "description": "broadcast",
            "supportedApi": [34],
            "supportedRomFamily": ["hyperos1"],
            "steps": [
                {
                    "id": "b1",
                    "type": "broadcast_probe",
                    "description": "probe",
                    "timeoutSeconds": 10,
                    "expected": expected,
                },
            ],
        })

    def test_registered_negative_pass_sentinel(self) -> None:
        plan = self._broadcast_plan("FastReboot", "SENTINEL")
        rc, out, err, out_dir = self._run(plan)
        self.assertEqual(rc, 0, f"stdout={out!r} stderr={err!r}")
        report = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(report["steps"][0]["status"], "PASS")
        self.assertEqual(report["steps"][0]["probeResult"], "SENTINEL")

    def test_registered_negative_pass_failed(self) -> None:
        plan = self._broadcast_plan("RestartSystemUI", "FAILED")
        rc, out, err, out_dir = self._run(plan)
        self.assertEqual(rc, 0, f"stdout={out!r} stderr={err!r}")
        report = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(report["steps"][0]["status"], "PASS")
        self.assertEqual(report["steps"][0]["probeResult"], "FAILED")

    def test_unregistered_action_fail(self) -> None:
        plan = self._broadcast_plan("unregistered_action", "HANDLED")
        rc, out, err, out_dir = self._run(plan)
        self.assertEqual(rc, 1, f"stdout={out!r} stderr={err!r}")
        report = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(report["steps"][0]["status"], "FAIL")
        self.assertEqual(report["steps"][0]["probeResult"], "HANDLED")

    def test_missing_token_fail(self) -> None:
        plan = self._broadcast_plan("missing_token", "HANDLED")
        rc, out, err, out_dir = self._run(plan)
        self.assertEqual(rc, 1, f"stdout={out!r} stderr={err!r}")
        report = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(report["steps"][0]["status"], "FAIL")
        self.assertEqual(report["steps"][0]["probeResult"], "HANDLED")

    def test_unknown_broadcast_kind_errors(self) -> None:
        plan = self._broadcast_plan("NotARealKind")
        rc, out, err, _ = self._run(plan)
        self.assertEqual(rc, 2, f"stdout={out!r} stderr={err!r}")


if __name__ == "__main__":
    unittest.main()
