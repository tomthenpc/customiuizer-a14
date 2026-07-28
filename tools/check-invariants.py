#!/usr/bin/env python3
"""Static gate for the invariants that keep this module from bricking a device.

Usage:
    python tools/check-invariants.py            # check the whole module source
    python tools/check-invariants.py --staged   # check only files staged in git

Exit code 0 means every invariant holds. Any other exit code means at least one
rule in AGENTS.md was violated and the change must not be committed.

Each rule below exists because the exact defect it detects was found in this
repository, in code that compiled, passed lint and passed the unit tests. The
build cannot catch them: they are runtime contracts with the Android framework
and with libxposed, not type errors.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"

# Files that are allowed to break a rule, with the reason. Keep this list short;
# every entry is a place where the invariant is enforced rather than consumed.
ALLOWED = {
    "no-raw-register-receiver": {
        "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt",
    },
    "guard-framework-callbacks": {
        # The settings app is the module's own process. A throw there shows a
        # normal app crash dialog; it cannot take a system process down.
        "tv/withaibuild/customiuizer/MainApplication.kt",
        "tv/withaibuild/customiuizer/tasker/UnlockReceiver.kt",
    },
}

REFLECTION = re.compile(r"XposedHelpers\.|\bcallMethod\(|\bgetObjectField\(|\bsetObjectField\(")

LINE_COMMENT = re.compile(r"//[^\n]*")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)


def strip_comments(text: str) -> str:
    """Blanks out comments, preserving every newline so line numbers stay correct."""

    def blank(match: re.Match[str]) -> str:
        return re.sub(r"[^\n]", " ", match.group(0))

    return LINE_COMMENT.sub(blank, BLOCK_COMMENT.sub(blank, text))


class Finding:
    def __init__(self, rule: str, path: Path, line: int, detail: str) -> None:
        self.rule = rule
        self.path = path
        self.line = line
        self.detail = detail

    def __str__(self) -> str:
        rel = self.path.relative_to(REPO_ROOT).as_posix()
        return f"{rel}:{self.line}: [{self.rule}] {self.detail}"


def rel_posix(path: Path) -> str:
    return path.relative_to(SOURCE_ROOT).as_posix()


def is_allowed(rule: str, path: Path) -> bool:
    return rel_posix(path) in ALLOWED.get(rule, set())


def block_at(text: str, search_from: int) -> tuple[str, int]:
    """Returns the brace-balanced block starting at the first '{' at or after search_from."""
    start = text.index("{", search_from)
    depth = 0
    index = start
    while index < len(text):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                break
        index += 1
    return text[start : index + 1], start


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


# --- rules -----------------------------------------------------------------

CALLBACK_SIGNATURES = (
    r"override fun handleMessage\(",
    r"override fun onReceive\(",
    r"override fun onChange\(",
    r"override fun run\(\)",
)


def check_guard_framework_callbacks(path: Path, text: str) -> list[Finding]:
    """Framework-invoked callbacks run outside the MethodHook try/catch.

    A reflective miss on a ROM that renamed a field then propagates out of the
    module and kills system_server, SystemUI or Launcher. Wrap the body in
    ModuleHelper.guarded, or catch inside it.

    PreferenceObserver.onChange is exempt: ModuleHelper.handlePreferenceChanged
    already isolates every observer it dispatches to.
    """
    if is_allowed("guard-framework-callbacks", path):
        return []
    findings = []
    for signature in CALLBACK_SIGNATURES:
        for match in re.finditer(signature, text):
            body, start = block_at(text, match.end() - 1)
            header = text[match.start() : start]
            if "guarded" in header or "guarded" in body or "try" in body:
                continue
            if not REFLECTION.search(body):
                continue
            if ": ModuleHelper.PreferenceObserver" in text[max(0, match.start() - 400) : match.start()]:
                continue
            findings.append(
                Finding(
                    "guard-framework-callbacks",
                    path,
                    line_of(text, match.start()),
                    "callback performs reflection but is not wrapped in ModuleHelper.guarded",
                )
            )
    return findings


DEFERRED_CALLBACKS = (
    r"\bRunnable\s*\(?\s*\{",
    r"\b(?:post|postDelayed|postAtTime|postOnAnimation|runOnUiThread)\s*\(\s*\{",
    r"\bThread\s*\(\s*\{",
    r"\bset(?:On\w+Listener)\s*\{",
    r"\b(?:withEndAction|doOnLayout|addUpdateListener|postFrameCallback)\s*\(?\s*\{",
)


def check_guard_deferred_callbacks(path: Path, text: str) -> list[Finding]:
    """Lambdas that run later are outside the hook's try/catch, exactly like named callbacks.

    The round-one rule only matched `override fun run()` and friends, so
    `postDelayed(Runnable { ... })` slipped through — including two bodies posted
    to the PhoneWindowManager handler inside system_server, where an uncaught
    throw reboots the device rather than restarting an app.

    Anything deferred from `mods/` must be wrapped in ModuleHelper.guarded.
    """
    if "customiuizer/mods/" not in path.as_posix():
        return []
    findings = []
    for pattern in DEFERRED_CALLBACKS:
        for match in re.finditer(pattern, text):
            body, start = block_at(text, match.end() - 1)
            if "guarded" in body or "try" in body or "runCatching" in body:
                continue
            # An empty lambda cannot throw; it is a deliberate no-op replacement.
            if not body.strip("{} \n\t"):
                continue
            findings.append(
                Finding(
                    "guard-deferred-callbacks",
                    path,
                    line_of(text, match.start()),
                    "deferred body runs outside the hook try/catch; wrap it in ModuleHelper.guarded",
                )
            )
    return findings


def check_coroutine_scopes_handle_failure(path: Path, text: str) -> list[Finding]:
    """A SupervisorJob does not swallow failures, it only stops them cascading.

    An uncaught exception in `launch` still reaches the thread's default handler,
    which inside SystemUI or Launcher kills the process. Every scope the module
    runs in a host process must carry ModuleHelper.coroutineFailureHandler, so a
    coroutine added later cannot forget it.
    """
    if "customiuizer/mods/" not in path.as_posix():
        return []
    findings = []
    for match in re.finditer(r"CoroutineScope\(", text):
        end = text.find("\n", match.start())
        statement = text[match.start() : end if end != -1 else len(text)]
        if "coroutineFailureHandler" in statement:
            continue
        findings.append(
            Finding(
                "coroutine-scopes-handle-failure",
                path,
                line_of(text, match.start()),
                "add + ModuleHelper.coroutineFailureHandler to this scope",
            )
        )
    return findings


def check_no_raw_register_receiver(path: Path, text: str) -> list[Finding]:
    """Receivers registered straight on a Context outlive their hook target.

    Cleanup keyed on the hooked instance cannot see the registration a previous
    instance made, so every recreation of the target leaves another live
    receiver behind. Use ModuleHelper.registerModuleReceiver (one per key) or
    registerOwnedReceiver (one per live owner).

    A raw registration is accepted only when the same file unregisters that
    exact receiver, which is how the screen-state, weather and step-counter
    controllers manage their own paired lifetime.
    """
    if is_allowed("no-raw-register-receiver", path):
        return []
    if "customiuizer/mods/" not in path.as_posix():
        return []
    findings = []
    for match in re.finditer(r"\.registerReceiver\(\s*([^,\n]*)", text):
        receiver = match.group(1).strip()
        # A null receiver is a synchronous sticky-broadcast read, not a registration.
        if receiver.startswith("null"):
            continue
        # Anonymous receivers can never be unregistered; they always need the registry.
        if not re.fullmatch(r"[\w.]+", receiver):
            findings.append(
                Finding(
                    "no-raw-register-receiver",
                    path,
                    line_of(text, match.start()),
                    "anonymous receiver cannot be unregistered; "
                    "use ModuleHelper.registerModuleReceiver / registerOwnedReceiver",
                )
            )
            continue
        if f"unregisterReceiver({receiver}" in text:
            continue
        # A declared field plus an unregister path in the same file is a managed
        # lifetime, even when the unregister call goes through a local alias.
        declared_field = re.search(rf"^\s*(private )?(var|val) {re.escape(receiver)}\b", text, re.MULTILINE)
        if declared_field and "unregisterReceiver(" in text:
            continue
        findings.append(
            Finding(
                "no-raw-register-receiver",
                path,
                line_of(text, match.start()),
                "use ModuleHelper.registerModuleReceiver / registerOwnedReceiver, "
                "or unregister this exact receiver in the same file",
            )
        )
    return findings


def check_no_looperless_handler(path: Path, text: str) -> list[Finding]:
    """Handler() with no Looper binds to whichever thread ran the hook.

    In a hook that is not guaranteed to run on a Looper thread it throws
    outright. Always pass an explicit Looper.
    """
    findings = []
    for match in re.finditer(r"\bHandler\(\s*\)", text):
        findings.append(
            Finding(
                "no-looperless-handler",
                path,
                line_of(text, match.start()),
                "pass an explicit Looper, e.g. Handler(context.mainLooper)",
            )
        )
    return findings


def check_no_redundant_arg_marshalling(path: Path, text: str) -> list[Finding]:
    """getArgsArray + proceed(args) is only for hooks that rewrite arguments.

    It allocates the argument list and a copy of it on every invocation, and
    makes the framework re-marshal every argument on proceed. Hooks that only
    read arguments must use Chain.getArg(i) / Chain.getArgs() and Chain.proceed().
    """
    findings = []
    for match in re.finditer(r"override fun intercept\(", text):
        body, start = block_at(text, match.end() - 1)
        if "getArgsArray" not in body:
            continue
        if re.search(r"\bargs\w*\[\s*[^\]]+\]\s*=[^=]", body):
            continue
        findings.append(
            Finding(
                "no-redundant-arg-marshalling",
                path,
                line_of(text, match.start()),
                "hook does not rewrite arguments; use Chain.getArg(i) and Chain.proceed()",
            )
        )
    return findings


def check_no_legacy_xposed(path: Path, text: str) -> list[Finding]:
    """The module runs on libxposed API 101/102 only."""
    findings = []
    for match in re.finditer(r"de\.robv\.android\.xposed", text):
        findings.append(
            Finding(
                "no-legacy-xposed",
                path,
                line_of(text, match.start()),
                "legacy Xposed API is not available at runtime",
            )
        )
    return findings


def check_no_regex_split_on_literal(path: Path, text: str) -> list[Finding]:
    """split("x".toRegex()) compiles a Pattern on every call.

    Java's String.split takes a single-character fast path that does not touch
    the regex engine; the mechanical Kotlin translation loses it.

    Only single-character delimiters are flagged; a genuine pattern such as
    "\\s+" has to stay a Regex.
    """
    findings = []
    for match in re.finditer(r'split\(\s*"(?:\\\\)?[^"\\+*?\[\]{}()^$]"\.toRegex\(\)', text):
        findings.append(
            Finding(
                "no-regex-split-on-literal",
                path,
                line_of(text, match.start()),
                "split on a literal delimiter, not a compiled Regex",
            )
        )
    return findings


RULES = (
    check_guard_framework_callbacks,
    check_guard_deferred_callbacks,
    check_coroutine_scopes_handle_failure,
    check_no_raw_register_receiver,
    check_no_looperless_handler,
    check_no_redundant_arg_marshalling,
    check_no_legacy_xposed,
    check_no_regex_split_on_literal,
)


def staged_kotlin_files() -> list[Path]:
    result = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    files = []
    for name in result.stdout.splitlines():
        path = REPO_ROOT / name
        if path.suffix == ".kt" and path.is_file() and SOURCE_ROOT in path.parents:
            files.append(path)
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="check only files staged in git")
    args = parser.parse_args()

    files = staged_kotlin_files() if args.staged else sorted(SOURCE_ROOT.rglob("*.kt"))
    findings: list[Finding] = []
    for path in files:
        text = strip_comments(path.read_text(encoding="utf-8"))
        for rule in RULES:
            findings.extend(rule(path, text))

    if not findings:
        print(f"check-invariants: {len(files)} files, no violations")
        return 0

    by_rule: dict[str, list[Finding]] = {}
    for finding in findings:
        by_rule.setdefault(finding.rule, []).append(finding)

    for rule, items in sorted(by_rule.items()):
        doc = next(r for r in RULES if r.__name__.replace("check_", "").replace("_", "-") == rule).__doc__
        print(f"\n=== {rule} ({len(items)}) ===")
        print((doc or "").strip())
        print()
        for finding in items:
            print(f"  {finding}")

    print(f"\ncheck-invariants: {len(findings)} violation(s) across {len(files)} files")
    return 1


if __name__ == "__main__":
    sys.exit(main())
