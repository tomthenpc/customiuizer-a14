#!/usr/bin/env python3
"""Generate a v7 capability-scored progress snapshot from TASK_STATE and SMART_OPERATION_STATE.

Modes:
  --write   generate and write docs/progress/A14_PROGRESS_CURRENT.{json,md}
  --check   compare the generated semantic snapshot with the committed files (read-only)
  --print   print the generated semantic snapshot to stdout (read-only)
  no args   print help and exit with non-zero
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
TASK_STATE = REPO_ROOT / "TASK_STATE.md"
SMART_STATE = REPO_ROOT / "SMART_OPERATION_STATE.md"
OUT_JSON = REPO_ROOT / "docs" / "progress" / "A14_PROGRESS_CURRENT.json"
OUT_MD = REPO_ROOT / "docs" / "progress" / "A14_PROGRESS_CURRENT.md"

STATE_FACTORS = {
    "TODO": 0.0,
    "BLOCKED_INTERNAL": 0.0,
    "DIAGNOSTIC_MODE": 0.0,
    "IN_PROGRESS": 0.50,
    "VERIFIED_STATIC": 0.70,
    "VERIFIED_BUILD": 0.85,
    "VERIFIED_CI": 0.95,
    "VERIFIED_DEVICE": 1.00,
    "COMPLETE": 1.00,
    "STATIC_OWNER_COMPLETE": 0.70,
    "CORE_COMPLETE": 0.70,
    "BLOCKED_EXTERNAL": 0.0,
    "NOT_APPLICABLE": 0.0,
    "UNKNOWN": 0.0,
}

MILESTONE_IDS = {"P14", "P15", "P16"}

DOMAIN_WEIGHTS = {
    "Baseline and control": 8,
    "Runtime architecture / routing / ownership": 22,
    "Runtime safety / lifecycle / concurrency": 18,
    "Performance / memory / APK / R8": 12,
    "ROM intelligence / compatibility": 10,
    "Java / Kotlin boundary": 8,
    "Build / CI / signing / artifacts": 12,
    "Documentation / provenance": 5,
    "Device validation": 5,
}

DOMAIN_MAP = {
    "P0": "Baseline and control",
    "P1": "Baseline and control",
    "P13": "Baseline and control",
    "P2": "Runtime architecture / routing / ownership",
    "P3": "Runtime architecture / routing / ownership",
    "P4": "Java / Kotlin boundary",
    "P9": "Java / Kotlin boundary",
    "P5": "Runtime safety / lifecycle / concurrency",
    "P6": "Runtime safety / lifecycle / concurrency",
    "P7": "Runtime safety / lifecycle / concurrency",
    "P8": "Performance / memory / APK / R8",
    "P10": "ROM intelligence / compatibility",
    "P11": "Build / CI / signing / artifacts",
    "P12": "Documentation / provenance",
}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def git_rev(name: str) -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", name],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=True,
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError:
        return "pending"


def parse_smart_state() -> dict[str, str]:
    text = SMART_STATE.read_text(encoding="utf-8")
    block = re.search(r"```text(.*?)```", text, re.S)
    if not block:
        return {}
    state = {}
    for line in block.group(1).strip().splitlines():
        if ":" in line:
            key, value = line.split(":", 1)
            state[key.strip()] = value.strip()
    return state


def parse_task_sections(text: str) -> dict[str, dict[str, Any]]:
    """Parse top-level and nested P# sections, returning leaves with state and parent."""
    pattern = re.compile(r"^#{1,2} (P\d+(?:\.\d+)?)(?:\s|—|$)", re.MULTILINE)
    sections: dict[str, dict[str, Any]] = {}
    matches = list(pattern.finditer(text))
    for i, match in enumerate(matches):
        sid = match.group(1)
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        window = text[start:end]
        state_match = re.search(r"State: `([^`]+)`", window)
        sections[sid] = {
            "state": state_match.group(1) if state_match else "UNKNOWN",
            "text": text[start:end],
        }

    # Determine children and leaves.
    for sid in sections:
        sections[sid]["children"] = [
            s for s in sections if s != sid and s.startswith(sid + ".")
        ]

    leaves = {
        sid: info
        for sid, info in sections.items()
        if not info["children"] and sid not in MILESTONE_IDS
    }
    return leaves


def parse_issue_table(text: str) -> list[dict[str, str]]:
    table_match = re.search(r"\|\s*ID\s*\|.*\n((?:\|[^\n]+\|\n?)+)", text)
    if not table_match:
        return []

    rows = [
        line
        for line in table_match.group(1).strip().splitlines()
        if line.startswith("|")
    ]
    issues: list[dict[str, str]] = []
    for row in rows:
        cells = [c.strip() for c in row.split("|")][1:-1]
        if len(cells) < 6:
            continue
        issues.append(
            {
                "id": cells[0],
                "priority": cells[1],
                "area": cells[2],
                "state": cells[3],
                "evidence": cells[4],
                "acceptance": cells[5],
            }
        )
    return issues


def state_factor(state: str) -> float:
    return STATE_FACTORS.get(state.upper(), 0.0)


def item_bucket(state: str) -> str:
    s = state.upper()
    if s == "COMPLETE":
        return "complete"
    if s in ("IN_PROGRESS", "STATIC_OWNER_COMPLETE", "CORE_COMPLETE"):
        return "in_progress"
    if s == "BLOCKED_EXTERNAL":
        return "blocked_external"
    if s in ("BLOCKED_INTERNAL", "DIAGNOSTIC_MODE"):
        return "blocked_internal"
    return "not_started"


@dataclass
class CapabilityItem:
    id: str
    domain: str
    weight: float
    state: str
    factor: float
    earned: float
    bucket: str
    evidence_level: str = "pending"
    evidence_paths: list[str] = field(default_factory=list)
    evidence_commands: list[str] = field(default_factory=list)
    evidence_commit: str = "pending"
    device_evidence: str = "NOT_EXERCISED"


def build_capability_items(leaves: dict[str, dict[str, Any]], issues: list[dict[str, str]]) -> list[CapabilityItem]:
    items: list[CapabilityItem] = []

    # Count leaves per domain for equal weight distribution within a domain.
    domain_counts: dict[str, int] = {}
    for sid, info in leaves.items():
        parent = sid.split(".")[0]
        domain = DOMAIN_MAP.get(parent)
        if domain:
            domain_counts[domain] = domain_counts.get(domain, 0) + 1

    # Add leaf tasks.
    for sid, info in leaves.items():
        parent = sid.split(".")[0]
        domain = DOMAIN_MAP.get(parent)
        if not domain:
            continue
        if domain_counts[domain] == 0:
            continue
        weight = DOMAIN_WEIGHTS[domain] / domain_counts[domain]
        state = info["state"].upper()
        factor = state_factor(state)
        items.append(
            CapabilityItem(
                id=sid,
                domain=domain,
                weight=round(weight, 2),
                state=state,
                factor=round(factor, 2),
                earned=round(weight * factor, 2),
                bucket=item_bucket(state),
                evidence_commit="pending",
                device_evidence="NOT_EXERCISED" if domain == "Device validation" else "",
            )
        )

    # Add the only device-validation issue as the domain item.
    for issue in issues:
        if issue["id"] == "DEVICE-001":
            weight = DOMAIN_WEIGHTS["Device validation"]
            state = issue["state"].upper()
            factor = state_factor(state)
            items.append(
                CapabilityItem(
                    id=issue["id"],
                    domain="Device validation",
                    weight=weight,
                    state=state,
                    factor=round(factor, 2),
                    earned=round(weight * factor, 2),
                    bucket=item_bucket(state),
                    evidence_commit="pending",
                    device_evidence="NOT_EXERCISED",
                )
            )

    return items


def compute_progress(items: list[CapabilityItem]) -> dict[str, Any]:
    buckets = {
        "complete": 0,
        "in_progress": 0,
        "not_started": 0,
        "blocked_internal": 0,
        "blocked_external": 0,
    }
    for it in items:
        buckets[it.bucket] = buckets.get(it.bucket, 0) + 1

    total = sum(buckets.values())
    if sum(buckets.values()) != total:
        # Defensive; sum should equal total by construction.
        pass

    domain_scores: dict[str, dict[str, float]] = {}
    for d in DOMAIN_WEIGHTS:
        domain_items = [it for it in items if it.domain == d]
        if not domain_items:
            domain_scores[d] = {"weight": DOMAIN_WEIGHTS[d], "earned": 0.0, "percent": 0.0}
            continue
        earned = sum(it.earned for it in domain_items)
        weight = DOMAIN_WEIGHTS[d]
        domain_scores[d] = {
            "weight": weight,
            "earned": round(earned, 2),
            "percent": round(earned / weight * 100, 1) if weight else 0.0,
        }

    project_total = sum(DOMAIN_WEIGHTS.values())
    project_earned = sum(it.earned for it in items)
    project_progress = round(project_earned / project_total * 100, 1) if project_total else 0.0

    machine_items = [it for it in items if it.domain != "Device validation"]
    machine_total = project_total - DOMAIN_WEIGHTS["Device validation"]
    machine_earned = sum(it.earned for it in machine_items)
    machine_progress = round(machine_earned / machine_total * 100, 1) if machine_total else 0.0

    open_p0 = sum(1 for it in items if it.id.startswith("P0") and it.bucket != "complete")
    open_p1 = sum(
        1
        for it in items
        if any(it.id.startswith(p) for p in ("P1", "ALG-", "LIFECYCLE-")) and it.bucket != "complete"
    )

    return {
        "taskCounts": {
            "total": total,
            **buckets,
        },
        "domainScores": domain_scores,
        "projectProgressPercent": project_progress,
        "machineProgressPercent": machine_progress,
        "openP0": open_p0,
        "openP1": open_p1,
        "externalBlocks": buckets["blocked_external"],
    }


def stage_from_progress(machine: float) -> str:
    if machine < 30:
        return "BASELINE_AND_CONTROL"
    if machine < 60:
        return "ARCHITECTURE_AND_ROUTING"
    if machine < 80:
        return "INTEGRATION_AND_EVIDENCE"
    return "FINALIZATION_AND_DEVICE"


def generate_snapshot(source_commit: str, source_tree: str) -> dict[str, Any]:
    smart = parse_smart_state()
    task_text = read_text(TASK_STATE)
    leaves = parse_task_sections(task_text)
    issues = parse_issue_table(task_text)
    items = build_capability_items(leaves, issues)
    progress = compute_progress(items)

    verified_tree = smart.get("LastVerifiedTree", "pending")
    if not verified_tree or verified_tree.endswith("^{tree}"):
        verified_tree = "pending"

    return {
        "schemaVersion": 7,
        "generatedAt": datetime.now(timezone.utc).astimezone().isoformat(),
        "sourceCommit": source_commit,
        "sourceTree": source_tree,
        "verifiedTree": verified_tree,
        "verifiedMode": smart.get("LastVerifiedMode", "pending"),
        "ciState": smart.get("LastCIState", "NOT_CONFIGURED"),
        "ciRun": smart.get("LastCIRun", ""),
        "ciJob": smart.get("LastCIJob", ""),
        "ciCommit": smart.get("LastCICommit", ""),
        "projectProgress": progress["projectProgressPercent"],
        "machineProgress": progress["machineProgressPercent"],
        "stage": stage_from_progress(progress["machineProgressPercent"]),
        "domainScores": progress["domainScores"],
        "capabilityItems": [asdict(it) for it in items],
        "openP0": progress["openP0"],
        "openP1": progress["openP1"],
        "externalBlocks": progress["externalBlocks"],
        "notes": "v7 capability-scored snapshot; device domain excluded from machine progress.",
    }


def build_markdown(snapshot: dict[str, Any]) -> str:
    md = ["# A14 Progress Current (v7)\n\n"]
    md.append("```text\n")
    md.append(f"GeneratedAt: {snapshot['generatedAt']}\n")
    md.append(f"SourceCommit: {snapshot['sourceCommit']}\n")
    md.append(f"SourceTree: {snapshot['sourceTree']}\n")
    md.append(f"VerifiedTree: {snapshot['verifiedTree']}\n")
    md.append(f"VerifiedMode: {snapshot['verifiedMode']}\n")
    md.append(f"CIState: {snapshot['ciState']}\n")
    md.append(f"CIRun: {snapshot['ciRun']}\n")
    md.append(f"CIJob: {snapshot['ciJob']}\n")
    md.append(f"CICommit: {snapshot['ciCommit']}\n")
    md.append("```\n\n")

    md.append("## Progress\n\n")
    md.append(f"- ProjectProgress: {snapshot['projectProgress']}%\n")
    md.append(f"- MachineProgress: {snapshot['machineProgress']}%\n")
    md.append(f"- Stage: {snapshot['stage']}\n")
    md.append(f"- OpenP0: {snapshot['openP0']}\n")
    md.append(f"- OpenP1: {snapshot['openP1']}\n")
    md.append(f"- ExternalBlocks: {snapshot['externalBlocks']}\n\n")

    md.append("## Domain Scores\n\n")
    md.append("| Domain | Weight | Earned | Percent |\n")
    md.append("|---|---:|---:|---:|\n")
    for domain, scores in snapshot["domainScores"].items():
        md.append(f"| {domain} | {scores['weight']} | {scores['earned']} | {scores['percent']}% |\n")

    md.append("\n## Capability Items\n\n")
    md.append("| ID | Domain | Weight | State | Factor | Earned |\n")
    md.append("|---|---|---|---:|---:|---:|\n")
    for it in snapshot["capabilityItems"]:
        md.append(
            f"| {it['id']} | {it['domain']} | {it['weight']} | {it['state']} | {it['factor']} | {it['earned']} |\n"
        )

    md.append("\n## Notes\n\n")
    md.append(f"{snapshot['notes']}\n")

    return "".join(md)


def normalize_markdown(md: str) -> str:
    lines = []
    for line in md.splitlines():
        for key in (
            "GeneratedAt",
            "SourceCommit",
            "SourceTree",
            "VerifiedTree",
            "VerifiedMode",
            "CIState",
            "CIRun",
            "CIJob",
            "CICommit",
        ):
            if re.match(rf"^\s*-?\s*{re.escape(key)}:\s", line):
                line = f"{key}: <volatile>"
                break
        lines.append(line)
    return "\n".join(lines)


def volatile_fields() -> set[str]:
    return {
        "generatedAt",
        "sourceCommit",
        "sourceTree",
        "verifiedTree",
        "ciState",
        "ciRun",
        "ciJob",
        "ciCommit",
    }


def check_snapshot(snapshot: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if not OUT_JSON.is_file() or not OUT_MD.is_file():
        errors.append("Progress snapshot files are missing; run --write to generate")
        return errors

    try:
        existing_json = json.loads(OUT_JSON.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as e:
        errors.append(f"Could not read existing {OUT_JSON}: {e}")
        return errors

    fresh = snapshot
    ignore = volatile_fields()

    for key in set(fresh.keys()) | set(existing_json.keys()):
        if key in ignore:
            continue
        if key not in fresh:
            errors.append(f"Generated snapshot missing key {key}")
        elif key not in existing_json:
            errors.append(f"Existing snapshot missing key {key}")
        elif fresh[key] != existing_json[key]:
            errors.append(f"Progress snapshot drift on {key}: generated differs from committed")

    existing_md = OUT_MD.read_text(encoding="utf-8")
    expected_md = build_markdown(snapshot)
    if normalize_markdown(existing_md) != normalize_markdown(expected_md):
        errors.append("Progress markdown does not match generated content")

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate or check A14 v7 progress snapshot")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--write", action="store_true", help="write generated snapshot to disk")
    group.add_argument("--check", action="store_true", help="check existing snapshot is fresh (read-only)")
    group.add_argument("--print", action="store_true", help="print generated snapshot to stdout (read-only)")
    args = parser.parse_args(argv)

    if not (args.write or args.check or args.print):
        parser.print_help()
        return 2

    source_commit = git_rev("HEAD")
    source_tree = git_rev("HEAD^{tree}")
    snapshot = generate_snapshot(source_commit, source_tree)

    if args.print:
        print(json.dumps(snapshot, indent=2))
        return 0

    if args.check:
        errors = check_snapshot(snapshot)
        if errors:
            print("Progress snapshot drift:")
            for e in errors:
                print(f"  - {e}")
            return 1
        print("Progress snapshot is fresh.")
        return 0

    if args.write:
        OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
        OUT_JSON.write_text(json.dumps(snapshot, indent=2), encoding="utf-8", newline="\n")
        OUT_MD.write_text(build_markdown(snapshot), encoding="utf-8", newline="\n")
        print(f"Wrote {OUT_JSON}")
        print(f"Wrote {OUT_MD}")
        return 0

    # Unreachable, but defensive.
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
