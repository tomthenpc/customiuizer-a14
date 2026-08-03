#!/usr/bin/env python3
"""Mutation, hermeticity and determinism tests for repository gates.

The tool never mutates the real worktree during mutation testing. Every
mutation is applied to a detached temporary git worktree and is expected to be
"killed" by a configured gate. A surviving mutation is a concrete test gap.
"""
from __future__ import annotations

import argparse
import base64
import contextlib
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Callable


def normalize_command(command: str) -> str:
    """Use gradlew.bat on Windows when the configured command starts with gradlew."""
    if os.name == "nt" and command.startswith("gradlew "):
        return "gradlew.bat" + command[len("gradlew"):]
    return command


def run(
    command: str,
    cwd: Path,
    timeout: int,
    env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess:
    merged = os.environ.copy()
    if env:
        merged.update(env)
    return subprocess.run(
        normalize_command(command),
        cwd=cwd,
        shell=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout,
        env=merged,
    )


def run_quiet(command: list[str], cwd: Path, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        command,
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=check,
    )


def repo_root() -> Path:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        text=True,
        capture_output=True,
        check=True,
    )
    return Path(result.stdout.strip()).resolve()


def tracked_hashes(root: Path) -> dict[str, str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=root,
        stdout=subprocess.PIPE,
        check=True,
    )
    hashes: dict[str, str] = {}
    for raw in result.stdout.split(b"\0"):
        if not raw:
            continue
        rel = raw.decode("utf-8", errors="surrogateescape")
        path = root / rel
        if path.is_file():
            hashes[rel] = hashlib.sha256(path.read_bytes()).hexdigest()
        else:
            hashes[rel] = "<missing>"
    return hashes


def git_status_porcelain(root: Path) -> list[tuple[str, str]]:
    """Return parsed `git status --porcelain=v1 --untracked-files=all` entries."""
    result = subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=root,
        stdout=subprocess.PIPE,
        text=True,
        check=True,
    )
    entries: list[tuple[str, str]] = []
    for line in result.stdout.splitlines():
        if not line:
            continue
        status = line[:2]
        path = line[3:]
        entries.append((status, path))
    return entries


def active_worktrees(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "worktree", "list", "--porcelain"],
        cwd=root,
        stdout=subprocess.PIPE,
        text=True,
        check=True,
    )
    worktrees: list[Path] = []
    for line in result.stdout.splitlines():
        if line.startswith("worktree "):
            worktrees.append(Path(line[len("worktree "):]).resolve())
    return worktrees


@contextlib.contextmanager
def detached_worktree(root: Path):
    temp_parent = Path(tempfile.mkdtemp(prefix="brutal-worktree-"))
    target = temp_parent / "repo"
    worktree_proc = subprocess.run(
        ["git", "worktree", "add", "--detach", "--force", str(target), "HEAD"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if worktree_proc.returncode != 0:
        raise RuntimeError(worktree_proc.stdout)
    try:
        yield target
    finally:
        start = time.monotonic()
        while True:
            remove_proc = subprocess.run(
                ["git", "worktree", "remove", "--force", str(target)],
                cwd=root,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
            if remove_proc.returncode == 0:
                break
            if time.monotonic() - start > 10:
                break
            time.sleep(0.1)
        shutil.rmtree(temp_parent, ignore_errors=True)


def replace_first(path: Path, pattern: str, replacement: str, flags: int = 0) -> None:
    text = path.read_text(encoding="utf-8")
    changed, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"mutation pattern not found in {path}: {pattern}")
    path.write_text(changed, encoding="utf-8")


def replace_all(path: Path, pattern: str, replacement: str, flags: int = 0) -> int:
    text = path.read_text(encoding="utf-8")
    changed, count = re.subn(pattern, replacement, text, flags=flags)
    if count == 0:
        raise RuntimeError(f"mutation pattern not found in {path}: {pattern}")
    path.write_text(changed, encoding="utf-8")
    return count


def append_after_line(path: Path, prefix: str, lines: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    out = []
    done = False
    for line in text.splitlines():
        out.append(line)
        if not done and line.startswith(prefix):
            out.extend(lines)
            done = True
    if not done:
        raise RuntimeError(f"line prefix not found: {prefix}")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def mutate_duplicate_smart(root: Path, cfg: dict) -> None:
    path = root / cfg["smart_state"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"(?m)^CurrentObjective:.*$", text)
    if not m:
        raise RuntimeError("CurrentObjective missing")
    path.write_text(text[:m.end()] + "\n" + m.group(0) + text[m.end():], encoding="utf-8")


def mutate_fake_ci_pass(root: Path, cfg: dict) -> None:
    path = root / cfg["smart_state"]
    replace_first(path, r"(?m)^LastCIState:.*$", "LastCIState: PASS")
    text = path.read_text(encoding="utf-8")
    if "LastCIRun:" not in text:
        append_after_line(
            path,
            "LastCIState:",
            [
                "LastCIRun: https://github.com/example/example/actions/runs/1",
                "LastCIJob: https://github.com/example/example/actions/runs/1/job/1",
                "LastCICommit: 0000000000000000000000000000000000000000",
            ],
        )


def mutate_symbolic_tree(root: Path, cfg: dict) -> None:
    replace_first(root / cfg["smart_state"], r"(?m)^LastVerifiedTree:.*$", "LastVerifiedTree: HEAD^{tree}")


def mutate_ambiguous_resume(root: Path, cfg: dict) -> None:
    replace_first(root / cfg["smart_state"], r"(?m)^ResumeTask:(.*)$", r"ResumeTask:\1 or skip to P99")


def mutate_task_drift(root: Path, cfg: dict) -> None:
    replace_first(root / cfg["task_state"], r"State:\s*`COMPLETE`", "State: `TODO`")


def mutate_progress_tamper(root: Path, cfg: dict) -> None:
    path = root / cfg["progress_json"]
    data = json.loads(path.read_text(encoding="utf-8"))
    data["__BRUTAL_MUTATION__"] = {"projectProgress": 999.0}
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def workflow_path(root: Path, cfg: dict, full: bool = False) -> Path:
    paths = cfg["full_workflows"] if full else cfg["fast_workflows"]
    if not paths:
        raise RuntimeError("workflow path not configured")
    return root / paths[0]


def mutate_remove_fetch_depth(root: Path, cfg: dict) -> None:
    path = workflow_path(root, cfg)
    text = path.read_text(encoding="utf-8")
    changed, count = re.subn(r"(?m)^\s*fetch-depth\s*:\s*0\s*\n", "", text, count=1)
    if count != 1:
        raise RuntimeError("fetch-depth: 0 not found")
    path.write_text(changed, encoding="utf-8")


def mutate_wrong_branch(root: Path, cfg: dict) -> None:
    replace_first(workflow_path(root, cfg), re.escape(cfg["expected_branch"]), "main")


def mutate_signing_leak(root: Path, cfg: dict) -> None:
    path = workflow_path(root, cfg)
    with path.open("a", encoding="utf-8") as f:
        f.write(
            "\n# BRUTAL MUTATION\n"
            "      - name: forbidden signing\n"
            "        run: ./gradlew -PofficialRelease=true :app:assembleDevelop\n"
        )


def mutate_hardcoded_api37(root: Path, cfg: dict) -> None:
    path = workflow_path(root, cfg)
    with path.open("a", encoding="utf-8") as f:
        f.write(
            "\n# BRUTAL MUTATION\n"
            "      - name: brittle sdk\n"
            '        run: sdkmanager "platforms;android-37" "build-tools;37.0.0"\n'
        )


def mutate_windows_path(root: Path, cfg: dict) -> None:
    path = root / "tools" / "__brutal_windows_path_mutation.py"
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = base64.b64decode(
        "ZnJvbSBwYXRobGliIGltcG9ydCBQYXRoCnggPSBQYXRoKCJDOlxcdGVtcFxc"
        "cmVwbyIpCnkgPSAiYS9iIi5yZXBsYWNlKCIvIiwgIlxcIikK"
    ).decode("utf-8")
    path.write_text(payload, encoding="utf-8")


def mutate_duplicate_feature_id(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    ids = list(re.finditer(r'\bid\s*=\s*([A-Za-z0-9_]+)', text))
    unique = []
    for m in ids:
        if m.group(1) not in [u.group(1) for u in unique]:
            unique.append(m)
        if len(unique) == 2:
            break
    if len(unique) < 2:
        raise RuntimeError("two FeatureSpec ids not found")
    second = unique[1]
    changed = text[:second.start(1)] + unique[0].group(1) + text[second.end(1):]
    path.write_text(changed, encoding="utf-8")


def mutate_preference_default(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"getBoolean\s*\(([^,\n]+),\s*(false|true)\s*\)", text)
    if not m:
        raise RuntimeError("literal getBoolean default not found")
    flipped = "true" if m.group(2) == "false" else "false"
    changed = text[:m.start(2)] + flipped + text[m.end(2):]
    path.write_text(changed, encoding="utf-8")


def mutate_process_scope(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"\btarget\s*=\s*FeatureTarget\.([A-Z0-9_]+)", text)
    if not m:
        raise RuntimeError("FeatureTarget target not found")
    replacements = ["SYSTEM_UI", "LAUNCHER", "SYSTEM_PACKAGE", "SYSTEM_SERVER"]
    target = next((x for x in replacements if x != m.group(1)), "SYSTEM_UI")
    changed = text[:m.start(1)] + target + text[m.end(1):]
    path.write_text(changed, encoding="utf-8")


def mutate_install_phase(root: Path, cfg: dict) -> None:
    path = root / cfg["catalog_file"]
    text = path.read_text(encoding="utf-8")
    m = re.search(r"\bphase\s*=\s*InstallPhase\.([A-Z0-9_]+)", text)
    if not m:
        raise RuntimeError("InstallPhase phase not found")
    replacements = ["PACKAGE_READY", "APPLICATION_ATTACHED", "SYSTEM_SERVER_STARTING", "MODULE_LOADED"]
    target = next((x for x in replacements if x != m.group(1)), "PACKAGE_READY")
    changed = text[:m.start(1)] + target + text[m.end(1):]
    path.write_text(changed, encoding="utf-8")


def mutate_remove_dispatch(root: Path, cfg: dict) -> None:
    roots = [root / p for p in cfg.get("installer_roots", ["app/src/main/java"])]
    for base in roots:
        for path in [*base.rglob("*.kt"), *base.rglob("*.java")]:
            text = path.read_text(encoding="utf-8")
            changed, count = re.subn(
                r"(?m)^.*\.installAll\([^\n]*\n",
                "",
                text,
                count=1,
            )
            if count:
                path.write_text(changed, encoding="utf-8")
                return
    raise RuntimeError("FeatureInstallRegistry.installAll call not found")


def mutation_source_file(root: Path, body: str) -> None:
    path = root / "app/src/main/java/brutal_mutation/InjectedHazard.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "package brutal_mutation\n\n" + body + "\n",
        encoding="utf-8",
    )


def mutate_fatal_swallow(root: Path, cfg: dict) -> None:
    mutation_source_file(
        root,
        'object InjectedHazard { fun run() { try { error("x") } catch (t: Throwable) { } } }',
    )


def mutate_static_context(root: Path, cfg: dict) -> None:
    mutation_source_file(
        root,
        "object InjectedHazard { @JvmField var context: android.content.Context? = null }",
    )


def mutate_eager_thread(root: Path, cfg: dict) -> None:
    mutation_source_file(
        root,
        'object InjectedHazard { val worker = android.os.HandlerThread("bad").apply { start() } }',
    )


def maybe_extra_mutators() -> dict[str, Callable[[Path, dict], None]]:
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location(
            "brutal_a14_mutators",
            Path(__file__).resolve().parent / "brutal_a14_mutators.py",
        )
        if spec is None or spec.loader is None:
            return {}
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return getattr(module, "MUTATORS", {})
    except Exception:
        return {}


MUTATORS: dict[str, Callable[[Path, dict], None]] = {
    "duplicate_smart_key": mutate_duplicate_smart,
    "fake_ci_pass": mutate_fake_ci_pass,
    "symbolic_verified_tree": mutate_symbolic_tree,
    "ambiguous_resume": mutate_ambiguous_resume,
    "task_state_drift": mutate_task_drift,
    "progress_json_tamper": mutate_progress_tamper,
    "remove_fetch_depth": mutate_remove_fetch_depth,
    "wrong_ci_branch": mutate_wrong_branch,
    "signing_leak": mutate_signing_leak,
    "hardcoded_api37": mutate_hardcoded_api37,
    "windows_path": mutate_windows_path,
    "duplicate_feature_id": mutate_duplicate_feature_id,
    "preference_default_flip": mutate_preference_default,
    "process_scope_flip": mutate_process_scope,
    "install_phase_flip": mutate_install_phase,
    "remove_installer_dispatch": mutate_remove_dispatch,
    "fatal_swallow": mutate_fatal_swallow,
    "static_context_leak": mutate_static_context,
    "eager_handler_thread": mutate_eager_thread,
}
MUTATORS.update(maybe_extra_mutators())


class ConfigError(Exception):
    pass


def validate_config(cfg: dict) -> None:
    if not isinstance(cfg, dict):
        raise ConfigError("config must be a JSON object")

    if cfg.get("schema_version") != 1:
        raise ConfigError("config schema_version must be 1")

    for key in ("mutations", "hermetic_commands", "determinism_command", "determinism_outputs", "gates", "expected_branch"):
        if key not in cfg:
            raise ConfigError(f"config missing required key: {key}")

    mutations = cfg["mutations"]
    if not isinstance(mutations, list) or not mutations:
        raise ConfigError("mutations must be a non-empty list")

    minimum = cfg.get("minimum_mutations")
    if minimum is None:
        raise ConfigError("minimum_mutations is required")
    if not isinstance(minimum, int) or minimum < 1:
        raise ConfigError("minimum_mutations must be a positive integer")
    if minimum > len(mutations):
        raise ConfigError(f"minimum_mutations ({minimum}) exceeds configured mutation count ({len(mutations)})")

    required = cfg.get("required_mutations", [])
    if not isinstance(required, list):
        raise ConfigError("required_mutations must be a list")
    names = [m["name"] for m in mutations]
    missing = [r for r in required if r not in names]
    if missing:
        raise ConfigError(f"required mutation(s) missing from config: {missing}")

    seen: set[str] = set()
    duplicates = []
    for m in mutations:
        name = m.get("name")
        if not name or not isinstance(name, str):
            raise ConfigError("every mutation must have a non-empty name")
        if name in seen:
            duplicates.append(name)
        seen.add(name)
        mutator = m.get("mutator")
        if not mutator or not isinstance(mutator, str):
            raise ConfigError(f"mutation {name!r} missing mutator")
        if mutator not in MUTATORS:
            raise ConfigError(f"mutation {name!r} references unknown mutator {mutator!r}")
        gate = m.get("gate")
        if not gate or not isinstance(gate, str):
            raise ConfigError(f"mutation {name!r} missing gate")
        if gate not in cfg["gates"]:
            raise ConfigError(f"mutation {name!r} references unknown gate {gate!r}")
    if duplicates:
        raise ConfigError(f"duplicate mutation names: {duplicates}")

    gates = cfg["gates"]
    if not isinstance(gates, dict) or not gates:
        raise ConfigError("gates must be a non-empty dict")
    for gate_name, command in gates.items():
        if not command or not isinstance(command, str):
            raise ConfigError(f"gate {gate_name!r} has an empty command")

    hermetic = cfg["hermetic_commands"]
    if not isinstance(hermetic, list) or not hermetic:
        raise ConfigError("hermetic_commands must be a non-empty list")

    determinism_outputs = cfg["determinism_outputs"]
    if not isinstance(determinism_outputs, list) or not determinism_outputs:
        raise ConfigError("determinism_outputs must be a non-empty list")
    if len(determinism_outputs) != len(set(determinism_outputs)):
        raise ConfigError("determinism_outputs must be unique")


def _is_allowed_untracked(rel: str, allowed_untracked: set[str]) -> bool:
    for prefix in allowed_untracked:
        if rel.startswith(prefix) or rel.startswith("." + prefix.rstrip("/")):
            return True
    return False


def collect_status_failures(
    root: Path,
    allowed_untracked: set[str],
    baseline_untracked: set[str] | None = None,
) -> tuple[bool, list[str]]:
    """Check git status for tracked changes and new untracked files.

    If *baseline_untracked* is provided, only untracked files not in the baseline
    are reported; this lets the suite detect files created by the commands under
    test without failing on pre-existing untracked work.
    """
    failures: list[str] = []
    for status, rel in git_status_porcelain(root):
        if status == "??":
            if _is_allowed_untracked(rel, allowed_untracked):
                continue
            if baseline_untracked is not None and rel in baseline_untracked:
                continue
            failures.append(f"untracked: {rel}")
        elif status.startswith("D"):
            failures.append(f"deleted: {rel}")
        elif status:
            failures.append(f"modified ({status.strip()}): {rel}")
    return not failures, failures


def allowed_untracked_patterns() -> set[str]:
    return {
        ".gradle/",
        "build/",
        "app/build/",
        "__pycache__/",
    }


def hermeticity(root: Path, cfg: dict, timeout: int) -> int:
    allowed = allowed_untracked_patterns()
    baseline_status = git_status_porcelain(root)
    baseline_untracked = {rel for status, rel in baseline_status if status == "??" and not _is_allowed_untracked(rel, allowed)}

    _, pre_failures = collect_status_failures(root, allowed, baseline_untracked=baseline_untracked)
    if pre_failures:
        print("Hermeticity FAILED: existing untracked files in protected paths")
        for f in pre_failures[:30]:
            print(f"  {f}")
        if len(pre_failures) > 30:
            print(f"  ... and {len(pre_failures) - 30} more")
        return 1

    before = tracked_hashes(root)
    for command in cfg["hermetic_commands"]:
        result = run(command, root, timeout)
        if result.returncode:
            print(f"Hermeticity FAILED: command failed: {command}")
            print(result.stdout[-3000:])
            return 1

    after = tracked_hashes(root)
    dirty = sorted(k for k in set(before) | set(after) if before.get(k) != after.get(k))
    if dirty:
        print("Hermeticity FAILED: tracked files changed by read-only tests")
        for d in dirty[:30]:
            print(f"  {d}")
        return 1

    _, post_failures = collect_status_failures(root, allowed, baseline_untracked=baseline_untracked)
    if post_failures:
        print("Hermeticity FAILED: new untracked or dirty files after tests")
        for f in post_failures[:30]:
            print(f"  {f}")
        return 1

    worktrees = active_worktrees(root)
    stale = [w for w in worktrees if "brutal-worktree-" in w.as_posix()]
    if stale:
        print("Hermeticity FAILED: stale brutal worktrees remain")
        for w in stale[:10]:
            print(f"  {w}")
        return 1

    print(f"Hermeticity passed: {len(cfg['hermetic_commands'])} command(s), working tree clean")
    return 0


VOLATILE_KEYS = {
    "generatedAt",
    "sourceCommit",
    "sourceTree",
    "verifiedTree",
    "verifiedMode",
    "ciState",
    "ciRun",
    "ciJob",
    "ciCommit",
    "auditTime",
    "timestamp",
    "builtAt",
    "builtBy",
}


def _normalize_json(text: str) -> str:
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return text
    if isinstance(data, dict):
        for key in list(data.keys()):
            if key in VOLATILE_KEYS:
                del data[key]
        return json.dumps(data, indent=2, sort_keys=True)
    return text


def _pascal_key(key: str) -> str:
    if not key:
        return key
    return key[0].upper() + key[1:]


def _normalize_text(text: str) -> str:
    for key in VOLATILE_KEYS:
        for form in {key, _pascal_key(key)}:
            text = re.sub(
                rf"(?m)^\s*(?:{re.escape(form)})\s*[:=]\s*.*$\n?",
                "",
                text,
            )
    text = re.sub(r"\n\n+", "\n", text)
    return text


def normalize_determinism_output(path: Path, text: str) -> str:
    """Remove known volatile fields while keeping the structural payload."""
    if path.suffix == ".json":
        return _normalize_json(text)
    return _normalize_text(text)


def determinism(root: Path, cfg: dict, timeout: int) -> int:
    command = cfg["determinism_command"]
    outputs = [Path(p) for p in cfg["determinism_outputs"]]
    snapshots: list[dict[str, str]] = []
    expected_output_paths = {p.as_posix() for p in outputs}

    with detached_worktree(root) as work:
        for env in (
            {"PYTHONHASHSEED": "1", "TZ": "UTC", "LC_ALL": "C"},
            {"PYTHONHASHSEED": "777", "TZ": "Asia/Tokyo", "LC_ALL": "C"},
        ):
            before = tracked_hashes(work)
            result = run(command, work, timeout, env)
            if result.returncode:
                print(f"Determinism FAILED: command failed: {command}")
                print(result.stdout[-3000:])
                return 1

            for rel in outputs:
                path = work / rel
                if not path.exists():
                    print(f"Determinism FAILED: output missing: {rel}")
                    return 1

            # The command must not create files outside the declared outputs or modify
            # tracked files other than the declared outputs.
            after_files = {p for p in work.rglob("*") if p.is_file() and not _is_build_artifact(p, work) and p.name != ".git"}
            after_rel = {p.relative_to(work).as_posix() for p in after_files}
            new_files = after_rel - set(before.keys())
            unexpected_new = new_files - expected_output_paths
            if unexpected_new:
                print(f"Determinism FAILED: unexpected new files: {sorted(unexpected_new)[:20]}")
                return 1

            after = tracked_hashes(work)
            changed = [rel for rel in set(before) if before[rel] != after.get(rel, "<missing>")]
            unexpected_changed = [rel for rel in changed if rel not in expected_output_paths]
            if unexpected_changed:
                print(f"Determinism FAILED: modified tracked files outside outputs: {unexpected_changed[:20]}")
                return 1

            current: dict[str, str] = {}
            for rel in outputs:
                path = work / rel
                text = path.read_text(encoding="utf-8")
                text = normalize_determinism_output(path, text)
                current[rel.as_posix()] = hashlib.sha256(text.encode()).hexdigest()
            snapshots.append(current)

    if snapshots[0] != snapshots[1]:
        print("Determinism FAILED: output differs between runs")
        print(json.dumps({"run1": snapshots[0], "run2": snapshots[1]}, indent=2))
        return 1

    print(f"Determinism passed for {len(outputs)} output(s)")
    return 0


def _is_build_artifact(path: Path, root: Path) -> bool:
    rel = path.relative_to(root).as_posix()
    for prefix in (".git/", ".gradle/", "build/", "app/build/", "__pycache__/"):
        if rel.startswith(prefix):
            return True
    return False


def mutation_test(root: Path, cfg: dict, timeout: int, selected: set[str] | None, ignore_minimum: bool = False) -> int:
    command_by_mutation: dict[str, tuple[str, int]] = {}
    results: list[tuple[str, str, str]] = []

    for case in cfg["mutations"]:
        name = case["name"]
        if selected and name not in selected:
            continue
        mutator_name = case["mutator"]
        mutator = MUTATORS.get(mutator_name)
        if mutator is None:
            results.append((name, "ERROR", f"unknown mutator {mutator_name!r}"))
            continue
        gate = (
            cfg["gates"][case["gate"]]
            .replace("{name}", name)
            .replace("{mutator}", mutator_name)
        )
        case_timeout = case.get("timeout", timeout)
        command_by_mutation[name] = (gate, case_timeout)

    if selected and not command_by_mutation:
        print("Mutation test FAILED: --case did not match any configured mutation")
        return 1

    if not command_by_mutation:
        print("Mutation test FAILED: no mutations to run")
        return 1

    for name, (gate, case_timeout) in command_by_mutation.items():
        status = "NOT_RUN"
        detail = ""
        try:
            with detached_worktree(root) as work:
                index = next(i for i, m in enumerate(cfg["mutations"]) if m["name"] == name)
                mutator = MUTATORS[cfg["mutations"][index]["mutator"]]
                mutator(work, cfg)
                result = run(gate, work, case_timeout)
                if result.returncode != 0:
                    status = "KILLED"
                else:
                    status = "SURVIVED"
                detail = result.stdout[-2500:]
        except subprocess.TimeoutExpired as exc:
            status = "TIMEOUT"
            detail = f"timed out after {exc.timeout}s"
        except Exception as exc:
            status = "ERROR"
            detail = repr(exc)
        results.append((name, status, detail))

    failed = False
    for name, status, detail in results:
        print(f"{status:9} {name}")
        if status != "KILLED":
            failed = True
            if detail:
                print("  " + detail.replace("\n", "\n  "))

    killed = sum(1 for _, s, _ in results if s == "KILLED")
    total = len(results)
    print(f"Mutation score: {killed}/{total} killed")
    if total == 0:
        print("Mutation test FAILED: 0 mutations run")
        return 1
    if not ignore_minimum and killed < cfg.get("minimum_mutations", total):
        print(f"Mutation test FAILED: killed count {killed} below minimum {cfg.get('minimum_mutations')}")
        return 1
    if failed:
        print("SURVIVED_MUTATION means the current test suite did not detect an injected defect.")
        return 1
    print("All mutations killed.")
    return 0


def main(argv=None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--config", required=True)
    p.add_argument("--timeout", type=int, default=900)
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("hermeticity")
    sub.add_parser("determinism")
    m = sub.add_parser("mutate")
    m.add_argument("--case", action="append")
    m.add_argument("--ignore-minimum", action="store_true", help="do not enforce minimum_mations when running a subset")
    sub.add_parser("all")
    args = p.parse_args(argv)

    root = repo_root()
    cfg = json.loads((root / args.config).read_text(encoding="utf-8"))

    try:
        validate_config(cfg)
    except ConfigError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        return 1

    if args.command == "hermeticity":
        return hermeticity(root, cfg, args.timeout)
    if args.command == "determinism":
        return determinism(root, cfg, args.timeout)
    if args.command == "mutate":
        selected = set(args.case or [])
        return mutation_test(root, cfg, args.timeout, selected or None, ignore_minimum=args.ignore_minimum)

    code = hermeticity(root, cfg, args.timeout)
    if code:
        return code
    code = determinism(root, cfg, args.timeout)
    if code:
        return code
    return mutation_test(root, cfg, args.timeout, None)


if __name__ == "__main__":
    raise SystemExit(main())
