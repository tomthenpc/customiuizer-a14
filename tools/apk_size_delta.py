#!/usr/bin/env python3
"""Generate a reproducible, machine-verifiable APK size delta report.

Usage:
    python tools/apk_size_delta.py \
        --baseline-commit 55fc2a21... \
        --current-commit 1856c4e2...

This tool does not modify build configuration or source code. It only reads
existing APK size baseline/current JSON files and the build configuration
(app/build.gradle.kts), then writes the delta JSON and markdown report.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
PERF_DIR = REPO_ROOT / "docs" / "performance"
GRADLE_FILE = REPO_ROOT / "app" / "build.gradle.kts"

DELTA_FIELDS = [
    "apkFileBytes",
    "zipEntriesUncompressedBytes",
    "zipEntriesCompressedBytes",
    "fileCount",
    "dexUncompressedBytes",
    "dexCompressedBytes",
    "resourcesArscUncompressedBytes",
    "resourcesArscCompressedBytes",
    "libUncompressedBytes",
    "libCompressedBytes",
    "resUncompressedBytes",
    "resCompressedBytes",
    "assetsUncompressedBytes",
    "assetsCompressedBytes",
    "metaUncompressedBytes",
    "metaCompressedBytes",
    "manifestUncompressedBytes",
    "manifestCompressedBytes",
]


class GradleInfo:
    def __init__(self, gradle_text: str) -> None:
        self.text = gradle_text
        self.applicationId = self._string("applicationId")
        self.minSdk = self._literal_int("minSdk")
        self.targetSdk = self._literal_int("targetSdk")
        self.abi = self._first(r'abiFilters\s*\+?=\s*"([^"]+)"')

        # versionName and versionCode may reference `lastVersionName` / `lastVersion`.
        last_version = self._int_var("lastVersion")
        last_version_name = self._string_var("lastVersionName")
        self.versionCode = self._resolve_or_literal("versionCode", last_version)
        self.versionName = self._resolve_or_string("versionName", last_version_name)

    def _first(self, pattern: str) -> str | None:
        match = re.search(pattern, self.text)
        return match.group(1) if match else None

    def _string(self, key: str) -> str | None:
        return self._first(rf"{re.escape(key)}\s*=\s*\"([^\"]+)\"")

    def _literal_int(self, key: str) -> int | None:
        match = re.search(rf"{re.escape(key)}\s*=\s*(\d+)", self.text)
        return int(match.group(1)) if match else None

    def _int_var(self, key: str) -> int | None:
        match = re.search(rf"val\s+{re.escape(key)}\s*=\s*(\d+)", self.text)
        return int(match.group(1)) if match else None

    def _string_var(self, key: str) -> str | None:
        match = re.search(rf"val\s+{re.escape(key)}\s*=\s*\"([^\"]+)\"", self.text)
        return match.group(1) if match else None

    def _resolve_or_literal(self, key: str, fallback: int | None) -> int | None:
        lit = self._literal_int(key)
        if lit is not None:
            return lit
        match = re.search(rf"{re.escape(key)}\s*=\s*(\w+)", self.text)
        if match and match.group(1) in ("lastVersion",) and fallback is not None:
            return fallback
        return fallback

    def _resolve_or_string(self, key: str, fallback: str | None) -> str | None:
        lit = self._string(key)
        if lit is not None:
            return lit
        match = re.search(rf"{re.escape(key)}\s*=\s*(\w+)", self.text)
        if match and match.group(1) in ("lastVersionName",) and fallback is not None:
            return fallback
        return fallback


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def percent(delta: int, baseline: int) -> float:
    if baseline == 0:
        return 0.0 if delta == 0 else float("inf")
    return (delta / baseline) * 100.0


def trend(delta: int) -> str:
    if delta > 0:
        return "increase"
    if delta < 0:
        return "decrease"
    return "unchanged"


def compute_deltas(baseline: dict, current: dict) -> dict:
    metrics: dict[str, dict] = {}
    for field in DELTA_FIELDS:
        b = baseline[field]
        c = current[field]
        d = c - b
        metrics[field] = {
            "baseline": b,
            "current": c,
            "delta": d,
            "deltaPercent": round(percent(d, b), 6),
            "trend": trend(d),
        }
    return metrics


def zip_entry_diffs(baseline: dict, current: dict) -> dict:
    b_files = {f["name"]: f for f in baseline["files"]}
    c_files = {f["name"]: f for f in current["files"]}

    added = []
    removed = []
    changed = []

    for name, c in c_files.items():
        if name not in b_files:
            added.append({
                "name": name,
                "bucket": c["bucket"],
                "uncompressedSize": c["uncompressedSize"],
                "compressedSize": c["compressedSize"],
            })
            continue
        b = b_files[name]
        if (
            b["uncompressedSize"] != c["uncompressedSize"]
            or b["compressedSize"] != c["compressedSize"]
        ):
            changed.append({
                "name": name,
                "bucket": c["bucket"],
                "baselineUncompressed": b["uncompressedSize"],
                "currentUncompressed": c["uncompressedSize"],
                "uncompressedDelta": c["uncompressedSize"] - b["uncompressedSize"],
                "baselineCompressed": b["compressedSize"],
                "currentCompressed": c["compressedSize"],
                "compressedDelta": c["compressedSize"] - b["compressedSize"],
            })

    for name, b in b_files.items():
        if name not in c_files:
            removed.append({
                "name": name,
                "bucket": b["bucket"],
                "uncompressedSize": b["uncompressedSize"],
                "compressedSize": b["compressedSize"],
            })

    changed_sorted = sorted(changed, key=lambda x: x["compressedDelta"], reverse=True)
    return {
        "added": sorted(added, key=lambda x: x["compressedSize"], reverse=True),
        "removed": sorted(removed, key=lambda x: x["compressedSize"], reverse=True),
        "changed": changed_sorted,
        "topIncreases": [c for c in changed_sorted if c["compressedDelta"] > 0][:20],
        "topDecreases": [c for c in changed_sorted if c["compressedDelta"] < 0][-20:][::-1],
    }


def conclude(debug_metrics: dict, develop_metrics: dict) -> str:
    """Pick one of the five allowed conclusion categories based on observed deltas."""
    significant = any(
        abs(m["delta"]) > 0
        for m in list(debug_metrics.values()) + list(develop_metrics.values())
    )
    if not significant:
        return "NO_MEANINGFUL_CHANGE"
    any_increase = any(m["delta"] > 0 for m in list(debug_metrics.values()) + list(develop_metrics.values()))
    any_decrease = any(m["delta"] < 0 for m in list(debug_metrics.values()) + list(develop_metrics.values()))
    if any_increase and any_decrease:
        return "MIXED_CHANGE"
    if any_increase:
        return "EXPLAINED_INCREASE"
    if any_decrease:
        return "EXPLAINED_DECREASE"
    return "NO_MEANINGFUL_CHANGE"


def generate(
    baseline_commit: str,
    current_commit: str,
    out_json: Path,
    out_md: Path,
) -> int:
    gradle_info = GradleInfo(GRADLE_FILE.read_text(encoding="utf-8"))

    debug_base = load_json(PERF_DIR / "A14_APK_SIZE_BASELINE.json")
    debug_cur = load_json(PERF_DIR / "A14_APK_SIZE_CURRENT.json")
    develop_base = load_json(PERF_DIR / "A14_APK_SIZE_BASELINE_DEVELOP.json")
    develop_cur = load_json(PERF_DIR / "A14_APK_SIZE_CURRENT_DEVELOP.json")

    debug_metrics = compute_deltas(debug_base, debug_cur)
    develop_metrics = compute_deltas(develop_base, develop_cur)
    debug_entry_diffs = zip_entry_diffs(debug_base, debug_cur)
    develop_entry_diffs = zip_entry_diffs(develop_base, develop_cur)

    def enrich_current(raw: dict, variant: str) -> dict:
        return {
            "sourceCommit": current_commit,
            "variant": variant,
            "versionName": gradle_info.versionName,
            "versionCode": gradle_info.versionCode,
            "sha256": raw["sha256"],
            "apkFileBytes": raw["apkFileBytes"],
            "apkPath": raw["apkPath"],
        }

    def enrich_baseline(raw: dict, variant: str) -> dict:
        return {
            "sourceCommit": baseline_commit,
            "variant": variant,
            "sha256": raw["sha256"],
            "apkFileBytes": raw["apkFileBytes"],
            "apkPath": raw["apkPath"],
        }

    build_config = {
        "versionCode": gradle_info.versionCode,
        "versionName": gradle_info.versionName,
        "applicationId": gradle_info.applicationId,
        "minSdk": gradle_info.minSdk,
        "targetSdk": gradle_info.targetSdk,
        "abi": gradle_info.abi,
    }

    conclusion = conclude(debug_metrics, develop_metrics)

    result = {
        "reportVersion": 1,
        "baselineCommit": baseline_commit,
        "currentSourceCommit": current_commit,
        "buildConfig": build_config,
        "conclusion": conclusion,
        "variants": {
            "debug": {
                "description": "Uncompressed, non-R8 development diagnostic build",
                "baseline": enrich_baseline(debug_base, "debug"),
                "current": enrich_current(debug_cur, "debug"),
                "metrics": debug_metrics,
                "entryDiffs": debug_entry_diffs,
            },
            "develop": {
                "description": "R8 + resource-shrinking unsigned build closer to, but not equivalent to, a signed release",
                "baseline": enrich_baseline(develop_base, "develop"),
                "current": enrich_current(develop_cur, "develop"),
                "metrics": develop_metrics,
                "entryDiffs": develop_entry_diffs,
            },
        },
    }

    with out_json.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(result, f, indent=2, ensure_ascii=False)
        f.write("\n")

    out_md.write_text(_render_markdown(result), encoding="utf-8")
    return 0


def _render_markdown(data: dict) -> str:
    lines: list[str] = [
        "# A14 APK Size Delta Report",
        "",
        "## 1. Measurement scope",
        "",
        "This report compares two builds of the same A14 source tree:",
        "",
        f"- Baseline commit: `{data['baselineCommit']}`",
        f"- Current source commit: `{data['currentSourceCommit']}`",
        "",
        "Variants measured:",
        "",
        "- **Debug**: `assembleDebug`, uncompressed, non-R8, diagnostic build.",
        "- **Develop**: `assembleDevelop`, unsigned, R8 + resource-shrinking; closer to a release APK but **not** a signed release.",
        "",
        "## 2. Baseline provenance",
        "",
        "| Variant | Baseline commit | SHA-256 | APK bytes |",
        "| --- | --- | --- | --- |",
    ]
    for variant, v in data["variants"].items():
        b = v["baseline"]
        lines.append(f"| {b['variant']} | `{b['sourceCommit']}` | `{b['sha256']}` | {b['apkFileBytes']} |")

    lines += [
        "",
        "## 3. Current build provenance",
        "",
        "| Variant | Current commit | Version | Version code | SHA-256 | APK bytes |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for variant, v in data["variants"].items():
        c = v["current"]
        lines.append(
            f"| {c['variant']} | `{c['sourceCommit']}` | {c['versionName']} | {c['versionCode']} | "
            f"`{c['sha256']}` | {c['apkFileBytes']} |"
        )

    lines += [
        "",
        "## 4. Build configuration",
        "",
        "| applicationId | minSdk | targetSdk | ABI |",
        "| --- | --- | --- | --- |",
        f"| {data['buildConfig']['applicationId']} | {data['buildConfig']['minSdk']} | {data['buildConfig']['targetSdk']} | {data['buildConfig']['abi']} |",
        "",
        "## 5. Debug comparison",
        "",
        "| Metric | Baseline | Current | Delta | Delta % | Trend |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for metric, vals in data["variants"]["debug"]["metrics"].items():
        lines.append(
            f"| {metric} | {vals['baseline']} | {vals['current']} | "
            f"{vals['delta']} | {vals['deltaPercent']:.4f}% | {vals['trend']} |"
        )

    lines += [
        "",
        "## 6. Develop/R8 comparison",
        "",
        "| Metric | Baseline | Current | Delta | Delta % | Trend |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for metric, vals in data["variants"]["develop"]["metrics"].items():
        lines.append(
            f"| {metric} | {vals['baseline']} | {vals['current']} | "
            f"{vals['delta']} | {vals['deltaPercent']:.4f}% | {vals['trend']} |"
        )

    lines += [
        "",
        "## 7. Bucket-level changes",
        "",
        "### Debug",
        "",
        _entry_summary(data["variants"]["debug"]["entryDiffs"]),
        "",
        "### Develop",
        "",
        _entry_summary(data["variants"]["develop"]["entryDiffs"]),
        "",
        "## 8. Largest entry changes",
        "",
        "### Debug top 20 compressed-size increases",
        "",
        _entry_table(data["variants"]["debug"]["entryDiffs"]["topIncreases"]),
        "",
        "### Debug top 20 compressed-size decreases",
        "",
        _entry_table(data["variants"]["debug"]["entryDiffs"]["topDecreases"]),
        "",
        "### Develop top 20 compressed-size increases",
        "",
        _entry_table(data["variants"]["develop"]["entryDiffs"]["topIncreases"]),
        "",
        "### Develop top 20 compressed-size decreases",
        "",
        _entry_table(data["variants"]["develop"]["entryDiffs"]["topDecreases"]),
        "",
        "## 9. Interpretation",
        "",
        f"Conclusion: `{data['conclusion']}`",
        "",
        "The changes above reflect differences between the baseline build and the current build. "
        "Because the Debug build is uncompressed and does not run R8, it is primarily useful for "
        "diagnosing raw source growth. The Develop build applies R8 and resource shrinking, so "
        "bucket-level shifts there are closer to what a release artifact would experience, but it "
        "remains unsigned and is not equivalent to an official signed release.",
        "",
        "A smaller APK is not automatically a performance improvement, and a larger APK is not "
        "automatically a regression; the interpretation must be anchored to dex, resource, library "
        "and asset bucket changes rather than the headline APK byte count.",
        "",
        "## 10. Limitations",
        "",
        "- This is a static build-size measurement, not a runtime or device performance measurement.",
        "- No real device was exercised during this comparison.",
        "- The Develop variant is unsigned and excludes official signing metadata; it is not a "
          "release-quality APK.",
        "- APK size differences between builds may include nondeterministic build artifacts, "
          "timestamps, and generated auxiliary files.",
        "",
        "## Reproduction commands",
        "",
        "```text",
        "./gradlew --no-daemon clean :app:assembleDebug :app:assembleDevelop",
        "python tools/apk_size_report.py app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk --out docs/performance/A14_APK_SIZE_CURRENT.json",
        "python tools/apk_size_report.py app/build/outputs/apk/develop/CustoMIUIzer-A14-r14.16.1-develop-unsigned.apk --out docs/performance/A14_APK_SIZE_CURRENT_DEVELOP.json",
        f"python tools/apk_size_delta.py --baseline-commit {data['baselineCommit']} --current-commit {data['currentSourceCommit']}",
        "```",
        "",
    ]
    return "\n".join(lines)


def _entry_summary(diffs: dict) -> str:
    return (
        f"- Added: {len(diffs['added'])}\n"
        f"- Removed: {len(diffs['removed'])}\n"
        f"- Changed: {len(diffs['changed'])}"
    )


def _entry_table(entries: list[dict]) -> str:
    if not entries:
        return "_No entries."
    rows = ["| Name | Bucket | Baseline compressed | Current compressed | Delta |", "| --- | --- | --- | --- | --- |"]
    for e in entries:
        baseline = e.get("baselineCompressed", "-")
        current = e.get("currentCompressed", "-")
        rows.append(f"| `{e['name']}` | {e['bucket']} | {baseline} | {current} | {e['compressedDelta']} |")
    return "\n".join(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate APK size delta JSON and report.")
    parser.add_argument("--baseline-commit", required=True, help="40-char baseline SHA")
    parser.add_argument("--current-commit", required=True, help="40-char current source SHA")
    parser.add_argument("--out-json", default=str(PERF_DIR / "A14_APK_SIZE_DELTA.json"))
    parser.add_argument("--out-md", default=str(PERF_DIR / "A14_APK_SIZE_DELTA.md"))
    args = parser.parse_args()

    return generate(
        baseline_commit=args.baseline_commit,
        current_commit=args.current_commit,
        out_json=Path(args.out_json),
        out_md=Path(args.out_md),
    )


if __name__ == "__main__":
    sys.exit(main())
