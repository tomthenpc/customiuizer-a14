"""Parsers for ADB regression logcat and command output."""

from __future__ import annotations

import re
from typing import Any


MODULE_LOAD_RE = re.compile(
    r"CustoMIUIzer\s+(\S+(?:\.\S+)?)\s+\((\d+)\)\s+loaded\s+in\s+(\S+)",
    re.IGNORECASE,
)

# Hook summary lines are expected to carry all counters in one of two forms:
#   [HookSummary] process=... stage=... installed=... classMissing=... ...
#   HookSummary;process;stage;installed;classMissing;...
HOOK_SUMMARY_RE = re.compile(
    r"(?:\[HookSummary\]\s+|\bHookSummary;)"
    r"(?:process=|)"
    r"(?P<process>\S+);?\s*"
    r"(?:stage=|)"
    r"(?P<stage>\S+);?\s*"
    r"(?:installed=|)(?P<installed>\d+);?\s*"
    r"(?:classMissing=|)(?P<classMissing>\d+);?\s*"
    r"(?:memberMissing=|)(?P<memberMissing>\d+);?\s*"
    r"(?:failed=|)(?P<failed>\d+);?\s*"
    r"(?:silentSkipped=|)(?P<silentSkipped>\d+);?\s*"
    r"(?:dexkitFailed=|)(?P<dexkitFailed>\d+);?\s*"
    r"(?:dexkitNoMatch=|)(?P<dexkitNoMatch>\d+);?\s*"
    r"(?:prefsUnavailable=|)(?P<prefsUnavailable>\d+)",
    re.IGNORECASE,
)

# A looser regex for the key=value style with arbitrary order.
HOOK_SUMMARY_KV_RE = re.compile(r"\bHookSummary\b", re.IGNORECASE)

CRASH_MARKERS = (
    "FATAL EXCEPTION",
    "WATCHDOG",
    "system_server crash",
    "SystemUI crash loop",
    "Launcher crash loop",
    "AndroidRuntime",
    "*** FATAL",
)


def parse_module_markers(text: str) -> dict[str, str]:
    """Return a mapping of process name to 'version (code)' for module load markers."""
    markers: dict[str, str] = {}
    for line in text.splitlines():
        m = MODULE_LOAD_RE.search(line)
        if not m:
            continue
        version = m.group(1)
        code = m.group(2)
        process = m.group(3).strip().rstrip(",;.")
        markers[process] = f"{version} ({code})"
    return markers


def _parse_kv_hook(line: str) -> dict[str, Any] | None:
    """Parse a HookSummary key=value form of the summary line.

    Handles the real LSPosed verbose format where the line is prefixed by a
    timestamp and tag, e.g. ``[Pengeek] CustoMIUIzer HookSummary ...``.  Keys
    may appear in any order and only the keys actually present are populated.
    """
    if not HOOK_SUMMARY_KV_RE.search(line):
        return None
    keys = (
        "process", "stage", "installed", "classMissing", "memberMissing",
        "failed", "silentSkipped", "dexkitFailed", "dexkitNoMatch", "prefsUnavailable",
    )
    defaults: dict[str, Any] = {"process": "", "stage": ""}
    record: dict[str, Any] = {k: defaults.get(k, 0) for k in keys}
    found_any = False
    for key in keys:
        pattern = rf"\b{key}=(\S+)"
        m = re.search(pattern, line, re.IGNORECASE)
        if not m:
            continue
        found_any = True
        value = m.group(1).rstrip(";,")
        if key in ("process", "stage"):
            record[key] = value
        else:
            try:
                record[key] = int(value)
            except ValueError:
                return None
    if not found_any:
        return None
    return record


def parse_hook_summary(text: str) -> list[dict[str, Any]]:
    """Parse all HookSummary records from logcat output."""
    records: list[dict[str, Any]] = []
    for line in text.splitlines():
        m = HOOK_SUMMARY_RE.search(line)
        if m:
            record = {
                "process": m.group("process"),
                "stage": m.group("stage"),
                "installed": int(m.group("installed")),
                "classMissing": int(m.group("classMissing")),
                "memberMissing": int(m.group("memberMissing")),
                "failed": int(m.group("failed")),
                "silentSkipped": int(m.group("silentSkipped")),
                "dexkitFailed": int(m.group("dexkitFailed")),
                "dexkitNoMatch": int(m.group("dexkitNoMatch")),
                "prefsUnavailable": int(m.group("prefsUnavailable")),
            }
            records.append(record)
            continue
        record = _parse_kv_hook(line)
        if record:
            records.append(record)
    return records


def hook_summary_totals(records: list[dict[str, Any]]) -> dict[str, int]:
    """Sum all numeric HookSummary counters across records."""
    totals: dict[str, int] = {}
    for rec in records:
        for key in ("installed", "classMissing", "memberMissing", "failed",
                    "silentSkipped", "dexkitFailed", "dexkitNoMatch", "prefsUnavailable"):
            totals[key] = totals.get(key, 0) + rec.get(key, 0)
    return totals


def parse_crash_markers(text: str) -> list[dict[str, str]]:
    """Return crash-like lines found in the text."""
    crashes: list[dict[str, str]] = []
    seen: set[str] = set()
    for line in text.splitlines():
        upper = line.upper()
        for marker in CRASH_MARKERS:
            if marker.upper() in upper:
                if line not in seen:
                    seen.add(line)
                    crashes.append({
                        "line": line,
                        "marker": marker,
                    })
                break
    return crashes


def compare_pids(
    before: dict[str, list[int]],
    after: dict[str, list[int]],
) -> dict[str, Any]:
    """Compare process PIDs before and after an operation."""
    processes: dict[str, Any] = {}
    any_restarted = False
    all_names = set(before.keys()) | set(after.keys())
    for name in sorted(all_names):
        before_pids = set(before.get(name, []))
        after_pids = set(after.get(name, []))
        changed = before_pids != after_pids
        restarted = bool(before_pids and after_pids and not before_pids & after_pids)
        if restarted:
            any_restarted = True
        processes[name] = {
            "before": sorted(before_pids),
            "after": sorted(after_pids),
            "changed": changed,
            "restarted": restarted,
            "alive": bool(after_pids),
        }
    return {
        "processes": processes,
        "anyRestarted": any_restarted,
    }
