#!/usr/bin/env python3
"""Offline ROM contract scanner stub.

Requires the ROM's services.jar, SystemUI and Launcher APKs to have been
pre-disassembled with baksmali. Given a contract.json that lists the
classes/methods/fields the module depends on, it reports which entries are
still present in the supplied smali trees.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


def load_contract(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def find_class(smali_root: Path, class_name: str) -> Path | None:
    # Convert com.example.Foo -> com/example/Foo.smali
    parts = class_name.split(".")
    rel = Path(*parts).with_suffix(".smali")
    candidate = smali_root / rel
    return candidate if candidate.is_file() else None


def scan(smali_root: Path, contract: dict) -> dict:
    report: dict = {}
    for cls_name, members in contract.items():
        smali_file = find_class(smali_root, cls_name)
        if smali_file is None:
            report[cls_name] = {"present": False, "methods": {}, "fields": {}}
            continue
        text = smali_file.read_text(encoding="utf-8")
        method_status = {}
        for m in members.get("methods", []):
            # Simple name match; full descriptor matching needs descriptor conversion.
            method_status[m] = bool(re.search(rf"\.method.*\s{m}[\(:<;]", text))
        field_status = {}
        for f in members.get("fields", []):
            field_status[f] = bool(re.search(rf"\.field.*\s{f}:", text))
        report[cls_name] = {"present": True, "methods": method_status, "fields": field_status}
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="Scan a ROM's smali tree against a contract.")
    parser.add_argument("--contract", required=True, type=Path, help="Path to contract.json")
    parser.add_argument("--smali-root", required=True, type=Path, help="Root of pre-disassembled smali tree")
    parser.add_argument("--output", type=Path, default=Path("build/rom-contract-report.json"))
    args = parser.parse_args()

    contract = load_contract(args.contract)
    report = scan(args.smali_root, contract)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"Report written to {args.output}")

    missing = [c for c, r in report.items() if not r["present"]]
    if missing:
        print(f"Missing classes: {', '.join(missing)}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
