"""Top-level run orchestration for the ADB regression framework."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import plan, redaction, report, steps


class RegressionError(Exception):
    def __init__(self, message: str, exit_code: int = 2) -> None:
        super().__init__(message)
        self.exit_code = exit_code


def _safe_env() -> dict[str, str]:
    deny_patterns = ("TOKEN", "SECRET", "PASSWORD", "KEY", "CREDENTIAL",
                     "CHAT_ID", "BOT", "TELEGRAM", "GITHUB", "AWS", "AZURE", "GCP")
    return {k: v for k, v in os.environ.items()
            if not any(p.lower() in k.lower() for p in deny_patterns)}


def _exec(adb: Path, serial: str | None, args: list[str], timeout: int) -> tuple[int, str, str, float]:
    """Run an adb command with explicit timeout.  Never uses shell=True."""
    adb_str = str(adb)
    if os.name == "nt" and adb_str.lower().endswith((".cmd", ".bat")):
        cmd = ["cmd", "/c", adb_str]
    else:
        cmd = [adb_str]
    if serial:
        cmd += ["-s", serial]
    cmd.extend(args)

    start = time.perf_counter()
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=timeout,
            env=_safe_env(),
            encoding="utf-8",
            errors="replace",
        )
        elapsed = time.perf_counter() - start
        return proc.returncode, proc.stdout, proc.stderr, elapsed
    except subprocess.TimeoutExpired as exc:
        elapsed = time.perf_counter() - start
        return -1, (exc.stdout or ""), f"timeout after {timeout}s", elapsed
    except FileNotFoundError:
        raise RegressionError(f"adb executable disappeared: {adb}", 2)
    except OSError as exc:
        raise RegressionError(f"adb execution failed: {exc}", 2)


def _is_simulation(preflight: dict[str, Any], serial: str | None) -> bool:
    if serial and serial.upper().startswith("FAKE"):
        return True
    if preflight.get("manufacturer") == "Xiaomi" and preflight.get("model") == "FakePhone":
        return True
    return False


def _write_json(path: Path, data: Any) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def run(
    adb: Path,
    serial: str,
    preflight: dict[str, Any],
    args: Any,
) -> int:
    """Run a regression plan.  Returns 0/1/2/3."""
    plan_path = Path(args.plan).expanduser().resolve()
    if not plan_path.is_file():
        print(f"adb-regression: plan not found: {plan_path}", file=sys.stderr)
        return 2

    validate_code, messages = plan.validate(plan_path)
    for m in messages:
        stream = sys.stderr if validate_code != 0 else sys.stdout
        print(m, file=stream)
    if validate_code != 0:
        return validate_code

    plan_data = plan_path.read_text(encoding="utf-8")
    data = json.loads(plan_data)

    out_root = Path(args.output).expanduser().resolve()
    run_id = uuid.uuid4().hex
    out_dir = out_root / run_id
    out_dir.mkdir(parents=True, exist_ok=True)

    simulation = _is_simulation(preflight, serial)
    preflight["simulation"] = simulation

    preflight_path = out_dir / "preflight.json"
    _write_json(preflight_path, preflight)

    ctx: dict[str, Any] = {
        "adb": adb,
        "serial": serial,
        "preflight": preflight,
        "timeout": args.timeout,
        "allow_dangerous": bool(getattr(args, "allow_dangerous", False)),
        "verbose": bool(getattr(args, "verbose", False)),
        "out_dir": out_dir,
        "run_id": run_id,
        "planId": data.get("planId"),
        "run_adb": lambda args, timeout: _exec(adb, serial, args, timeout),
        "commands": [],
        "snapshots": {},
        "last_logcat": "",
        "simulation": simulation,
    }

    step_results: list[dict[str, Any]] = []
    exit_code = 0

    all_steps = list(data.get("steps", []))
    cleanup = list(data.get("cleanup", []))

    try:
        for step in all_steps:
            if not isinstance(step, dict):
                step_results.append({
                    "id": "<invalid>",
                    "type": "<invalid>",
                    "status": "ERROR",
                    "message": "step is not an object",
                })
                exit_code = 2
                break
            try:
                result = steps.execute(ctx, step)
            except Exception as exc:
                result = {
                    "id": step.get("id", "<unknown>"),
                    "type": step.get("type", "<unknown>"),
                    "status": "ERROR",
                    "message": f"internal: {exc}",
                }
            step_results.append(result)

            status = result["status"]
            if status == "MANUAL_PENDING":
                exit_code = 3
                break
            if status in ("FAIL", "ERROR"):
                if step.get("continueOnFailure"):
                    if exit_code == 0:
                        exit_code = 1
                    continue
                if exit_code == 0:
                    exit_code = 1 if status == "FAIL" else 2
                break

    finally:
        for cstep in cleanup:
            try:
                steps.execute(ctx, cstep)
            except Exception:
                pass

        # Always produce a final report with the evidence collected so far.
        try:
            report.generate(ctx, data, step_results, out_dir, exit_code)
        except Exception as exc:
            print(f"adb-regression: report generation failed: {exc}", file=sys.stderr)

    return exit_code
