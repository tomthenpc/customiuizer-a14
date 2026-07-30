#!/usr/bin/env python3
"""Audit feature semantics for CustoMIUIzer A14.

Modes:
  --init      Build feature-semantics/a14.json from source extraction.
  --validate  Validate a14.json against the schema and current source.
  --markdown  Render docs/FEATURE_EFFECT_AND_RESTART_MATRIX.md.

Exit codes:
  0  success / valid
  1  validation or coverage error
  2  schema/input error
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent

SCHEMA_PATH = REPO_ROOT / "feature-semantics" / "schema.json"
INVENTORY_PATH = REPO_ROOT / "feature-semantics" / "a14.json"
MARKDOWN_PATH = REPO_ROOT / "docs" / "FEATURE_EFFECT_AND_RESTART_MATRIX.md"

XML_KEY_RE = re.compile(r'android:key="([^"]+)"')
XML_DEFAULT_RE = re.compile(r'android:defaultValue="([^"]*)"')
XML_TAG_RE = re.compile(r'<[^>]*?android:key="[^"]+"[^>]*?>', re.DOTALL)

PREFS_KEY_RE = re.compile(r'mPrefs\.get\w*\s*\(\s*"([^"]+)"')

THEME_RE = re.compile(r'setThemeValueReplacement\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"')
FAKE_RE = re.compile(r'addFakeResource\s*\(\s*"([^"]+)"')

JAVA_FUNC_RE = re.compile(
    r'^\s*(?:public|protected|private|static|final|synchronized|abstract|'
    r'@(?:\w+\.)*\w+(?:\([^)]*\))?\s*)*'
    r'(?:[\w<>\[\],\?\s]+)\s+(\w+)\s*\('
)
KT_FUNC_RE = re.compile(r'^\s*(?:[a-zA-Z]+\s+)*fun\s+(?:<[^>]+>\s+)?(\w+)\s*\(')
KT_INIT_RE = re.compile(r'^\s*init\s*\{')

INSTALL_PHASE_BY_FUNCTION = {
    "onSystemServerStarting": "SYSTEM_SERVER_START",
    "onPackageReady": "PACKAGE_READY",
    "onApplicationAttach": "APPLICATION_ATTACH",
    "onSystemUIStarted": "SYSTEMUI_INIT",
    "initSystemUI": "SYSTEMUI_INIT",
    "initResources": "RESOURCE_INIT",
    "onResourcesLoaded": "RESOURCE_INIT",
    "onReceiverEvent": "RECEIVER_EVENT",
    "loadDexKit": "DEXKIT_ONE_SHOT",
}


def _rel(repo_root: Path, target: Path) -> str:
    try:
        return target.relative_to(repo_root).as_posix()
    except ValueError:
        return target.as_posix()


def _file_functions(text: str, language: str) -> list[tuple[int, str]]:
    """Return list of (1-based line number, function name)."""
    functions: list[tuple[int, str]] = []
    for i, line in enumerate(text.splitlines(), start=1):
        if language == "kotlin":
            m = KT_FUNC_RE.match(line)
            if m:
                functions.append((i, m.group(1)))
            m = KT_INIT_RE.match(line)
            if m:
                functions.append((i, "init"))
        else:
            m = JAVA_FUNC_RE.match(line)
            if m:
                name = m.group(1)
                if name in ("if", "while", "for", "switch", "catch", "synchronized"):
                    continue
                functions.append((i, name))
    return functions


def _nearest_function(text: str, line: int, language: str) -> str | None:
    functions = _file_functions(text, language)
    best = None
    for ln, name in functions:
        if ln <= line:
            best = name
    return best


def _sanitize_id(raw: str, seen: set[str]) -> str:
    s = raw.lower()
    s = re.sub(r'[^a-z0-9]', '_', s)
    s = re.sub(r'_+', '_', s).strip('_')
    if not s:
        s = "feature"
    if s[0].isdigit():
        s = "f_" + s
    base = s
    counter = 1
    while s in seen:
        s = f"{base}_{counter}"
        counter += 1
    seen.add(s)
    return s


def _target_package_from_path(source_file: str) -> str:
    p = source_file.lower()
    if "systemui" in p:
        return "com.android.systemui"
    if "launcher" in p:
        return "com.miui.home"
    if "system" in p and "systemui" not in p:
        return "android"
    if "globalaction" in p:
        return "android"
    if "packagepermissions" in p:
        return "android"
    return "UNKNOWN"


def _infer_install_phase(func_name: str | None, source_file: str | None) -> str:
    if func_name and func_name in INSTALL_PHASE_BY_FUNCTION:
        return INSTALL_PHASE_BY_FUNCTION[func_name]
    if source_file and "MainModule.java" in source_file:
        return "UNKNOWN"
    return "UNKNOWN"


def _extract_xml_keys(repo_root: Path) -> dict[str, dict[str, Any]]:
    xml_dir = repo_root / "app" / "src" / "main" / "res" / "xml"
    found: dict[str, dict[str, Any]] = {}
    if not xml_dir.is_dir():
        return found
    for xml_file in sorted(xml_dir.glob("*.xml")):
        text = xml_file.read_text(encoding="utf-8")
        rel = _rel(repo_root, xml_file)
        # fast per-tag extraction for default value
        for m in XML_TAG_RE.finditer(text):
            tag = m.group(0)
            km = XML_KEY_RE.search(tag)
            if not km:
                continue
            key = html.unescape(km.group(1))
            default_match = XML_DEFAULT_RE.search(tag)
            default_value = html.unescape(default_match.group(1)) if default_match else ""
            if key not in found:
                found[key] = {
                    "xmlSource": rel,
                    "defaultValue": default_value,
                }
            else:
                found[key].setdefault("xmlSource", rel)
    return found


def _extract_code_keys(
    repo_root: Path,
    file_paths: list[Path],
    language: str,
    source_type: str,
) -> dict[str, dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    for fp in sorted(file_paths):
        text = fp.read_text(encoding="utf-8")
        rel = _rel(repo_root, fp)
        lines = text.splitlines()
        for m in PREFS_KEY_RE.finditer(text):
            key = m.group(1)
            line_no = text[:m.start()].count("\n") + 1
            func = _nearest_function(text, line_no, language)
            if key not in found:
                found[key] = {
                    "sourceFile": rel,
                    "installer_func": func,
                    "evidence": f'mPrefs call at {rel}:{line_no}',
                    "line": line_no,
                    "xmlSource": "",
                    "defaultValue": "",
                }
            else:
                found[key].setdefault("sourceFile", rel)
    return found


def _extract_resource_names(
    repo_root: Path,
    file_paths: list[Path],
) -> dict[str, dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}
    for fp in sorted(file_paths):
        text = fp.read_text(encoding="utf-8")
        rel = _rel(repo_root, fp)
        language = "java" if fp.suffix == ".java" else "kotlin"
        for m in THEME_RE.finditer(text):
            pkg = m.group(1)
            rtype = m.group(2)
            name = m.group(3)
            key = f"theme:{pkg}:{rtype}:{name}"
            line_no = text[:m.start()].count("\n") + 1
            func = _nearest_function(text, line_no, language)
            if key not in found:
                found[key] = {
                    "sourceFile": rel,
                    "installer_func": func,
                    "evidence": f'setThemeValueReplacement at {rel}:{line_no}',
                    "line": line_no,
                    "targetPackage": pkg,
                    "kind": "theme",
                    "resourceName": name,
                }
        for m in FAKE_RE.finditer(text):
            name = m.group(1)
            key = f"fake:{name}"
            line_no = text[:m.start()].count("\n") + 1
            func = _nearest_function(text, line_no, language)
            if key not in found:
                found[key] = {
                    "sourceFile": rel,
                    "installer_func": func,
                    "evidence": f'addFakeResource at {rel}:{line_no}',
                    "line": line_no,
                    "kind": "fake",
                    "resourceName": name,
                }
    return found


def discover_features(repo_root: Path) -> dict[str, dict[str, Any]]:
    """Discover all preference/resource keys and their source metadata."""
    features: dict[str, dict[str, Any]] = {}

    # 1. XML preference keys (used for xmlSource/defaultValue)
    xml_meta = _extract_xml_keys(repo_root)

    # 2. mPrefs keys in MainModule.java
    main_module = repo_root / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "MainModule.java"
    main_keys: dict[str, dict[str, Any]] = {}
    if main_module.is_file():
        main_keys = _extract_code_keys(repo_root, [main_module], "java", "main")

    # 3. mPrefs keys in mods .kt
    mods_dir = repo_root / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "mods"
    kt_files = list(mods_dir.rglob("*.kt")) if mods_dir.is_dir() else []
    kt_keys = _extract_code_keys(repo_root, kt_files, "kotlin", "kt")

    # 4. Resource names in MainModule.java and mods .kt
    resource_files = ([main_module] if main_module.is_file() else []) + kt_files
    resource_keys = _extract_resource_names(repo_root, resource_files)

    # Merge preference keys from MainModule and mods; prefer code source over XML.
    code_order = [main_keys, kt_keys]
    for code_map in code_order:
        for key, meta in code_map.items():
            if key not in features:
                features[key] = {
                    "sourceFile": meta.get("sourceFile", ""),
                    "installer_func": meta.get("installer_func"),
                    "evidence": meta.get("evidence", ""),
                    "line": meta.get("line", 0),
                    "xmlSource": xml_meta.get(key, {}).get("xmlSource", ""),
                    "defaultValue": xml_meta.get(key, {}).get("defaultValue", ""),
                }
            else:
                features[key].setdefault("sourceFile", meta.get("sourceFile"))

    # Add XML-only keys
    for key, meta in xml_meta.items():
        if key not in features:
            features[key] = {
                "sourceFile": meta.get("xmlSource", ""),
                "installer_func": None,
                "evidence": f'android:key in {meta.get("xmlSource", "")}',
                "line": 0,
                "xmlSource": meta.get("xmlSource", ""),
                "defaultValue": meta.get("defaultValue", ""),
            }

    # Add resource keys
    for key, meta in resource_keys.items():
        if key not in features:
            features[key] = meta
        else:
            # Resource and preference should not collide, but keep resource source if no source file.
            features[key].setdefault("sourceFile", meta.get("sourceFile"))

    return features


def _build_installer(meta: dict[str, Any]) -> str:
    func = meta.get("installer_func")
    src = meta.get("sourceFile", "")
    if not src or src == "":
        return "UNKNOWN"
    if func:
        return f"{src}:{func}"
    return "UNKNOWN"


def _build_entry(key: str, meta: dict[str, Any], feature_id: str) -> dict[str, Any]:
    source_file = meta.get("sourceFile", "")
    installer = _build_installer(meta)
    func = meta.get("installer_func")

    is_resource = key.startswith(("theme:", "fake:"))
    is_xml_only = not is_resource and meta.get("xmlSource") and not func

    if is_resource:
        install_phase = _infer_install_phase(func, source_file)
        runtime_mode = "RESOURCE_REPLACEMENT"
        target_package = meta.get("targetPackage") or _target_package_from_path(source_file)
        enable_effect = "Replaces or injects the resource value"
        disable_effect = "No resource replacement"
        value_change_effect = "Resource replacement value is updated"
        confidence = "INFERRED"
    elif is_xml_only:
        install_phase = "APP_UI_ONLY"
        runtime_mode = "UNKNOWN"
        target_package = "UNKNOWN"
        enable_effect = "UNKNOWN"
        disable_effect = "UNKNOWN"
        value_change_effect = "UNKNOWN"
        confidence = "INFERRED"
    else:
        install_phase = _infer_install_phase(func, source_file)
        runtime_mode = "UNKNOWN"
        target_package = _target_package_from_path(source_file)
        enable_effect = "Applies hook when enabled"
        disable_effect = "Restores default behavior"
        value_change_effect = "Updates applied hook behavior"
        if install_phase != "UNKNOWN" and "MainModule.java" in source_file:
            confidence = "INFERRED"
        else:
            confidence = "UNKNOWN"

    feature_name = meta.get("resourceName", key)
    # Keep the name human-readable; don't use the synthetic theme:/fake: prefix in display.
    if is_resource and ":" in feature_name:
        feature_name = feature_name.split(":")[-1]

    return {
        "featureId": feature_id,
        "featureName": feature_name,
        "preferenceKeys": [key],
        "xmlSource": meta.get("xmlSource", ""),
        "defaultValue": meta.get("defaultValue", ""),
        "sourceFile": source_file,
        "installer": installer,
        "targetPackage": target_package,
        "installPhase": install_phase,
        "runtimeReadMode": runtime_mode,
        "enableEffect": enable_effect,
        "disableEffect": disable_effect,
        "valueChangeEffect": value_change_effect,
        "restartTarget": "UNKNOWN",
        "hotReloadable": False,
        "confidence": confidence,
        "evidence": meta.get("evidence", ""),
        "notes": "",
    }


def generate_inventory(repo_root: Path, features: dict[str, dict[str, Any]]) -> dict[str, Any]:
    seen: set[str] = set()
    entries = []
    for key in sorted(features):
        feature_id = _sanitize_id(key, seen)
        entry = _build_entry(key, features[key], feature_id)
        entries.append(entry)
    return {
        "schemaVersion": 1,
        "contractName": "CustoMIUIzer A14 feature effect and restart semantics",
        "romFamily": "HyperOS 1 / Android 14",
        "androidApi": 34,
        "exclusions": [],
        "entries": entries,
    }


def load_inventory(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def load_schema(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def _collect_enum_values(schema: dict[str, Any], field: str) -> set[str]:
    """Walk schema definitions for the enum of a field."""
    entry = schema.get("definitions", {}).get("featureEntry", {})
    props = entry.get("properties", {})
    prop = props.get(field, {})
    return set(prop.get("enum", []))


def _collect_required(schema: dict[str, Any]) -> set[str]:
    entry = schema.get("definitions", {}).get("featureEntry", {})
    return set(entry.get("required", []))


def _entry_value_types_ok(entry: dict[str, Any]) -> list[str]:
    errors = []
    if not isinstance(entry.get("featureId"), str):
        errors.append("featureId must be a string")
    if not isinstance(entry.get("featureName"), str):
        errors.append("featureName must be a string")
    if not isinstance(entry.get("preferenceKeys"), list) or not entry.get("preferenceKeys"):
        errors.append("preferenceKeys must be a non-empty list")
    if not isinstance(entry.get("sourceFile"), str):
        errors.append("sourceFile must be a string")
    if not isinstance(entry.get("installer"), str):
        errors.append("installer must be a string")
    if not isinstance(entry.get("targetPackage"), str):
        errors.append("targetPackage must be a string")
    if not isinstance(entry.get("installPhase"), str):
        errors.append("installPhase must be a string")
    if not isinstance(entry.get("runtimeReadMode"), str):
        errors.append("runtimeReadMode must be a string")
    if not isinstance(entry.get("enableEffect"), str):
        errors.append("enableEffect must be a string")
    if not isinstance(entry.get("disableEffect"), str):
        errors.append("disableEffect must be a string")
    if not isinstance(entry.get("valueChangeEffect"), str):
        errors.append("valueChangeEffect must be a string")
    if not isinstance(entry.get("restartTarget"), str):
        errors.append("restartTarget must be a string")
    if not isinstance(entry.get("hotReloadable"), bool):
        errors.append("hotReloadable must be a boolean")
    if not isinstance(entry.get("confidence"), str):
        errors.append("confidence must be a string")
    if not isinstance(entry.get("evidence"), str):
        errors.append("evidence must be a string")
    return errors


def _validate_schema_shape(schema: dict[str, Any]) -> list[str]:
    errors = []
    required_root = {"schemaVersion", "contractName", "romFamily", "androidApi", "exclusions", "entries"}
    for k in required_root:
        if k not in schema:
            errors.append(f"Schema requires root field '{k}'")
    if not isinstance(schema.get("exclusions"), list):
        errors.append("root 'exclusions' must be a list")
    return errors


def validate_inventory(
    repo_root: Path,
    inventory: dict[str, Any],
    schema: dict[str, Any],
    discovered: set[str] | None = None,
) -> tuple[int, list[str]]:
    """Validate inventory and compare against discovered keys.

    Returns (exit_code, errors). 0 = ok, 1 = validation/coverage, 2 = schema/input.
    """
    errors: list[str] = []

    shape_errors = _validate_schema_shape(inventory)
    if shape_errors:
        # These are schema/input errors (exit 2)
        return 2, shape_errors

    required = _collect_required(schema)
    phase_enum = _collect_enum_values(schema, "installPhase")
    runtime_enum = _collect_enum_values(schema, "runtimeReadMode")
    restart_enum = _collect_enum_values(schema, "restartTarget")
    confidence_enum = _collect_enum_values(schema, "confidence")

    entries = inventory.get("entries", [])
    if not isinstance(entries, list):
        return 2, ["'entries' must be a list"]

    seen_ids: set[str] = set()
    seen_keys: set[str] = set()

    for i, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"entry[{i}] is not an object")
            continue
        for field in required:
            if field not in entry:
                errors.append(f"entry[{i}] missing required field '{field}'")
        errors.extend(_entry_value_types_ok(entry))

        if entry.get("featureId") in seen_ids:
            errors.append(f"duplicate featureId: {entry.get('featureId')}")
        if "featureId" in entry:
            seen_ids.add(entry["featureId"])

        for key in entry.get("preferenceKeys", []):
            if key in seen_keys:
                errors.append(f"duplicate preferenceKeys value: {key}")
            seen_keys.add(key)

        if entry.get("installPhase") not in phase_enum:
            errors.append(f"entry[{i}] illegal installPhase: {entry.get('installPhase')}")
        if entry.get("runtimeReadMode") not in runtime_enum:
            errors.append(f"entry[{i}] illegal runtimeReadMode: {entry.get('runtimeReadMode')}")
        if entry.get("restartTarget") not in restart_enum:
            errors.append(f"entry[{i}] illegal restartTarget: {entry.get('restartTarget')}")
        if entry.get("confidence") not in confidence_enum:
            errors.append(f"entry[{i}] illegal confidence: {entry.get('confidence')}")

        # sourceFile existence
        src = entry.get("sourceFile", "")
        if src and src != "UNKNOWN" and src != "":
            if not (repo_root / src).is_file():
                errors.append(f"entry[{i}] sourceFile does not exist: {src}")

        # installer existence: if it has a colon, left part is file; if just UNKNOWN, skip
        inst = entry.get("installer", "")
        if inst and inst != "UNKNOWN" and ":" in inst:
            file_part = inst.split(":", 1)[0]
            if file_part and not (repo_root / file_part).is_file():
                errors.append(f"entry[{i}] installer file does not exist: {file_part}")

    # exclusions reason check at root
    for i, ex in enumerate(inventory.get("exclusions", [])):
        reason = ex.get("reason", "")
        if not reason or not isinstance(reason, str):
            errors.append(f"exclusion[{i}] missing a reason")

    # coverage comparison
    if discovered is not None:
        excluded_keys = {ex.get("preferenceKey", "") for ex in inventory.get("exclusions", [])}
        missing = []
        for key in discovered:
            if key not in seen_keys and key not in excluded_keys:
                missing.append(key)
        if missing:
            for key in sorted(missing)[:20]:
                errors.append(f"discovered key not in inventory or exclusions: {key}")
            if len(missing) > 20:
                errors.append(f"... and {len(missing) - 20} more unlisted keys")

    if errors:
        return 1, errors
    return 0, []


def generate_markdown(inventory: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("# Feature Effect and Restart Matrix")
    lines.append("")
    lines.append("Generated from `feature-semantics/a14.json`.")
    lines.append("")

    # Group by restartTarget, then installPhase
    grouped: dict[str, dict[str, list[dict[str, Any]]]] = defaultdict(lambda: defaultdict(list))
    for entry in inventory.get("entries", []):
        rt = entry.get("restartTarget", "UNKNOWN")
        ip = entry.get("installPhase", "UNKNOWN")
        grouped[rt][ip].append(entry)

    for rt in sorted(grouped):
        lines.append(f"## restartTarget: {rt}")
        for ip in sorted(grouped[rt]):
            lines.append(f"### installPhase: {ip}")
            lines.append("")
            lines.append("| Feature | preference key | target package | enable | disable | value-change | evidence | confidence |")
            lines.append("|--------|----------------|----------------|--------|---------|--------------|----------|------------|")
            for entry in sorted(grouped[rt][ip], key=lambda e: e.get("featureId", "")):
                pkeys = ", ".join(entry.get("preferenceKeys", []))
                ev = entry.get("evidence", "").replace("|", "\\|")
                lines.append(
                    f'| {entry.get("featureName", "").replace("|", "\\|")} '
                    f'| {pkeys.replace("|", "\\|")} '
                    f'| {entry.get("targetPackage", "")} '
                    f'| {entry.get("enableEffect", "").replace("|", "\\|")} '
                    f'| {entry.get("disableEffect", "").replace("|", "\\|")} '
                    f'| {entry.get("valueChangeEffect", "").replace("|", "\\|")} '
                    f'| {ev} '
                    f'| {entry.get("confidence", "")} |'
                )
            lines.append("")

    return "\n".join(lines)


def cmd_init(args: argparse.Namespace) -> int:
    features = discover_features(args.repo_root)
    inventory = generate_inventory(args.repo_root, features)
    INVENTORY_PATH.parent.mkdir(parents=True, exist_ok=True)
    with INVENTORY_PATH.open("w", encoding="utf-8") as f:
        json.dump(inventory, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"Wrote {len(inventory['entries'])} entries to {INVENTORY_PATH}")
    return 0


def cmd_validate(args: argparse.Namespace) -> int:
    if not SCHEMA_PATH.is_file():
        print(f"Schema not found: {SCHEMA_PATH}", file=sys.stderr)
        return 2
    if not INVENTORY_PATH.is_file():
        print(f"Inventory not found: {INVENTORY_PATH}", file=sys.stderr)
        return 2
    try:
        schema = load_schema(SCHEMA_PATH)
    except (json.JSONDecodeError, OSError) as e:
        print(f"Failed to load schema: {e}", file=sys.stderr)
        return 2
    try:
        inventory = load_inventory(INVENTORY_PATH)
    except (json.JSONDecodeError, OSError) as e:
        print(f"Failed to load inventory: {e}", file=sys.stderr)
        return 2
    try:
        discovered = set(discover_features(args.repo_root).keys())
    except OSError as e:
        print(f"Failed to rediscover features: {e}", file=sys.stderr)
        return 2
    code, errors = validate_inventory(args.repo_root, inventory, schema, discovered)
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
    if code == 0:
        print("Validation passed")
    return code


def cmd_markdown(args: argparse.Namespace) -> int:
    if not INVENTORY_PATH.is_file():
        print(f"Inventory not found: {INVENTORY_PATH}", file=sys.stderr)
        return 2
    try:
        inventory = load_inventory(INVENTORY_PATH)
    except (json.JSONDecodeError, OSError) as e:
        print(f"Failed to load inventory: {e}", file=sys.stderr)
        return 2
    md = generate_markdown(inventory)
    MARKDOWN_PATH.parent.mkdir(parents=True, exist_ok=True)
    with MARKDOWN_PATH.open("w", encoding="utf-8") as f:
        f.write(md)
        f.write("\n")
    print(f"Wrote markdown to {MARKDOWN_PATH}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Feature semantics auditor for CustoMIUIzer A14")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT, help="repository root (default: parent of tools/)")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--init", action="store_true", help="generate feature-semantics/a14.json")
    group.add_argument("--validate", action="store_true", help="validate a14.json")
    group.add_argument("--markdown", action="store_true", help="generate docs/FEATURE_EFFECT_AND_RESTART_MATRIX.md")
    args = parser.parse_args(argv)
    if args.init:
        return cmd_init(args)
    if args.validate:
        return cmd_validate(args)
    if args.markdown:
        return cmd_markdown(args)
    return 2


if __name__ == "__main__":
    sys.exit(main())
