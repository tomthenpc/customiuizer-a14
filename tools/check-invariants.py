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
import json
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
        "tv/withaibuild/customiuizer/mods/utils/ReceiverRegistry.kt",
    },
    "no-direct-hook-installation": {
        # The wrappers are the only places that may call the underlying Xposed
        # helpers directly. Everyone else must go through ModuleHelper so that
        # HookDiagnostics can record every install attempt.
        "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt",
        "tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt",
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


def check_no_direct_hook_installation(path: Path, text: str) -> list[Finding]:
    """Hook installation must go through ModuleHelper so diagnostics are recorded."""
    if is_allowed("no-direct-hook-installation", path):
        return []
    findings = []
    for match in re.finditer(r"XposedHelpers\.(findAndHookMethod|findAndHookConstructor|hookAllMethods|hookAllConstructors)\s*\(", text):
        findings.append(
            Finding(
                "no-direct-hook-installation",
                path,
                line_of(text, match.start()),
                "hook installation bypasses ModuleHelper; route through ModuleHelper and add allowlist if truly unavoidable",
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


SET_ID_ALLOWED = {
    "tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt",
}

API_VERSION_ALLOWED = {
    "tv/withaibuild/customiuizer/MainModule.java",
    "tv/withaibuild/customiuizer/mods/utils/XposedApiCapabilities.kt",
}


def check_api102_isolation(path: Path, text: str) -> list[Finding]:
    """API 102 hook features are isolated behind a capability gate.

    - setId may only be called from Api102HookBridge.
    - replaceHook is not enabled in production.
    - HotReloadingParam / HotReloadedParam are not used.
    - getApiVersion may only be read from the module entry cold path.
    """
    rel = rel_posix(path)
    findings: list[Finding] = []

    for match in re.finditer(r"\bsetId\s*\(", text):
        if rel not in SET_ID_ALLOWED:
            findings.append(
                Finding(
                    "api102-isolation",
                    path,
                    line_of(text, match.start()),
                    "setId may only be called from Api102HookBridge",
                )
            )

    for match in re.finditer(r"\breplaceHook\s*\(", text):
        findings.append(
            Finding(
                "api102-isolation",
                path,
                line_of(text, match.start()),
                "replaceHook is not enabled",
            )
        )

    for match in re.finditer(r"\bHotReload(?:ing|ed)Param\b", text):
        findings.append(
            Finding(
                "api102-isolation",
                path,
                line_of(text, match.start()),
                "hot reload parameters are not enabled",
            )
        )

    for match in re.finditer(r"\bgetApiVersion\s*\(\s*\)", text):
        if rel not in API_VERSION_ALLOWED:
            findings.append(
                Finding(
                    "api102-isolation",
                    path,
                    line_of(text, match.start()),
                    "getApiVersion may only be read from the module entry cold path",
                )
            )

    return findings


FEATURE_INSTALL_REGISTRY = "tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"
FEATURE_DEFINITION_ROOT = "tv/withaibuild/customiuizer/mods/utils/feature/"
SYSTEM_SERVER_INSTALLER = "tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt"
DEVICE_INFO_MONITOR = "tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt"
HOOKER_CLASS_HELPER = "tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt"
MODULE_HELPER = "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt"
SYSTEM_LOCK_SCREEN_HOOKS = "tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt"


def check_feature_install_oom_cleanup(path: Path, text: str) -> list[Finding]:
    """Feature install OOM must set FAILED_TRANSIENT before rethrowing."""
    if rel_posix(path) != FEATURE_INSTALL_REGISTRY:
        return []
    findings = []
    for match in re.finditer(r"catch\s*\(\s*oom\s*:\s*OutOfMemoryError\s*\)\s*\{", text):
        body, _ = block_at(text, match.start())
        if "FeatureInstallState.set" not in body or "FAILED_TRANSIENT" not in body:
            findings.append(
                Finding(
                    "feature-install-oom-cleanup",
                    path,
                    line_of(text, match.start()),
                    "install OOM catch must set FeatureInstallState to FAILED_TRANSIENT",
                )
            )
        if "throw oom" not in body:
            findings.append(
                Finding(
                    "feature-install-oom-cleanup",
                    path,
                    line_of(text, match.start()),
                    "install OOM catch must rethrow the OutOfMemoryError",
                )
            )
    return findings


def check_feature_install_boundary(path: Path, text: str) -> list[Finding]:
    """Feature definitions must delegate Throwable isolation to FeatureInstallRegistry.

    A local catch(Throwable) hides OutOfMemoryError from the registry's fatal boundary and also
    loses the feature id/name diagnostics recorded by that boundary.
    """
    rel = rel_posix(path)
    if not rel.startswith(FEATURE_DEFINITION_ROOT) and rel != SYSTEM_SERVER_INSTALLER:
        return []

    return [
        Finding(
            "feature-install-boundary",
            path,
            line_of(text, match.start()),
            "feature installer must not catch Throwable; let FeatureInstallRegistry isolate and record it",
        )
        for match in re.finditer(r"catch\s*\([^)]*\bThrowable\b[^)]*\)", text)
    ]


def check_device_info_monitor_hot_path(path: Path, text: str) -> list[Finding]:
    """The two-second device monitor must avoid Formatter churn and preserve OOM propagation."""
    if rel_posix(path) != DEVICE_INFO_MONITOR:
        return []
    findings = []
    for match in re.finditer(r"\bString\.format\s*\(", text):
        findings.append(
            Finding(
                "device-info-monitor-hot-path",
                path,
                line_of(text, match.start()),
                "two-second monitor path must use the cached fixed-decimal formatter",
            )
        )

    for generic in re.finditer(r"catch\s*\(\s*[_A-Za-z]\w*\s*:\s*Throwable\s*\)", text):
        preceding = None
        for oom in re.finditer(
            r"catch\s*\(\s*([A-Za-z]\w*)\s*:\s*OutOfMemoryError\s*\)",
            text[:generic.start()],
        ):
            preceding = oom
        safe = False
        if preceding is not None:
            oom_body, oom_body_start = block_at(text, preceding.start())
            between = text[oom_body_start + len(oom_body):generic.start()]
            safe = not between.strip() and re.search(
                rf"\bthrow\s+{re.escape(preceding.group(1))}\b",
                oom_body,
            ) is not None
        if not safe:
            findings.append(
                Finding(
                    "device-info-monitor-hot-path",
                    path,
                    line_of(text, generic.start()),
                    "device monitor catch(Throwable) must be preceded by an OOM rethrow catch",
                )
            )
    return findings


def check_method_hook_fatal_boundary(path: Path, text: str) -> list[Finding]:
    """The shared before/after adapters must not turn OOM into a logged success."""
    if rel_posix(path) != HOOKER_CLASS_HELPER:
        return []
    findings = []
    for callback_name in ("beforeHook", "afterHook"):
        match = re.search(rf"override\s+fun\s+{callback_name}\s*\(", text)
        if match is None:
            findings.append(
                Finding(
                    "method-hook-fatal-boundary",
                    path,
                    1,
                    f"shared {callback_name} callback is missing",
                )
            )
            continue
        body, _ = block_at(text, match.start())
        oom = re.search(
            r"catch\s*\(\s*([A-Za-z]\w*)\s*:\s*OutOfMemoryError\s*\)\s*\{\s*throw\s+\1\s*\}",
            body,
        )
        generic = re.search(r"catch\s*\(\s*[A-Za-z]\w*\s*:\s*Throwable\s*\)", body)
        if oom is None or generic is None or oom.start() > generic.start():
            findings.append(
                Finding(
                    "method-hook-fatal-boundary",
                    path,
                    line_of(text, match.start()),
                    f"{callback_name} must rethrow OutOfMemoryError before catch(Throwable)",
                )
            )
    return findings


def check_module_helper_fatal_boundaries(path: Path, text: str) -> list[Finding]:
    """Shared runtime helpers may isolate ordinary failures but must propagate OOM."""
    if rel_posix(path) != MODULE_HELPER:
        return []
    findings = []
    for generic in re.finditer(r"catch\s*\(\s*([_A-Za-z]\w*)\s*:\s*Throwable\s*\)", text):
        body, _ = block_at(text, generic.start())
        variable = generic.group(1)
        if variable != "_" and re.search(rf"\bthrow\s+{re.escape(variable)}\b", body):
            continue

        preceding = None
        for oom in re.finditer(
            r"catch\s*\(\s*([A-Za-z]\w*)\s*:\s*OutOfMemoryError\s*\)",
            text[:generic.start()],
        ):
            preceding = oom
        safe = False
        if preceding is not None:
            oom_body, oom_body_start = block_at(text, preceding.start())
            between = text[oom_body_start + len(oom_body):generic.start()]
            safe = not between.strip() and re.search(
                rf"\bthrow\s+{re.escape(preceding.group(1))}\b",
                oom_body,
            ) is not None
        if not safe:
            findings.append(
                Finding(
                    "module-helper-fatal-boundary",
                    path,
                    line_of(text, generic.start()),
                    "ModuleHelper catch(Throwable) must rethrow it or follow an OOM rethrow catch",
                )
            )
    return findings


def check_charging_info_hot_path(path: Path, text: str) -> list[Finding]:
    """Charging hint updates must skip disabled detail I/O and avoid Formatter churn."""
    if rel_posix(path) != SYSTEM_LOCK_SCREEN_HOOKS:
        return []
    method = re.search(r"fun\s+ChargingInfoHook\s*\(", text)
    if method is None:
        return [Finding("charging-info-hot-path", path, 1, "ChargingInfoHook is missing")]
    body, _ = block_at(text, method.start())
    findings = []
    for match in re.finditer(r"\bString\.format\s*\(", body):
        findings.append(
            Finding(
                "charging-info-hot-path",
                path,
                line_of(text, method.start() + match.start()),
                "charging hint hot path must use cached fixed-decimal formatters",
            )
        )
    disabled_return = body.find("!showCurr && !showVolt && !showWatt && !showTemp")
    detail_allocation = body.find("ArrayList<String>")
    sysfs_read = body.find("/sys/class/power_supply/battery/uevent")
    if disabled_return < 0 or any(
        position >= 0 and disabled_return > position
        for position in (detail_allocation, sysfs_read)
    ):
        findings.append(
            Finding(
                "charging-info-hot-path",
                path,
                line_of(text, method.start()),
                "all-disabled charging details must return before collection allocation and sysfs I/O",
            )
        )
    return findings


REFLECTION_CACHE = "tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"


def check_reflection_cache_get_declared_method_oom(path: Path, text: str) -> list[Finding]:
    """ReflectionCache.getDeclaredMethod must catch and rethrow OOM before the generic Throwable handler."""
    if rel_posix(path) != REFLECTION_CACHE:
        return []
    match = re.search(r"\bgetDeclaredMethod\s*\(", text)
    if not match:
        return []
    # Find the nearest preceding try block.
    try_match = None
    for m in re.finditer(r"\btry\s*\{", text[: match.start()]):
        try_match = m
    if try_match is None:
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "getDeclaredMethod is not inside a try block",
            )
        ]
    block, block_start = block_at(text, try_match.start())
    block_end = block_start + len(block)
    if match.start() < try_match.end() or match.end() > block_end:
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "getDeclaredMethod is not inside the nearest try block",
            )
        ]
    after = text[block_end:]
    oom_catch = re.search(r"catch\s*\(\s*oom\s*:\s*OutOfMemoryError\s*\)\s*\{\s*throw\s+oom\s*\}", after)
    t_catch = re.search(r"catch\s*\(\s*t\s*:\s*Throwable\s*\)", after)
    if not oom_catch:
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "getDeclaredMethod try block lacks catch (oom: OutOfMemoryError) { throw oom }",
            )
        ]
    if not t_catch or oom_catch.start() > t_catch.start():
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "catch (oom: OutOfMemoryError) must precede catch (t: Throwable)",
            )
        ]
    return []


def check_docs_zero_object_wording(docs_dir: Path | None = None) -> list[Finding]:
    """Docs must not use the old "disabled feature zero objects" wording."""
    if docs_dir is None:
        docs_dir = REPO_ROOT / "docs"
    if not docs_dir.is_dir():
        return []
    findings = []
    banned = (
        (re.compile(r"关闭功能\s*零运行对象"), "关闭功能零FeatureDefinition；零业务installer对象；零Hook对象；仅保留固定LazyFeatureSpec元数据和轻量lambda"),
        (re.compile(r"disabled\s+feature\s+(?:zero|0)\s+(?:running\s+)?objects?", re.IGNORECASE), "zero FeatureDefinition / zero installer / zero Hook wording"),
    )
    for path in docs_dir.rglob("*.md"):
        file_text = path.read_text(encoding="utf-8")
        for pattern, suggestion in banned:
            for m in pattern.finditer(file_text):
                findings.append(
                    Finding(
                        "docs-zero-object-wording",
                        path,
                        line_of(file_text, m.start()),
                        f"forbidden wording; use '{suggestion}'",
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
    check_no_direct_hook_installation,
    check_no_legacy_xposed,
    check_no_regex_split_on_literal,
    check_api102_isolation,
    check_feature_install_oom_cleanup,
    check_feature_install_boundary,
    check_device_info_monitor_hot_path,
    check_method_hook_fatal_boundary,
    check_module_helper_fatal_boundaries,
    check_charging_info_hot_path,
    check_reflection_cache_get_declared_method_oom,
)


def _git_changed_files(ref: str | None = None) -> list[Path]:
    cmd = ["git", "diff", "--name-only", "--diff-filter=ACMR"]
    if ref:
        cmd.extend([ref])
    result = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True, check=True)
    files = []
    for name in result.stdout.splitlines():
        path = REPO_ROOT / name
        if path.suffix == ".kt" and path.is_file() and SOURCE_ROOT in path.parents:
            files.append(path)
    return files


def staged_kotlin_files() -> list[Path]:
    return _git_changed_files("--cached")


def changed_kotlin_files() -> list[Path]:
    """Files changed relative to HEAD (staged or unstaged)."""
    return _git_changed_files("HEAD")


MANIFEST = REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
MAIN_MODULE = REPO_ROOT / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "MainModule.java"
XPOSED_HELPERS = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "XposedHelpers.java"
JAVA_FATAL_BOUNDARIES = (
    MAIN_MODULE,
    XPOSED_HELPERS,
)


CONTRACTS_DIR = REPO_ROOT / "rom-contracts"
SCHEMA_FILE = CONTRACTS_DIR / "schema.json"

EXPECTED_PROCESS_PACKAGE = {
    "system_server": "android",
    "systemui": "com.android.systemui",
    "launcher": "com.miui.home",
    "securitycenter": "com.miui.securitycenter",
}

FRAMEWORK_TARGETS = {"framework"}


def check_dexkit_close_oom(path: Path | None = None) -> list[Finding]:
    """DexKit bridge cleanup must not swallow OutOfMemoryError."""
    if path is None:
        path = XPOSED_HELPERS
    text = strip_comments(path.read_text(encoding="utf-8"))
    method = text.find("public static void closeBridge()")
    if method < 0:
        return [Finding("dexkit-close-oom", path, 1, "closeBridge method is missing")]

    body, _ = block_at(text, method)
    oom = re.search(r"catch\s*\(\s*OutOfMemoryError\s+oom\s*\)", body)
    if oom is None or "throw oom;" not in body[oom.start():]:
        return [
            Finding(
                "dexkit-close-oom",
                path,
                line_of(text, method),
                "closeBridge must catch and rethrow OutOfMemoryError before generic Throwable",
            )
        ]
    return []


def check_java_fatal_boundaries(paths: tuple[Path, ...] | None = None) -> list[Finding]:
    """Java runtime boundaries may isolate Throwable only after explicitly rethrowing OOM."""
    if paths is None:
        paths = JAVA_FATAL_BOUNDARIES
    findings = []
    for path in paths:
        text = strip_comments(path.read_text(encoding="utf-8"))
        for generic in re.finditer(r"catch\s*\(\s*Throwable\s+(\w+)\s*\)", text):
            body, _ = block_at(text, generic.start())
            variable = generic.group(1)
            if re.search(rf"\bthrow\s+{re.escape(variable)}\s*;", body):
                continue

            preceding = None
            for oom in re.finditer(r"catch\s*\(\s*OutOfMemoryError\s+(\w+)\s*\)", text[:generic.start()]):
                preceding = oom
            if preceding is not None:
                oom_body, oom_body_start = block_at(text, preceding.start())
                between = text[oom_body_start + len(oom_body):generic.start()]
                oom_variable = preceding.group(1)
                if not between.strip() and re.search(
                    rf"\bthrow\s+{re.escape(oom_variable)}\s*;",
                    oom_body,
                ):
                    continue

            findings.append(
                Finding(
                    "java-fatal-boundary",
                    path,
                    line_of(text, generic.start()),
                    "catch(Throwable) must rethrow it or be immediately preceded by an OOM rethrow catch",
                )
            )
    return findings


def check_xposed_throwable_log_oom(path: Path | None = None) -> list[Finding]:
    """Throwable logging is a shared isolation boundary and must rethrow OOM before formatting."""
    if path is None:
        path = XPOSED_HELPERS
    text = strip_comments(path.read_text(encoding="utf-8"))
    methods = list(re.finditer(r"public\s+static\s+void\s+log\s*\([^)]*Throwable\s+(\w+)[^)]*\)", text))
    if len(methods) != 2:
        return [
            Finding(
                "xposed-throwable-log-oom",
                path,
                1,
                f"expected two Throwable log overloads, found {len(methods)}",
            )
        ]
    findings = []
    for method in methods:
        body, _ = block_at(text, method.start())
        variable = method.group(1)
        guard = re.search(
            rf"if\s*\(\s*{re.escape(variable)}\s+instanceof\s+OutOfMemoryError\s*\)\s*"
            rf"throw\s*\(\s*OutOfMemoryError\s*\)\s*{re.escape(variable)}\s*;",
            body,
        )
        formatter = body.find("Log.getStackTraceString")
        if guard is None or formatter < 0 or guard.start() > formatter:
            findings.append(
                Finding(
                    "xposed-throwable-log-oom",
                    path,
                    line_of(text, method.start()),
                    "Throwable log overload must rethrow OOM before formatting the stack trace",
                )
            )
    return findings


def smali_to_fqcn(class_desc: str) -> str:
    return class_desc[1:-1].replace("/", ".")


def check_rom_contracts() -> list[Finding]:
    """Every ROM contract entry must be traceable to a real source file and consistent target."""
    if not CONTRACTS_DIR.is_dir():
        return []

    findings: list[Finding] = []
    for contract_path in CONTRACTS_DIR.glob("*.json"):
        if contract_path.name == "schema.json":
            continue

        try:
            contract = json.loads(contract_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            findings.append(Finding("rom-contracts", contract_path, 0, f"invalid JSON: {e}"))
            continue

        if contract.get("schemaVersion") != 1:
            findings.append(Finding("rom-contracts", contract_path, 0, "unsupported schemaVersion"))

        for target in contract.get("targets", []):
            target_name = target.get("target", "?")
            pkg = target.get("targetProcessPackage", "")

            if target_name not in FRAMEWORK_TARGETS:
                expected = EXPECTED_PROCESS_PACKAGE.get(target_name)
                if expected and pkg != expected:
                    findings.append(
                        Finding(
                            "rom-contracts",
                            contract_path,
                            0,
                            f"target '{target_name}' has targetProcessPackage '{pkg}', expected '{expected}'",
                        )
                    )

            class_desc = target.get("class", "")
            if not class_desc.startswith("L") or not class_desc.endswith(";"):
                findings.append(Finding("rom-contracts", contract_path, 0, f"class '{class_desc}' is not a smali descriptor"))
                continue

            source_file = REPO_ROOT / target.get("sourceFile", "")
            source_hook = target.get("sourceHookFunction", "")
            if not source_file.is_file():
                findings.append(Finding("rom-contracts", contract_path, 0, f"sourceFile {target.get('sourceFile')} does not exist"))
                continue

            source_text = source_file.read_text(encoding="utf-8")
            fqcn = smali_to_fqcn(class_desc)
            if fqcn not in source_text:
                findings.append(Finding("rom-contracts", contract_path, 0, f"class {fqcn} not found in {target.get('sourceFile')}"))

            for method in target.get("methods", []):
                method_name = method.get("name", "")
                if method_name and f'"{method_name}"' not in source_text:
                    findings.append(Finding("rom-contracts", contract_path, 0, f"method '{method_name}' not referenced in {target.get('sourceFile')}"))

                if method.get("required", True) and not method.get("descriptor"):
                    findings.append(Finding("rom-contracts", contract_path, 0, f"required method '{method_name}' is missing descriptor"))

            if source_hook and source_hook not in source_text:
                findings.append(Finding("rom-contracts", contract_path, 0, f"sourceHookFunction '{source_hook}' not found in {target.get('sourceFile')}"))
                continue

            # Derive expected target from the install entry / parameter type.
            if source_hook == "onSystemServerStarting" or source_hook == "onPackageReady":
                method_match = re.search(
                    rf"(?:public\s+void|fun)\s+{re.escape(source_hook)}\s*\(([^)]*)\)",
                    MAIN_MODULE.read_text(encoding="utf-8") if source_file == MAIN_MODULE else source_text,
                )
                if method_match and "SystemServerStartingParam" in method_match.group(1):
                    if target_name != "system_server" or pkg != "android":
                        findings.append(
                            Finding(
                                "rom-contracts",
                                contract_path,
                                0,
                                f"{source_hook} uses SystemServerStartingParam; process target must be 'system_server' (LSPosed scope 'system'), got '{target_name}' / '{pkg}'",
                            )
                        )
            else:
                signature_match = re.search(
                    rf"fun\s+{re.escape(source_hook)}\s*\(([^)]*)\)",
                    source_text,
                )
                if signature_match:
                    params = signature_match.group(1)
                    if "SystemServerStartingParam" in params:
                        if target_name != "system_server" or pkg != "android":
                            findings.append(
                                Finding(
                                    "rom-contracts",
                                    contract_path,
                                    0,
                                    f"{source_hook} uses SystemServerStartingParam; process target must be 'system_server' (LSPosed scope 'system'), got '{target_name}' / '{pkg}'",
                                )
                            )
                    elif "PackageReadyParam" in params:
                        if target_name == "system_server":
                            findings.append(
                                Finding(
                                    "rom-contracts",
                                    contract_path,
                                    0,
                                    f"{source_hook} uses PackageReadyParam but is declared as '{target_name}'",
                                )
                            )

    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="check only files staged in git")
    parser.add_argument("--changed", action="store_true", help="check files changed relative to HEAD (staged or unstaged)")
    args = parser.parse_args()

    if args.staged and args.changed:
        parser.error("--staged and --changed are mutually exclusive")

    if args.staged:
        files = staged_kotlin_files()
    elif args.changed:
        files = changed_kotlin_files()
    else:
        files = sorted(SOURCE_ROOT.rglob("*.kt"))
    findings: list[Finding] = []
    for path in files:
        text = strip_comments(path.read_text(encoding="utf-8"))
        for rule in RULES:
            findings.extend(rule(path, text))

    findings.extend(check_rom_contracts())
    findings.extend(check_docs_zero_object_wording())
    findings.extend(check_dexkit_close_oom())
    findings.extend(check_java_fatal_boundaries())
    findings.extend(check_xposed_throwable_log_oom())

    if not findings:
        print(f"check-invariants: {len(files)} files, no violations")
        return 0

    by_rule: dict[str, list[Finding]] = {}
    for finding in findings:
        by_rule.setdefault(finding.rule, []).append(finding)

    for rule, items in sorted(by_rule.items()):
        try:
            doc = next(r for r in RULES if r.__name__.replace("check_", "").replace("_", "-") == rule).__doc__
        except StopIteration:
            doc = None
        print(f"\n=== {rule} ({len(items)}) ===")
        if doc:
            print((doc or "").strip())
            print()
        for finding in items:
            print(f"  {finding}")

    print(f"\ncheck-invariants: {len(findings)} violation(s) across {len(files)} files")
    return 1


if __name__ == "__main__":
    sys.exit(main())
