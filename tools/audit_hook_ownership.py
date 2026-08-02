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
"""

from __future__ import annotations

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


def main() -> int:
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

    out = REPO_ROOT / "docs" / "audit" / "A14_HOOK_OWNERSHIP_INVENTORY.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join(md), encoding="utf-8", newline="\n")
    print(f"Wrote {out} with {total} hook sites")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
