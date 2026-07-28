#!/usr/bin/env python3
"""Compare memory baseline summary JSONs across versions or scenarios.

Usage:
    python tools/compare-memory-baseline.py \
        --baseline .devin/memory-audit/summary_baseline_disabled.json \
        --current  .devin/memory-audit/summary_current_user_config.json \
        --output   .devin/memory-audit/comparison.md

The script reads the `samples` arrays in each summary, computes the median PSS,
RSS, Java heap, native heap, private dirty and FD count per target, and writes
a Markdown table. It highlights values that differ by more than a configurable
percentage and flags linear growth across the current scenario's samples.
"""

import argparse
import json
import statistics
import sys
from pathlib import Path


def load_summary(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def median(values: list[float | int | None]) -> float | None:
    nums = [v for v in values if v is not None]
    return statistics.median(nums) if nums else None


def pct_diff(a: float | int | None, b: float | int | None) -> float | None:
    if a is None or b is None or a == 0:
        return None
    return (b - a) / a * 100.0


def extract_per_target(summary: dict) -> dict:
    targets: dict[str, list[dict]] = {}
    for sample in summary.get("samples", []):
        target = sample.get("target") or sample.get("process_name") or "unknown"
        targets.setdefault(target, []).append(sample)

    result = {}
    for target, samples in targets.items():
        result[target] = {
            "pss_kb": median([s["meminfo"].get("total_pss_kb") for s in samples]),
            "rss_kb": median([s["meminfo"].get("total_rss_kb") for s in samples]),
            "java_heap_kb": median([s["meminfo"].get("java_heap_kb") for s in samples]),
            "native_heap_kb": median([s["meminfo"].get("native_heap_kb") for s in samples]),
            "private_dirty_kb": median([s["meminfo"].get("private_dirty_kb") for s in samples]),
            "fd_count": median([s.get("fd_count") for s in samples]),
            "sample_count": len(samples),
        }
    return result


def detect_linear_growth(summary: dict, threshold_pct: float = 5.0) -> list[str]:
    """Return a list of target/fields where values increase across consecutive samples."""
    warnings: list[str] = []
    by_target: dict[str, list[dict]] = {}
    for sample in summary.get("samples", []):
        target = sample.get("target") or sample.get("process_name") or "unknown"
        by_target.setdefault(target, []).append(sample)

    fields = [
        ("total_pss_kb", ["meminfo", "total_pss_kb"]),
        ("total_rss_kb", ["meminfo", "total_rss_kb"]),
        ("java_heap_kb", ["meminfo", "java_heap_kb"]),
        ("native_heap_kb", ["meminfo", "native_heap_kb"]),
    ]

    for target, samples in by_target.items():
        samples = sorted(samples, key=lambda s: s.get("sample", 0))
        if len(samples) < 3:
            continue
        for name, path in fields:
            values = []
            for s in samples:
                v = s
                for key in path:
                    v = v.get(key) if isinstance(v, dict) else None
                    if v is None:
                        break
                values.append(v)

            if len(values) < 3 or None in values:
                continue

            if all(values[i] < values[i + 1] for i in range(len(values) - 1)):
                growth = pct_diff(values[0], values[-1])
                if growth and growth >= threshold_pct:
                    warnings.append(
                        f"{target} {name}: monotonic increase {values[0]} -> {values[-1]} "
                        f"({growth:+.1f}%) across {len(values)} samples"
                    )
    return warnings


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare memory baseline summaries")
    parser.add_argument("--baseline", required=True, type=Path, help="Baseline summary JSON")
    parser.add_argument("--current", required=True, type=Path, help="Current summary JSON")
    parser.add_argument("--output", required=True, type=Path, help="Output Markdown file")
    parser.add_argument("--threshold", type=float, default=10.0, help="Percentage threshold for flagging differences")
    args = parser.parse_args()

    baseline = load_summary(args.baseline)
    current = load_summary(args.current)

    baseline_metrics = extract_per_target(baseline)
    current_metrics = extract_per_target(current)

    all_targets = sorted(set(baseline_metrics) | set(current_metrics))

    lines = [
        "# Memory Baseline Comparison",
        "",
        f"- Baseline scenario: `{baseline.get('scenario', 'unknown')}`",
        f"- Current scenario: `{current.get('scenario', 'unknown')}`",
        f"- Diff threshold: {args.threshold}%",
        "",
        "| Target | Metric | Baseline | Current | Diff |",
        "| --- | --- | --- | --- | --- |",
    ]

    for target in all_targets:
        base = baseline_metrics.get(target, {})
        cur = current_metrics.get(target, {})
        for metric in ["pss_kb", "rss_kb", "java_heap_kb", "native_heap_kb", "private_dirty_kb", "fd_count"]:
            b = base.get(metric)
            c = cur.get(metric)
            diff = pct_diff(b, c)

            b_str = f"{b:.0f}" if b is not None else "n/a"
            c_str = f"{c:.0f}" if c is not None else "n/a"
            if diff is not None:
                d_str = f"{diff:+.1f}%"
                if abs(diff) >= args.threshold:
                    d_str = f"**{d_str}**"
            else:
                d_str = "n/a"

            lines.append(f"| {target} | {metric} | {b_str} | {c_str} | {d_str} |")

    warnings = detect_linear_growth(current)
    if warnings:
        lines.extend(["", "## Linear Growth Warnings (current scenario)", ""])
        for w in warnings:
            lines.append(f"- {w}")
    else:
        lines.extend(["", "## Linear Growth Warnings (current scenario)", "", "None detected."])

    lines.extend([
        "",
        "## Interpretation",
        "",
        "- PSS/RSS differences within the threshold may be system noise.",
        "- Sustained monotonic growth across samples within one scenario is a stronger",
        "  signal of a leak or repeated registration than a single large value.",
        "- Compare against the same device, ROM, module configuration and uptime.",
    ])

    args.output.write_text("\n".join(lines), encoding="utf-8")
    print(f"Comparison written to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
