"""Tests for the ADB regression run command."""

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


class RunnerTest(unittest.TestCase):
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
        # The runner creates a unique subdirectory inside --output when it gets that far.
        actual_out = out_dir
        if out_dir.is_dir():
            run_dirs = [d for d in out_dir.iterdir() if d.is_dir()]
            if run_dirs:
                actual_out = run_dirs[0]
        return proc.returncode, proc.stdout, proc.stderr, actual_out

    def test_real_a14_smoke_returns_3(self) -> None:
        if not A14_SMOKE.is_file():
            self.skipTest("a14-smoke.json not found")
        rc, out, err, _ = self._run(A14_SMOKE, scenario="ok")
        self.assertEqual(rc, 3, f"stdout={out!r} stderr={err!r}")

    def test_fixture_plan_no_manual_returns_0(self) -> None:
        plan = self._write_plan({
            "schemaVersion": 1,
            "planId": "fixture-ok",
            "description": "ok",
            "supportedApi": [34],
            "supportedRomFamily": ["hyperos1"],
            "steps": [
                {
                    "id": "s1",
                    "type": "collect_diagnostics",
                    "description": "x",
                    "timeoutSeconds": 10,
                    "evidenceFiles": ["preflight.json"],
                },
                {
                    "id": "s2",
                    "type": "logcat_assert",
                    "description": "markers",
                    "timeoutSeconds": 10,
                    "expected": {
                        "patterns": [
                            "CustoMIUIzer .* loaded in .*system_server",
                        ],
                    },
                },
            ],
        })
        rc, out, err, _ = self._run(plan, scenario="module_markers")
        self.assertEqual(rc, 0, f"stdout={out!r} stderr={err!r}")

    def test_assertion_fail_returns_1(self) -> None:
        plan = self._write_plan({
            "schemaVersion": 1,
            "planId": "fixture-fail",
            "description": "fail",
            "supportedApi": [34],
            "supportedRomFamily": ["hyperos1"],
            "steps": [
                {
                    "id": "s1",
                    "type": "logcat_assert",
                    "description": "missing",
                    "timeoutSeconds": 10,
                    "expected": {
                        "patterns": ["this pattern does not exist"],
                    },
                },
            ],
        })
        rc, out, err, _ = self._run(plan, scenario="ok")
        self.assertEqual(rc, 1, f"stdout={out!r} stderr={err!r}")

    def test_environment_error_returns_2(self) -> None:
        rc, out, err, _ = self._run(A14_SMOKE, state="no_devices", scenario="ok")
        self.assertEqual(rc, 2, f"stdout={out!r} stderr={err!r}")

    def test_continue_on_failure_and_cleanup(self) -> None:
        plan = self._write_plan({
            "schemaVersion": 1,
            "planId": "continue",
            "description": "continue",
            "supportedApi": [34],
            "supportedRomFamily": ["hyperos1"],
            "steps": [
                {
                    "id": "fail-step",
                    "type": "logcat_assert",
                    "description": "fail",
                    "timeoutSeconds": 10,
                    "continueOnFailure": True,
                    "expected": {
                        "patterns": ["will not match"],
                    },
                },
                {
                    "id": "pass-step",
                    "type": "shell",
                    "description": "ok",
                    "command": ["getprop", "ro.product.model"],
                    "timeoutSeconds": 10,
                },
            ],
            "cleanup": [
                {
                    "id": "cleanup",
                    "type": "sleep",
                    "description": "cleanup",
                    "timeoutSeconds": 1,
                },
            ],
        })
        rc, out, err, out_dir = self._run(plan, scenario="ok")
        self.assertEqual(rc, 1)
        report = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
        statuses = {s["id"]: s["status"] for s in report["steps"]}
        self.assertEqual(statuses["fail-step"], "FAIL")
        self.assertEqual(statuses["pass-step"], "PASS")

    def test_report_consistency_and_manifest(self) -> None:
        plan = self._write_plan({
            "schemaVersion": 1,
            "planId": "manifest",
            "description": "manifest",
            "supportedApi": [34],
            "supportedRomFamily": ["hyperos1"],
            "steps": [
                {
                    "id": "s1",
                    "type": "collect_diagnostics",
                    "description": "x",
                    "timeoutSeconds": 10,
                    "evidenceFiles": ["preflight.json"],
                },
            ],
        })
        rc, _, _, out_dir = self._run(plan, scenario="ok")
        self.assertEqual(rc, 0)
        report = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
        self.assertTrue(report["simulation"])
        manifest = json.loads((out_dir / "manifest.json").read_text(encoding="utf-8"))
        self.assertTrue(manifest["simulation"])
        paths = [f["path"] for f in manifest["files"]]
        self.assertIn("preflight.json", paths)
        self.assertEqual(paths, sorted(paths))
        for f in manifest["files"]:
            self.assertEqual(len(f["sha256"]), 64)

    def _write_plan(self, data: dict) -> Path:
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        p = Path(td.name) / "plan.json"
        p.write_text(json.dumps(data, indent=2), encoding="utf-8")
        return p


if __name__ == "__main__":
    unittest.main()
