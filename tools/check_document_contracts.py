#!/usr/bin/env python3
"""Check documentation contract metadata and current/snapshot uniqueness."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parents[1]

ALLOWED_KINDS = {"CURRENT", "SNAPSHOT", "GENERATED", "EXTERNAL_CHECKLIST", "PLAN"}
ALLOWED_EVIDENCE_STATES = {"STATIC", "BUILD", "CI", "DEVICE", "MIXED", "REMOTE_STATIC_PLUS_RECORDED_BUILD"}
REQUIRED_SMART_KEYS = [
    "DocumentKind",
    "Product",
    "Repository",
    "Branch",
    "EvidenceCommit",
    "EvidenceState",
    "GeneratedBy",
    "SourceOfTruth",
]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def parse_metadata(text: str) -> dict[str, str] | None:
    """Parse the first metadata ```text block that contains DocumentKind."""
    for match in re.finditer(r"```text\s*\n(.*?)\n```", text, re.DOTALL):
        block = match.group(1)
        result: dict[str, str] = {}
        for line in block.splitlines():
            line = line.strip()
            if ":" not in line:
                continue
            key, value = line.split(":", 1)
            result[key.strip()] = value.strip()
        if "DocumentKind" in result:
            return result
    return None


def git_commit_exists(sha: str) -> bool:
    try:
        subprocess.run(
            ["git", "cat-file", "-t", sha],
            cwd=REPO_ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return True
    except subprocess.CalledProcessError:
        return False


def list_documents(root: Path) -> list[Path]:
    docs: list[Path] = []
    for pattern in ["docs/**/*.md", "docs/**/*.json"]:
        for p in root.glob(pattern):
            if p.is_file():
                docs.append(p)
    return docs


def check_document_contracts(only_staged: bool = False) -> list[str]:
    errors: list[str] = []

    if only_staged:
        result = subprocess.run(
            ["git", "diff", "--cached", "--name-only"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            errors.append("Failed to list staged files")
            return errors
        paths = [REPO_ROOT / p for p in result.stdout.splitlines() if p]
        doc_paths = [p for p in paths if "docs" in p.parts]
    else:
        doc_paths = list_documents(REPO_ROOT)

    current_docs: list[Path] = []
    v4_enforced = {
        "docs/audit/A14_FULL_REVIEW_V4.md",
        "docs/audit/A14_ALGORITHM_OPTIMIZATION_PLAN_V4.md",
        "docs/audit/A14_DOCUMENT_UPDATE_PLAN_V4.md",
        "docs/audit/DOCUMENTATION_CONTRACT_V4.md",
        "docs/audit/CHECKPOINT_AND_CI_TRANSACTION_V4.md",
        "docs/audit/ALGORITHM_OPTIMIZATION_GOVERNANCE_V4.md",
        "docs/DOCUMENT_INDEX.md",
    }

    for path in doc_paths:
        rel = path.relative_to(REPO_ROOT).as_posix()
        text = read_text(path)
        meta = parse_metadata(text)
        if meta is None:
            if rel in v4_enforced:
                errors.append(f"{rel}: missing metadata block")
            continue

        kind = meta.get("DocumentKind")
        if not kind:
            errors.append(f"{path.relative_to(REPO_ROOT)}: missing DocumentKind")
        elif kind not in ALLOWED_KINDS:
            errors.append(f"{path.relative_to(REPO_ROOT)}: unknown DocumentKind '{kind}'")

        if kind == "CURRENT" and path.suffix == ".md":
            current_docs.append(path)

        branch = meta.get("Branch")
        if branch and branch != "devin/a14-rom-intelligence-audit":
            errors.append(f"{path.relative_to(REPO_ROOT)}: wrong Branch '{branch}'")

        evidence_commit = meta.get("EvidenceCommit")
        if evidence_commit and evidence_commit != "pending" and not git_commit_exists(evidence_commit):
            errors.append(f"{path.relative_to(REPO_ROOT)}: EvidenceCommit '{evidence_commit}' not found in repo")

        evidence_state = meta.get("EvidenceState")
        if evidence_state and evidence_state not in ALLOWED_EVIDENCE_STATES:
            errors.append(f"{path.relative_to(REPO_ROOT)}: invalid EvidenceState '{evidence_state}'")

    # CURRENT uniqueness per topic: simple heuristic by filename stem.
    seen: dict[str, Path] = {}
    for path in current_docs:
        stem = path.stem
        if stem in seen:
            errors.append(f"Multiple CURRENT docs with same stem '{stem}': {seen[stem]} and {path}")
        seen[stem] = path

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="A14 document contract checker")
    parser.add_argument("--staged", action="store_true", help="only check staged docs")
    args = parser.parse_args()

    errors = check_document_contracts(only_staged=args.staged)
    if errors:
        print("DOCUMENT CONTRACT VIOLATIONS:")
        for err in errors:
            print(f"  - {err}")
        return 1

    print("Document contract checks pass.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
