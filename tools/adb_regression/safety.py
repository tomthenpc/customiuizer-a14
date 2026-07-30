"""ADB shell command safety policy for the regression framework."""

from __future__ import annotations

import re
from typing import Iterable

# Commands that may be used by preflight / read-only steps.
READONLY_COMMANDS = {
    "getprop", "pidof", "ps", "pm", "dumpsys", "logcat", "cmd", "stat",
    "sha256sum", "am",
}

# Denied everywhere, even with --allow-dangerous.
PERMANENTLY_FORBIDDEN = {
    "rm", "mv", "cp", "dd", "mkfs", "reboot", "stop", "start", "setprop",
    "settings", "locksettings", "device_policy", "input", "kill", "killall",
    "pkill", "svc", "su", "sh", "bash", "toybox", "chmod", "chown", "mount",
    "remount",
}

# Extra commands allowed only when step.dangerous and --allow-dangerous.
DANGEROUS_COMMANDS: set[str] = set()

# Disallowed in any argument string.
FORBIDDEN_CHARS = {";", "|", "&", ">", "<", "`", "$", "\n", "\r"}
FORBIDDEN_PATTERNS = (
    "rm ", "mv ", "cp ", "dd ", "mkfs", "reboot", "stop ", "start ",
    "setprop", "pm clear", "pm uninstall", "cmd package uninstall",
    "settings put", "settings delete", "locksettings", "device_policy",
    "input keyevent POWER", "input swipe", "input tap", "input text",
    "kill ", "killall", "pkill ", "force-stop", "svc power", "su -c",
    ">>", ">/", "</", ">",
)

# Subcommand restrictions for commands that have multiple modes.
_ALLOWED_PM_SUB = {"list", "path", "dump"}
_ALLOWED_AM_SUB = {"broadcast"}  # only for registered negative broadcast actions


def _contains_metachar(text: str) -> bool:
    return any(c in text for c in FORBIDDEN_CHARS)


def _contains_forbidden_substring(text: str) -> bool:
    t = text.lower()
    for pat in FORBIDDEN_PATTERNS:
        if pat in t:
            return True
    return False


def validate_command(
    args: Iterable[str],
    allow_dangerous: bool = False,
    expected_broadcast_action: str | None = None,
) -> tuple[bool, str]:
    """
    Validate a list of adb shell arguments (after ``adb shell``).
    Returns (ok, reason).
    """
    args = list(args)
    if not args:
        return False, "empty command"

    for a in args:
        if _contains_metachar(a):
            return False, f"metacharacter in argument: {a!r}"

    # Coarse deny list first.
    whole = " ".join(args).lower()
    if _contains_forbidden_substring(whole):
        return False, "forbidden substring"

    base = args[0]
    if base in PERMANENTLY_FORBIDDEN:
        return False, f"permanently forbidden command: {base}"

    if base not in READONLY_COMMANDS and (base not in DANGEROUS_COMMANDS or not allow_dangerous):
        return False, f"command not in allowlist: {base}"

    # Subcommand checks.
    if base == "pm" and len(args) > 1 and args[1] not in _ALLOWED_PM_SUB:
        return False, f"pm subcommand not allowed: {args[1]}"

    if base == "am" and (len(args) < 2 or args[1] != "broadcast"):
        return False, "am only allowed for broadcast"
    if base == "am":
        if not expected_broadcast_action:
            return False, "am broadcast requires a registered action in step.expected.action"
        try:
            idx = args.index("-a")
            if idx + 1 >= len(args) or args[idx + 1] != expected_broadcast_action:
                return False, f"unregistered broadcast action: {args[idx + 1] if idx + 1 < len(args) else None}"
        except ValueError:
            return False, "broadcast missing -a <action>"

    if base in DANGEROUS_COMMANDS and not allow_dangerous:
        return False, f"dangerous command requires --allow-dangerous: {base}"

    return True, ""
