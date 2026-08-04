#!/usr/bin/env python3
"""Scan A14 source and classify production hook ownership.

Outputs docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md with categories:
- REGISTRY_FEATURE (feature install definitions)
- INSTALLER_INFRASTRUCTURE (installer, main module, helpers)
- RESOURCE_INFRASTRUCTURE (resource hooks)
- API_BRIDGE (XposedHelpers, HookerClassHelper)
- LEGACY_EXCEPTION (legacy de.robv... paths)
- DEAD_CANDIDATE (retirement audit candidates)
- UNKNOWN (needs classification)

The default --check mode regenerates the inventory into a temporary directory,
compares it to the committed file using LF/UTF-8 normalization, and exits
non-zero if the tracked file would drift. It never overwrites the tracked file.
To regenerate the tracked inventory, pass --write. To write to a custom path,
pass --output <path> with --write.
"""

from __future__ import annotations

import argparse
import tempfile
import re
from pathlib import Path
from collections import defaultdict

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"

HOOK_CALL_RE = re.compile(
    r"(XposedHelpers|XposedBridge)\.(findAndHookMethod|findAndHookConstructor|hookAllMethods|hookAllConstructors|hookMethod|newInstance|set\w+)"
    r"|findAndHookMethod\s*\("
    r"|hookAllMethods\s*\("
    r"|hookAllConstructors\s*\("
)

LEGACY_RE = re.compile(r"de\.robv\.android\.xposed")


def classify(path: Path, line: int, text: str) -> str:
    rel = path.relative_to(SOURCE_ROOT).as_posix()
    if LEGACY_RE.search(text):
        return "LEGACY_EXCEPTION"
    if "ResourceHooks" in rel or "resource" in path.name.lower():
        return "RESOURCE_INFRASTRUCTURE"
    if "XposedHelpers" in rel or "HookerClassHelper" in rel:
        return "API_BRIDGE"
    if "Installer" in rel or "MainModule" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "/feature/" in rel or "Features" in rel or "Hooks" in rel:
        return "REGISTRY_FEATURE"
    # Most top-level mods/*.kt files are FeatureDefinition implementations.
    if rel.startswith("tv/withaibuild/customiuizer/mods/") and not rel.startswith("tv/withaibuild/customiuizer/mods/utils/"):
        return "REGISTRY_FEATURE"
    # Business-feature helper classes in utils/ that still contain feature-specific hook calls.
    if "DeviceInfoMonitor" in rel or "LockScreenAlbumArtController" in rel or "ControlCenterPluginRuntime" in rel:
        return "REGISTRY_FEATURE"
    if "mods/utils/ModuleHelper" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "mods/utils/ReflectionCache" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "PreferenceObserverRegistry" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    if "SystemUiBootstrapCoordinator" in rel:
        return "INSTALLER_INFRASTRUCTURE"
    return "UNKNOWN"


def nearest_function(lines: list[str], line_idx: int) -> str | None:
    for i in range(line_idx, -1, -1):
        stripped = lines[i].strip()
        if stripped.startswith("fun ") or stripped.startswith("@JvmField"):
            continue
        m = re.match(r"(?:\s)*(?:fun|override fun|open fun)\s+(\w+)", stripped)
        if m:
            return m.group(1)
        m = re.match(r"(?:\s)*(?:public|private|protected|static|\s)+(?:\w+\s+)+(\w+)\s*\(", stripped)
        if m and m.group(1) not in ("if", "while", "for", "switch", "catch"):
            return m.group(1)
    return None


def _generate_markdown() -> str:
    groups: dict[str, list[tuple[str, int, str, str, str]]] = defaultdict(list)
    total = 0
    for path in sorted(SOURCE_ROOT.rglob("*.kt")) + sorted(SOURCE_ROOT.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        for i, line in enumerate(lines, start=1):
            if HOOK_CALL_RE.search(line):
                total += 1
                cat = classify(path, i, text)
                func = nearest_function(lines, i - 1) or "?"
                rel = path.relative_to(SOURCE_ROOT).as_posix()
                groups[cat].append((rel, i, func, line.strip(), path.suffix))

    md = ["# A14 Hook Ownership Inventory\n\n"]
    md.append(f"Total hook call sites scanned: {total}\n\n")
    md.append("| Category | Count |\n|---|---|\n")
    for cat in sorted(groups, key=lambda c: (-len(groups[c]), c)):
        md.append(f"| {cat} | {len(groups[cat])} |\n")
    md.append("\n")

    for cat in sorted(groups, key=lambda c: (-len(groups[c]), c)):
        md.append(f"## {cat}\n\n")
        md.append("| File | Line | Function | Snippet |\n")
        md.append("|---|---|---|---|\n")
        for rel, line, func, snippet, _ in groups[cat]:
            snippet = snippet.replace("|", "\\|").replace("\n", " ").replace("\r", " ")[:120]
            md.append(f"| `{rel}` | {line} | `{func}` | `{snippet}` |\n")
        md.append("\n")

    return "".join(md)


def _normalize_text(text: str) -> str:
    """Normalize to LF-only, UTF-8 text."""
    return text.replace("\r\n", "\n").replace("\r", "\n")


def _normalize_for_compare(text: str) -> str:
    """Strip trailing whitespace and normalize line endings."""
    normalized = _normalize_text(text).rstrip("\n")
    return "\n".join(line.rstrip() for line in normalized.splitlines())


def _write_inventory(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def generate(out: Path | None = None) -> int:
    text = _normalize_text(_generate_markdown())
    if out is None:
        out = REPO_ROOT / "docs" / "audit" / "A14_HOOK_OWNERSHIP_INVENTORY.md"
    _write_inventory(out, text)
    print(f"Wrote {out}")
    return 0


def check() -> int:
    """Regenerate in a temp directory and compare to the committed file."""
    expected_path = REPO_ROOT / "docs" / "audit" / "A14_HOOK_OWNERSHIP_INVENTORY.md"
    generated = _normalize_for_compare(_generate_markdown())

    with tempfile.TemporaryDirectory() as td:
        candidate = Path(td) / "A14_HOOK_OWNERSHIP_INVENTORY.md"
        _write_inventory(candidate, generated)
        candidate_text = _normalize_for_compare(candidate.read_text(encoding="utf-8"))

    if not expected_path.exists():
        print(f"Committed inventory missing: {expected_path}")
        print("Drift summary: file does not exist")
        return 1

    committed_text = _normalize_for_compare(expected_path.read_text(encoding="utf-8"))

    if candidate_text == committed_text:
        print("Hook ownership inventory is up to date")
        return 0

    # Show a compact drift summary.
    candidate_lines = candidate_text.splitlines()
    committed_lines = committed_text.splitlines()
    drift: list[str] = []
    for i, (a, b) in enumerate(zip(candidate_lines, committed_lines), start=1):
        if a != b:
            drift.append(f"line {i}: expected {a!r}, got {b!r}")
            if len(drift) >= 10:
                break
    if len(candidate_lines) != len(committed_lines):
        drift.append(f"line count differs: candidate={len(candidate_lines)} committed={len(committed_lines)}")

    print(f"Drift detected in {expected_path}")
    for line in drift[:10]:
        print(f"  {line}")
    return 1


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument(
        "--check",
        action="store_true",
        default=True,
        help="verify the committed inventory is up to date without writing it (default)",
    )
    p.add_argument(
        "--write",
        action="store_true",
        help="explicitly write the inventory; required to overwrite the tracked file",
    )
    p.add_argument(
        "--output",
        type=Path,
        default=None,
        help="custom output path (only used with --write)",
    )
    args = p.parse_args()
    if args.write:
        return generate(args.output)
    return check()


if __name__ == "__main__":
    raise SystemExit(main())
