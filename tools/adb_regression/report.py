"""Report and manifest generation for the ADB regression framework."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import parsers, redaction


def _write_json(path: Path, data: Any) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _summary(step_results: list[dict[str, Any]]) -> dict[str, int]:
    summary = {
        "total": len(step_results),
        "passed": 0,
        "failed": 0,
        "manualPending": 0,
        "skipped": 0,
        "errors": 0,
    }
    for r in step_results:
        s = r["status"]
        if s == "PASS":
            summary["passed"] += 1
        elif s == "FAIL":
            summary["failed"] += 1
        elif s == "MANUAL_PENDING":
            summary["manualPending"] += 1
        elif s == "SKIPPED":
            summary["skipped"] += 1
        elif s == "ERROR":
            summary["errors"] += 1
    return summary


def _process_table(ctx: dict[str, Any]) -> str:
    first = ctx.get("snapshots", {}).get("_first", {})
    latest = ctx.get("snapshots", {}).get("_latest", {})
    if not first and not latest:
        return "No process snapshots recorded."
    comp = parsers.compare_pids(first, latest)
    lines = ["| process | before | after | changed | restarted |", "|---|---|---|---|---|"]
    for name, info in comp["processes"].items():
        lines.append(
            f"| {name} | {info['before']} | {info['after']} | {info['changed']} | {info['restarted']} |"
        )
    return "\n".join(lines)


def _hook_table(records: list[dict[str, Any]]) -> str:
    if not records:
        return "No HookSummary records found."
    lines = [
        "| process | stage | installed | classMissing | memberMissing | failed | silentSkipped | dexkitFailed | dexkitNoMatch | prefsUnavailable |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for r in records:
        lines.append(
            f"| {r.get('process')} | {r.get('stage')} | {r.get('installed')} | "
            f"{r.get('classMissing')} | {r.get('memberMissing')} | {r.get('failed')} | "
            f"{r.get('silentSkipped')} | {r.get('dexkitFailed')} | {r.get('dexkitNoMatch')} | {r.get('prefsUnavailable')} |"
        )
    return "\n".join(lines)


def _redact_plan(plan: dict[str, Any]) -> dict[str, Any]:
    return plan


def generate(
    ctx: dict[str, Any],
    plan: dict[str, Any],
    step_results: list[dict[str, Any]],
    out_dir: Path,
    exit_code: int,
) -> None:
    """Generate all report and evidence files for a run."""
    preflight = ctx.get("preflight", {})
    run_id = ctx.get("run_id", "unknown")
    plan_id = plan.get("planId", "unknown")
    simulation = bool(ctx.get("simulation", False))
    timestamp = datetime.now(timezone.utc).isoformat()

    report = {
        "schemaVersion": 1,
        "runId": run_id,
        "planId": plan_id,
        "planDescription": plan.get("description", ""),
        "timestamp": timestamp,
        "simulation": simulation,
        "deviceId": preflight.get("deviceId", "unknown"),
        "exitCode": exit_code,
        "summary": _summary(step_results),
        "steps": step_results,
        "evidenceConfidence": ctx.get("evidence_confidence", "VERIFIED"),
        "selectedLogSource": ctx.get("selected_log_source", parsers.LOG_SOURCE_ADB),
        "lsposedLogFile": ctx.get("lsposed_log_basename", ""),
    }

    _write_json(out_dir / "report.json", report)

    md_lines = [
        "# ADB Regression Report",
        "",
        f"- **Plan**: {plan_id}",
        f"- **Run ID**: {run_id}",
        f"- **Timestamp**: {timestamp}",
        f"- **Simulation**: {simulation}",
        f"- **Device ID**: `{preflight.get('deviceId', 'unknown')}`",
        f"- **Exit code**: {exit_code}",
        "",
        "## Summary",
        "",
        f"- total: {report['summary']['total']}",
        f"- passed: {report['summary']['passed']}",
        f"- failed: {report['summary']['failed']}",
        f"- manualPending: {report['summary']['manualPending']}",
        f"- skipped: {report['summary']['skipped']}",
        f"- errors: {report['summary']['errors']}",
        "",
        "## Steps",
        "",
        "| id | type | status | message |",
        "|---|---|---|---|",
    ]
    for r in step_results:
        md_lines.append(
            f"| {r.get('id')} | {r.get('type')} | {r.get('status')} | {r.get('message')} |"
        )
    (out_dir / "report.md").write_text("\n".join(md_lines) + "\n", encoding="utf-8")

    _write_json(out_dir / "commands.json", ctx.get("commands", []))

    # Derived reports from the selected log source.
    selected_text = ctx.get("selected_log_text", ctx.get("last_logcat", ""))
    selected_source = ctx.get("selected_log_source", parsers.LOG_SOURCE_ADB)
    _write_json(out_dir / "module-load.json", parsers.parse_module_markers(selected_text, source=selected_source))
    hook_records = parsers.parse_hook_summary(selected_text, source=selected_source)
    _write_json(out_dir / "hook-summary.json", {
        "records": hook_records,
        "totals": parsers.hook_summary_totals(hook_records),
    })
    (out_dir / "hook-summary.md").write_text(
        "# Hook Summary\n\n" + _hook_table(hook_records) + "\n",
        encoding="utf-8",
    )

    # Crash detection always comes from the live ADB logcat.
    last_logcat = ctx.get("last_logcat", "")
    _write_json(out_dir / "crash-summary.json", parsers.parse_crash_markers(last_logcat, source=parsers.LOG_SOURCE_ADB))

    # Only filtered, redacted evidence lines are stored; the full user log is not.
    filtered: list[str] = []
    seen: set[str] = set()
    for line in parsers.filter_interesting_lines(selected_text):
        if line not in seen:
            seen.add(line)
            filtered.append(redaction.redact(line, serial=ctx.get("serial")))
    for line in parsers.filter_interesting_lines(last_logcat):
        if line not in seen:
            seen.add(line)
            filtered.append(redaction.redact(line, serial=ctx.get("serial")))
    (out_dir / "filtered-logcat.txt").write_text("\n".join(filtered) + "\n", encoding="utf-8")

    # Process comparison between the first and latest snapshots.
    first = ctx.get("snapshots", {}).get("_first", {})
    latest = ctx.get("snapshots", {}).get("_latest", {})
    _write_json(out_dir / "process-comparison.json", parsers.compare_pids(first, latest))

    # Preflight already written by the runner; ensure it is in the manifest.
    if not (out_dir / "preflight.json").is_file():
        _write_json(out_dir / "preflight.json", preflight)

    # Manifest with all generated files, POSIX paths, stable sorted.
    files: list[dict[str, Any]] = []
    for p in sorted(out_dir.iterdir(), key=lambda x: x.name):
        if not p.is_file() or p.name == "manifest.json":
            continue
        rel = p.relative_to(out_dir).as_posix()
        files.append({
            "path": rel,
            "sha256": _sha256_file(p),
            "size": p.stat().st_size,
        })
    manifest = {
        "schemaVersion": 1,
        "runId": run_id,
        "planId": plan_id,
        "simulation": simulation,
        "files": files,
    }
    _write_json(out_dir / "manifest.json", manifest)
