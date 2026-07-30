"""Tests for device evidence proposal generation."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT = REPO_ROOT / "tools" / "adb-regression.py"
sys.path.insert(0, str(REPO_ROOT / "tools"))


class EvidenceProposeTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temps: list[tempfile.TemporaryDirectory] = []

    def tearDown(self) -> None:
        for td in self._temps:
            td.cleanup()

    def _make_report_dir(self) -> Path:
        td = tempfile.TemporaryDirectory()
        self._temps.append(td)
        return Path(td.name)

    def _report(
        self,
        report_dir: Path,
        simulation: bool = False,
        evidence_confidence: str = "VERIFIED",
        steps: list[dict] | None = None,
        extra: dict | None = None,
    ) -> Path:
        steps = steps or [
            {"id": "baseline-start", "type": "collect_diagnostics", "status": "PASS"},
            {"id": "module-load-markers", "type": "logcat_assert", "status": "PASS"},
        ]
        report: dict = {
            "schemaVersion": 1,
            "runId": "r-test",
            "planId": "a14-smoke",
            "planSha256": "abcd1234",
            "gitCommit": "deadbeef",
            "moduleVersion": "1.0.0-test",
            "androidApi": 34,
            "fingerprint": "Xiaomi/xxx/xxx:14/...",
            "romFamily": "hyperos1",
            "timestamp": "2025-01-01T00:00:00+00:00",
            "simulation": simulation,
            "deviceId": "hashed",
            "exitCode": 0,
            "summary": {"total": 2, "passed": 2, "failed": 0, "manualPending": 0, "skipped": 0, "errors": 0},
            "steps": steps,
            "evidenceConfidence": evidence_confidence,
            "selectedLogSource": "ADB_LOGCAT",
            "lsposedLogFile": "",
        }
        if extra:
            report.update(extra)
        (report_dir / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        (report_dir / "extra.log").write_text("some evidence\n", encoding="utf-8")
        return report_dir / "report.json"

    def test_simulation_rejected(self):
        from adb_regression import evidence

        report_dir = self._make_report_dir()
        report = self._report(report_dir, simulation=True)
        output = report_dir / "proposal.json"
        rc = evidence.propose(report, output)
        self.assertEqual(rc, 2, f"simulation should be rejected with rc=2")
        self.assertTrue(output.is_file())
        data = json.loads(output.read_text(encoding="utf-8"))
        self.assertTrue(data.get("rejected"))
        self.assertEqual(data.get("rejectionReason"), "SIMULATION_ONLY")
        self.assertEqual(data.get("evidenceConfidence"), "SIMULATION_ONLY")
        self.assertEqual(data.get("proposals"), [])

    def test_real_report_has_ten_plus_entries(self):
        from adb_regression import evidence

        report_dir = self._make_report_dir()
        report = self._report(report_dir)
        output = report_dir / "proposal.json"
        rc = evidence.propose(report, output)
        self.assertEqual(rc, 0, f"real report should succeed")
        self.assertTrue(output.is_file())
        data = json.loads(output.read_text(encoding="utf-8"))
        proposals = data.get("proposals", [])
        self.assertGreaterEqual(len(proposals), 10, f"expected at least 10 proposals, got {len(proposals)}")

    def test_reviewer_status_pending(self):
        from adb_regression import evidence

        report_dir = self._make_report_dir()
        report = self._report(report_dir)
        output = report_dir / "proposal.json"
        evidence.propose(report, output)
        data = json.loads(output.read_text(encoding="utf-8"))
        for p in data["proposals"]:
            self.assertEqual(p["reviewerStatus"], "PENDING_REVIEW")

    def test_device_verified_not_written(self):
        from adb_regression import evidence

        report_dir = self._make_report_dir()
        report = self._report(report_dir)
        output = report_dir / "proposal.json"
        evidence.propose(report, output)
        data = json.loads(output.read_text(encoding="utf-8"))
        for p in data["proposals"]:
            self.assertNotEqual(p["result"], "DEVICE_VERIFIED")
            self.assertNotEqual(p["evidenceConfidence"], "DEVICE_VERIFIED")
            self.assertNotEqual(p["reviewerStatus"], "DEVICE_VERIFIED")

    def test_unverified_log(self):
        from adb_regression import evidence

        report_dir = self._make_report_dir()
        report = self._report(report_dir, evidence_confidence="UNVERIFIED")
        output = report_dir / "proposal.json"
        evidence.propose(report, output)
        data = json.loads(output.read_text(encoding="utf-8"))
        for p in data["proposals"]:
            self.assertEqual(p["evidenceConfidence"], "UNVERIFIED")
            self.assertNotEqual(p["result"], "DEVICE_VERIFIED")

    def test_skipped_not_upgraded(self):
        from adb_regression import evidence

        report_dir = self._make_report_dir()
        report = self._report(
            report_dir,
            steps=[
                {"id": "s1", "type": "collect_diagnostics", "status": "PASS"},
                {"id": "s2", "type": "manual_checkpoint", "status": "SKIPPED"},
            ],
        )
        output = report_dir / "proposal.json"
        evidence.propose(report, output)
        data = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(data["proposals"][0]["result"], "SKIPPED")
        self.assertNotEqual(data["proposals"][0]["evidenceConfidence"], "DEVICE_VERIFIED")

    def test_missing_report_schema_exit_two(self):
        from adb_regression import evidence

        report_dir = self._make_report_dir()
        bad = report_dir / "report.json"
        bad.write_text("{\"planId\": \"x\"}", encoding="utf-8")
        output = report_dir / "proposal.json"
        rc = evidence.propose(bad, output)
        self.assertEqual(rc, 2)

    def test_cli_wiring(self):
        report_dir = self._make_report_dir()
        report = self._report(report_dir)
        output = report_dir / "proposal.json"
        proc = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "propose-evidence",
                "--report",
                str(report),
                "--output",
                str(output),
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )
        self.assertEqual(proc.returncode, 0, f"stderr: {proc.stderr}")
        data = json.loads(output.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(data["proposals"]), 10)


if __name__ == "__main__":
    unittest.main()
