#!/usr/bin/env python3
"""Mechanical validation for docs/A14_CURRENT_ARCHITECTURE.md.

This test is the evidence that the CURRENT architecture document is faithful to
the source tree at the declared EvidenceCommit.
"""

from __future__ import annotations

import re
import subprocess
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DOC = REPO_ROOT / "docs" / "A14_CURRENT_ARCHITECTURE.md"

EXPECTED_REPOSITORY = "tomthenpc/customiuizer-a14"
EXPECTED_BRANCH = "devin/a14-rom-intelligence-audit"

REQUIRED_SECTIONS = [
    "1. Startup and installation entry points",
    "2. Process and package routing",
    "3. Feature architecture",
    "4. Hook ownership",
    "5. ClassLoader and lifecycle",
    "6. Java/Kotlin boundary",
    "7. Configuration and event flow",
    "8. Verification architecture",
    "9. Current known limitations",
    "10. Evidence",
]

# Key architecture symbols and the source files that must contain them at EvidenceCommit.
KEY_SYMBOLS = {
    "app/src/main/java/tv/withaibuild/customiuizer/MainModule.java": [
        "MainModule",
        "onPackageReady",
        "PackageReadyParam",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ProcessRouter.kt": [
        "ProcessRouter",
        "resolve",
        "ProcessScope",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt": [
        "PreferenceBootstrap",
        "bootstrap",
        "isReady",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/utils/PrefMap.kt": [
        "PrefMap",
        "replaceSnapshot",
        "put",
        "remove",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt": [
        "FeatureInstallRegistry",
        "register",
        "installAll",
        "FeatureSpec",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/LazyFeatureSpec.kt": [
        "LazyFeatureSpec",
        "enabled",
        "factory",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt": [
        "FeatureId",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt": [
        "SystemServerInstaller",
        "install",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.kt": [
        "SystemUiInstaller",
        "install",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.kt": [
        "LauncherInstaller",
        "install",
        "handleLoadLauncher",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.kt": [
        "SecurityCenterInstaller",
        "install",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.kt": [
        "GenericAppInstaller",
        "installPostAttach",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.kt": [
        "PackageInstallerRouter",
        "install",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt": [
        "SystemUiBootstrapCoordinator",
        "install",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt": [
        "Api102HookBridge",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ResourceHooks.kt": [
        "ResourceHooks",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt": [
        "ReflectionCache",
        "LoaderState",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt": [
        "ModuleHelper",
        "findAndHookMethod",
        "guarded",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt": [
        "ControlCenterGestureRuntimeHolder",
        "bind",
        "unbind",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt": [
        "GestureMachine",
        "clear",
        "prepare",
    ],
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt": [
        "PhysicalGestureArbiter",
        "release",
        "releaseOwner",
        "releaseAll",
    ],
}


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


def read_doc() -> str:
    return DOC.read_text(encoding="utf-8")


class CurrentArchitectureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.doc_text = read_doc()
        self.metadata = parse_metadata(self.doc_text)
        self.assertIsNotNone(self.metadata, "Document metadata block not found")

    def test_document_exists(self) -> None:
        self.assertTrue(DOC.is_file(), f"{DOC} does not exist")

    def test_document_kind_is_current(self) -> None:
        self.assertEqual("CURRENT", self.metadata.get("DocumentKind"))

    def test_repository_and_branch(self) -> None:
        self.assertEqual(EXPECTED_REPOSITORY, self.metadata.get("Repository"))
        self.assertEqual(EXPECTED_BRANCH, self.metadata.get("Branch"))

    def test_evidence_commit_is_40_char_sha(self) -> None:
        sha = self.metadata.get("EvidenceCommit", "")
        self.assertRegex(sha, r"^[0-9a-f]{40}$", f"EvidenceCommit is not a 40-char SHA: {sha!r}")

    def test_evidence_commit_exists_and_is_ancestor(self) -> None:
        sha = self.metadata["EvidenceCommit"]
        exists = git_run(["git", "cat-file", "-e", f"{sha}^{{commit}}"])
        self.assertEqual(0, exists.returncode, f"EvidenceCommit {sha} is not a valid commit object")
        ancestor = git_run(["git", "merge-base", "--is-ancestor", sha, "HEAD"])
        self.assertEqual(0, ancestor.returncode, f"EvidenceCommit {sha} is not an ancestor of HEAD")

    def test_required_sections_present(self) -> None:
        for section in REQUIRED_SECTIONS:
            self.assertIn(section, self.doc_text, f"Missing required section: {section}")

    def test_referenced_source_paths_exist_at_evidence_commit(self) -> None:
        sha = self.metadata["EvidenceCommit"]
        # Collect all repo-relative path-like tokens from the document.
        paths = set()
        for match in re.finditer(r"`([^`\n]+)`", self.doc_text):
            token = match.group(1).strip()
            if re.match(r"^(?:app|tools|scripts|docs|feature-semantics)/[-.\w/]+", token):
                paths.add(token)
        for path in re.findall(r"\b(?:app|tools|scripts|docs|feature-semantics)/[-.\w/]+", self.doc_text):
            paths.add(path)

        for path in sorted(paths):
            result = git_run(["git", "cat-file", "-e", f"{sha}:{path}"])
            self.assertEqual(
                0,
                result.returncode,
                f"Referenced source path {path} does not exist at EvidenceCommit {sha}",
            )

    def test_key_symbols_exist_in_declared_source_paths(self) -> None:
        sha = self.metadata["EvidenceCommit"]
        for path, symbols in KEY_SYMBOLS.items():
            content = git_run(["git", "show", f"{sha}:{path}"])
            self.assertEqual(0, content.returncode, f"Could not read {path} at {sha}")
            for symbol in symbols:
                self.assertRegex(
                    content.stdout,
                    rf"\b{re.escape(symbol)}\b",
                    f"Symbol {symbol!r} not found in {path} at {sha}",
                )

    def test_no_pending_or_tbd_as_completed_conclusions(self) -> None:
        # TBD must not appear.
        self.assertNotIn("TBD", self.doc_text, "Document contains TBD")
        self.assertNotIn("tbd", self.doc_text, "Document contains tbd")

        # "pending" is only allowed in the known-limitations / evidence_pending context.
        # It must not be paired with completion language.
        self.assertFalse(
            re.search(r"(?i)\bpending\b.*\b(?:complete|completed|done|finished)\b", self.doc_text),
            "Document pairs 'pending' with completion language",
        )

    def test_no_a13_references(self) -> None:
        self.assertNotIn("A13", self.doc_text, "Document references A13")
        self.assertNotIn("customiuizer-a13", self.doc_text, "Document references A13 repository")
        self.assertNotIn("a13", self.doc_text.lower(), "Document references a13")

    def test_no_nonexistent_source_paths(self) -> None:
        # The EvidenceCommit-based test already covers most paths. This also checks the
        # current working tree for paths that should exist now (including the doc and test).
        for match in re.finditer(r"`([^`\n]+)`", self.doc_text):
            token = match.group(1).strip()
            if re.match(r"^(?:app|tools|scripts|docs|feature-semantics)/[-.\w/]+", token):
                p = REPO_ROOT / token
                self.assertTrue(
                    p.exists() or (REPO_ROOT / (token + ".md")).exists() or (REPO_ROOT / (token + ".kt")).exists(),
                    f"Path {token} does not exist in the current working tree",
                )

    def test_p12_3_and_p12_4_not_described_as_complete(self) -> None:
        self.assertRegex(
            self.doc_text,
            r"P12\.3[^\n]*TODO",
            "P12.3 must be described as TODO",
        )
        self.assertRegex(
            self.doc_text,
            r"P12\.4[^\n]*TODO",
            "P12.4 must be described as TODO",
        )
        self.assertIsNone(
            re.search(
                r"P12\.[34][^\n]*(?:\bCOMPLETE\b|\bcompleted\b|\bdone\b|\bfinished\b)",
                self.doc_text,
                re.IGNORECASE,
            ),
            "P12.3 or P12.4 must not be described as completed",
        )


if __name__ == "__main__":
    unittest.main()
