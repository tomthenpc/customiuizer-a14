"""Step executors for the ADB regression framework."""

from __future__ import annotations

import json
import re
import time
from pathlib import Path
from typing import Any

from . import parsers, redaction
from .safety import validate_command


class StepError(Exception):
    pass


def _result(
    step: dict[str, Any],
    status: str,
    message: str = "",
    **extra: Any,
) -> dict[str, Any]:
    return {
        "id": step["id"],
        "type": step["type"],
        "description": step.get("description", ""),
        "status": status,
        "message": message,
        **extra,
    }


def _write_json(path: Path, data: Any) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _timeout(step: dict[str, Any], ctx: dict[str, Any]) -> int:
    return step.get("timeoutSeconds", ctx.get("timeout", 60))


def _run_adb(ctx: dict[str, Any], args: list[str], timeout: int) -> tuple[int, str, str, float]:
    return ctx["run_adb"](args, timeout)


def execute_shell(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    cmd = step.get("command") or step.get("args") or []
    if not isinstance(cmd, list):
        return _result(step, "ERROR", "shell command must be an argument array")
    allow_dangerous = ctx.get("allow_dangerous", False) and bool(step.get("dangerous"))
    expected = step.get("expected")
    expected_action = expected.get("action") if isinstance(expected, dict) else None
    ok, reason = validate_command(cmd, allow_dangerous=allow_dangerous, expected_broadcast_action=expected_action)
    if not ok:
        return _result(step, "ERROR", f"unsafe shell: {reason}")

    rc, out, err, elapsed = _run_adb(ctx, ["shell"] + cmd, _timeout(step, ctx))
    out = redaction.redact(out, serial=ctx.get("serial"))
    err = redaction.redact(err, serial=ctx.get("serial"))
    ctx.setdefault("commands", []).append({
        "stepId": step["id"],
        "args": ["shell"] + cmd,
        "rc": rc,
        "stdout": out,
        "stderr": err,
        "elapsed": round(elapsed, 3),
    })

    status = "PASS" if rc == 0 else "FAIL"
    if rc == 0 and isinstance(expected, dict):
        if "contains" in expected and expected["contains"] not in out:
            status = "FAIL"
            message = f"missing expected output: {expected['contains']!r}"
        elif "notContains" in expected and expected["notContains"] in out:
            status = "FAIL"
            message = f"unexpected output: {expected['notContains']!r}"
        elif "returnCode" in expected and rc != expected["returnCode"]:
            status = "FAIL"
            message = f"return code {rc}, expected {expected['returnCode']}"
        else:
            message = f"rc={rc}"
    else:
        message = f"rc={rc}"

    return _result(
        step, status, message,
        returnCode=rc,
        stdout=out,
        stderr=err,
        elapsed=round(elapsed, 3),
    )


def execute_sleep(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    seconds = step.get("duration", step.get("seconds", step.get("timeoutSeconds", 1)))
    time.sleep(seconds)
    return _result(step, "PASS", f"slept {seconds}s")


def _package_version_text(ctx: dict[str, Any], pkg: str, timeout: int) -> tuple[int, str, str, float]:
    return _run_adb(ctx, ["shell", "dumpsys", "package", pkg], timeout)


def _is_package_installed(ctx: dict[str, Any], pkg: str, timeout: int) -> bool:
    rc, out, _, _ = _run_adb(ctx, ["shell", "pm", "list", "packages", pkg], timeout)
    return rc == 0 and pkg in out


def execute_package_installed(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    expected = step.get("expected", {})
    pkg = expected.get("package") if isinstance(expected, dict) else None
    if not pkg:
        pkg = step.get("package")
    if not pkg:
        return _result(step, "ERROR", "package_installed requires a package")
    installed = _is_package_installed(ctx, pkg, _timeout(step, ctx))
    return _result(step, "PASS" if installed else "FAIL", f"{pkg} installed={installed}")


def execute_package_version(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    expected = step.get("expected", {})
    pkg = expected.get("package") if isinstance(expected, dict) else None
    if not pkg:
        pkg = step.get("package")
    if not pkg:
        return _result(step, "ERROR", "package_version requires a package")
    rc, out, err, _ = _package_version_text(ctx, pkg, _timeout(step, ctx))
    version_name = ""
    version_code = 0
    if rc == 0:
        m = re.search(r"versionName=([^\s\n]+)", out)
        if m:
            version_name = m.group(1)
        m = re.search(r"versionCode=(\d+)", out)
        if m:
            version_code = int(m.group(1))

    ok = rc == 0
    if ok and "versionName" in expected and expected["versionName"] != version_name:
        ok = False
    if ok and "versionCode" in expected and expected["versionCode"] != version_code:
        ok = False

    return _result(
        step, "PASS" if ok else "FAIL",
        f"{pkg} versionName={version_name!r} versionCode={version_code}",
        package=pkg,
        versionName=version_name,
        versionCode=version_code,
    )


def _pidof(ctx: dict[str, Any], name: str, timeout: int) -> list[int]:
    rc, out, _, _ = _run_adb(ctx, ["shell", "pidof", name], timeout)
    if rc != 0 or not out.strip():
        return []
    try:
        return [int(p) for p in out.strip().split() if p.isdigit()]
    except ValueError:
        return []


def execute_process_alive(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    expected = step.get("expected", {})
    name = expected.get("process") if isinstance(expected, dict) else None
    if not name:
        name = step.get("process")
    if not name:
        return _result(step, "ERROR", "process_alive requires a process")
    pids = _pidof(ctx, name, _timeout(step, ctx))
    return _result(step, "PASS" if pids else "FAIL", f"{name} pids={pids}")


def execute_process_snapshot(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    expected = step.get("expected", {})
    names = expected.get("processes") if isinstance(expected, dict) else None
    if not names:
        names = step.get("processes")
    if not isinstance(names, list):
        return _result(step, "ERROR", "process_snapshot requires a processes list")

    snapshot: dict[str, list[int]] = {}
    timeout = _timeout(step, ctx)
    for name in names:
        snapshot[name] = _pidof(ctx, name, timeout)

    ctx.setdefault("snapshots", {})[step["id"]] = snapshot
    ctx["snapshots"]["_latest"] = snapshot
    if "_first" not in ctx["snapshots"]:
        ctx["snapshots"]["_first"] = snapshot

    for ef in step.get("evidenceFiles", []):
        _write_json(ctx["out_dir"] / ef, snapshot)

    return _result(step, "PASS", f"recorded {len(snapshot)} processes")


def execute_process_restart_observed(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    expected = step.get("expected", {})
    if not isinstance(expected, dict):
        return _result(step, "ERROR", "process_restart_observed requires expected")
    name = expected.get("process")
    if not name:
        return _result(step, "ERROR", "process_restart_observed requires expected.process")
    before = ctx.setdefault("snapshots", {}).get("_first", {})
    before_pids = before.get(name, [])
    after_pids = _pidof(ctx, name, _timeout(step, ctx))
    comparison = parsers.compare_pids({name: before_pids}, {name: after_pids})
    info = comparison["processes"][name]
    should_restart = expected.get("shouldRestart", True)
    status = "PASS" if (info["restarted"] and should_restart) or (not info["restarted"] and not should_restart) else "FAIL"
    return _result(
        step, status,
        f"{name} before={info['before']} after={info['after']} restarted={info['restarted']}",
        comparison=comparison,
    )


def _logcat(ctx: dict[str, Any], timeout: int) -> tuple[int, str]:
    rc, out, err, _ = _run_adb(ctx, ["logcat", "-d", "-s", "-t", "5000"], timeout)
    out = redaction.redact(out, serial=ctx.get("serial"))
    ctx["last_logcat"] = out
    return rc, out


def execute_logcat_assert(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    expected = step.get("expected", {})
    if not isinstance(expected, dict):
        expected = {}
    timeout = _timeout(step, ctx)
    try:
        rc, text = _logcat(ctx, timeout)
    except Exception as exc:
        return _result(step, "ERROR", f"logcat capture failed: {exc}")
    if rc == -1:
        return _result(step, "ERROR", "logcat timed out")

    patterns = expected.get("patterns", [])
    absent = expected.get("absent", [])
    missing = [p for p in patterns if not re.search(p, text, re.IGNORECASE)]
    present_absent = [a for a in absent if re.search(a, text, re.IGNORECASE)]
    ok = not missing and not present_absent

    messages: list[str] = []
    if missing:
        messages.append(f"missing patterns: {missing}")
    if present_absent:
        messages.append(f"forbidden present: {present_absent}")
    message = "; ".join(messages) if messages else "logcat ok"

    markers = parsers.parse_module_markers(text)
    crashes = parsers.parse_crash_markers(text)

    for ef in step.get("evidenceFiles", []):
        path = ctx["out_dir"] / ef
        if "marker" in ef or "load" in ef or "module" in ef:
            _write_json(path, markers)
        elif "crash" in ef:
            _write_json(path, crashes)
        else:
            _write_json(path, {
                "missingPatterns": missing,
                "presentForbidden": present_absent,
                "markers": markers,
                "crashes": crashes,
            })

    return _result(
        step, "PASS" if ok else "FAIL", message,
        missingPatterns=missing,
        presentForbidden=present_absent,
        markers=markers,
        crashes=crashes,
    )


def execute_hook_summary(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    expected = step.get("expected", {})
    if not isinstance(expected, dict):
        expected = {}
    timeout = _timeout(step, ctx)
    try:
        # Logcat may already be in ctx from a previous step; otherwise fetch it.
        if ctx.get("last_logcat"):
            text = ctx["last_logcat"]
        else:
            rc, text = _logcat(ctx, timeout)
            if rc == -1:
                return _result(step, "ERROR", "logcat timed out")
    except Exception as exc:
        return _result(step, "ERROR", f"logcat capture failed: {exc}")

    records = parsers.parse_hook_summary(text)
    totals = parsers.hook_summary_totals(records)

    for ef in step.get("evidenceFiles", []):
        _write_json(ctx["out_dir"] / ef, {
            "records": records,
            "totals": totals,
        })

    process = expected.get("process")
    if process and not any(r["process"] == process for r in records):
        return _result(
            step, "FAIL",
            f"no HookSummary for {process}",
            records=records, totals=totals,
        )

    fail = 0
    for key in ("failed", "silentSkipped", "dexkitFailed", "dexkitNoMatch", "prefsUnavailable", "classMissing", "memberMissing"):
        if key in expected and expected[key] is not None:
            limit = expected[key]
            if totals.get(key, 0) > limit:
                fail += 1

    if fail:
        return _result(
            step, "FAIL",
            f"HookSummary exceeded expected limits: {totals}",
            records=records, totals=totals,
        )

    return _result(
        step, "PASS",
        f"HookSummary OK: {totals}",
        records=records, totals=totals,
    )


def execute_broadcast_probe(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    # Never execute arbitrary broadcasts.
    if step.get("manual"):
        return _result(step, "MANUAL_PENDING", "broadcast probe requires manual verification")
    return _result(step, "SKIPPED", "arbitrary broadcasts are not executed")


def execute_collect_diagnostics(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    timeout = _timeout(step, ctx)
    text = ""
    try:
        if ctx.get("last_logcat"):
            text = ctx["last_logcat"]
        else:
            rc, text = _logcat(ctx, timeout)
    except Exception as exc:
        text = ""

    preflight = ctx.get("preflight", {})
    pids: dict[str, list[int]] = {}
    for name in ("system_server", "com.android.systemui", "com.miui.home"):
        pids[name] = _pidof(ctx, name, timeout)

    ctx.setdefault("snapshots", {})["_latest"] = pids
    if "_first" not in ctx["snapshots"]:
        ctx["snapshots"]["_first"] = pids

    for ef in step.get("evidenceFiles", []):
        path = ctx["out_dir"] / ef
        if "preflight" in ef:
            _write_json(path, preflight)
        elif "snapshot" in ef or "process" in ef:
            _write_json(path, pids)
        elif "logcat" in ef:
            path.write_text(text, encoding="utf-8")
        else:
            _write_json(path, {
                "logcatLines": len(text.splitlines()),
                "crashes": parsers.parse_crash_markers(text),
                "pids": pids,
            })

    return _result(
        step, "PASS", "diagnostics collected",
        logcatLines=len(text.splitlines()),
        crashes=len(parsers.parse_crash_markers(text)),
        pids=pids,
    )


def execute_manual_checkpoint(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    data = {
        "status": "MANUAL_PENDING",
        "stepId": step["id"],
        "description": step.get("description", ""),
    }
    for ef in step.get("evidenceFiles", []):
        _write_json(ctx["out_dir"] / ef, data)
    return _result(
        step, "MANUAL_PENDING",
        step.get("description", "manual checkpoint"),
    )


STEP_HANDLERS: dict[str, Any] = {
    "shell": execute_shell,
    "sleep": execute_sleep,
    "package_installed": execute_package_installed,
    "package_version": execute_package_version,
    "process_alive": execute_process_alive,
    "process_snapshot": execute_process_snapshot,
    "process_restart_observed": execute_process_restart_observed,
    "logcat_assert": execute_logcat_assert,
    "hook_summary": execute_hook_summary,
    "broadcast_probe": execute_broadcast_probe,
    "collect_diagnostics": execute_collect_diagnostics,
    "manual_checkpoint": execute_manual_checkpoint,
}


def execute(ctx: dict[str, Any], step: dict[str, Any]) -> dict[str, Any]:
    handler = STEP_HANDLERS.get(step.get("type"))
    if not handler:
        return _result(step, "ERROR", f"unknown step type: {step.get('type')}")
    return handler(ctx, step)
