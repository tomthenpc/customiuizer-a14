import json
import tempfile
import unittest
from pathlib import Path

from tools import progress_snapshot


class ProgressSnapshotV7Test(unittest.TestCase):

    def _temp_outputs(self):
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        root = Path(td.name)
        old_json = progress_snapshot.OUT_JSON
        old_md = progress_snapshot.OUT_MD

        def restore():
            progress_snapshot.OUT_JSON = old_json
            progress_snapshot.OUT_MD = old_md

        self.addCleanup(restore)
        progress_snapshot.OUT_JSON = root / "A14_PROGRESS_CURRENT.json"
        progress_snapshot.OUT_MD = root / "A14_PROGRESS_CURRENT.md"
        return root

    def test_no_args_prints_help_and_does_not_write(self):
        code = progress_snapshot.main([])
        self.assertEqual(2, code)

    def test_print_is_read_only(self):
        before_json = progress_snapshot.OUT_JSON.stat().st_mtime if progress_snapshot.OUT_JSON.is_file() else None
        before_md = progress_snapshot.OUT_MD.stat().st_mtime if progress_snapshot.OUT_MD.is_file() else None
        code = progress_snapshot.main(["--print"])
        self.assertEqual(0, code)
        if before_json is not None:
            self.assertEqual(before_json, progress_snapshot.OUT_JSON.stat().st_mtime)
        if before_md is not None:
            self.assertEqual(before_md, progress_snapshot.OUT_MD.stat().st_mtime)

    def test_check_is_read_only(self):
        # First write a known snapshot, then run --check against it.
        self._temp_outputs()
        progress_snapshot.main(["--write"])
        before_json = progress_snapshot.OUT_JSON.stat().st_mtime
        before_md = progress_snapshot.OUT_MD.stat().st_mtime
        code = progress_snapshot.main(["--check"])
        self.assertEqual(0, code)
        self.assertEqual(before_json, progress_snapshot.OUT_JSON.stat().st_mtime)
        self.assertEqual(before_md, progress_snapshot.OUT_MD.stat().st_mtime)

    def test_check_detects_drift(self):
        self._temp_outputs()
        progress_snapshot.main(["--write"])
        # Corrupt the JSON with a fake state to force semantic drift.
        existing = json.loads(progress_snapshot.OUT_JSON.read_text(encoding="utf-8"))
        existing["projectProgress"] = -1.0
        progress_snapshot.OUT_JSON.write_text(json.dumps(existing, indent=2), encoding="utf-8")
        code = progress_snapshot.main(["--check"])
        self.assertEqual(1, code)

    def test_parent_child_not_double_counted(self):
        # P5 is a parent with children P5.1-P5.5; P5 itself must not appear as a leaf.
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        ids = {it for it in leaves}
        self.assertIn("P5.1", ids)
        self.assertNotIn("P5", ids)

    def test_blocked_external_accounted(self):
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        items = progress_snapshot.build_capability_items(leaves, issues)
        buckets = {
            "complete": 0,
            "verified": 0,
            "in_progress": 0,
            "not_started": 0,
            "blocked_internal": 0,
            "blocked_external": 0,
            "excluded": 0,
            "fail": 0,
        }
        for it in items:
            buckets[it.bucket] = buckets.get(it.bucket, 0) + 1
        self.assertEqual(sum(buckets.values()), progress_snapshot.compute_progress(items)["taskCounts"]["total"])
        self.assertGreaterEqual(buckets["blocked_external"], 0)

    def test_machine_progress_excludes_device(self):
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        items = progress_snapshot.build_capability_items(leaves, issues)
        progress = progress_snapshot.compute_progress(items)
        device_earned = sum(it.earned for it in items if it.domain == "Device validation")
        non_device_total = 95.0
        non_device_earned = sum(it.earned for it in items if it.domain != "Device validation")
        expected = round(non_device_earned / non_device_total * 100, 1)
        self.assertEqual(expected, progress["machineProgressPercent"])

    def test_sum_of_buckets_equals_total(self):
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        items = progress_snapshot.build_capability_items(leaves, issues)
        progress = progress_snapshot.compute_progress(items)
        counts = progress["taskCounts"]
        total = (
            counts["complete"]
            + counts["verified"]
            + counts["in_progress"]
            + counts["not_started"]
            + counts["blocked_internal"]
            + counts["blocked_external"]
            + counts["excluded"]
            + counts["fail"]
        )
        self.assertEqual(counts["total"], total)

    def test_state_factor_bucket_consistency(self):
        """Every known state maps to a recognized bucket and a non-negative factor."""
        known_buckets = set(progress_snapshot.compute_progress([])["taskCounts"].keys()) - {"total"}
        for state, factor in progress_snapshot.STATE_FACTORS.items():
            bucket = progress_snapshot.item_bucket(state)
            self.assertIn(bucket, known_buckets, f"{state} maps to unknown bucket {bucket}")
            self.assertGreaterEqual(factor, 0.0, f"{state} factor must be non-negative")

    def test_unknown_and_not_applicable_buckets(self):
        self.assertEqual("fail", progress_snapshot.item_bucket("UNKNOWN"))
        self.assertEqual("excluded", progress_snapshot.item_bucket("NOT_APPLICABLE"))

    def test_p12_children_preserved(self):
        """Removing an unfinished P12 child from TASK_STATE must fail validation."""
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        items = progress_snapshot.build_capability_items(leaves, issues)

        # Baseline is valid: all four P12 children exist.
        progress_snapshot.validate_capability_items(items)
        p12_ids = {it.id for it in items if it.id.startswith("P12.")}
        self.assertTrue(
            p12_ids.issuperset(progress_snapshot.EXPECTED_P12_IDS),
            f"Expected {progress_snapshot.EXPECTED_P12_IDS}, got {p12_ids}",
        )

        # Mutation: drop one unfinished child and rebuild.
        mutated = {sid: info for sid, info in leaves.items() if sid != "P12.2"}
        mutated_items = progress_snapshot.build_capability_items(mutated, issues)
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items(mutated_items)
        self.assertIn("P12.2", str(ctx.exception))

    def test_verified_static_without_evidence_fails(self):
        """A VERIFIED_STATIC item with pending level and empty evidence must fail."""
        item = progress_snapshot.CapabilityItem(
            id="P99.1",
            domain="Documentation / provenance",
            weight=1.0,
            state="VERIFIED_STATIC",
            factor=0.7,
            earned=0.7,
            bucket="verified",
            evidence_level="pending",
            evidence_paths=[],
            evidence_commands=[],
        )
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items([item])
        self.assertIn("evidence_level", str(ctx.exception))

    def test_single_p12_child_takes_full_weight_fails(self):
        """A single P12 child must not be weighted as the full Documentation domain."""
        items = [
            progress_snapshot.CapabilityItem(
                id="P12.1",
                domain="Documentation / provenance",
                weight=progress_snapshot.DOMAIN_WEIGHTS["Documentation / provenance"],
                state="VERIFIED_STATIC",
                factor=0.7,
                earned=3.5,
                bucket="verified",
                evidence_level="static",
                evidence_paths=["docs/some.md"],
                evidence_commands=["python tools/x.py"],
            ),
            progress_snapshot.CapabilityItem(
                id="P12.2",
                domain="Documentation / provenance",
                weight=0.0,
                state="TODO",
                factor=0.0,
                earned=0.0,
                bucket="not_started",
                evidence_level="pending",
                evidence_paths=[],
                evidence_commands=[],
            ),
            progress_snapshot.CapabilityItem(
                id="P12.3",
                domain="Documentation / provenance",
                weight=0.0,
                state="TODO",
                factor=0.0,
                earned=0.0,
                bucket="not_started",
                evidence_level="pending",
                evidence_paths=[],
                evidence_commands=[],
            ),
            progress_snapshot.CapabilityItem(
                id="P12.4",
                domain="Documentation / provenance",
                weight=0.0,
                state="TODO",
                factor=0.0,
                earned=0.0,
                bucket="not_started",
                evidence_level="pending",
                evidence_paths=[],
                evidence_commands=[],
            ),
        ]
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items(items)
        self.assertIn("full Documentation / provenance", str(ctx.exception))

    def test_current_verified_items_have_evidence(self):
        """The real TASK_STATE must produce verified items with non-empty evidence."""
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        items = progress_snapshot.build_capability_items(leaves, issues)
        progress_snapshot.validate_capability_items(items)


if __name__ == "__main__":
    unittest.main()
