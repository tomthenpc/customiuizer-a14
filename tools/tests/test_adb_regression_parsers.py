"""Tests for ADB regression parsers and redaction."""

import os
import platform
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools"))

import adb_regression.parsers as parsers
import adb_regression.redaction as redaction
import adb_regression.steps as steps


class ParsersTest(unittest.TestCase):
    def test_module_markers_present(self) -> None:
        text = (
            "06-01 12:00:00.000  1234  1234 I CustoMIUIzer: "
            "CustoMIUIzer 14.13.8 (14130800) loaded in system_server\n"
            "06-01 12:00:01.000  2345  2345 I CustoMIUIzer: "
            "CustoMIUIzer 14.13.8 (14130800) loaded in com.android.systemui\n"
        )
        markers = parsers.parse_module_markers(text)
        self.assertEqual(len(markers), 2)
        processes = {m["process"] for m in markers}
        self.assertIn("system_server", processes)
        self.assertIn("com.android.systemui", processes)
        for m in markers:
            self.assertEqual(m["source"], parsers.LOG_SOURCE_ADB)
            self.assertTrue(m["timestamp"])
            self.assertEqual(m["version"], "14.13.8")
            self.assertEqual(m["code"], "14130800")
            self.assertEqual(m["load"], f"{m['version']} ({m['code']})")
            self.assertEqual(m["rawStage"], "")
            self.assertEqual(m["normalizedStage"], "")

    def test_module_markers_absent(self) -> None:
        self.assertEqual(parsers.parse_module_markers("nothing here"), [])

    def test_hook_summary_multi_stage(self) -> None:
        text = (
            "[HookSummary] process=com.android.systemui stage=init installed=42 "
            "classMissing=0 memberMissing=0 failed=0 silentSkipped=0 "
            "dexkitFailed=0 dexkitNoMatch=0 prefsUnavailable=0\n"
            "[HookSummary] process=com.android.systemui stage=ready installed=55 "
            "classMissing=1 memberMissing=2 failed=0 silentSkipped=0 "
            "dexkitFailed=0 dexkitNoMatch=0 prefsUnavailable=0\n"
        )
        records = parsers.parse_hook_summary(text)
        self.assertEqual(len(records), 2)
        self.assertEqual(records[0]["process"], "com.android.systemui")
        self.assertEqual(records[0]["stage"], "init")
        self.assertEqual(records[0]["rawStage"], "init")
        self.assertEqual(records[0]["normalizedStage"], "init")
        self.assertEqual(records[0]["installed"], 42)
        self.assertEqual(records[0]["source"], parsers.LOG_SOURCE_ADB)
        totals = parsers.hook_summary_totals(records)
        self.assertEqual(totals["installed"], 97)
        self.assertEqual(totals["classMissing"], 1)
        self.assertEqual(totals["memberMissing"], 2)

    def test_hook_summary_failed_prefs_dexkit(self) -> None:
        text = (
            "[HookSummary] process=system_server stage=init installed=1 "
            "classMissing=0 memberMissing=0 failed=1 silentSkipped=0 "
            "dexkitFailed=1 dexkitNoMatch=1 prefsUnavailable=1\n"
        )
        records = parsers.parse_hook_summary(text)
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["failed"], 1)
        self.assertEqual(records[0]["dexkitFailed"], 1)
        self.assertEqual(records[0]["dexkitNoMatch"], 1)
        self.assertEqual(records[0]["prefsUnavailable"], 1)

    def test_hook_summary_class_member_missing(self) -> None:
        text = (
            "[HookSummary] process=com.android.systemui stage=init installed=10 "
            "classMissing=2 memberMissing=3 failed=0 silentSkipped=0 "
            "dexkitFailed=0 dexkitNoMatch=0 prefsUnavailable=0\n"
        )
        records = parsers.parse_hook_summary(text)
        self.assertEqual(records[0]["classMissing"], 2)
        self.assertEqual(records[0]["memberMissing"], 3)

    def test_hook_summary_malformed(self) -> None:
        text = (
            "[HookSummary] process=com.android.systemui stage=init installed=100 "
            "classMissing=zero memberMissing=0 failed=0\n"
        )
        records = parsers.parse_hook_summary(text)
        self.assertEqual(records, [])

    def test_crash_detection(self) -> None:
        text = (
            "06-01 12:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main\n"
            "06-01 12:00:01.000  1234  1234 E Watchdog: WATCHDOG: Killing system_server\n"
            "06-01 12:00:02.000  1234  1234 E system_server: system_server crash\n"
        )
        crashes = parsers.parse_crash_markers(text)
        self.assertEqual(len(crashes), 3)
        for c in crashes:
            self.assertEqual(c["source"], parsers.LOG_SOURCE_ADB)
            self.assertTrue(c["timestamp"])
            self.assertIn("marker", c)
        markers = {c["marker"] for c in crashes}
        self.assertIn("FATAL EXCEPTION", markers)
        self.assertIn("WATCHDOG", markers)
        self.assertIn("system_server crash", markers)

    def test_pid_compare_unchanged(self) -> None:
        comp = parsers.compare_pids(
            {"system_server": [1234]},
            {"system_server": [1234]},
        )
        self.assertFalse(comp["anyRestarted"])
        self.assertFalse(comp["processes"]["system_server"]["changed"])

    def test_pid_compare_restarted(self) -> None:
        comp = parsers.compare_pids(
            {"system_server": [1234]},
            {"system_server": [5678]},
        )
        self.assertTrue(comp["anyRestarted"])
        self.assertTrue(comp["processes"]["system_server"]["restarted"])

    def test_redaction_serial(self) -> None:
        text = "device serial FAKE001 and another token=abc12345"
        out = redaction.redact(text, serial="FAKE001")
        self.assertNotIn("FAKE001", out)
        self.assertNotIn("abc12345", out)

    def test_logcat_timeout_step(self) -> None:
        import tempfile
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        ctx = {
            "run_adb": lambda _args, _timeout: (-1, "", "timeout after 2s", 2.0),
            "out_dir": Path(td.name),
            "commands": [],
            "serial": "FAKE001",
        }
        result = steps.execute(ctx, {
            "id": "t1",
            "type": "logcat_assert",
            "description": "x",
            "timeoutSeconds": 2,
            "expected": {"patterns": ["x"]},
        })
        self.assertEqual(result["status"], "ERROR")
        self.assertIn("timed out", result["message"].lower())

    def test_lsposed_module_marker(self) -> None:
        text = (
            "[Pengeek] CustoMIUIzer r14.13.8 (186) loaded in com.android.systemui\n"
        )
        markers = parsers.parse_module_markers(text, source=parsers.LOG_SOURCE_LSP)
        self.assertEqual(len(markers), 1)
        self.assertEqual(markers[0]["process"], "com.android.systemui")
        self.assertEqual(markers[0]["version"], "r14.13.8")
        self.assertEqual(markers[0]["code"], "186")
        self.assertEqual(markers[0]["source"], parsers.LOG_SOURCE_LSP)
        self.assertIsNone(markers[0]["timestamp"])

    def test_lsposed_module_marker_with_timestamp(self) -> None:
        text = (
            "06-01 12:00:00.000  1234  1234 I Pengeek: "
            "[Pengeek] CustoMIUIzer r14.13.8 (186) loaded in com.android.systemui\n"
        )
        markers = parsers.parse_module_markers(text, source=parsers.LOG_SOURCE_LSP)
        self.assertEqual(len(markers), 1)
        self.assertEqual(markers[0]["process"], "com.android.systemui")
        self.assertEqual(markers[0]["source"], parsers.LOG_SOURCE_LSP)
        self.assertEqual(markers[0]["timestamp"], "06-01 12:00:00.000")

    def test_lsposed_hook_summary_real(self) -> None:
        text = (
            "[Pengeek] CustoMIUIzer HookSummary "
            "stage=onPackageReady process=com.android.systemui installed=43 "
            "classMissing=0 memberMissing=0 failed=0 silentSkipped=0 "
            "dexkitFailed=0 dexkitNoMatch=0 prefsUnavailable=0\n"
        )
        records = parsers.parse_hook_summary(text, source=parsers.LOG_SOURCE_LSP)
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["process"], "com.android.systemui")
        self.assertEqual(records[0]["stage"], "package-ready")
        self.assertEqual(records[0]["rawStage"], "onPackageReady")
        self.assertEqual(records[0]["normalizedStage"], "package-ready")
        self.assertEqual(records[0]["installed"], 43)
        self.assertEqual(records[0]["source"], parsers.LOG_SOURCE_LSP)

    def test_lsposed_hook_summary_reordered(self) -> None:
        text = (
            "[Pengeek] CustoMIUIzer HookSummary "
            "installed=12 prefsUnavailable=1 process=system_server "
            "failed=2 classMissing=3 silentSkipped=4 memberMissing=5 "
            "dexkitFailed=6 dexkitNoMatch=7 stage=init\n"
        )
        records = parsers.parse_hook_summary(text, source=parsers.LOG_SOURCE_LSP)
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["process"], "system_server")
        self.assertEqual(records[0]["stage"], "init")
        self.assertEqual(records[0]["rawStage"], "init")
        self.assertEqual(records[0]["installed"], 12)
        self.assertEqual(records[0]["classMissing"], 3)
        self.assertEqual(records[0]["memberMissing"], 5)
        self.assertEqual(records[0]["failed"], 2)
        self.assertEqual(records[0]["silentSkipped"], 4)
        self.assertEqual(records[0]["dexkitFailed"], 6)
        self.assertEqual(records[0]["dexkitNoMatch"], 7)
        self.assertEqual(records[0]["prefsUnavailable"], 1)
        self.assertEqual(records[0]["source"], parsers.LOG_SOURCE_LSP)

    def test_stage_normalization(self) -> None:
        text = (
            "[HookSummary] process=com.android.systemui stage=onPackageReady installed=1\n"
            "[HookSummary] process=system_server stage=onSystemServerStarting installed=2\n"
            "[HookSummary] process=com.miui.home stage=post-init installed=3\n"
            "[HookSummary] process=com.miui.home stage=post-attach installed=4\n"
        )
        records = parsers.parse_hook_summary(text)
        normalized = {r["stage"] for r in records}
        self.assertIn("package-ready", normalized)
        self.assertIn("system-server-finished", normalized)
        self.assertIn("post-init", normalized)
        self.assertIn("post-attach", normalized)


if __name__ == "__main__":
    unittest.main()
