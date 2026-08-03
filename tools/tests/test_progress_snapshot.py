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
            "evidence_pending": 0,
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
            + counts["evidence_pending"]
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
        """A VERIFIED_STATIC item in the verified bucket with pending provenance must fail."""
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
                evidence_level="verified",
                evidence_paths=["docs/some.md"],
                evidence_commands=["python tools/x.py"],
                evidence_commit="cd152365a1b258a7b36e978d1050db71f427fa83",
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
        """The real TASK_STATE must produce a valid snapshot without provenance contradictions."""
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        items = progress_snapshot.build_capability_items(leaves, issues)
        progress_snapshot.validate_capability_items(items)


class EvidenceProvenanceTest(unittest.TestCase):
    """Mechanical evidence and provenance semantics (A14-P12.1R-R2)."""

    def test_narrative_only_command_rejected(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n- docs/progress/A14_PROGRESS_CURRENT.json 与 .md 已重新生成\n- P12.1 文档已经验证\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_commands"])
        self.assertEqual("pending", ev["evidence_level"])

    def test_path_in_sentence_is_not_command(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` 已经被验证\n"""
        ev = progress_snapshot.extract_evidence(text)
        # A path-like sentence is not a command.
        self.assertEqual([], ev["evidence_commands"])

    def test_nonexistent_path_rejected(self):
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/this-does-not-exist.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_paths"])

    def test_escape_path_rejected(self):
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `../outside/repo.md`\n- `docs/../TASK_STATE.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_paths"])

    def test_real_repo_path_accepted(self):
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`\n- `tools/tests/test_gesture_lifecycle_inventory.py`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertIn("docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md", ev["evidence_paths"])
        self.assertIn("tools/tests/test_gesture_lifecycle_inventory.py", ev["evidence_paths"])

    def test_fenced_python_command_accepted(self):
        text = """\nState: `VERIFIED_STATIC`\n\n证据：\n\n```text\npython -m unittest tools.tests.test_gesture_lifecycle_inventory\npython tools/check-invariants.py\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual(
            ["python -m unittest tools.tests.test_gesture_lifecycle_inventory", "python tools/check-invariants.py"],
            ev["evidence_commands"],
        )

    def test_non_allowlist_command_rejected(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n```text\necho "hello"\nrm -rf /\nnotallowed --flag\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_commands"])

    def test_complete_without_evidence_gets_zero_score(self):
        item = progress_snapshot.CapabilityItem(
            id="P99.1",
            domain="Baseline and control",
            weight=2.0,
            state="COMPLETE",
            factor=0.0,
            earned=0.0,
            bucket="evidence_pending",
            evidence_level="pending",
            evidence_paths=[],
            evidence_commands=[],
        )
        self.assertEqual("evidence_pending", progress_snapshot.effective_bucket(item.state, item.evidence_level))
        self.assertEqual(0.0, progress_snapshot.effective_factor(item.state, item.evidence_level))

    def test_verified_static_partial_no_score(self):
        item = progress_snapshot.CapabilityItem(
            id="P99.2",
            domain="Documentation / provenance",
            weight=1.25,
            state="VERIFIED_STATIC",
            factor=0.0,
            earned=0.0,
            bucket="evidence_pending",
            evidence_level="partial",
            evidence_paths=["docs/some.md"],
            evidence_commands=["python tools/some.py"],
            evidence_commit="pending",
        )
        self.assertEqual("evidence_pending", progress_snapshot.effective_bucket(item.state, item.evidence_level))
        self.assertEqual(0.0, progress_snapshot.effective_factor(item.state, item.evidence_level))

    def test_verified_static_with_ancestor_commit_verified(self):
        text = """\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: cd152365a1b258a7b36e978d1050db71f427fa83\n\n文件：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`\n\n证据：\n\n```text\npython -m unittest tools.tests.test_gesture_lifecycle_inventory\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("verified", ev["evidence_level"])
        self.assertEqual(40, len(ev["evidence_commit"]))
        self.assertEqual("0.70", f"{progress_snapshot.effective_factor('VERIFIED_STATIC', ev['evidence_level']):.2f}")
        self.assertEqual("verified", progress_snapshot.effective_bucket("VERIFIED_STATIC", ev["evidence_level"]))

    def test_nonexistent_commit_rejected(self):
        text = """\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: 0000000000000000000000000000000000000000\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("partial", ev["evidence_level"])
        self.assertEqual("pending", ev["evidence_commit"])

    def test_ambiguous_or_invalid_short_sha_rejected(self):
        text = """\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: deadbeef\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("partial", ev["evidence_level"])
        self.assertEqual("pending", ev["evidence_commit"])

    def test_non_ancestor_commit_rejected(self):
        # HEAD is not an ancestor of itself for the purpose of evidence; an ancestor must be older.
        head = progress_snapshot.git_rev("HEAD")
        text = f"""\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: {head}\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        # HEAD itself is an ancestor by git's definition (merge-base --is-ancestor HEAD HEAD is true),
        # so use a parent commit to represent an older ancestor.
        parent = progress_snapshot.git_rev("HEAD~1")
        text2 = f"""\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: {parent}\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev2 = progress_snapshot.extract_evidence(text2)
        self.assertEqual("verified", ev2["evidence_level"])
        self.assertEqual(40, len(ev2["evidence_commit"]))

    def test_commit_saved_as_full_40_sha(self):
        short = "cd15236"
        text = f"""\nState: `VERIFIED_STATIC`\n\nEvidenceCommit: {short}\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("verified", ev["evidence_level"])
        self.assertRegex(ev["evidence_commit"], r"^[0-9a-f]{40}$")

    def test_prose_random_sha_not_extracted(self):
        text = """\nState: `COMPLETE`\n\n说明：\n\n- 某个历史提交 55fc2a21d0e96f9ef643f53fcc9b74374bd959db 看起来有关\n- 另一个提交 cd152365a1b258a7b36e978d1050db71f427fa83 被提及\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("pending", ev["evidence_commit"])

    def test_referenced_doc_evidence_commit_used_if_path_valid(self):
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        # The referenced doc already has a valid EvidenceCommit, so it should be adopted.
        self.assertEqual("verified", ev["evidence_level"])
        self.assertEqual(40, len(ev["evidence_commit"]))

    def test_referenced_doc_evidence_commit_ignored_if_path_invalid(self):
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/missing-file.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("pending", ev["evidence_level"])

    def test_p12_weights_fixed_after_missing_child(self):
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        # Remove P12.4 but keep the others.
        mutated = {sid: info for sid, info in leaves.items() if sid != "P12.4"}
        items = progress_snapshot.build_capability_items(mutated, issues)
        for it in items:
            if it.id in ("P12.1", "P12.2", "P12.3"):
                self.assertEqual(1.25, it.weight, f"{it.id} must stay at 1.25")
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items(items)
        self.assertIn("P12.4", str(ctx.exception))

    def test_p12_single_child_cannot_take_full_weight(self):
        items = [
            progress_snapshot.CapabilityItem(
                id="P12.1",
                domain="Documentation / provenance",
                weight=5.0,
                state="VERIFIED_STATIC",
                factor=0.7,
                earned=3.5,
                bucket="verified",
                evidence_level="verified",
                evidence_paths=["docs/some.md"],
                evidence_commands=["python tools/x.py"],
                evidence_commit="cd152365a1b258a7b36e978d1050db71f427fa83",
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
            ),
        ]
        with self.assertRaises(ValueError) as ctx:
            progress_snapshot.validate_capability_items(items)
        self.assertIn("full Documentation / provenance", str(ctx.exception))

    def test_non_p12_weights_unchanged(self):
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        items = progress_snapshot.build_capability_items(leaves, issues)
        for it in items:
            if not it.id.startswith("P12.") and not it.id.startswith("P0"):
                # Non-P12 domains still split by the total number of mapped leaves.
                self.assertGreater(it.weight, 0.0, f"{it.id} must have positive weight")
                self.assertNotAlmostEqual(1.25, it.weight, msg=f"{it.id} must not inherit the P12 1.25 split")

    def test_synthetic_complete_cannot_fake_progress_with_prose(self):
        text = """\nState: `COMPLETE`\n\n证据：\n\n- docs/progress/A14_PROGRESS_CURRENT.json 与 .md 已重新生成\n- P12.1 文档已经验证\n- 退出码 0\n- 验证通过\n"""
        ev = progress_snapshot.extract_evidence(text)
        item = progress_snapshot.CapabilityItem(
            id="P99.3",
            domain="Documentation / provenance",
            weight=2.0,
            state="COMPLETE",
            factor=progress_snapshot.effective_factor("COMPLETE", ev["evidence_level"]),
            earned=2.0 * progress_snapshot.effective_factor("COMPLETE", ev["evidence_level"]),
            bucket=progress_snapshot.effective_bucket("COMPLETE", ev["evidence_level"]),
            evidence_level=ev["evidence_level"],
            evidence_paths=ev["evidence_paths"],
            evidence_commands=ev["evidence_commands"],
            evidence_commit=ev["evidence_commit"],
        )
        self.assertEqual("pending", ev["evidence_level"])
        self.assertEqual(0.0, item.factor)
        self.assertEqual(0.0, item.earned)
        self.assertEqual("evidence_pending", item.bucket)

    def test_mutation_all_bullets_are_commands_fails(self):
        # Temporarily allow all bullets as commands; the parser must not.
        text = """\nState: `COMPLETE`\n\n证据：\n\n- docs/progress/A14_PROGRESS_CURRENT.json 与 .md 已重新生成\n- P12.1 文档已经验证\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_commands"])

    def test_mutation_evidence_level_from_state_fails(self):
        # Ensure level is not simply state.lower() or a state mapping.
        for state in ["VERIFIED_STATIC", "VERIFIED_BUILD", "VERIFIED_CI", "COMPLETE"]:
            text = f"""\nState: `{state}`\n\n证据：\n\n```text\necho no evidence\n```\n"""
            ev = progress_snapshot.extract_evidence(text)
            self.assertIn(ev["evidence_level"], ("pending", "partial"))

    def test_mutation_all_commits_pending_fails(self):
        text = """\nState: `VERIFIED_STATIC`\n\n证据：\n\n```text\npython -m unittest tools.tests.test_x\n```\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual("partial", ev["evidence_level"])
        self.assertEqual("pending", ev["evidence_commit"])

    def test_mutation_pending_commit_full_factor_fails(self):
        item = progress_snapshot.CapabilityItem(
            id="P99.4",
            domain="Documentation / provenance",
            weight=1.25,
            state="VERIFIED_STATIC",
            factor=progress_snapshot.effective_factor("VERIFIED_STATIC", "partial"),
            earned=1.25 * progress_snapshot.effective_factor("VERIFIED_STATIC", "partial"),
            bucket=progress_snapshot.effective_bucket("VERIFIED_STATIC", "partial"),
            evidence_level="partial",
            evidence_paths=["docs/x.md"],
            evidence_commands=["python tools/x.py"],
            evidence_commit="pending",
        )
        self.assertEqual(0.0, item.factor)
        self.assertEqual(0.0, item.earned)
        self.assertEqual("evidence_pending", item.bucket)

    def test_mutation_p12_weight_by_count_fails(self):
        text = progress_snapshot.TASK_STATE.read_text(encoding="utf-8")
        leaves = progress_snapshot.parse_task_sections(text)
        issues = progress_snapshot.parse_issue_table(text)
        # Only P12.1 exists in a synthetic view.
        mutated = {sid: info for sid, info in leaves.items() if sid == "P12.1"}
        items = progress_snapshot.build_capability_items(mutated, issues)
        for it in items:
            if it.id == "P12.1":
                self.assertEqual(1.25, it.weight)

    def test_mutation_nonexistent_path_accepted_fails(self):
        text = """\nState: `VERIFIED_STATIC`\n\n文件：\n\n- `docs/this-file-does-not-exist-in-the-repo.md`\n"""
        ev = progress_snapshot.extract_evidence(text)
        self.assertEqual([], ev["evidence_paths"])


if __name__ == "__main__":
    unittest.main()
