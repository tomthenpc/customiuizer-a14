"""ADB regression plan validation."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from . import broadcast
from .safety import validate_command

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
FEATURE_SEMANTICS = REPO_ROOT / "feature-semantics" / "a14.json"

VALID_STEP_TYPES = {
    "shell", "sleep", "package_installed", "package_version", "process_alive",
    "process_snapshot", "process_restart_observed", "logcat_assert", "hook_summary",
    "broadcast_probe", "collect_diagnostics", "manual_checkpoint",
}

SUPPORTED_API = {28, 29, 30, 31, 32, 33, 34}
SUPPORTED_ROM_FAMILIES = {"hyperos1", "miui14", "aosp"}


def _load_semantics() -> tuple[set[str], set[str]]:
    if not FEATURE_SEMANTICS.is_file():
        return set(), set()
    data = json.loads(FEATURE_SEMANTICS.read_text(encoding="utf-8"))
    features: set[str] = set()
    prefs: set[str] = set()
    items = data if isinstance(data, list) else data.get("entries", [])
    for item in items:
        if isinstance(item, dict):
            fid = item.get("featureId")
            if fid:
                features.add(fid)
            for pk in item.get("preferenceKeys", []):
                if isinstance(pk, str):
                    prefs.add(pk)
    return features, prefs


FEATURES, PREFERENCE_KEYS = _load_semantics()


def _bad_path(value: Any) -> bool:
    if not isinstance(value, str) or not value:
        return False
    p = Path(value)
    if p.is_absolute():
        return True
    try:
        resolved = (REPO_ROOT / p).resolve()
        root = REPO_ROOT.resolve()
        if not str(resolved).startswith(str(root)):
            return True
    except (ValueError, OSError):
        return True
    return False


def _validate_step(step: Any, seen_ids: set[str], serial: str | None = None) -> tuple[bool, bool, list[str]]:
    """Return (schema_ok, semantic_ok, errors)."""
    if not isinstance(step, dict):
        return False, False, ["step must be an object"]

    errors: list[str] = []
    semantic: list[str] = []

    if "id" not in step or not isinstance(step["id"], str) or not step["id"]:
        errors.append("step missing id")
    elif step["id"] in seen_ids:
        errors.append(f"duplicate step id: {step['id']}")
    else:
        seen_ids.add(step["id"])
        if not re.match(r"^[a-zA-Z0-9_-]+$", step["id"]):
            errors.append(f"invalid step id: {step['id']}")

    if "type" not in step or step["type"] not in VALID_STEP_TYPES:
        errors.append(f"unknown or missing step type: {step.get('type')}")

    if "description" in step and not isinstance(step.get("description"), str):
        errors.append(f"step {step.get('id')} description must be string")

    if "timeoutSeconds" in step:
        t = step["timeoutSeconds"]
        if not isinstance(t, int) or t <= 0 or t > 3600:
            errors.append(f"step {step.get('id')} timeoutSeconds must be 1..3600")

    for b in ("dangerous", "manual", "continueOnFailure"):
        if b in step and not isinstance(step[b], bool):
            errors.append(f"step {step.get('id')} {b} must be boolean")

    # shell command safety
    if step.get("type") == "shell":
        cmd = step.get("command") or step.get("args") or []
        if isinstance(cmd, str):
            errors.append(f"step {step.get('id')} shell command must be an argument array")
        elif isinstance(cmd, list):
            allow_dangerous = bool(step.get("dangerous"))
            expected_action = step.get("expected", {}).get("action") if isinstance(step.get("expected"), dict) else None
            ok, reason = validate_command(cmd, allow_dangerous=allow_dangerous, expected_broadcast_action=expected_action)
            if not ok:
                errors.append(f"step {step.get('id')} unsafe shell: {reason}")

    # broadcast probe validation
    if step.get("type") == "broadcast_probe":
        expected = step.get("expected", {})
        if not isinstance(expected, dict):
            errors.append(f"step {step.get('id')} broadcast_probe expected must be an object")
        else:
            kind = expected.get("broadcastKind") or step.get("broadcastKind")
            if not kind:
                errors.append(f"step {step.get('id')} broadcast_probe requires broadcastKind")
            elif kind not in broadcast.BROADCAST_ACTIONS:
                errors.append(f"step {step.get('id')} unknown broadcastKind: {kind}")
            result = expected.get("result")
            if result is not None and result not in broadcast.PERMITTED_RESULTS:
                errors.append(f"step {step.get('id')} invalid broadcast result: {result}")

    # feature/preference links
    for fid in step.get("linkedFeatureIds", []):
        if FEATURES and fid not in FEATURES:
            semantic.append(f"step {step.get('id')} linkedFeatureId not found: {fid}")
    for pk in step.get("linkedPreferenceKeys", []):
        if PREFERENCE_KEYS and pk not in PREFERENCE_KEYS:
            semantic.append(f"step {step.get('id')} linkedPreferenceKey not found: {pk}")

    # evidence files path traversal
    for ef in step.get("evidenceFiles", []):
        if _bad_path(ef):
            errors.append(f"step {step.get('id')} evidenceFiles path traversal/absolute: {ef}")

    return len(errors) == 0, len(semantic) == 0, errors + semantic


def validate(plan_path: Path) -> tuple[int, list[str]]:
    """
    Validate a plan.  Returns (exit_code, messages).
    0 = legal, 1 = semantic inconsistency, 2 = schema/safety/input error.
    """
    if not plan_path.is_file():
        return 2, [f"plan not found: {plan_path}"]

    try:
        raw = plan_path.read_text(encoding="utf-8")
        plan = json.loads(raw)
    except json.JSONDecodeError as exc:
        return 2, [f"malformed JSON: {exc}"]
    except OSError as exc:
        return 2, [f"cannot read plan: {exc}"]

    if not isinstance(plan, dict):
        return 2, ["plan must be a JSON object"]

    schema_ok = True
    semantic_ok = True
    errors: list[str] = []
    semantic: list[str] = []

    for k in ("schemaVersion", "planId", "description", "supportedApi", "supportedRomFamily", "steps"):
        if k not in plan:
            errors.append(f"missing required field: {k}")

    if errors:
        return 2, errors

    if plan.get("schemaVersion") != 1:
        return 2, [f"unsupported schemaVersion: {plan.get('schemaVersion')}"]

    if not isinstance(plan.get("planId"), str) or not plan["planId"]:
        errors.append("planId must be a non-empty string")

    apis = plan.get("supportedApi", [])
    if not isinstance(apis, list) or not all(isinstance(a, int) and a in SUPPORTED_API for a in apis):
        errors.append(f"supportedApi must be a list of supported API levels: {SUPPORTED_API}")

    roms = plan.get("supportedRomFamily", [])
    if not isinstance(roms, list) or not all(isinstance(r, str) and r in SUPPORTED_ROM_FAMILIES for r in roms):
        errors.append(f"supportedRomFamily must be one of: {SUPPORTED_ROM_FAMILIES}")

    steps = plan.get("steps", [])
    if not isinstance(steps, list) or not steps:
        errors.append("steps must be a non-empty array")

    if errors:
        return 2, errors

    seen_ids: set[str] = set()
    for step in steps:
        s_schema, s_sem, s_msgs = _validate_step(step, seen_ids)
        if not s_schema:
            schema_ok = False
        if not s_sem:
            semantic_ok = False
        for m in s_msgs:
            if "not found" in m:
                semantic.append(m)
            else:
                errors.append(m)

    # Cleanup steps also validated.
    for step in plan.get("cleanup", []):
        s_schema, s_sem, s_msgs = _validate_step(step, seen_ids)
        if not s_schema:
            schema_ok = False
            errors.extend(s for s in s_msgs if "not found" not in s)
        if not s_sem:
            semantic_ok = False
            semantic.extend(s for s in s_msgs if "not found" in s)

    if not schema_ok:
        return 2, errors
    if not semantic_ok:
        return 1, semantic
    return 0, ["plan is valid"]
