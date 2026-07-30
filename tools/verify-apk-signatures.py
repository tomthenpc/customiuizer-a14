#!/usr/bin/env python3
"""Verify APK signature state for local builds and CI.

Cross-platform helper around the Android SDK's `apksigner`.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def find_apksigner() -> Path:
    """Locate apksigner in the Android SDK (latest build-tools) or on PATH."""
    # Prefer a system / PATH apksigner if it exists.
    from shutil import which
    path_apksigner = which("apksigner")
    if path_apksigner:
        return Path(path_apksigner)

    sdk: str | None = None
    local = REPO_ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].strip()
                break
    if not sdk:
        for env in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
            if os.environ.get(env):
                sdk = os.environ[env]
                break
    if not sdk:
        raise SystemExit("Android SDK not found. Set sdk.dir in local.properties or ANDROID_SDK_ROOT / ANDROID_HOME.")

    build_tools_root = Path(sdk) / "build-tools"
    if not build_tools_root.is_dir():
        raise SystemExit(f"build-tools directory not found at {build_tools_root}")
    # Pick the highest installed build-tools version.
    versions = [d for d in build_tools_root.iterdir() if d.is_dir()]
    if not versions:
        raise SystemExit(f"no build-tools versions found under {build_tools_root}")
    versions.sort(key=lambda p: p.name, reverse=True)
    apksigner = versions[0] / ("apksigner.bat" if sys.platform == "win32" else "apksigner")
    if not apksigner.is_file():
        raise SystemExit(f"apksigner not found at {apksigner}")
    return apksigner


def run_verify(apksigner: Path, apk: Path) -> tuple[int, str]:
    cmd = [str(apksigner), "verify", "--print-certs", "-v", str(apk)]
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    return p.returncode, (p.stdout + p.stderr)


def sha256_from_output(output: str) -> str | None:
    for line in output.splitlines():
        if "SHA-256" in line:
            # Common format: "Signer #1 certificate SHA-256 digest: ab:cd:..."
            m = re.search(r"SHA-256[^:]*:\s*([0-9a-fA-F:]+)", line)
            if m:
                return m.group(1).replace(":", "").lower()
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify APK signature state for local builds and CI")
    parser.add_argument("--debug-apk", required=True, type=Path, help="path to the Debug APK")
    parser.add_argument("--release-apk", required=True, type=Path, help="path to the Release APK")
    parser.add_argument(
        "--release-kind",
        required=True,
        choices=("ci", "official"),
        help="expected release APK state: unsigned CI or officially signed",
    )
    parser.add_argument("--expected-sha256", help="expected SHA-256 certificate fingerprint for an official build")
    parser.add_argument("--allow-debug-unsigned", action="store_true", help="do not fail if the debug APK is unsigned")
    args = parser.parse_args()

    apksigner = find_apksigner()
    errors: list[str] = []

    for path, label in ((args.debug_apk, "Debug"), (args.release_apk, "Release")):
        if not path.is_file():
            errors.append(f"{label} APK not found: {path}")

    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1

    # Debug: must be signed by Android debug key unless explicitly allowed otherwise.
    code, out = run_verify(apksigner, args.debug_apk)
    if code != 0 and not args.allow_debug_unsigned:
        errors.append(f"Debug APK is not signed: {args.debug_apk}\n{out}")
    elif code != 0:
        print(f"Debug APK is unsigned (allowed by --allow-debug-unsigned): {args.debug_apk}")
    else:
        print(f"Debug APK is signed: {args.debug_apk}")

    # Release: must match the requested state.
    code, out = run_verify(apksigner, args.release_apk)
    if args.release_kind == "ci":
        if code == 0:
            errors.append(f"Unsigned CI release APK must not be signed: {args.release_apk}")
        else:
            print(f"Unsigned CI release APK is not signed: {args.release_apk}")
    else:  # official
        if code != 0:
            errors.append(f"Official release APK is not signed: {args.release_apk}\n{out}")
        else:
            print(f"Official release APK is signed: {args.release_apk}")
            fp = sha256_from_output(out)
            if fp:
                print(f"Certificate SHA-256: {fp}")
                if args.expected_sha256 and fp != args.expected_sha256.replace(":", "").lower():
                    errors.append(
                        f"Certificate SHA-256 mismatch: expected {args.expected_sha256}, got {fp}"
                    )

    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1

    print("APK signature verification passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
