#!/usr/bin/env python3
"""Local verification entry point for CustoMIUIzer A14.

This script replaces the ad-hoc gradle/ADB/signature workflow with a single,
offline-safe gate.  It never builds an APK, never touches a device, and never
runs lintVitalRelease/R8.

Examples:
    python tools/verify.py fast
    python tools/verify.py fast --tests PreferenceBootstrapTest
    python tools/verify.py fast --tests PreferenceBootstrapTest ModuleReceiverRegistrationTest
    python tools/verify.py full
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Gradle wrapper name depends on the host.
GRADLEW = "gradlew.bat" if os.name == "nt" else "gradlew"
GRADLEW_PATH = REPO_ROOT / GRADLEW

# Build tasks that are never allowed in this verification script.
PROHIBITED_TASKS = {
    "assemble",
    "package",
    "bundle",
    "install",
    "sign",
    "publish",
    "officialRelease",
    "lintVitalRelease",
}


def fail(message: str) -> None:
    print(f"verify: {message}", file=sys.stderr)
    sys.exit(1)


def run(cmd: list[str]) -> int:
    """Run a command in the repo root, streaming output."""
    print(f"\n=== {' '.join(cmd)} ===")
    return subprocess.call(cmd, cwd=REPO_ROOT)


def gradle(*tasks: str) -> int:
    """Run gradle tasks, after making sure none are prohibited."""
    for task in tasks:
        if any(prohibited in task for prohibited in PROHIBITED_TASKS):
            fail(f"prohibited task requested: {task}")
    if not GRADLEW_PATH.exists():
        fail(f"gradle wrapper not found: {GRADLEW_PATH}")
    return run([str(GRADLEW_PATH), "--no-daemon", *tasks])


def check_invariants() -> int:
    return run([sys.executable, str(REPO_ROOT / "tools" / "check-invariants.py")])


def read_build_gradle() -> str:
    build_file = REPO_ROOT / "app" / "build.gradle.kts"
    if build_file.exists():
        return build_file.read_text(encoding="utf-8")
    build_file = build_file.with_suffix("")
    if build_file.exists():
        return build_file.read_text(encoding="utf-8")
    fail("app/build.gradle.kts not found")


def read_module_prop() -> str:
    prop = (
        REPO_ROOT
        / "app"
        / "src"
        / "main"
        / "resources"
        / "META-INF"
        / "xposed"
        / "module.prop"
    )
    if not prop.exists():
        fail("module.prop not found")
    return prop.read_text(encoding="utf-8")


def require_in_text(text: str, pattern: str, what: str) -> None:
    if not re.search(pattern, text):
        fail(f"{what} not found in build configuration")


def check_static_rules() -> int:
    """Verify project-level A14 invariants that do not require gradle."""
    build = read_build_gradle()
    prop = read_module_prop()

    require_in_text(build, r'applicationId\s*=\s*"tv\.withaibuild\.customiuizer\.r14"', "applicationId")
    require_in_text(build, r'compileSdk\s*=\s*3[4-9]', "compileSdk >= 34")
    require_in_text(build, r'minSdk\s*=\s*34', "minSdk 34")
    require_in_text(build, r'targetSdk\s*=\s*34', "targetSdk 34")

    require_in_text(prop, r"minApiVersion\s*=\s*101", "module.prop minApiVersion=101")
    require_in_text(prop, r"targetApiVersion\s*=\s*102", "module.prop targetApiVersion=102")
    require_in_text(prop, r"staticScope\s*=\s*false", "module.prop staticScope=false")

    # Legacy Xposed package must not appear outside the three allowed boundary files.
    allowed_legacy = {
        "app/src/main/java/tv/withaibuild/customiuizer/MainModule.java",
        "app/src/main/java/tv/withaibuild/customiuizer/XposedHelpers.java",
        "app/src/main/java/tv/withaibuild/customiuizer/utils/MemberUtilsX.java",
    }
    source_root = REPO_ROOT / "app" / "src" / "main" / "java"
    for path in source_root.rglob("*.kt"):
        rel = path.relative_to(REPO_ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        if "de.robv.android.xposed" in text and rel not in allowed_legacy:
            fail(f"legacy Xposed package referenced in {rel}")

    print("verify: static rules passed")
    return 0


def fast(tests: list[str] | None) -> int:
    code = check_static_rules()
    if code != 0:
        return code
    code = check_invariants()
    if code != 0:
        return code

    if tests:
        test_args = []
        for t in tests:
            test_args.extend(["--tests", t])
        code = gradle("testDebugUnitTest", *test_args)
    else:
        code = gradle("compileDebugKotlin", "compileDebugJavaWithJavac")
    return code


def full() -> int:
    code = check_static_rules()
    if code != 0:
        return code
    code = check_invariants()
    if code != 0:
        return code

    code = gradle("compileDebugKotlin", "compileDebugJavaWithJavac")
    if code != 0:
        return code

    code = gradle("testDebugUnitTest")
    if code != 0:
        return code

    code = gradle("lintDebug")
    return code


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    fast_parser = subparsers.add_parser("fast", help="static checks + compile + optional tests")
    fast_parser.add_argument(
        "--tests",
        nargs="+",
        help="run only these test classes (e.g. PreferenceBootstrapTest)",
    )

    subparsers.add_parser("full", help="static checks + invariants + compile + tests + lintDebug")

    args = parser.parse_args()

    if args.command == "fast":
        return fast(args.tests)
    if args.command == "full":
        return full()
    return 2


if __name__ == "__main__":
    sys.exit(main())
