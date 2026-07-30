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


class FakeAdbTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temps: list[tempfile.TemporaryDirectory] = []

    def tearDown(self) -> None:
        for td in self._temps:
            td.cleanup()

    def _make_wrapper(self, tmp: Path) -> Path:
        """Create a platform-specific shim so the fake_adb.py can be used as --adb."""
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

    def _run(self, state: str, extra: list[str] | None = None, serial: str | None = None) -> tuple[int, str, str, Path]:
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        tmp_path = Path(td.name)
        wrapper = self._make_wrapper(tmp_path)
        out_dir = tmp_path / "out"
        env = os.environ.copy()
        env["ADB_FAKE_STATE"] = state
        args = [sys.executable, str(SCRIPT), "preflight", "--adb", str(wrapper), "--timeout", "5", "--output", str(out_dir)]
        if serial:
            args += ["--serial", serial]
        if extra:
            args += extra
        proc = subprocess.run(args, capture_output=True, text=True, env=env, timeout=60)
        return proc.returncode, proc.stdout, proc.stderr, out_dir

    def _assert_ok(self, rc, state, out_dir, stdout, stderr):
        self.assertEqual(rc, 0, f"state={state} rc={rc} stdout={stdout!r} stderr={stderr!r}")

    def test_preflight_basic(self):
        rc, stdout, stderr, out_dir = self._run("ok")
        self._assert_ok(rc, "ok", out_dir, stdout, stderr)
        self.assertTrue((out_dir / "preflight.json").is_file())
        data = json.loads((out_dir / "preflight.json").read_text(encoding="utf-8"))
        self.assertEqual(data["androidApi"], 34)
        self.assertEqual(data["model"], "FakePhone")
        self.assertEqual(data["module"]["versionName"], "1.0.0")
        self.assertEqual(data["module"]["versionCode"], 1000)
        self.assertNotIn("FAKE001", data["deviceId"])

    def test_no_devices(self):
        rc, _, _, _ = self._run("no_devices")
        self.assertEqual(rc, 2)

    def test_unauthorized(self):
        rc, _, _, _ = self._run("unauthorized", serial="FAKE001")
        self.assertEqual(rc, 2)

    def test_offline(self):
        rc, _, _, _ = self._run("offline", serial="FAKE001")
        self.assertEqual(rc, 2)

    def test_multi_requires_serial(self):
        rc, _, _, _ = self._run("multi")
        self.assertEqual(rc, 2)

    def test_multi_with_serial(self):
        rc, stdout, stderr, out_dir = self._run("multi", serial="FAKE001")
        self._assert_ok(rc, "multi", out_dir, stdout, stderr)
        data = json.loads((out_dir / "preflight.json").read_text(encoding="utf-8"))
        self.assertEqual(data["model"], "FakePhone")

    def test_module_not_installed(self):
        rc, stdout, stderr, out_dir = self._run("no_module")
        self._assert_ok(rc, "no_module", out_dir, stdout, stderr)
        data = json.loads((out_dir / "preflight.json").read_text(encoding="utf-8"))
        self.assertFalse(data["module"]["installed"])

    def test_validate_plan_ok(self):
        plan = REPO_ROOT / "adb-regression" / "a14-smoke.json"
        if not plan.is_file():
            self.skipTest("a14-smoke.json not yet provided")
        proc = subprocess.run(
            [sys.executable, str(SCRIPT), "validate-plan", "--plan", str(plan)],
            capture_output=True,
            text=True,
            timeout=60,
        )
        self.assertEqual(proc.returncode, 0)


if __name__ == "__main__":
    unittest.main()
