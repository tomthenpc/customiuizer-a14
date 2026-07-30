#!/usr/bin/env python3
"""Fake `adb` executable for ADB regression fixture tests."""

from __future__ import annotations

import os
import sys
import time


def _prop(key: str, state: str) -> str:
    props = {
        "ro.product.manufacturer": "Xiaomi",
        "ro.product.model": "FakePhone",
        "ro.build.version.sdk": "34",
        "ro.build.version.release": "14",
        "ro.build.fingerprint": "Xiaomi/fake-device:14/UKQ1.230917.001:user/release-keys",
        "ro.build.version.incremental": "V816.0.0.0.UMKCNXM",
        "ro.miui.ui.version.name": "V816",
        "ro.miui.ui.version.code": "816000000",
        "ro.product.mod_device": "fake",
        "sys.boot_completed": "1",
    }
    return props.get(key, "")


def _dumpsys_package(pkg: str, state: str) -> str:
    if state == "no_module" and pkg == "tv.withaibuild.customiuizer.r14":
        return f"Unable to find package: {pkg}\n"
    installed = pkg in {
        "tv.withaibuild.customiuizer.r14",
        "com.android.systemui",
        "com.miui.home",
        "com.miui.securitycenter",
        "org.lsposed.manager",
    }
    if not installed:
        return f"Unable to find package: {pkg}\n"
    return (
        f"Package [{pkg}] ({pkg}):\n"
        f"  versionName=1.0.0\n"
        f"  versionCode=1000\n"
        f"  signatures: PackageSignatures\n"
        f"    [AB:CD:EF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00]\n"
    )


def _pidof(name: str, scenario: str) -> str:
    pids = {
        "system_server": "1234",
        "com.android.systemui": "2345",
        "com.miui.home": "3456",
    }
    base = pids.get(name, "")
    if not base:
        return ""
    if scenario == "pid_changes":
        offset = int(os.environ.get("FAKE_PID_OFFSET", "0"))
        return str(int(base) + offset)
    return base


def _hook_line(
    process: str,
    stage: str,
    installed: int = 100,
    class_missing: int = 0,
    member_missing: int = 0,
    failed: int = 0,
    silent_skipped: int = 0,
    dexkit_failed: int = 0,
    dexkit_no_match: int = 0,
    prefs_unavailable: int = 0,
) -> str:
    return (
        f"[HookSummary] process={process} stage={stage} installed={installed} "
        f"classMissing={class_missing} memberMissing={member_missing} failed={failed} "
        f"silentSkipped={silent_skipped} dexkitFailed={dexkit_failed} "
        f"dexkitNoMatch={dexkit_no_match} prefsUnavailable={prefs_unavailable}"
    )


def _logcat_output(scenario: str) -> list[str]:
    if scenario == "timeout":
        time.sleep(20)
        return []

    if scenario == "module_markers":
        return [
            "06-01 12:00:00.000  1234  1234 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in system_server",
            "06-01 12:00:01.000  2345  2345 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in com.android.systemui",
            "06-01 12:00:02.000  3456  3456 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in com.miui.home",
        ]

    if scenario == "hook_summary_systemui":
        return [
            _hook_line("com.android.systemui", "init"),
            _hook_line("com.android.systemui", "ready"),
        ]

    if scenario == "hook_summary_system_server":
        return [
            _hook_line("system_server", "init"),
            _hook_line("system_server", "ready"),
        ]

    if scenario == "hook_summary_launcher":
        return [
            _hook_line("com.miui.home", "init"),
            _hook_line("com.miui.home", "ready"),
        ]

    if scenario == "malformed_summary":
        return [
            "[HookSummary] process=com.android.systemui stage=init installed=100 "
            "classMissing=zero memberMissing=0 failed=0",
        ]

    if scenario == "failed_gt_0":
        return [_hook_line("com.android.systemui", "init", failed=1)]

    if scenario == "prefs_unavailable_gt_0":
        return [_hook_line("com.android.systemui", "init", prefs_unavailable=1)]

    if scenario == "dexkit_failed_gt_0":
        return [_hook_line("com.android.systemui", "init", dexkit_failed=1)]

    if scenario == "dexkit_no_match_gt_0":
        return [_hook_line("com.android.systemui", "init", dexkit_no_match=1)]

    if scenario == "class_member_missing":
        return [_hook_line("com.android.systemui", "init", class_missing=2, member_missing=3)]

    if scenario == "crash":
        return [
            "06-01 12:00:00.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main",
            "06-01 12:00:01.000  1234  1234 E Watchdog: WATCHDOG: Killing system_server",
            "06-01 12:00:02.000  1234  1234 E system_server: system_server crash",
        ]

    # Default / a14-smoke-pending: markers + hook summaries, no crash.
    return [
        "06-01 12:00:00.000  1234  1234 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in system_server",
        "06-01 12:00:01.000  2345  2345 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in com.android.systemui",
        "06-01 12:00:02.000  3456  3456 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in com.miui.home",
        _hook_line("system_server", "init"),
        _hook_line("system_server", "ready"),
        _hook_line("com.android.systemui", "init"),
        _hook_line("com.android.systemui", "ready"),
        _hook_line("com.miui.home", "init"),
    ]


def main() -> int:
    state = os.environ.get("ADB_FAKE_STATE", "ok")
    scenario = os.environ.get("FAKE_ADB_SCENARIO", "ok")
    args = sys.argv[1:]
    if not args:
        return 1

    if args[0] == "version":
        print("Android Debug Bridge version 1.0.41")
        return 0

    if args[0] == "devices" and len(args) > 1 and args[1] == "-l":
        if state in ("no_devices",):
            print("List of devices attached")
            return 0
        if state == "unauthorized":
            print("List of devices attached")
            print("FAKE001               unauthorized usb:33685504X product:fake device:fake")
            return 0
        if state == "offline":
            print("List of devices attached")
            print("FAKE001               offline usb:33685504X product:fake device:fake")
            return 0
        if state == "multi":
            print("List of devices attached")
            print("FAKE001               device usb:33685504X product:fake device:fake")
            print("FAKE002               device usb:33685504X product:fake device:fake")
            return 0
        print("List of devices attached")
        print("FAKE001               device usb:33685504X product:fake model:FakePhone device:fake transport_id:1")
        return 0

    # Handle -s <serial> ...
    if args[0] == "-s" and len(args) >= 3:
        cmd = args[2:]
        if len(cmd) >= 3 and cmd[0] == "shell" and cmd[1] == "getprop" and len(cmd) == 3:
            print(_prop(cmd[2], state))
            return 0
        if len(cmd) >= 3 and cmd[0] == "shell" and cmd[1] == "dumpsys" and cmd[2] == "package" and len(cmd) == 4:
            out = _dumpsys_package(cmd[3], state)
            if "Unable to find package" in out:
                return 1
            print(out, end="")
            return 0
        if len(cmd) >= 3 and cmd[0] == "shell" and cmd[1] == "pidof" and len(cmd) == 3:
            print(_pidof(cmd[2], scenario))
            return 0
        if cmd == ["shell", "su", "-c", "true"]:
            if state == "no_su":
                return 1
            return 0
        if cmd == ["shell", "id"]:
            if state == "no_su":
                print("uid=2000(shell)")
            else:
                print("uid=0(root) gid=0(root)")
            return 0
        if len(cmd) >= 2 and cmd[0] == "logcat" and any(x == "-d" for x in cmd):
            for line in _logcat_output(scenario):
                print(line)
            return 0
        if len(cmd) >= 2 and cmd[0] == "shell" and cmd[1] == "pm" and len(cmd) == 3 and cmd[2] == "list packages":
            for pkg in ["tv.withaibuild.customiuizer.r14", "com.android.systemui", "com.miui.home", "org.lsposed.manager"]:
                print(f"package:{pkg}")
            return 0

    # Unknown command
    print(f"fake_adb: unhandled command: {args}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
