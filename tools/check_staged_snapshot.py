#!/usr/bin/env python3
"""Verify the staged snapshot is a coherent transaction."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parents[1]

SECRETS_PATTERNS = [
    r"BEGIN (RSA |DSA |EC |OPENSSH )?PRIVATE KEY",
    r"BEGIN CERTIFICATE",
    r"api[_-]?key\s*=\s*['\"][^'\"]+['\"]",
    r"password\s*=\s*['\"][^'\"]+['\"]",
    r"token\s*=\s*['\"][^'\"]+['\"]",
]

FORBIDDEN_STAGED = {
    "*.apk",
    "*.keystore",
    "*.jks",
    "keystore.properties",
    "local.properties",
    "google-services.json",
    "*.keystore.properties",
}


def staged_files() -> list[Path]:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-only"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    return [REPO_ROOT / p for p in result.stdout.splitlines() if p]


def parse_commit_message(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def check_staged_snapshot(commit_msg_path: str | None = None, is_qualifying: bool = False) -> list[str]:
    errors: list[str] = []
    files = staged_files()

    if not files:
        errors.append("No staged files")
        return errors

    has_source = any(p.suffix in {".kt", ".java", ".py", ".ps1"} for p in files)
    has_test = any("test" in p.name.lower() or "/tests/" in str(p) for p in files)
    has_doc = any("docs/" in str(p) for p in files)
    has_state = any(p.name in {"TASK_STATE.md", "SMART_OPERATION_STATE.md"} for p in files)

    for p in files:
        rel = p.relative_to(REPO_ROOT).as_posix()
        if rel == "tools/check_staged_snapshot.py":
            continue
        text = p.read_text(encoding="utf-8", errors="replace")
        for pat in SECRETS_PATTERNS:
            if re.search(pat, text, re.IGNORECASE):
                errors.append(f"{rel}: possible secret pattern")

        if any(p.match(g) for g in FORBIDDEN_STAGED):
            errors.append(f"{p.relative_to(REPO_ROOT)}: forbidden staged file pattern")

    if is_qualifying:
        if not (has_source or has_test or has_doc):
            errors.append("Qualifying commit must contain source/test/doc changes, not only state")

    if has_state and not (has_source or has_test or has_doc or has_state):
        errors.append("Staged state change without work product is a state-only checkpoint")

    if commit_msg_path:
        msg = parse_commit_message(Path(commit_msg_path))
        if has_state and not re.search(r"(state|checkpoint|docs|tools)", msg, re.IGNORECASE):
            errors.append("Commit message should mention state/docs/tools when state files are staged")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="A14 staged snapshot checker")
    parser.add_argument("--commit-msg", help="path to commit message file")
    parser.add_argument("--qualifying", action="store_true", help="expect a qualifying checkpoint")
    args = parser.parse_args()

    errors = check_staged_snapshot(commit_msg_path=args.commit_msg, is_qualifying=args.qualifying)
    if errors:
        print("STAGED SNAPSHOT VIOLATIONS:")
        for err in errors:
            print(f"  - {err}")
        return 1

    print("Staged snapshot checks pass.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
