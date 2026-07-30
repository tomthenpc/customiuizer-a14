"""Tests for manual checkpoint / Tasker support."""

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
A14_SMOKE = REPO_ROOT / "adb-regression" / "a14-smoke.json"


class TaskerTest(unittest.TestCase):
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

    def _write_manual_results(self, checkpoints: list[dict]) -> Path:
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        p = Path(td.name) / "manual-results.json"
        p.write_text(json.dumps({"checkpoints": checkpoints}, indent=2), encoding="utf-8")
        return p

    def _run(
        self,
        plan: Path,
        state: str = "ok",
        scenario: str = "ok",
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
        env["FAKE_ADB_SCENARIO"] = scenario
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

    def _manual_plan(self, step_id: str = "m1") -> Path:
        return self._write_plan({
            "schemaVersion": 1,
            "planId": "manual-test",
            "description": "manual",
            "supportedApi": [34],
            "supportedRomFamily": ["hyperos1"],
            "steps": [
                {
                    "id": step_id,
                    "type": "manual_checkpoint",
                    "description": "manual checkpoint",
                    "timeoutSeconds": 10,
                    "manual": True,
                    "evidenceFiles": ["manual-checkpoints.json"],
                },
            ],
        })

    def test_manual_results_all_pass(self) -> None:
        results = self._write_manual_results([
            {"stepId": "m1", "status": "PASS", "notes": "verified"},
        ])
        plan = self._manual_plan()
        rc, out, err, out_dir = self._run(plan, extra=["--manual-results", str(results)])
        self.assertEqual(rc, 0, f"stdout={out!r} stderr={err!r}")
        report = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(report["steps"][0]["status"], "PASS")

    def test_manual_missing_required_exit_3(self) -> None:
        plan = self._manual_plan()
        rc, out, err, _ = self._run(plan)
        self.assertEqual(rc, 3, f"stdout={out!r} stderr={err!r}")

    def test_manual_fail_exit_1(self) -> None:
        results = self._write_manual_results([
            {"stepId": "m1", "status": "FAIL", "notes": "saw a token"},
        ])
        plan = self._manual_plan()
        rc, out, err, _ = self._run(plan, extra=["--manual-results", str(results)])
        self.assertEqual(rc, 1, f"stdout={out!r} stderr={err!r}")

    def test_a14_smoke_all_pass_manual_results(self) -> None:
        if not A14_SMOKE.is_file():
            self.skipTest("a14-smoke.json not found")
        results = self._write_manual_results([
            {
                "stepId": "broadcast-negative-placeholder",
                "status": "PASS",
                "notes": "negative broadcasts blocked with SENTINEL/FAILED",
            },
            {
                "stepId": "tasker-manual-placeholder",
                "status": "PASS",
                "notes": "no token or Bundle in logs",
            },
        ])
        rc, out, err, _ = self._run(
            A14_SMOKE,
            scenario="ok",
            extra=["--manual-results", str(results)],
        )
        self.assertEqual(rc, 0, f"stdout={out!r} stderr={err!r}")

    def test_manual_notes_redacted(self) -> None:
        from adb_regression import tasker
        raw = (
            "token=abc123 bundle data; /home/user/secret; "
            "user@example.com; dGVzdA=="
        )
        redacted = tasker.redact_notes(raw)
        self.assertNotIn("abc123", redacted)
        self.assertNotIn("/home/user/secret", redacted)
        self.assertNotIn("user@example.com", redacted)
        self.assertNotIn("dGVzdA==", redacted)

    def test_load_manual_results_maps_by_step_id(self) -> None:
        from adb_regression import tasker
        p = self._write_manual_results([
            {"stepId": "a", "status": "PASS", "notes": "ok"},
            {"stepId": "b", "status": "FAIL", "notes": "bad"},
        ])
        results = tasker.load_manual_results(p)
        self.assertEqual(results["a"]["status"], "PASS")
        self.assertEqual(results["b"]["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
