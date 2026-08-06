#!/usr/bin/env python3
"""Static gate for preference observer onChange key matching.

Production [PreferenceObserver] implementations must match against canonical
short keys only. This script flags any observer whose onChange body still
contains raw `pref_key_` string literals or helper calls for prefix handling.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from source_hazard_scan import find_block_end

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"

# Matches an object/class that is a PreferenceObserver implementation.
# Captures the text following the declaration up to the matching close brace.
OBSERVER_PATTERN = re.compile(
    r"(?:object\s*:\s*|class\s+\w+\s*(?::\s*)?|\s*:\s*)"
    r"(?:ModuleHelper\.)?PreferenceObserver\s*(?:<[^>]+>\s*)?\{\s*",
    re.S,
)

# Matches an onChange method inside the observer body.
ONCHANGE_PATTERN = re.compile(
    r"override\s+fun\s+onChange\s*\(\s*\w+\s*:\s*String\?\s*\)\s*\{",
    re.S,
)

FORBIDDEN_PATTERNS = [
    re.compile(r'"pref_key_[^"]*"'),
    re.compile(r'\bpref_key_[a-zA-Z0-9_]+\b'),
    re.compile(r'\.contains\s*\(\s*"pref_key_'),
    re.compile(r'\.startsWith\s*\(\s*"pref_key_'),
    re.compile(r'\.endsWith\s*\(\s*"pref_key_'),
]

EXCLUDED_PATHS = {
    # Non-observer production code may still mention storage keys legitimately.
    "app/src/main/java/tv/withaibuild/customiuizer/utils/PreferenceKeys.kt",
    "app/src/main/java/tv/withaibuild/customiuizer/utils/PrefMap.kt",
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceBootstrap.kt",
    "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceObserverRegistry.kt",
}


def find_observer_bodies(text: str) -> list[tuple[int, int]]:
    """Return start/end offsets for each PreferenceObserver body in [text]."""
    bodies: list[tuple[int, int]] = []
    for match in OBSERVER_PATTERN.finditer(text):
        open_offset = match.end() - 1
        if open_offset < 0 or text[open_offset] != "{":
            continue
        close_offset = find_block_end(text, open_offset)
        if close_offset > 0:
            bodies.append((open_offset, close_offset))
    return bodies


def check_file(path: Path) -> list[str]:
    rel = path.relative_to(REPO_ROOT).as_posix()
    if rel in EXCLUDED_PATHS:
        return []

    text = path.read_text(encoding="utf-8")
    issues: list[str] = []
    for body_start, body_end in find_observer_bodies(text):
        body = text[body_start:body_end]
        for match in ONCHANGE_PATTERN.finditer(body):
            open_offset = match.end() - 1
            if open_offset < 0 or body[open_offset] != "{":
                continue
            close_offset = find_block_end(body, open_offset)
            if close_offset < 0:
                continue
            onchange_body = body[open_offset:close_offset]
            for pattern in FORBIDDEN_PATTERNS:
                if pattern.search(onchange_body):
                    line = text[:body_start + open_offset].count("\n") + 1
                    issues.append(f"{rel}:{line}: observer onChange contains pref_key_ pattern: {pattern.pattern}")
                    break
    return issues


def main() -> int:
    issues: list[str] = []
    for pattern in ("**/*.kt", "**/*.java"):
        for path in sorted(SOURCE_ROOT.rglob(pattern)):
            issues.extend(check_file(path))

    if issues:
        print("observer-key-contract: FAILED")
        for issue in issues:
            print(f"  {issue}")
        return 1

    print("observer-key-contract: passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
