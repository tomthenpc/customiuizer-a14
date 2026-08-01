#!/usr/bin/env python3
"""Compare two A14 APK size baseline JSON files.

Exit code 0 if the comparison is within budget, 1 otherwise.
"""

import argparse
import json
import sys
from pathlib import Path


def load(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def fmt(b: int) -> str:
    sign = "+" if b > 0 else ""
    return f"{sign}{b:,} bytes ({sign}{b / 1024:.1f} KB)"


def compare(before_path: str, after_path: str, apk_budget_kb: float, dex_budget_kb: float) -> int:
    before = load(before_path)
    after = load(after_path)

    apk_delta = after["totalBytes"] - before["totalBytes"]
    dex_delta = after["dexTotalBytes"] - before["dexTotalBytes"]
    arsc_delta = after["resourcesArscBytes"] - before["resourcesArscBytes"]
    lib_delta = after["libTotalBytes"] - before["libTotalBytes"]

    print(f"APK: {before['apkPath']} -> {after['apkPath']}")
    print(f"Total: {fmt(apk_delta)}")
    print(f"classes*.dex: {fmt(dex_delta)}")
    print(f"resources.arsc: {fmt(arsc_delta)}")
    print(f"lib/: {fmt(lib_delta)}")

    # Per-file top changes
    before_files = {f["name"]: f["size"] for f in before["files"]}
    after_files = {f["name"]: f["size"] for f in after["files"]}
    all_files = set(before_files) | set(after_files)
    changes = []
    for name in all_files:
        b = before_files.get(name, 0)
        a = after_files.get(name, 0)
        if a != b:
            changes.append((name, a - b))
    changes.sort(key=lambda x: abs(x[1]), reverse=True)
    print("\nTop file changes:")
    for name, delta in changes[:20]:
        print(f"  {name}: {fmt(delta)}")

    failed = False
    if abs(apk_delta) > apk_budget_kb * 1024:
        print(f"FAIL: APK delta {apk_delta / 1024:.1f} KB exceeds budget {apk_budget_kb} KB")
        failed = True
    if abs(dex_delta) > dex_budget_kb * 1024:
        print(f"FAIL: DEX delta {dex_delta / 1024:.1f} KB exceeds budget {dex_budget_kb} KB")
        failed = True
    return 1 if failed else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare two APK size baselines.")
    parser.add_argument("before", help="Path to the before baseline JSON.")
    parser.add_argument("after", help="Path to the after baseline JSON.")
    parser.add_argument("--apk-budget-kb", type=float, default=100.0, help="APK budget in KB.")
    parser.add_argument("--dex-budget-kb", type=float, default=50.0, help="DEX budget in KB.")
    args = parser.parse_args()
    return compare(args.before, args.after, args.apk_budget_kb, args.dex_budget_kb)


if __name__ == "__main__":
    sys.exit(main())
