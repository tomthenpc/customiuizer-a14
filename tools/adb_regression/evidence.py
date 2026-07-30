"""Device evidence proposal generation for the ADB regression framework."""

from __future__ import annotations

import hashlib
import json
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
FEATURE_SEMANTICS = REPO_ROOT / "feature-semantics" / "a14.json"
EVIDENCE_SCHEMA = REPO_ROOT / "adb-regression" / "evidence-schema.json"

_REQUIRED_REPORT_FIELDS = (
    "schemaVersion",
    "runId",
    "planId",
    "timestamp",
    "summary",
    "steps",
)

_REQUIRED_PROPOSAL_FIELDS = (
    "schemaVersion",
    "proposalId",
    "featureId",
    "preferenceKeys",
    "deviceScope",
    "buildFingerprint",
    "romFamily",
    "androidApi",
    "moduleVersion",
    "moduleCommit",
    "testPlanId",
    "testPlanSha256",
    "observedEnableEffect",
    "observedDisableEffect",
    "observedValueChangeEffect",
    "observedRestartTarget",
    "result",
    "timestamp",
    "evidenceFiles",
    "evidenceFileHashes",
    "reviewerStatus",
    "evidenceConfidence",
)


class EvidenceError(Exception):
    def __init__(self, message: str, exit_code: int = 2) -> None:
        super().__init__(message)
        self.exit_code = exit_code


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise EvidenceError(f"malformed JSON: {exc}", 2)
    except OSError as exc:
        raise EvidenceError(f"cannot read file: {exc}", 2)


def _validate_report(data: Any) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return ["report must be a JSON object"]
    if data.get("schemaVersion") != 1:
        errors.append("unsupported report schemaVersion")
    for k in _REQUIRED_REPORT_FIELDS:
        if k not in data:
            errors.append(f"missing report field: {k}")
    if "simulation" in data and not isinstance(data["simulation"], bool):
        errors.append("simulation must be a boolean")
    for k in ("planSha256", "gitCommit", "moduleVersion", "fingerprint", "romFamily"):
        v = data.get(k)
        if v is not None and not isinstance(v, (str, int, float, bool)):
            errors.append(f"{k} has an invalid type")
    if "androidApi" in data and not isinstance(data["androidApi"], int):
        errors.append("androidApi must be an integer")
    return errors


def _preflight_fallback(report_dir: Path) -> dict[str, Any]:
    pf = report_dir / "preflight.json"
    if pf.is_file():
        try:
            return json.loads(pf.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {}


def _extract_context(report: dict[str, Any], report_dir: Path) -> dict[str, Any]:
    ctx: dict[str, Any] = {
        "planId": report.get("planId", "unknown"),
        "planSha256": report.get("planSha256", ""),
        "gitCommit": report.get("gitCommit", ""),
        "moduleVersion": report.get("moduleVersion", ""),
        "androidApi": report.get("androidApi", 0),
        "fingerprint": report.get("fingerprint", ""),
        "romFamily": report.get("romFamily", ""),
        "timestamp": report.get("timestamp", ""),
        "simulation": bool(report.get("simulation", False)),
        "evidenceConfidence": report.get("evidenceConfidence", "VERIFIED"),
    }
    pf = _preflight_fallback(report_dir)
    if not ctx["gitCommit"]:
        ctx["gitCommit"] = pf.get("gitCommit", "unknown")
    if not ctx["moduleVersion"]:
        ctx["moduleVersion"] = pf.get("module", {}).get("versionName", "unknown")
    if not ctx["androidApi"]:
        ctx["androidApi"] = pf.get("androidApi", 0)
    if not ctx["fingerprint"]:
        ctx["fingerprint"] = pf.get("fingerprint", "unknown")
    if not ctx["romFamily"]:
        ctx["romFamily"] = pf.get("romFamily", "unknown")
    if not ctx["timestamp"]:
        ctx["timestamp"] = pf.get("timestamp", "")
    if not ctx["timestamp"]:
        ctx["timestamp"] = datetime.now(timezone.utc).isoformat()
    return ctx


def _overall_result(report: dict[str, Any]) -> str:
    if report.get("simulation"):
        return "SIMULATION"
    steps = report.get("steps", [])
    if not isinstance(steps, list):
        return "ERROR"
    statuses = [s.get("status") for s in steps if isinstance(s, dict)]
    if any(s in ("MANUAL_PENDING", "PENDING") for s in statuses):
        return "PENDING"
    if any(s == "SKIPPED" for s in statuses):
        return "SKIPPED"
    if any(s in ("FAIL", "ERROR") for s in statuses):
        return "FAIL"
    base_conf = report.get("evidenceConfidence", "VERIFIED")
    if base_conf not in ("VERIFIED",):
        return base_conf
    return "PASS"


def _evidence_confidence(report: dict[str, Any]) -> str:
    if report.get("simulation"):
        return "SIMULATION_ONLY"
    return report.get("evidenceConfidence", "VERIFIED")


def _select_features(entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Pick a small, representative set of low-risk feature entries."""
    selected: list[dict[str, Any]] = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        pks = entry.get("preferenceKeys", [])
        if not pks:
            continue
        if any(not isinstance(p, str) or "${" in p for p in pks):
            continue
        selected.append(entry)
        if len(selected) >= 12:
            break
    return selected


def _collect_evidence_files(report_dir: Path) -> tuple[list[str], list[str]]:
    manifest = report_dir / "manifest.json"
    paths: list[str] = []
    hashes: list[str] = []
    if manifest.is_file():
        try:
            data = json.loads(manifest.read_text(encoding="utf-8"))
            for f in data.get("files", []):
                if isinstance(f, dict) and "path" in f:
                    p = report_dir / f["path"]
                    if p.is_file():
                        rel = p.relative_to(report_dir).as_posix()
                        paths.append(rel)
                        hashes.append(_sha256_file(p))
        except Exception:
            pass
    if not paths:
        for p in sorted(report_dir.iterdir()):
            if p.is_file() and p.name not in ("manifest.json", "proposal.json"):
                rel = p.relative_to(report_dir).as_posix()
                paths.append(rel)
                hashes.append(_sha256_file(p))
    return paths, hashes


def _validate_proposal(proposal: Any) -> tuple[bool, list[str]]:
    if not isinstance(proposal, dict):
        return False, ["proposal must be an object"]
    errors: list[str] = []
    for k in _REQUIRED_PROPOSAL_FIELDS:
        if k not in proposal:
            errors.append(f"proposal missing {k}")
    if proposal.get("reviewerStatus") != "PENDING_REVIEW":
        errors.append("reviewerStatus must be PENDING_REVIEW")
    if proposal.get("result") == "DEVICE_VERIFIED":
        errors.append("result must not be DEVICE_VERIFIED")
    if proposal.get("evidenceConfidence") == "DEVICE_VERIFIED":
        errors.append("evidenceConfidence must not be DEVICE_VERIFIED")
    return len(errors) == 0, errors


def _build_proposals(
    ctx: dict[str, Any],
    selected: list[dict[str, Any]],
    evidence_files: list[str],
    evidence_hashes: list[str],
    result: str,
    confidence: str,
) -> list[dict[str, Any]]:
    proposals: list[dict[str, Any]] = []
    ts = ctx["timestamp"]
    for entry in selected:
        fid = entry.get("featureId", "unknown")
        proposal = {
            "schemaVersion": 1,
            "proposalId": f"evidence-{ctx['planId']}-{fid}-{uuid.uuid4().hex[:8]}",
            "featureId": fid,
            "preferenceKeys": entry.get("preferenceKeys", []),
            "deviceScope": entry.get("targetPackage") or "device",
            "buildFingerprint": ctx["fingerprint"],
            "romFamily": ctx["romFamily"],
            "androidApi": ctx["androidApi"],
            "moduleVersion": ctx["moduleVersion"],
            "moduleCommit": ctx["gitCommit"],
            "testPlanId": ctx["planId"],
            "testPlanSha256": ctx["planSha256"],
            "observedEnableEffect": entry.get("enableEffect", ""),
            "observedDisableEffect": entry.get("disableEffect", ""),
            "observedValueChangeEffect": entry.get("valueChangeEffect", ""),
            "observedRestartTarget": entry.get("restartTarget", ""),
            "result": result,
            "timestamp": ts,
            "evidenceFiles": evidence_files,
            "evidenceFileHashes": evidence_hashes,
            "reviewerStatus": "PENDING_REVIEW",
            "evidenceConfidence": confidence,
        }
        ok, errors = _validate_proposal(proposal)
        if not ok:
            raise EvidenceError(f"invalid proposal for {fid}: {'; '.join(errors)}", 2)
        proposals.append(proposal)
    return proposals


def _load_semantics() -> list[dict[str, Any]]:
    if not FEATURE_SEMANTICS.is_file():
        raise EvidenceError(f"feature semantics not found: {FEATURE_SEMANTICS}", 2)
    data = _load_json(FEATURE_SEMANTICS)
    if isinstance(data, dict):
        entries = data.get("entries", [])
    elif isinstance(data, list):
        entries = data
    else:
        raise EvidenceError("feature-semantics must contain an array of entries", 2)
    if not isinstance(entries, list):
        raise EvidenceError("feature-semantics entries must be an array", 2)
    return entries


def _write_output(output_path: Path, payload: dict[str, Any]) -> None:
    output_path = output_path.expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def propose(report_path: Path, output_path: Path) -> int:
    """Propose formal device evidence from a regression report.

    Returns 0 on success, 2 on rejection or validation failure.
    """
    report_path = report_path.expanduser().resolve()
    if not report_path.is_file():
        print(f"evidence: report not found: {report_path}", file=sys.stderr)
        return 2

    try:
        data = _load_json(report_path)
    except EvidenceError as exc:
        print(f"evidence: {exc}", file=sys.stderr)
        return exc.exit_code

    errors = _validate_report(data)
    if errors:
        for e in errors:
            print(f"evidence: {e}", file=sys.stderr)
        return 2

    report_dir = report_path.parent
    ctx = _extract_context(data, report_dir)
    result = _overall_result(data)
    confidence = _evidence_confidence(data)

    if data.get("simulation"):
        rejected = {
            "schemaVersion": 1,
            "planId": ctx["planId"],
            "testPlanId": ctx["planId"],
            "testPlanSha256": ctx["planSha256"],
            "timestamp": ctx["timestamp"],
            "rejected": True,
            "rejectionReason": "SIMULATION_ONLY",
            "evidenceConfidence": "SIMULATION_ONLY",
            "proposals": [],
        }
        _write_output(output_path, rejected)
        print(
            "evidence: simulation reports cannot produce formal device evidence "
            f"(rejection written to {output_path})",
            file=sys.stderr,
        )
        return 2

    try:
        entries = _load_semantics()
    except EvidenceError as exc:
        print(f"evidence: {exc}", file=sys.stderr)
        return exc.exit_code

    selected = _select_features(entries)
    if len(selected) < 10:
        print(
            f"evidence: only {len(selected)} feature entries found; need at least 10",
            file=sys.stderr,
        )
        return 2

    evidence_files, evidence_hashes = _collect_evidence_files(report_dir)
    try:
        proposals = _build_proposals(ctx, selected, evidence_files, evidence_hashes, result, confidence)
    except EvidenceError as exc:
        print(f"evidence: {exc}", file=sys.stderr)
        return exc.exit_code

    output = {
        "schemaVersion": 1,
        "planId": ctx["planId"],
        "testPlanId": ctx["planId"],
        "testPlanSha256": ctx["planSha256"],
        "timestamp": ctx["timestamp"],
        "rejected": False,
        "rejectionReason": "",
        "evidenceConfidence": confidence,
        "proposals": proposals,
    }
    _write_output(output_path, output)
    print(f"proposed {len(proposals)} evidence entries: {output_path}")
    return 0
