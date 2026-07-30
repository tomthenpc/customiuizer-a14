"""Broadcast probe registry and executor for the ADB regression framework.

This module provides a hard-coded, code-level registry of safe, negative
broadcast actions for the ``broadcast_probe`` step.  It never reads a real
token, never asserts a trusted sender, and never sends a high-privilege
positive broadcast.
"""

from __future__ import annotations

import re
from typing import Any

# Logical broadcast kind -> safe Android action string.
# These are all negative / no-op probes.  No action listed here is intended to
# trigger a privileged operation on a real device; the real module must reject
# or ignore broadcasts that lack a valid token or trusted sender.
BROADCAST_ACTIONS = {
    "FastReboot": "tv.withaibuild.customiuizer.r14.FAST_RESTART",
    "RestartSystemUI": "tv.withaibuild.customiuizer.r14.RESTART_SYSTEMUI",
    "RestartLauncher": "tv.withaibuild.customiuizer.r14.RESTART_LAUNCHER",
    "LockDevice": "tv.withaibuild.customiuizer.r14.LOCK_DEVICE",
    "TakeScreenshot": "tv.withaibuild.customiuizer.r14.TAKE_SCREENSHOT",
    "ForceClose": "tv.withaibuild.customiuizer.r14.FORCE_CLOSE",
    "SimulateMenu": "tv.withaibuild.customiuizer.r14.SIMULATE_MENU",
    "FetchCachedDevices": "tv.withaibuild.customiuizer.r14.FETCH_CACHED_DEVICES",
    "PUSHAPPCONFIG": "tv.withaibuild.customiuizer.r14.PUSHAPPCONFIG",
    # Negative-control test kinds.  These use dedicated probe actions so the
    # executor can verify the framework's own safety without touching real
    # high-privilege actions.
    "wrong_target": "tv.withaibuild.customiuizer.r14.WRONG_TARGET_PROBE",
    "unregistered_action": "tv.withaibuild.customiuizer.r14.UNREGISTERED_PROBE",
    "missing_token": "tv.withaibuild.customiuizer.r14.MISSING_TOKEN_PROBE",
    "missing_sender": "tv.withaibuild.customiuizer.r14.MISSING_SENDER_PROBE",
}

# Permitted probe result tokens from a broadcast target.
PERMITTED_RESULTS = {
    "SENTINEL",
    "FAILED",
    "HANDLED",
    "TIMEOUT",
    "NOT_AVAILABLE",
    "UNEXPECTED_SIDE_EFFECT",
}

_PASS_RESULTS = {"SENTINEL", "FAILED"}
_FAIL_RESULTS = {"HANDLED", "TIMEOUT", "UNEXPECTED_SIDE_EFFECT"}
_SKIP_RESULT = "NOT_AVAILABLE"


def action_for_kind(kind: str) -> str:
    """Return the Android action string for a logical broadcast kind."""
    if kind not in BROADCAST_ACTIONS:
        raise ValueError(f"unknown broadcast kind: {kind}")
    return BROADCAST_ACTIONS[kind]


def is_permitted_result(value: str) -> bool:
    return value in PERMITTED_RESULTS


def result_status(result: str) -> str:
    """Map a raw probe result to a step status.

    SENTINEL or FAILED  -> PASS (negative blocked as expected)
    HANDLED, TIMEOUT or UNEXPECTED_SIDE_EFFECT -> FAIL
    NOT_AVAILABLE       -> SKIPPED
    """
    if not is_permitted_result(result):
        return "ERROR"
    if result == _SKIP_RESULT:
        return "SKIPPED"
    if result in _PASS_RESULTS:
        return "PASS"
    if result in _FAIL_RESULTS:
        return "FAIL"
    return "ERROR"


def parse_probe_output(text: str) -> str:
    """Extract a result token from a broadcast probe response, or NOT_AVAILABLE."""
    if not text:
        return "NOT_AVAILABLE"
    # Prefer an explicit marker emitted by the fixture or a test harness.
    m = re.search(r"\[BroadcastProbe\]\s*result=(\w+)", text)
    if m:
        token = m.group(1)
        if is_permitted_result(token):
            return token
    # Accept a bare permitted token on its own line.
    pattern = "^(" + "|".join(PERMITTED_RESULTS) + ")$"
    m = re.search(pattern, text, re.MULTILINE)
    if m:
        return m.group(1)
    return "NOT_AVAILABLE"
