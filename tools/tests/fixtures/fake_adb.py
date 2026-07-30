#!/usr/bin/env python3
"""Fake `adb` executable for ADB regression fixture tests.

Reads ADB_FAKE_STATE env var to decide device state.
Supported commands:
  version
  devices -l
  -s <serial> shell getprop <key>
  -s <serial> shell dumpsys package <pkg>
  -s <serial> shell pidof <name>
  -s <serial> shell su -c true
  -s <serial> shell id
  -s <serial> logcat -d -s -t <n>
  -s <serial> shell pm list packages
"""

import os
import sys


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
    if state == "no_module":
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


def _pidof(name: str, state: str) -> str:
    pids = {
        "system_server": "1234",
        "com.android.systemui": "2345",
        "com.miui.home": "3456",
    }
    return pids.get(name, "")


def main() -> int:
    state = os.environ.get("ADB_FAKE_STATE", "ok")
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
            print(_pidof(cmd[2], state))
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
            if state == "with_markers":
                print("06-01 12:00:00.000  1234  1234 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in system_server")
                print("06-01 12:00:01.000  2345  2345 I CustoMIUIzer: CustoMIUIzer 14.13.8 (14130800) loaded in com.android.systemui")
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
