#!/usr/bin/env python3
"""APK size baseline report for CustoMIUIzer A14.

Outputs a machine-readable JSON and a human-readable Markdown summary of an
APK's contents.  The Markdown/JSON pair is intended to be committed so that
future changes can be compared with `tools/compare_apk_size.py`.
"""

import argparse
import hashlib
import json
import os
import zipfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def collect_apk_info(apk_path: str) -> dict[str, Any]:
    total_size = os.path.getsize(apk_path)
    info: dict[str, Any] = {
        "apkPath": os.path.abspath(apk_path),
        "sha256": sha256(apk_path),
        "totalBytes": total_size,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "files": [],
        "byExtension": defaultdict(int),
        "byDirectory": defaultdict(int),
    }

    with zipfile.ZipFile(apk_path, "r") as zf:
        for zi in zf.infolist():
            name = zi.filename
            size = zi.file_size
            compressed = zi.compress_size
            info["files"].append(
                {
                    "name": name,
                    "size": size,
                    "compressed": compressed,
                    "ratio": round(compressed / size, 4) if size else 1.0,
                }
            )

            # directory bucket
            if "/" in name:
                top_dir = name.split("/", 1)[0]
            else:
                top_dir = "_root_"
            info["byDirectory"][top_dir] += size

            # extension bucket
            _, ext = os.path.splitext(name)
            if not ext:
                ext = "(none)"
            info["byExtension"][ext] += size

    # named summary buckets
    info["dexTotalBytes"] = sum(
        f["size"] for f in info["files"] if f["name"].startswith("classes") and f["name"].endswith(".dex")
    )
    info["resourcesArscBytes"] = next(
        (f["size"] for f in info["files"] if f["name"] == "resources.arsc"), 0
    )
    info["libTotalBytes"] = info["byDirectory"].get("lib", 0)
    info["resTotalBytes"] = info["byDirectory"].get("res", 0)
    info["assetsTotalBytes"] = info["byDirectory"].get("assets", 0)
    info["metaInfTotalBytes"] = info["byDirectory"].get("META-INF", 0)

    # top files
    info["topFiles"] = sorted(info["files"], key=lambda x: x["size"], reverse=True)[:50]
    info["topExtensions"] = dict(
        sorted(info["byExtension"].items(), key=lambda x: x[1], reverse=True)[:20]
    )
    info["topDirectories"] = dict(
        sorted(info["byDirectory"].items(), key=lambda x: x[1], reverse=True)
    )

    # method estimate: use top 20 packages from classes.dex? Not available without dex parsing.
    info["fileCount"] = len(info["files"])
    info["methodCountEstimate"] = "not_available_without_dex_parser"

    return info


def write_json(info: dict[str, Any], out_path: str) -> None:
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(info, f, indent=2, default=str, sort_keys=False)
        f.write("\n")


def write_markdown(info: dict[str, Any], out_path: str) -> None:
    def fmt(b: int) -> str:
        return f"{b:,} bytes ({b / 1024 / 1024:.2f} MB)"

    lines = [
        "# A14 APK Size Baseline",
        "",
        f"APK: `{info['apkPath']}`",
        f"SHA-256: `{info['sha256']}`",
        f"Generated at: {info['generatedAtUtc']} UTC",
        "",
        "## Totals",
        "",
        f"- **Total size**: {fmt(info['totalBytes'])}",
        f"- **File count**: {info['fileCount']}",
        f"- **classes*.dex**: {fmt(info['dexTotalBytes'])}",
        f"- **resources.arsc**: {fmt(info['resourcesArscBytes'])}",
        f"- **lib/**: {fmt(info['libTotalBytes'])}",
        f"- **res/**: {fmt(info['resTotalBytes'])}",
        f"- **assets/**: {fmt(info['assetsTotalBytes'])}",
        f"- **META-INF/**: {fmt(info['metaInfTotalBytes'])}",
        "",
        "## Top directories",
        "",
        "| Directory | Bytes |",
        "|---|---|---|",
    ]
    for name, size in info["topDirectories"].items():
        lines.append(f"| {name} | {size} |")

    lines.extend(
        [
            "",
            "## Top extensions",
            "",
            "| Extension | Bytes |",
            "|---|---|---|",
        ]
    )
    for ext, size in info["topExtensions"].items():
        lines.append(f"| {ext} | {size} |")

    lines.extend(
        [
            "",
            "## Top 50 files",
            "",
            "| File | Size | Compressed |",
            "|---|---|---|",
        ]
    )
    for f in info["topFiles"]:
        lines.append(f"| `{f['name']}` | {f['size']} | {f['compressed']} |")

    lines.append("")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate an APK size baseline report.")
    parser.add_argument(
        "--apk",
        default="app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk",
        help="Path to the APK to analyze.",
    )
    parser.add_argument(
        "--out-dir",
        default="docs/performance",
        help="Directory to write the JSON and Markdown reports.",
    )
    args = parser.parse_args()

    apk_path = Path(args.apk).resolve()
    out_dir = Path(args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    if not apk_path.is_file():
        raise SystemExit(f"APK not found: {apk_path}")

    info = collect_apk_info(str(apk_path))

    json_path = out_dir / "A14_APK_SIZE_BASELINE.json"
    md_path = out_dir / "A14_APK_SIZE_BASELINE.md"

    write_json(info, str(json_path))
    write_markdown(info, str(md_path))

    print(f"Wrote {json_path}")
    print(f"Wrote {md_path}")
    print(f"APK total: {info['totalBytes']:,} bytes")
    print(f"classes*.dex: {info['dexTotalBytes']:,} bytes")
    print(f"resources.arsc: {info['resourcesArscBytes']:,} bytes")
    print(f"lib/: {info['libTotalBytes']:,} bytes")


if __name__ == "__main__":
    main()
