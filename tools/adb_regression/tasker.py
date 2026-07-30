"""Manual checkpoint / Tasker result support for the ADB regression framework."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from . import redaction

_CHECKPOINT_STATUSES = {"PASS", "FAIL", "SKIPPED", "PENDING"}


def _redact_user_paths(text: str) -> str:
    """Redact common absolute / home-relative paths from manual notes."""
    # Windows drive paths
    text = re.sub(r"\b[A-Za-z]:\\[^ ;]+", "<PATH>", text)
    # Unix home directories
    text = re.sub(r"/home/[^ ;]+", "<PATH>", text)
    text = re.sub(r"/Users/[^ ;]+", "<PATH>", text)
    # Tilde / relative paths that look like filesystem paths
    text = re.sub(r"\b~?/[^ ;]+", "<PATH>", text)
    return text


def _redact_account_info(text: str) -> str:
    """Redact email and phone-like strings from manual notes."""
    text = re.sub(
        r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b",
        "<EMAIL>",
        text,
    )
    text = re.sub(r"\b\+?\d[\d\s-]{7,}\d\b", "<PHONE>", text)
    return text


def _redact_high_entropy_base64(text: str) -> str:
    """Redact base64 strings that may carry tokens or bundles.

    This is intentionally shorter than ``redaction.redact`` so token-sized
    values are also caught.
    """
    return re.sub(r"\b[A-Za-z0-9+/]{6,}={0,2}", "<BASE64>", text)


def redact_notes(text: str) -> str:
    """Redact tokens, bundles, base64, user paths and account info from notes."""
    if not text:
        return text
    text = redaction.redact(text, serial=None)
    text = _redact_user_paths(text)
    text = _redact_account_info(text)
    text = _redact_high_entropy_base64(text)
    return text


def load_manual_results(path: Path | str) -> dict[str, dict[str, Any]]:
    """Load a manual checkpoint results file.

    The file is a JSON object with a ``checkpoints`` array:
    { "checkpoints": [{ "stepId": "...", "status": "PASS|...", "notes": "..." }] }

    Returns a dict keyed by ``stepId`` containing the status, redacted notes
    and the raw checkpoint.
    """
    p = Path(path).expanduser().resolve()
    if not p.is_file():
        raise FileNotFoundError(f"manual results not found: {p}")

    data = json.loads(p.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or "checkpoints" not in data:
        raise ValueError("manual results must contain a top-level 'checkpoints' array")

    checkpoints = data["checkpoints"]
    if not isinstance(checkpoints, list):
        raise ValueError("'checkpoints' must be an array")

    results: dict[str, dict[str, Any]] = {}
    for cp in checkpoints:
        if not isinstance(cp, dict):
            raise ValueError("each checkpoint must be an object")
        step_id = cp.get("stepId")
        if not step_id or not isinstance(step_id, str):
            raise ValueError("each checkpoint must have a non-empty string stepId")
        status = cp.get("status", "PENDING")
        if status not in _CHECKPOINT_STATUSES:
            raise ValueError(f"invalid checkpoint status: {status}")
        notes = redact_notes(cp.get("notes", ""))
        results[step_id] = {
            "status": status,
            "notes": notes,
            "raw": cp,
        }
    return results
