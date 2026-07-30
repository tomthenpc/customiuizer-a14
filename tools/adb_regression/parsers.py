"""Parsers for ADB regression logcat and command output."""

from __future__ import annotations

import re
from typing import Any


LOG_SOURCE_ADB = "ADB_LOGCAT"
LOG_SOURCE_LSP = "LSPOSED_VERBOSE"

_TIMESTAMP_RE = re.compile(
    r"(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3,6}|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3,6})\b"
)

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

STAGE_NORMALIZATION = {
    "onPackageReady": "package-ready",
    "onSystemServerStarting": "system-server-finished",
    "post-init": "post-init",
    "post-attach": "post-attach",
}

PROCESS_NORMALIZATION = {
    "system": "system_server",
}


def _parse_timestamp(line: str) -> str | None:
    m = _TIMESTAMP_RE.search(line)
    return m.group(1) if m else None


def _normalize_stage(raw: str) -> str:
    return STAGE_NORMALIZATION.get(raw, raw)


def _normalize_process(raw: str) -> str:
    return PROCESS_NORMALIZATION.get(raw, raw)


def _record(
    source: str,
    timestamp: str | None,
    raw_process: str,
    raw_stage: str,
    normalized_stage: str,
    **extra: Any,
) -> dict[str, Any]:
    return {
        "source": source,
        "timestamp": timestamp,
        "process": _normalize_process(raw_process),
        "rawProcess": raw_process,
        "rawStage": raw_stage,
        "normalizedStage": normalized_stage,
        **extra,
    }


def parse_module_markers(text: str, source: str = LOG_SOURCE_ADB) -> list[dict[str, Any]]:
    """Return a list of module-load marker records with source tracking."""
    records: list[dict[str, Any]] = []
    for line in text.splitlines():
        m = MODULE_LOAD_RE.search(line)
        if not m:
            continue
        version = m.group(1)
        code = m.group(2)
        raw_process = m.group(3).strip().rstrip(",;.")
        records.append(_record(
            source,
            _parse_timestamp(line),
            raw_process,
            "",
            "",
            version=version,
            code=code,
            load=f"{version} ({code})",
        ))
    return records


def _parse_kv_hook(line: str, source: str) -> dict[str, Any] | None:
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
    raw_stage = record["stage"]
    for key in keys:
        pattern = rf"\b{key}=(\S+)"
        m = re.search(pattern, line, re.IGNORECASE)
        if not m:
            continue
        found_any = True
        value = m.group(1).rstrip(";,")
        if key == "process":
            record[key] = value
        elif key == "stage":
            raw_stage = value
            record["stage"] = _normalize_stage(value)
        else:
            try:
                record[key] = int(value)
            except ValueError:
                return None
    if not found_any:
        return None
    raw_process = record["process"]
    record["process"] = _normalize_process(raw_process)
    record["rawProcess"] = raw_process
    record["rawStage"] = raw_stage
    record["normalizedStage"] = record["stage"]
    record["source"] = source
    record["timestamp"] = _parse_timestamp(line)
    return record


def parse_hook_summary(text: str, source: str = LOG_SOURCE_ADB) -> list[dict[str, Any]]:
    """Parse all HookSummary records from logcat output."""
    records: list[dict[str, Any]] = []
    for line in text.splitlines():
        m = HOOK_SUMMARY_RE.search(line)
        if m:
            raw_stage = m.group("stage")
            normalized = _normalize_stage(raw_stage)
            records.append(_record(
                source,
                _parse_timestamp(line),
                m.group("process"),
                raw_stage,
                normalized,
                stage=normalized,
                installed=int(m.group("installed")),
                classMissing=int(m.group("classMissing")),
                memberMissing=int(m.group("memberMissing")),
                failed=int(m.group("failed")),
                silentSkipped=int(m.group("silentSkipped")),
                dexkitFailed=int(m.group("dexkitFailed")),
                dexkitNoMatch=int(m.group("dexkitNoMatch")),
                prefsUnavailable=int(m.group("prefsUnavailable")),
            ))
            continue
        record = _parse_kv_hook(line, source)
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


def parse_crash_markers(text: str, source: str = LOG_SOURCE_ADB) -> list[dict[str, Any]]:
    """Return crash-like records found in the text."""
    crashes: list[dict[str, Any]] = []
    seen: set[str] = set()
    for line in text.splitlines():
        upper = line.upper()
        for marker in CRASH_MARKERS:
            if marker.upper() in upper:
                if line not in seen:
                    seen.add(line)
                    crashes.append(_record(
                        source,
                        _parse_timestamp(line),
                        "",
                        "",
                        "",
                        line=line,
                        marker=marker,
                    ))
                break
    return crashes


def is_evidence_line(line: str) -> bool:
    """Return True if a raw log line contains a marker, hook or crash."""
    if MODULE_LOAD_RE.search(line):
        return True
    if HOOK_SUMMARY_RE.search(line):
        return True
    if HOOK_SUMMARY_KV_RE.search(line):
        return True
    upper = line.upper()
    for marker in CRASH_MARKERS:
        if marker.upper() in upper:
            return True
    return False


def filter_interesting_lines(text: str) -> list[str]:
    """Return only lines that contain module, hook or crash evidence."""
    return [line for line in text.splitlines() if is_evidence_line(line)]


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
