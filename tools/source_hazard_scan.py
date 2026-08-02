#!/usr/bin/env python3
"""Static hazard scanner for injected Android runtime code.

The scanner is intentionally strict. Existing reviewed findings can be frozen
in a JSON baseline. CI fails only on new fingerprints unless --strict-all is
used. Add `BRUTAL_ALLOW:<RULE>` on the same line for a narrow reviewed waiver.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path


@dataclass(frozen=True)
class Finding:
    rule: str
    path: str
    line: int
    snippet: str

    @property
    def fingerprint(self) -> str:
        normalized = re.sub(r"\s+", " ", self.snippet.strip())
        raw = f"{self.rule}\0{self.path}\0{normalized}".encode()
        return hashlib.sha256(raw).hexdigest()[:20]


RULES: list[tuple[str, re.Pattern[str], str]] = [
    (
        "EMPTY_CATCH",
        re.compile(r"catch\s*\([^)]*\)\s*\{\s*(?://[^\n]*)?\s*\}", re.S),
        "empty catch hides runtime failures",
    ),
    (
        "GLOBAL_SCOPE",
        re.compile(r"\bGlobalScope\s*\."),
        "GlobalScope has no injectable lifecycle owner",
    ),
    (
        "THREAD_SLEEP",
        re.compile(r"\bThread\.sleep\s*\("),
        "blocking sleep in injected production source",
    ),
    (
        "RUN_BLOCKING",
        re.compile(r"\brunBlocking\s*\{"),
        "runBlocking can block SystemUI/system_server",
    ),
    (
        "PRINT_STACK_TRACE",
        re.compile(r"\.printStackTrace\s*\("),
        "printStackTrace is uncontrolled production diagnostics",
    ),
    (
        "SYSTEM_OUT",
        re.compile(r"\bSystem\.(?:out|err)\."),
        "System.out/System.err in injected production source",
    ),
    (
        "NATIVE_LOAD",
        re.compile(r"\bSystem\.load(?:Library)?\s*\("),
        "native loading requires explicit benchmark and ABI review",
    ),
    (
        "STATIC_STRONG_ANDROID_OWNER",
        re.compile(
            r"(?m)^\s*(?:public|private|protected|internal)?\s*(?:static\s+|@JvmField\s+)?"
            r"(?:var|val|[A-Za-z0-9_<>?.]+\s+)"
            r"[A-Za-z0-9_]*\s*(?::\s*)?"
            r"(?:Context|Activity|View|Fragment|Window|Drawable)\??\s*(?:=|;)"
        ),
        "potential strong Android owner; require scoped owner or WeakReference",
    ),
    (
        "EAGER_HANDLER_THREAD",
        re.compile(r"HandlerThread\s*\([^)]*\)[\s\S]{0,160}?\.start\s*\("),
        "eager HandlerThread start; worker must be lazy and bounded",
    ),
    (
        "UNBOUNDED_GLOBAL_COLLECTION",
        re.compile(
            r"(?m)^\s*(?:static\s+|@JvmField\s+)?(?:val|var|final\s+\w+\s+)"
            r"\w+\s*(?::[^=\n]+)?=\s*(?:mutableListOf|mutableMapOf|mutableSetOf|"
            r"ArrayList|HashMap|HashSet|ConcurrentHashMap)\s*[<(]"
        ),
        "global mutable collection needs hard bound and lifecycle cleanup",
    ),
]


def line_number(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def scan_file(path: Path, repo_root: Path) -> list[Finding]:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return []
    rel = path.relative_to(repo_root).as_posix()
    findings: list[Finding] = []
    for rule, pattern, _ in RULES:
        for match in pattern.finditer(text):
            line = line_number(text, match.start())
            source_line = text.splitlines()[line - 1] if text.splitlines() else ""
            if f"BRUTAL_ALLOW:{rule}" in source_line:
                continue
            snippet = re.sub(r"\s+", " ", match.group(0))[:220]
            findings.append(Finding(rule, rel, line, snippet))

    # Catch(Throwable) requires visible fatal propagation in the local block.
    catch_pattern = re.compile(r"catch\s*\(\s*(\w+)\s*:\s*Throwable\s*\)\s*\{", re.M)
    for match in catch_pattern.finditer(text):
        start = match.end()
        window = text[start : start + 700]
        end = window.find("}")
        block = window if end < 0 else window[:end]
        if not re.search(
            r"\bthrow\b|rethrow|fatal|OutOfMemoryError|ThreadDeath|VirtualMachineError",
            block,
            re.I,
        ):
            line = line_number(text, match.start())
            source_line = text.splitlines()[line - 1]
            if "BRUTAL_ALLOW:CATCH_THROWABLE_NO_FATAL" not in source_line:
                findings.append(
                    Finding(
                        "CATCH_THROWABLE_NO_FATAL",
                        rel,
                        line,
                        re.sub(r"\s+", " ", text[match.start() : start + min(len(block), 220)]),
                    )
                )
    return findings


def collect(root: Path, paths: list[str]) -> list[Finding]:
    findings: list[Finding] = []
    for raw in paths:
        path = root / raw
        candidates = [path] if path.is_file() else [
            *path.rglob("*.kt"),
            *path.rglob("*.java"),
        ] if path.exists() else []
        for candidate in sorted(set(candidates)):
            findings.extend(scan_file(candidate, root))
    return findings


def load_baseline(path: Path) -> set[str]:
    if not path.exists():
        return set()
    data = json.loads(path.read_text(encoding="utf-8"))
    return set(data.get("fingerprints", []))


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--repo-root", default=".")
    p.add_argument(
        "--path",
        action="append",
        default=[],
        help="file or directory relative to repo; repeatable",
    )
    p.add_argument("--baseline", default="docs/audit/SOURCE_HAZARD_BASELINE.json")
    p.add_argument("--write-baseline", action="store_true")
    p.add_argument("--strict-all", action="store_true")
    p.add_argument("--json-output")
    args = p.parse_args(argv)

    root = Path(args.repo_root).resolve()
    paths = args.path or ["app/src/main/java"]
    findings = collect(root, paths)
    baseline_path = root / args.baseline

    if args.write_baseline:
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "schema": 1,
            "fingerprints": sorted(f.fingerprint for f in findings),
            "findings": [dict(asdict(f), fingerprint=f.fingerprint) for f in findings],
        }
        baseline_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote baseline with {len(findings)} finding(s): {baseline_path}")
        return 0

    baseline = set() if args.strict_all else load_baseline(baseline_path)
    new_findings = [f for f in findings if f.fingerprint not in baseline]
    payload = {
        "total": len(findings),
        "baseline": len(baseline),
        "new": len(new_findings),
        "findings": [dict(asdict(f), fingerprint=f.fingerprint) for f in new_findings],
    }
    if args.json_output:
        out = Path(args.json_output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    if new_findings:
        print(f"New source hazards: {len(new_findings)}")
        for f in new_findings:
            print(f"  {f.path}:{f.line}: {f.rule}: {f.snippet}")
        return 1
    print(f"Source hazard scan passed: {len(findings)} reviewed finding(s), 0 new")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
