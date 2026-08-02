#!/usr/bin/env python3
"""Independent oracle for docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md.

Parses the inventory, validates the front-matter EvidenceCommit, and checks that
every cited source line range still contains the symbols the document claims.
"""

import re
import subprocess
import unittest
from pathlib import Path

from tools import check_document_contracts

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DOC_PATH = REPO_ROOT / "docs" / "A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md"


def git_run(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def read_source(rel_path: str) -> str:
    return (REPO_ROOT / rel_path).read_text(encoding="utf-8", errors="replace")


def parse_metadata(text: str) -> dict[str, str] | None:
    return check_document_contracts.parse_metadata(text)


def expected_tokens_for_bullet(rel_path: str, bullet: str) -> list[str]:
    """Return the symbols that must appear in the cited source range."""
    tokens: list[str] = []
    lower = bullet.lower()

    if "attach/detach" in lower or ("attachedtowindow" in lower or "detachedfromwindow" in lower):
        tokens.extend(["onAttachedToWindow", "onDetachedFromWindow"])
        if "PhoneStatusBarView" in bullet or rel_path.endswith("SystemUIControlCenterHooks.kt"):
            tokens.extend(["statusBarMachine.prepare", "statusBarMachine.clear"])
        elif "ControlCenterWindowViewImpl" in bullet or rel_path.endswith("ControlCenterPluginRuntime.kt"):
            tokens.extend(["controlCenterMachine.prepare", "controlCenterMachine.clear"])
        else:
            # Fallback for both status-bar and control-center paths.
            tokens.extend([
                "statusBarMachine.prepare", "statusBarMachine.clear",
                "controlCenterMachine.prepare", "controlCenterMachine.clear",
            ])

    if "bind" in lower and "unbind" in lower and rel_path.endswith("ControlCenterGestureRuntimeHolder.kt"):
        tokens.extend(["fun bind", "fun unbind", "fun activeRuntime"])

    if "clear" in lower and rel_path.endswith("GestureMachine.kt"):
        tokens.extend(["fun clear(", "ownerId"])

    if "test" in lower or rel_path.endswith("ControlCenterGestureRuntimeHolderTest.kt"):
        # The test file does not contain `fun bind`; it contains test methods exercising bind/unbind/activeRuntime.
        for name in [
            "activeRuntime_isNotPublishedBeforeInstallHooksSucceed",
            "unbind_clearsMachineAndDropsRuntime",
            "sameLoader_doesNotReinstallHooks",
            "newLoader_clearsOldMachine",
            "repeatedLoaderReplacement_doesNotGrowRuntimeState",
            "oldLoaderDetach_doesNotClearNewRuntime",
        ]:
            tokens.append(name)

    return tokens


class GestureLifecycleInventoryDocTest(unittest.TestCase):

    def setUp(self) -> None:
        self.assertTrue(DOC_PATH.is_file(), f"Document missing: {DOC_PATH}")
        self.doc_text = DOC_PATH.read_text(encoding="utf-8", errors="replace")
        self.meta = parse_metadata(self.doc_text)

    def test_metadata_block_present(self) -> None:
        self.assertIsNotNone(self.meta, "Document metadata block missing")

    def test_document_kind_current(self) -> None:
        self.assertEqual("CURRENT", self.meta.get("DocumentKind"))

    def test_branch_is_authorized(self) -> None:
        self.assertEqual("devin/a14-rom-intelligence-audit", self.meta.get("Branch"))

    def test_evidence_commit_is_valid_and_ancestor(self) -> None:
        commit = self.meta.get("EvidenceCommit")
        self.assertIsNotNone(commit, "EvidenceCommit missing")
        self.assertRegex(commit, r"^[0-9a-f]{40}$", f"EvidenceCommit '{commit}' is not a 40-character SHA")
        ok, classification = check_document_contracts.validate_evidence_commit(commit)
        self.assertTrue(ok, f"EvidenceCommit '{commit}' invalid: {classification}")

    def test_generated_by_includes_check(self) -> None:
        generated_by = self.meta.get("GeneratedBy", "")
        self.assertIn("test_gesture_lifecycle_inventory", generated_by,
                      "GeneratedBy must reference the independent doc test")

    def test_evidence_section_has_claims(self) -> None:
        section_match = re.search(r"##\s+4\.\s+Evidence\s*\n(.*?)(?:\n##|\Z)", self.doc_text, re.S)
        self.assertIsNotNone(section_match, "## 4. Evidence section missing")
        self.evidence_text = section_match.group(1)
        # At least one file reference.
        files = re.findall(r"app/src/[^\s`]+", self.evidence_text)
        self.assertTrue(files, "Evidence section contains no source references")

    def test_each_evidence_claim_is_verified(self) -> None:
        section_match = re.search(r"##\s+4\.\s+Evidence\s*\n(.*?)(?:\n##|\Z)", self.doc_text, re.S)
        evidence_text = section_match.group(1)

        for bullet in evidence_text.splitlines():
            bullet = bullet.strip()
            if not bullet.startswith("-"):
                continue

            file_match = re.search(r"(app/src/[^\s`]+)", bullet)
            if not file_match:
                continue
            rel_path = file_match.group(1)
            path = REPO_ROOT / rel_path
            self.assertTrue(path.is_file(), f"Evidence references missing file: {rel_path}")

            source_text = read_source(rel_path)
            lines = source_text.splitlines()

            range_match = re.search(r"(?:line|lines)\s+(\d+)(?:-(\d+))?", bullet, re.IGNORECASE)
            if range_match:
                start = int(range_match.group(1))
                end = int(range_match.group(2)) if range_match.group(2) else start
                self.assertLessEqual(start, end, f"Line range inverted in: {bullet}")
                self.assertGreaterEqual(start, 1, f"Line numbers are 1-based in: {bullet}")
                self.assertLessEqual(end, len(lines),
                                     f"Range {start}-{end} exceeds {rel_path} length {len(lines)}")
                window = "\n".join(lines[start - 1:end])
            else:
                window = source_text

            expected = expected_tokens_for_bullet(rel_path, bullet)
            self.assertTrue(expected, f"No expected tokens inferred for evidence: {bullet}")

            missing = [tok for tok in expected if tok not in window]
            self.assertEqual(
                [],
                missing,
                f"{rel_path} (lines {start}-{end if range_match else 'all'}) missing tokens: {missing}",
            )

    def test_table_verified_by_files_exist(self) -> None:
        table_match = re.search(r"##\s+1\.\s+Owners.*\n\|.*\|\n((?:\|[^\n]+\|\n?)+)", self.doc_text, re.S)
        if not table_match:
            self.fail("Could not find owners table")
        table = table_match.group(1)
        for match in re.finditer(r"`?([\w/]+(?:\.py|\.kt|\.java))`?", table):
            rel = match.group(1)
            path = REPO_ROOT / rel
            self.assertTrue(path.is_file(), f"Verified-by file missing: {rel}")


if __name__ == "__main__":
    unittest.main()
