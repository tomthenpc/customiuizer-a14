#!/usr/bin/env python3
"""Mechanical validation for docs/A14_GESTURE_EVENT_CONTRACT.md.

This test is the evidence that the gesture event contract is faithful to the
source tree at the declared EvidenceCommit.
"""

from __future__ import annotations

import re
import subprocess
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DOC = REPO_ROOT / "docs" / "A14_GESTURE_EVENT_CONTRACT.md"

EXPECTED_REPOSITORY = "tomthenpc/customiuizer-a14"
EXPECTED_BRANCH = "devin/a14-rom-intelligence-audit"

REQUIRED_SECTIONS = [
    "1. Event Sources",
    "2. Event Data",
    "3. State Machine",
    "4. Event Ordering",
    "5. Ownership",
    "6. Side Effects",
    "7. Cleanup Contract",
    "8. Hot-Path Constraints",
    "9. Known Limitations",
    "10. Evidence",
]

KEY_SYMBOLS = {
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureEvent.kt": [
        "GestureEvent",
        "actionMasked",
        "ownerId",
        "downTime",
        "eventTime",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureState.kt": [
        "GestureState",
        "IDLE",
        "TRACKING",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt": [
        "GestureMachine",
        "prepare",
        "clear",
        "process",
        "dispatch",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureStateMachine.kt": [
        "GestureStateMachine",
        "handleMove",
        "TriggerDoubleTap",
        "TriggerLongPress",
        "BeginTracking",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt": [
        "PhysicalGestureArbiter",
        "Token",
        "tryAcquireOnDown",
        "release",
        "releaseOwner",
        "releaseAll",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt": [
        "GestureSideEffectGate",
        "OwnerFingerprint",
        "filter",
        "clearOwner",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt": [
        "ControlCenterGestureRuntimeHolder",
        "bind",
        "unbind",
        "activeRuntime",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSession.kt": [
        "GestureSession",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSnapshot.kt": [
        "GestureSnapshot",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureEntry.kt": [
        "GestureEntry",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureEffectExecutor.kt": [
        "GestureEffectExecutor",
        "execute",
    ],
    "docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md": [
        "DEVICE_LIFECYCLE_ENTRY_BLOCKED",
        "onDetachedFromWindow",
        "unbind",
    ],
}

LIFECYCLE_ACTIONS = ["prepare", "bind", "unbind", "clear", "release"]


def parse_metadata(text: str) -> dict[str, str] | None:
    for match in re.finditer(r"```text\s*\n(.*?)\n```", text, re.DOTALL):
        block = match.group(1)
        result: dict[str, str] = {}
        for line in block.splitlines():
            if ":" not in line:
                continue
            key, value = line.split(":", 1)
            result[key.strip()] = value.strip()
        if "DocumentKind" in result:
            return result
    return None


def git_run(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args, cwd=REPO_ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )


class GestureEventContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.doc_text = DOC.read_text(encoding="utf-8")
        self.metadata = parse_metadata(self.doc_text)
        self.assertIsNotNone(self.metadata, "Document metadata block not found")
        self.sha = self.metadata["EvidenceCommit"]

    def _validate_text(self, text: str, *, check_device_blocks: bool = True) -> None:
        """Run all contract checks on an arbitrary text."""
        meta = parse_metadata(text)
        self.assertIsNotNone(meta, "Metadata block missing")

        # 1. kind
        kind = meta.get("DocumentKind", "")
        self.assertEqual("CURRENT", kind)

        # 2. repo/branch
        self.assertEqual(EXPECTED_REPOSITORY, meta.get("Repository"))
        self.assertEqual(EXPECTED_BRANCH, meta.get("Branch"))

        # 3. evidence commit
        sha = meta["EvidenceCommit"]
        self.assertRegex(sha, r"^[0-9a-f]{40}$", "EvidenceCommit not a 40-char SHA")
        exists = git_run(["git", "cat-file", "-e", f"{sha}^{{commit}}"])
        self.assertEqual(0, exists.returncode, f"EvidenceCommit {sha} not a valid commit")
        ancestor = git_run(["git", "merge-base", "--is-ancestor", sha, "HEAD"])
        self.assertEqual(0, ancestor.returncode, f"EvidenceCommit {sha} not an ancestor of HEAD")

        # 4. sections
        for section in REQUIRED_SECTIONS:
            self.assertIn(section, text, f"Missing required section: {section}")

        # 5. lifecycle actions described
        lower = text.lower()
        for action in LIFECYCLE_ACTIONS:
            self.assertIn(action, lower, f"Lifecycle action {action!r} not described")

        # 6. recognized/cancelled side-effect constraints
        self.assertIn("一次手势最多", text, "Missing one-side-effect-per-gesture constraint")
        self.assertIn("cancelled", lower, "Missing cancelled/duplicate side effect constraint")

        # 7. device-lifecycle blocked
        if check_device_blocks:
            self.assertIn("DEVICE_LIFECYCLE_ENTRY_BLOCKED", text)

        # 8. P12.4 not completed
        self.assertIsNone(
            re.search(
                r"P12\.4[^\n]*(?:\bCOMPLETE\b|\bcompleted\b|\bdone\b|\bfinished\b)",
                text,
                re.IGNORECASE,
            ),
            "P12.4 must not be described as completed",
        )

        # 9. no A13
        self.assertNotIn("A13", text)
        self.assertNotIn("customiuizer-a13", text)

        # 10. no unverified device conclusions
        self.assertIsNone(
            re.search(r"(真实设备|real device).*(已验证|validated|verified|exercised)", text, re.I),
            "Document claims unverified device evidence is validated",
        )

        # 11. referenced source paths exist at EvidenceCommit
        paths = set()
        for match in re.finditer(r"`([^`\n]+)`", text):
            token = match.group(1).strip()
            if re.match(r"^(?:app|tools|scripts|docs|feature-semantics)/[-.\w/]+\.?(?:kt|java|md|py|ps1|json)?$", token):
                paths.add(token)
        for path in re.findall(r"\b(?:app|tools|scripts|docs|feature-semantics)/[-.\w/]+\.?(?:kt|java|md|py|ps1|json)?", text):
            paths.add(path)

        for path in sorted(paths):
            result = git_run(["git", "cat-file", "-e", f"{sha}:{path}"])
            self.assertEqual(
                0,
                result.returncode,
                f"Referenced source path {path} does not exist at EvidenceCommit {sha}",
            )

        # 12. key symbols exist in declared source paths
        for path, symbols in KEY_SYMBOLS.items():
            content = git_run(["git", "show", f"{sha}:{path}"])
            self.assertEqual(0, content.returncode, f"Could not read {path} at {sha}")
            for symbol in symbols:
                self.assertRegex(
                    content.stdout,
                    rf"\b{re.escape(symbol)}\b",
                    f"Symbol {symbol!r} not found in {path} at {sha}",
                )

    # ---- positive tests ----

    def test_01_document_exists(self) -> None:
        self.assertTrue(DOC.is_file())

    def test_02_document_kind(self) -> None:
        self.assertEqual("CURRENT", self.metadata.get("DocumentKind"))

    def test_03_repository_and_branch(self) -> None:
        self.assertEqual(EXPECTED_REPOSITORY, self.metadata.get("Repository"))
        self.assertEqual(EXPECTED_BRANCH, self.metadata.get("Branch"))

    def test_04_evidence_commit_valid_and_ancestor(self) -> None:
        self.assertRegex(self.sha, r"^[0-9a-f]{40}$")
        exists = git_run(["git", "cat-file", "-e", f"{self.sha}^{{commit}}"])
        self.assertEqual(0, exists.returncode)
        ancestor = git_run(["git", "merge-base", "--is-ancestor", self.sha, "HEAD"])
        self.assertEqual(0, ancestor.returncode)

    def test_05_all_referenced_paths_exist(self) -> None:
        # Use the full _validate_text but only the parts we need.
        self._validate_text(self.doc_text)

    def test_06_key_symbols_exist(self) -> None:
        # Covered by _validate_text.
        self._validate_text(self.doc_text)

    def test_07_required_sections_exist(self) -> None:
        for section in REQUIRED_SECTIONS:
            self.assertIn(section, self.doc_text)

    def test_08_lifecycle_actions_described(self) -> None:
        lower = self.doc_text.lower()
        for action in LIFECYCLE_ACTIONS:
            self.assertIn(action, lower)

    def test_09_recognized_cancelled_side_effect_constraints(self) -> None:
        lower = self.doc_text.lower()
        self.assertIn("一次手势最多", self.doc_text)
        self.assertIn("cancelled", lower)

    def test_10_p12_4_not_complete(self) -> None:
        self.assertIsNone(
            re.search(
                r"P12\.4[^\n]*(?:\bCOMPLETE\b|\bcompleted\b|\bdone\b|\bfinished\b)",
                self.doc_text,
                re.IGNORECASE,
            )
        )

    def test_11_no_a13_references(self) -> None:
        self.assertNotIn("A13", self.doc_text)
        self.assertNotIn("customiuizer-a13", self.doc_text)

    def test_12_no_unverified_device_conclusions(self) -> None:
        self.assertIsNone(
            re.search(r"(真实设备|real device).*(已验证|validated|verified|exercised)", self.doc_text, re.I)
        )

    # ---- mutation tests ----

    def _mutated_validate(self, text: str) -> None:
        with self.assertRaises(AssertionError):
            self._validate_text(text, check_device_blocks=False)

    def test_13_mutation_delete_cancellation_section(self) -> None:
        text = re.sub(r"## 3\. State Machine.*?(?=\n## |\Z)", "", self.doc_text, flags=re.S)
        self._mutated_validate(text)

    def test_14_mutation_delete_owner_cleanup_rule(self) -> None:
        text = re.sub(r"## 7\. Cleanup Contract.*?(?=\n## |\Z)", "", self.doc_text, flags=re.S)
        self._mutated_validate(text)

    def test_15_mutation_corrupt_source_path(self) -> None:
        text = self.doc_text.replace(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureEvent.kt",
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/NonExistentEvent.kt",
        )
        self._mutated_validate(text)

    def test_16_mutation_corrupt_evidence_commit(self) -> None:
        text = self.doc_text.replace(
            self.sha,
            "0000000000000000000000000000000000000000",
        )
        self._mutated_validate(text)

    def test_17_mutation_allow_duplicate_side_effects(self) -> None:
        text = self.doc_text.replace("一次手势最多", "可多次").replace(
            "side effects are not re-triggered",
            "side effects may be re-triggered",
        )
        self._mutated_validate(text)

    def test_18_mutation_resolve_device_lifecycle(self) -> None:
        text = self.doc_text.replace(
            "DEVICE_LIFECYCLE_ENTRY_BLOCKED",
            "DEVICE_LIFECYCLE_ENTRY_RESOLVED",
        )
        # This should fail because the contract requires the blocked marker.
        with self.assertRaises(AssertionError):
            self._validate_text(text, check_device_blocks=True)


if __name__ == "__main__":
    unittest.main()
