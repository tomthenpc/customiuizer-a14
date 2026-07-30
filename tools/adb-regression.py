#!/usr/bin/env python3
"""Safe local ADB regression framework for CustoMIUIzer A14.

This tool is designed to run against a real Android device connected over adb.
It never uploads device data and never performs destructive actions unless
explicitly authorized.  When no device is available, only the fake-adb fixture
tests can prove the framework itself is correct.
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).parent))

import adb_regression.plan as plan
import adb_regression.runner as runner

MODULE_PACKAGE = "tv.withaibuild.customiuizer.r14"

READONLY_SHELL_COMMANDS = {
    "getprop", "pidof", "ps", "pm", "dumpsys", "logcat",
    "cmd", "stat", "sha256sum", "am",
}

FORBIDDEN_SUBSTRINGS = {
    ";", "&", "|", "`", "$", "\n", "\r", ">", "<", "rm ", "mv ", "dd ",
    "mkfs", "reboot", "stop ", "start ", "setprop", "pm clear", "pm uninstall",
    "cmd package uninstall", "settings put", "settings delete", "device_policy",
    "locksettings", "input keyevent POWER", "input swipe", "input tap", "input text",
    "kill ", "killall", "pkill", "force-stop", "svc power", "su -c", ">>",
}

DANGEROUS_COMMANDS: set[str] = set()

PRIVACY_SENSITIVE_KEYS = (
    "ro.serialno", "ril.serialnumber", "ro.boot.serialno", "gsm.sim.operator.imsi",
    "net.hostname", "android_id", "wifi.ssid", "wifi.bssid",
)


def die(message: str, exit_code: int = 2) -> None:
    print(f"adb-regression: {message}", file=sys.stderr)
    sys.exit(exit_code)


def _normalize_path(value: str) -> Path:
    p = Path(value).expanduser().resolve()
    if not p.is_file():
        die(f"file not found: {p}", 2)
    return p


def find_adb(override: str | None = None) -> Path:
    if override:
        p = Path(override).expanduser().resolve()
        if not p.is_file():
            die(f"specified adb not found: {p}", 2)
        return p
    found = shutil.which("adb")
    if not found:
        die("adb not found in PATH; use --adb <path>", 2)
    return Path(found)


def _hash(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()[:16]


def _anonymize_serial(serial: str | None) -> str:
    if not serial:
        return "unknown"
    # Keep enough characters to disambiguate local devices but not the full serial.
    return _hash(serial)


def _safe_env() -> dict[str, str]:
    # Avoid leaking well-known secrets into child adb commands, but keep the
    # rest of the platform context.  ADB itself does not forward host env to
    # the device unless explicitly exported in a shell command.
    deny_patterns = ("TOKEN", "SECRET", "PASSWORD", "KEY", "CREDENTIAL",
                     "CHAT_ID", "BOT", "TELEGRAM", "GITHUB", "AWS", "AZURE", "GCP")
    return {k: v for k, v in os.environ.items()
            if not any(p.lower() in k.lower() for p in deny_patterns)}


def _run(
    adb: Path,
    args: list[str],
    timeout: int,
    serial: str | None = None,
    check: bool = True,
) -> tuple[int, str, str, float]:
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
        return -1, (exc.stdout or "").decode("utf-8", errors="replace"), f"timeout after {timeout}s", elapsed
    except FileNotFoundError:
        die(f"adb executable disappeared: {adb}", 2)
    except OSError as exc:
        die(f"adb execution failed: {exc}", 2)


def _adb_version(adb: Path) -> str:
    rc, out, err, _ = _run(adb, ["version"], 10)
    if rc != 0:
        die(f"adb version failed: {err or out}", 2)
    return out.strip()


def _list_devices(adb: Path, timeout: int) -> list[dict[str, str]]:
    rc, out, err, _ = _run(adb, ["devices", "-l"], timeout)
    if rc != 0:
        die(f"adb devices failed: {err or out}", 2)

    devices: list[dict[str, str]] = []
    for line in out.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split(None, 1)
        if not parts:
            continue
        serial = parts[0]
        state = parts[1].split()[0] if len(parts) > 1 else "unknown"
        props: dict[str, str] = {"serial": serial, "state": state}
        if "product:" in line:
            for m in re.finditer(r"(\w+):(\S+)", line):
                props[m.group(1)] = m.group(2)
        devices.append(props)
    return devices


def _select_serial(
    adb: Path, serial: str | None, timeout: int
) -> tuple[str, dict[str, Any]]:
    devices = _list_devices(adb, timeout)
    if not devices:
        die("no devices/emulators found", 2)

    if len(devices) > 1 and not serial:
        die("multiple devices connected; provide --serial", 2)

    if serial:
        for d in devices:
            if d["serial"] == serial:
                if d["state"] in ("unauthorized", "offline"):
                    die(f"device {serial} is {d['state']}", 2)
                return serial, d
        die(f"specified serial not found: {serial}", 2)

    # Single device
    d = devices[0]
    if d["state"] in ("unauthorized", "offline"):
        die(f"device {d['serial']} is {d['state']}", 2)
    return d["serial"], d


def _getprop(adb: Path, serial: str, key: str, timeout: int) -> str:
    rc, out, _, _ = _run(adb, ["shell", "getprop", key], timeout, serial=serial)
    return out.strip() if rc == 0 else ""


def _getprops(adb: Path, serial: str, keys: list[str], timeout: int) -> dict[str, str]:
    return {k: _getprop(adb, serial, k, timeout) for k in keys}


def _package_info(adb: Path, serial: str, package: str, timeout: int) -> dict[str, Any]:
    rc, out, _, _ = _run(adb, ["shell", "dumpsys", "package", package], timeout, serial=serial)
    info: dict[str, Any] = {"installed": rc == 0 and package in out}
    if not info["installed"]:
        return info

    version_name = re.search(r"versionName=([^\s\n]+)", out)
    version_code = re.search(r"versionCode=(\d+)", out)
    info["versionName"] = version_name.group(1) if version_name else ""
    info["versionCode"] = int(version_code.group(1)) if version_code else 0

    # First signing certificate line.
    cert = re.search(r"signatures:.*?(\[[a-fA-F0-9:]+\])", out, re.DOTALL)
    info["certificate"] = cert.group(1) if cert else ""
    return info


def _pid_of(adb: Path, serial: str, name: str, timeout: int) -> list[int]:
    rc, out, _, _ = _run(adb, ["shell", "pidof", name], timeout, serial=serial)
    if rc != 0 or not out.strip():
        return []
    try:
        return [int(p) for p in out.strip().split() if p.isdigit()]
    except ValueError:
        return []


def _has_root(adb: Path, serial: str, timeout: int) -> dict[str, bool]:
    rc, _, _, _ = _run(adb, ["shell", "su", "-c", "true"], timeout, serial=serial)
    su = rc == 0
    rc2, out2, _, _ = _run(adb, ["shell", "id"], timeout, serial=serial)
    root = rc2 == 0 and ("uid=0" in out2)
    return {"su": su, "root": root}


def _loaded_markers(adb: Path, serial: str, timeout: int) -> dict[str, str]:
    # This is a lightweight grep; no huge log is copied.
    rc, out, _, _ = _run(
        adb,
        ["logcat", "-d", "-s", "-t", "5000"],
        timeout,
        serial=serial,
    )
    if rc != 0:
        return {}
    markers: dict[str, str] = {}
    pattern = re.compile(rf"CustoMIUIzer\s+(\S+)\s+\((\d+)\)\s+loaded\s+in\s+(.+)")
    for line in out.splitlines():
        m = pattern.search(line)
        if m:
            markers[m.group(3).strip()] = f"{m.group(1)} ({m.group(2)})"
    return markers


def _collect_preflight(
    adb: Path,
    serial: str,
    timeout: int,
) -> dict[str, Any]:
    """Gather the baseline device report."""
    props = _getprops(
        adb,
        serial,
        [
            "ro.product.manufacturer",
            "ro.product.model",
            "ro.build.version.sdk",
            "ro.build.version.release",
            "ro.build.fingerprint",
            "ro.build.version.incremental",
            "ro.miui.ui.version.name",
            "ro.miui.ui.version.code",
            "ro.product.mod_device",
            "sys.boot_completed",
        ],
        timeout,
    )

    root_info = _has_root(adb, serial, timeout)
    module_info = _package_info(adb, serial, MODULE_PACKAGE, timeout)
    systemui_info = _package_info(adb, serial, "com.android.systemui", timeout)
    launcher_info = _package_info(adb, serial, "com.miui.home", timeout)
    security_info = _package_info(adb, serial, "com.miui.securitycenter", timeout)
    lsposed_installed = _package_info(adb, serial, "org.lsposed.manager", timeout)["installed"]

    pids = {
        "system_server": _pid_of(adb, serial, "system_server", timeout)[:1],
        "systemui": _pid_of(adb, serial, "com.android.systemui", timeout)[:1],
        "launcher": _pid_of(adb, serial, "com.miui.home", timeout)[:1],
    }

    return {
        "schemaVersion": 1,
        "toolVersion": "0.1.0",
        "runId": uuid.uuid4().hex,
        "deviceId": _anonymize_serial(serial),
        "rawSerialSource": "hashed",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "adbVersion": _adb_version(adb),
        "state": _list_devices(adb, timeout)[0].get("state", "unknown"),
        "manufacturer": props.get("ro.product.manufacturer", ""),
        "model": props.get("ro.product.model", ""),
        "androidApi": int(props.get("ro.build.version.sdk", "0") or 0),
        "androidRelease": props.get("ro.build.version.release", ""),
        "fingerprint": props.get("ro.build.fingerprint", ""),
        "buildIncremental": props.get("ro.build.version.incremental", ""),
        "miuiVersionName": props.get("ro.miui.ui.version.name", ""),
        "miuiVersionCode": props.get("ro.miui.ui.version.code", ""),
        "modDevice": props.get("ro.product.mod_device", ""),
        "bootCompleted": props.get("sys.boot_completed", "") == "1",
        "root": root_info,
        "modulePackage": MODULE_PACKAGE,
        "module": module_info,
        "systemui": systemui_info,
        "launcher": launcher_info,
        "securityCenter": security_info,
        "lsposedManagerInstalled": lsposed_installed,
        "pids": pids,
        "loadedMarkers": _loaded_markers(adb, serial, timeout),
    }


def _write_report(report: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    json_path = out_dir / "preflight.json"
    md_path = out_dir / "preflight.md"
    json_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    lines = ["# ADB Preflight Report\n", f"- Run ID: {report['runId']}", f"- Timestamp: {report['timestamp']}", f"- Device ID: `{report['deviceId']}`", ""]
    lines.append("## Device\n")
    for k in ["manufacturer", "model", "androidApi", "androidRelease", "fingerprint"]:
        lines.append(f"- {k}: {report.get(k)}")
    lines.append("")
    lines.append("## Module\n")
    mod = report.get("module", {})
    lines.append(f"- installed: {mod.get('installed')}")
    lines.append(f"- versionName: {mod.get('versionName', '')}")
    lines.append(f"- versionCode: {mod.get('versionCode', '')}")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def cmd_preflight(args: argparse.Namespace) -> int:
    adb = find_adb(args.adb)
    serial, _ = _select_serial(adb, args.serial, args.timeout)
    report = _collect_preflight(adb, serial, args.timeout)
    out_dir = Path(args.output).expanduser().resolve()
    _write_report(report, out_dir)
    print(f"preflight written to: {out_dir / 'preflight.json'}")
    return 0


def cmd_validate_plan(args: argparse.Namespace) -> int:
    plan_path = Path(args.plan).expanduser().resolve()
    if not plan_path.is_file():
        die(f"plan not found: {plan_path}", 2)
    exit_code, messages = plan.validate(plan_path)
    for m in messages:
        stream = sys.stderr if exit_code != 0 else sys.stdout
        print(m, file=stream)
    return exit_code


def cmd_run(args: argparse.Namespace) -> int:
    adb = find_adb(args.adb)
    serial, _ = _select_serial(adb, args.serial, args.timeout)
    preflight = _collect_preflight(adb, serial, args.timeout)
    return runner.run(adb=adb, serial=serial, preflight=preflight, args=args)


def cmd_propose_evidence(args: argparse.Namespace) -> int:
    die("propose-evidence not yet implemented in this milestone", 2)


def main() -> int:
    parent = argparse.ArgumentParser(add_help=False)
    parent.add_argument("--adb", help="path to adb executable")
    parent.add_argument("--serial", help="target device serial")
    parent.add_argument("--timeout", type=int, default=30, help="adb command timeout in seconds")
    parent.add_argument("--output", default="build/adb-regression", help="output directory")

    parser = argparse.ArgumentParser(description="CustoMIUIzer A14 ADB regression framework", parents=[parent])
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("preflight", help="collect device baseline", parents=[parent])
    p.set_defaults(func=cmd_preflight)

    p = sub.add_parser("validate-plan", help="validate a test plan JSON", parents=[parent])
    p.add_argument("--plan", required=True, help="path to adb-regression test plan")
    p.set_defaults(func=cmd_validate_plan)

    p = sub.add_parser("run", help="run a regression plan", parents=[parent])
    p.add_argument("--plan", required=True, help="path to adb-regression test plan")
    p.add_argument("--apk", help="path to APK to install")
    p.add_argument("--install", action="store_true", help="install the specified APK")
    p.add_argument("--allow-dangerous", action="store_true", help="allow dangerous steps")
    p.add_argument("--verbose", action="store_true", help="verbose output")
    p.set_defaults(func=cmd_run)

    p = sub.add_parser("propose-evidence", help="propose device evidence from a report", parents=[parent])
    p.add_argument("--report", required=True, help="path to report.json")
    p.set_defaults(func=cmd_propose_evidence)

    args = parser.parse_args()
    try:
        return args.func(args)
    except SystemExit as exc:
        return exc.code if isinstance(exc.code, int) else 1
    except Exception as exc:
        print(f"adb-regression: internal error: {exc}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
