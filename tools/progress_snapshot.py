#!/usr/bin/env python3
"""Generate a progress snapshot from TASK_STATE and SMART_OPERATION_STATE.

Outputs:
- docs/progress/A14_PROGRESS_CURRENT.json
- docs/progress/A14_PROGRESS_CURRENT.md
"""

from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TASK_STATE = REPO_ROOT / "TASK_STATE.md"
SMART_STATE = REPO_ROOT / "SMART_OPERATION_STATE.md"
OUT_JSON = REPO_ROOT / "docs" / "progress" / "A14_PROGRESS_CURRENT.json"
OUT_MD = REPO_ROOT / "docs" / "progress" / "A14_PROGRESS_CURRENT.md"


def parse_smart_state() -> dict:
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


def parse_task_state() -> list[dict]:
    text = TASK_STATE.read_text(encoding="utf-8")
    sections = re.split(r"\n#+ ", text)[1:]
    tasks = []
    for section in sections:
        title_match = re.match(r"(.+)\n", section)
        state_match = re.search(r"State:\s*`([^`]+)`", section)
        if title_match and state_match:
            title = title_match.group(1).strip()
            state = state_match.group(1)
            tasks.append({"title": title, "state": state})
    return tasks


def count_issue_states(text: str) -> dict:
    counts = {}
    for m in re.finditer(r"\|\s*([A-Z]+-[0-9]+)\s*\|\s*P[0-9]+\s*\|\s*[^|]+\|\s*([A-Z_]+)", text):
        state = m.group(2)
        counts[state] = counts.get(state, 0) + 1
    return counts


def compute_progress(tasks: list[dict], issue_counts: dict) -> dict:
    total = len(tasks)
    complete = sum(1 for t in tasks if t["state"] == "COMPLETE")
    in_progress = sum(1 for t in tasks if t["state"] == "IN_PROGRESS")
    not_started = sum(1 for t in tasks if t["state"] in ("TODO", "PENDING"))

    issue_total = sum(issue_counts.values())
    issue_complete = sum(
        v for k, v in issue_counts.items() if k in ("COMPLETE", "CORE_COMPLETE")
    )

    return {
        "taskCounts": {
            "total": total,
            "complete": complete,
            "inProgress": in_progress,
            "notStarted": not_started,
        },
        "issueCounts": issue_counts,
        "issueTotal": issue_total,
        "issueComplete": issue_complete,
        "projectProgressPercent": round(complete / total * 100, 1) if total else 0.0,
        "machineProgressPercent": round((complete + in_progress * 0.5) / total * 100, 1) if total else 0.0,
    }


def generate_snapshot() -> tuple[dict, str]:
    """Generate the current snapshot and Markdown content."""
    smart = parse_smart_state()
    tasks = parse_task_state()
    text = TASK_STATE.read_text(encoding="utf-8")
    issue_counts = count_issue_states(text)
    progress = compute_progress(tasks, issue_counts)

    now = datetime.now(timezone.utc).astimezone().isoformat()

    snapshot = {
        "schemaVersion": 1,
        "generatedAt": now,
        "smartState": smart,
        "progress": progress,
        "tasks": tasks,
    }

    md = [
        "# A14 Progress Current\n\n",
        "```text\n",
        f"GeneratedAt: {now}\n",
        "```\n\n",
        "## SMART State\n\n",
        "| Key | Value |\n|---|---|\n",
    ]
    for key, value in smart.items():
        md.append(f"| {key} | {value} |\n")

    md += [
        "\n## Progress\n\n",
        f"- ProjectProgress: {progress['projectProgressPercent']}%\n",
        f"- MachineProgress: {progress['machineProgressPercent']}%\n",
        f"- Tasks: {progress['taskCounts']['complete']} COMPLETE / {progress['taskCounts']['inProgress']} IN_PROGRESS / {progress['taskCounts']['notStarted']} not started of {progress['taskCounts']['total']}\n",
        f"- Issues: {progress['issueComplete']} complete / {progress['issueTotal']} total\n",
        "\n## Tasks\n\n",
        "| Task | State |\n|---|---|\n",
    ]
    for t in tasks:
        md.append(f"| {t['title']} | {t['state']} |\n")

    return snapshot, "".join(md)


def check_snapshot() -> int:
    """Return 0 if the generated snapshot matches the committed files."""
    snapshot, md = generate_snapshot()
    json_fresh = json.dumps(snapshot, indent=2)
    md_fresh = md

    errors = []
    if OUT_JSON.is_file():
        existing_json = OUT_JSON.read_text(encoding="utf-8")
        # `generatedAt` is allowed to drift.
        existing = json.loads(existing_json)
        fresh = json.loads(json_fresh)
        existing.pop("generatedAt", None)
        fresh.pop("generatedAt", None)
        if existing != fresh:
            errors.append(f"{OUT_JSON.relative_to(REPO_ROOT)} is stale")
    else:
        errors.append(f"{OUT_JSON.relative_to(REPO_ROOT)} is missing")

    if OUT_MD.is_file():
        existing_md = OUT_MD.read_text(encoding="utf-8")
        if not existing_md.startswith("# A14 Progress Current"):
            errors.append(f"{OUT_MD.relative_to(REPO_ROOT)} is malformed")
    else:
        errors.append(f"{OUT_MD.relative_to(REPO_ROOT)} is missing")

    if errors:
        print("Progress snapshot drift:")
        for e in errors:
            print(f"  - {e}")
        return 1

    print("Progress snapshot is fresh.")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate or check A14 progress snapshot")
    parser.add_argument("--check", action="store_true", help="check existing snapshot is up to date")
    args = parser.parse_args(argv)

    if args.check:
        return check_snapshot()

    snapshot, md = generate_snapshot()

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(snapshot, indent=2), encoding="utf-8")
    OUT_MD.write_text(md, encoding="utf-8")

    print(f"Wrote {OUT_JSON}")
    print(f"Wrote {OUT_MD}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
