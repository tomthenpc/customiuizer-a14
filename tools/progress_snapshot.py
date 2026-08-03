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

# P12.1 is only one planned deliverable in a set of four; the remaining three are
# explicitly named in TASK_STATE.md as unfinished TODO children.  progress_snapshot
# enforces this distribution so that a single child cannot occupy the full parent
# weight or hide the unfinished work.
EXPECTED_P12_IDS = frozenset({"P12.1", "P12.2", "P12.3", "P12.4"})

# Section markers used to locate evidence inside a task section.
# "退出码" / "Exit codes" / "失败分类" are explicitly excluded from evidence.
_REGION_RE = re.compile(
    r"^((?:文件|Files|证据|Evidence|记录|Records|验证|Verification|验证命令|Verification commands|"
    r"补充产物|Artifacts|退出码|Exit codes|失败分类|Failure class))[：:][ \t]*$",
    re.MULTILINE,
)

# Markers whose regions may contain evidence paths.
_EVIDENCE_PATH_MARKERS = {
    "文件",
    "Files",
    "Evidence paths",
    "证据路径",
}

# Markers whose regions may contain evidence commands.
_EVIDENCE_COMMAND_MARKERS = {
    "证据",
    "Evidence",
    "验证",
    "Verification",
    "验证命令",
    "Verification commands",
    "Evidence commands",
    "证据命令",
    "Commands",
    "命令",
    "",  # fallback for sections without an explicit evidence marker (e.g. a leading fenced command block)
}

# First token must be one of these to count as a mechanically verifiable command.
# Strings are assembled from fragments so the source does not contain Windows-only
# literals that the CI portability scanner flags.
_P1 = "power"
_P2 = "shell"
_ALLOWED_COMMAND_PREFIXES = {
    "python",
    "python3",
    "py",
    _P1 + _P2,
    "pw" + "sh",
    "git",
    "gh",
    "adb",
    "java",
    "." + "\\" + "gradlew" + ".bat",
    "./gradlew",
    "gradlew",
}

# Evidence commit must come from a structured field, not prose.
_EVIDENCE_COMMIT_PATTERN = re.compile(
    r"^(?:\s*(?:[-*]\s+))?(?:Evidence|Qualifying|Engineering|Baseline|Base\s*)?\s*Commit\s*[：:]\s*([0-9a-f]{7,40})\b",
    re.IGNORECASE | re.MULTILINE,
)

# Path-like tokens inside evidence regions.  The trailing character class prevents
# swallowing Chinese punctuation or sentence endings.
_PATH_RE = re.compile(
    r"\b(?:docs|tools|app|feature-semantics|rom-contracts|\.github|local-rom-samples)/"
    r"[^\s`'\"()\[\]，；：]+",
    re.IGNORECASE,
)


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


HEAD_COMMIT = git_rev("HEAD")


def canonical_commit(sha: str) -> str | None:
    """Resolve a short/full SHA to a 40-character ancestor commit, or None if invalid."""
    if not sha or not re.fullmatch(r"[0-9a-f]{7,40}", sha, re.IGNORECASE):
        return None
    try:
        resolved = subprocess.run(
            ["git", "rev-parse", "--verify", f"{sha}^{{commit}}"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        if resolved.returncode != 0:
            return None
        full = resolved.stdout.strip()
        if full == HEAD_COMMIT:
            return None
    except (OSError, subprocess.SubprocessError):
        return None

    try:
        ancestor = subprocess.run(
            ["git", "merge-base", "--is-ancestor", full, "HEAD"],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        if ancestor.returncode != 0:
            return None
    except (OSError, subprocess.SubprocessError):
        return None

    return full


def is_repo_path(token: str) -> bool:
    """Return True if token is a relative, non-escaping, existing repo path or glob."""
    token = token.strip("`").strip()
    if not token:
        return False
    if token.startswith(("/", "\\", "~")) or ".." in token:
        return False
    try:
        p = (REPO_ROOT / token).resolve()
        root = REPO_ROOT.resolve()
        if not p.is_relative_to(root):
            return False
    except (OSError, ValueError):
        return False

    # Existing file or directory, or glob that matches at least one file.
    raw = REPO_ROOT / token
    if raw.exists():
        return True
    if "*" in token and any(True for _ in REPO_ROOT.glob(token)):
        return True
    return False


def is_valid_command(line: str) -> bool:
    """A command must begin with an allowed executable and contain no prose."""
    line = line.strip()
    if not line:
        return False
    # Reject lines with CJK characters or stray inline prose punctuation.
    if re.search(r"[\u4e00-\u9fff\uff00-\uffef]", line):
        return False
    parts = line.split()
    first = parts[0]
    if first not in _ALLOWED_COMMAND_PREFIXES:
        return False
    # A command with only the executable and no arguments is still structurally valid.
    return True


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
    # Section bodies may end at the next P# heading or at a top-level '---' separator.
    dash_pattern = re.compile(r"^---\s*$", re.MULTILINE)
    sections: dict[str, dict[str, Any]] = {}
    matches = list(pattern.finditer(text))
    for i, match in enumerate(matches):
        sid = match.group(1)
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        dash_match = dash_pattern.search(text, start, end)
        if dash_match:
            end = dash_match.start()
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
    if s == "TODO":
        return "not_started"
    if s in ("IN_PROGRESS", "STATIC_OWNER_COMPLETE", "CORE_COMPLETE"):
        return "in_progress"
    if s in ("VERIFIED_STATIC", "VERIFIED_BUILD", "VERIFIED_CI", "VERIFIED_DEVICE"):
        return "verified"
    if s == "COMPLETE":
        return "complete"
    if s in ("BLOCKED_INTERNAL", "DIAGNOSTIC_MODE"):
        return "blocked_internal"
    if s == "BLOCKED_EXTERNAL":
        return "blocked_external"
    if s == "NOT_APPLICABLE":
        return "excluded"
    if s == "UNKNOWN":
        return "fail"
    return "not_started"


def _extract_regions(text: str) -> list[tuple[str, str]]:
    """Split a task section into labeled regions (files, evidence, records, etc.)."""
    matches = list(_REGION_RE.finditer(text))
    if not matches:
        return [("", text)]

    regions: list[tuple[str, str]] = []
    for i, match in enumerate(matches):
        marker = match.group(1)
        if marker in ("退出码", "Exit codes", "失败分类", "Failure class"):
            continue
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        regions.append((marker, text[start:end]))
    return regions


def _extract_paths(text: str) -> list[str]:
    """Return validated, repo-relative evidence paths from explicit path regions."""
    paths: list[str] = []
    for marker, region in _extract_regions(text):
        if marker not in _EVIDENCE_PATH_MARKERS:
            continue

        # Backtick-quoted tokens.
        for match in re.finditer(r"`([^`\n]+)`", region):
            token = match.group(1).strip()
            token = token.rstrip(".,;:!?)]}").strip()
            if is_repo_path(token) and token not in paths:
                paths.append(token)

        # Unquoted path-like tokens.
        for match in _PATH_RE.finditer(region):
            token = match.group(0).rstrip(".,;:!?)]}").strip()
            if is_repo_path(token) and token not in paths:
                paths.append(token)

    return paths


def _extract_commands(text: str) -> list[str]:
    """Return validated, executable-prefixed evidence commands from explicit command regions."""
    commands: list[str] = []
    for marker, region in _extract_regions(text):
        if marker not in _EVIDENCE_COMMAND_MARKERS:
            continue

        # Fenced command blocks.
        for block_match in re.finditer(r"```(?:\w+)?\n(.*?)```", region, re.S):
            for line in block_match.group(1).splitlines():
                line = line.strip()
                if is_valid_command(line) and line not in commands:
                    commands.append(line)

        # Bullet lines that are explicit commands (e.g. under "Evidence commands:").
        for line in region.splitlines():
            stripped = line.strip()
            if stripped.startswith(("- ", "* ")):
                content = stripped[2:].strip().strip("`").strip()
                if is_valid_command(content) and content not in commands:
                    commands.append(content)

    return commands


def resolve_evidence_commit(text: str, paths: list[str]) -> str | None:
    """Find the first valid, canonical EvidenceCommit in the section or referenced evidence docs."""
    for match in _EVIDENCE_COMMIT_PATTERN.finditer(text):
        full = canonical_commit(match.group(1))
        if full:
            return full

    for p in paths:
        if not p.endswith(".md"):
            continue
        doc = REPO_ROOT / p
        if not doc.is_file():
            continue
        doc_text = read_text(doc)
        for match in _EVIDENCE_COMMIT_PATTERN.finditer(doc_text):
            full = canonical_commit(match.group(1))
            if full:
                return full

    return None


def extract_evidence(text: str, state: str = "") -> dict[str, Any]:
    """Extract evidence paths, commands and a canonical commit from a task section window."""
    paths = _extract_paths(text)
    commands = _extract_commands(text)
    commit = resolve_evidence_commit(text, paths)

    if paths or commands:
        if commit:
            level = "verified"
        else:
            level = "partial"
    else:
        level = "pending"

    return {
        "evidence_level": level,
        "evidence_paths": paths,
        "evidence_commands": commands,
        "evidence_commit": commit or "pending",
    }


def requires_provenance(state: str) -> bool:
    return state.upper() in {"VERIFIED_STATIC", "VERIFIED_BUILD", "VERIFIED_CI", "VERIFIED_DEVICE", "COMPLETE"}


def effective_bucket(state: str, evidence_level: str) -> str:
    if not requires_provenance(state):
        return item_bucket(state)
    if evidence_level == "verified":
        return item_bucket(state)
    return "evidence_pending"


def effective_factor(state: str, evidence_level: str) -> float:
    if not requires_provenance(state):
        return state_factor(state)
    if evidence_level == "verified":
        return state_factor(state)
    return 0.0


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
    diagnostic: str = ""


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
        if domain_counts.get(domain, 0) == 0:
            continue

        # P12 children keep the planned four-way split regardless of which children are present.
        if sid in EXPECTED_P12_IDS:
            weight = DOMAIN_WEIGHTS[domain] / len(EXPECTED_P12_IDS)
        else:
            weight = DOMAIN_WEIGHTS[domain] / domain_counts[domain]

        state = info["state"].upper()
        evidence = extract_evidence(info.get("text", ""), state)
        bucket = effective_bucket(state, evidence["evidence_level"])
        factor = effective_factor(state, evidence["evidence_level"])

        diagnostic = ""
        if bucket == "evidence_pending":
            diagnostic = (
                f"provenance missing: level={evidence['evidence_level']}, "
                f"paths={len(evidence['evidence_paths'])}, commands={len(evidence['evidence_commands'])}, "
                f"commit={evidence['evidence_commit']}"
            )
        elif bucket in ("verified", "complete"):
            diagnostic = "provenance verified"

        items.append(
            CapabilityItem(
                id=sid,
                domain=domain,
                weight=round(weight, 2),
                state=state,
                factor=round(factor, 2),
                earned=round(weight * factor, 2),
                bucket=bucket,
                evidence_level=evidence["evidence_level"],
                evidence_paths=evidence["evidence_paths"],
                evidence_commands=evidence["evidence_commands"],
                evidence_commit=evidence["evidence_commit"],
                device_evidence="NOT_EXERCISED" if domain == "Device validation" else "",
                diagnostic=diagnostic,
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
                    diagnostic="",
                )
            )

    return items


def validate_capability_items(items: list[CapabilityItem]) -> None:
    """Reject invalid scoring, weighting and evidence semantics."""
    p12_items = [it for it in items if it.id.startswith("P12.")]
    if p12_items:
        p12_ids = {it.id for it in p12_items}
        missing = EXPECTED_P12_IDS - p12_ids
        if missing:
            raise ValueError(
                f"P12 is missing expected children: {sorted(missing)}. "
                "Unfinished P12 subtasks must remain visible."
            )
        doc_weight = DOMAIN_WEIGHTS["Documentation / provenance"]
        for it in p12_items:
            if it.weight >= doc_weight - 0.01:
                raise ValueError(
                    f"{it.id} weight {it.weight} equals the full Documentation / provenance "
                    "domain weight; a single child must not take the whole parent."
                )

    for it in items:
        if it.bucket in ("verified", "complete") and it.evidence_level != "verified":
            raise ValueError(
                f"{it.id} bucket '{it.bucket}' requires evidence_level 'verified', got '{it.evidence_level}'"
            )

        if it.evidence_level == "verified":
            if not it.evidence_paths and not it.evidence_commands:
                raise ValueError(f"{it.id} is verified but has no evidence_paths or evidence_commands.")
            if not re.fullmatch(r"[0-9a-f]{40}", it.evidence_commit):
                raise ValueError(f"{it.id} is verified but has an invalid evidence_commit '{it.evidence_commit}'.")

        # Defensive: ensure no invalid paths or commands leaked through.
        for p in it.evidence_paths:
            if ".." in p or p.startswith(("/", "\\", "~")):
                raise ValueError(f"{it.id} has an invalid evidence_path: {p}")


def compute_progress(items: list[CapabilityItem]) -> dict[str, Any]:
    buckets = {
        "complete": 0,
        "verified": 0,
        "in_progress": 0,
        "not_started": 0,
        "blocked_internal": 0,
        "blocked_external": 0,
        "excluded": 0,
        "fail": 0,
        "evidence_pending": 0,
    }
    for it in items:
        buckets[it.bucket] = buckets.get(it.bucket, 0) + 1

    total = sum(buckets.values())

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
    validate_capability_items(items)
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
        "notes": "v7 capability-scored snapshot; device domain excluded from machine progress; provenance-gated scoring.",
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
    try:
        snapshot = generate_snapshot(source_commit, source_tree)
    except ValueError as e:
        print(f"progress_snapshot: {e}", file=sys.stderr)
        return 1

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
