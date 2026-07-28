#!/usr/bin/env python3
r"""Stream LSPosed full.log analyzer for A14/A13.

Usage:
    python tools/analyze_lsposed_log.py "C:\path\full.log" --profile a14 --repo-root "." --output "build\log-analysis\r14-test"
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter, deque
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Set, Tuple


# ---------------------------------------------------------------------------
# Profiles and rules
# ---------------------------------------------------------------------------

PROFILES = {
    "a14": {
        "application_id": "tv.withaibuild.customiuizer.r14",
        "source_package": "tv.withaibuild.customiuizer",
        "module_markers": [
            "tv.withaibuild.customiuizer",
            "CustoMIUIzer",
            "MainModule",
            "ModuleHelper",
            "XposedHelpers",
            "HookerClassHelper",
            "ResourceHooks",
            "PrefMap",
        ],
    },
    "a13": {
        "application_id": "tv.withaibuild.customiuizer.r13",
        "source_package": "name.monwf.customiuizer",
        "module_markers": [
            "tv.withaibuild.customiuizer.r13",
            "name.monwf.customiuizer",
            "CustoMIUIzer",
            "MainModule",
            "ModuleHelper",
            "XposedHelpers",
            "HookerClassHelper",
            "ResourceHooks",
            "PrefMap",
        ],
    },
}

COMMON_TARGETS = {
    "android", "system_server", "com.android.systemui", "com.miui.home",
    "com.mi.android.globallauncher", "com.miui.securitycenter", "com.miui.powerkeeper",
    "com.miui.packageinstaller", "com.miui.screenshot", "com.android.settings",
    "com.android.incallui",
}

SEVERE_ANCHORS = (
    "FATAL EXCEPTION", "AndroidRuntime", "ANR in", "WATCHDOG", "Watchdog",
    "system_server crash", "Process has died", "ProcessRecord died", "native crash",
    "Fatal signal", "SIGSEGV", "SIGABRT", "DeadSystemException",
    "TransactionTooLargeException", "VerifyError", "LinkageError",
    "NoClassDefFoundError", "ExceptionInInitializerError",
)

HOOK_ANCHORS = (
    "Failed to hook", "Hook failed", "Failed hook", "Failed to load module",
    "Cannot load module", "XposedModule", "XposedModuleInterface", "LSPosed",
    "libxposed", "java_init.list", "module.prop", "RemotePreferences",
    "getRemotePreferences", "Chain.proceed", "intercept", "hook registration",
)

EXCEPTION_CLASSES = (
    "ClassNotFoundException", "NoSuchMethodException", "NoSuchFieldException",
    "IllegalAccessException", "InvocationTargetException", "ClassCastException",
    "NullPointerException", "IllegalArgumentException", "IllegalStateException",
    "AbstractMethodError", "IncompatibleClassChangeError", "UnsatisfiedLinkError",
    "SecurityException", "DeadObjectException", "RuntimeException",
)

NOISE_TAGS = (
    "ColorManager", "BlurController", "MisoundAsc", "FlagUtils", "SDM", "SRE",
    "MI-SF", "RefreshRateSelector", "sensors-hal", "RegisteredAidCache",
    "SmartPower.DisplayPolicy",
)

NOISE_MESSAGES = (
    "histogram value", "avc: denied", "Display index not found",
    "No subscribers registered", "SDK version is too low", " Enter", " Exit",
    "sensorCallback", "setSREStrength", "refresh rate", "brightness",
)

LOG_RE = re.compile(
    r"^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3,6})\s+"
    r"(\d+)\s+(\d+)\s+([VDIWEAF])\s+(.*?):\s(.*)$"
)

HEX_RE = re.compile(r"0x[0-9a-fA-F]+|\b[0-9a-fA-F]{7,}\b")
DEC_RE = re.compile(r"\b\d{4,}\b")
UUID_RE = re.compile(r"[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
OBJHASH_RE = re.compile(r"@[0-9a-fA-F]{5,}")


def file_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest().upper()


def parse_line(raw: str, line_no: int) -> Optional[dict]:
    m = LOG_RE.match(raw.rstrip("\n\r"))
    if not m:
        return None
    date, time, pid, tid, level, tag, msg = m.groups()
    return {
        "line_no": line_no,
        "date": date,
        "time": time,
        "pid": int(pid),
        "tid": int(tid),
        "level": level,
        "tag": tag.strip(),
        "message": msg,
        "raw": raw.rstrip("\n\r"),
        "process": None,
    }


def discover_process(rec: dict, pid_map: Dict[int, str]) -> Optional[str]:
    pid = rec["pid"]
    msg = rec["message"]
    tag = rec["tag"]

    # Update pid map from AM/process lines
    if tag in ("ActivityManager", "ActivityManagerShell", "am_proc_start", "am_proc_bound"):
        for pat in (
            r"Start proc\s+([\w\.]+)",
            r"am_proc_start.*?\s+([\w\.]+)",
            r"am_proc_bound.*?\s+([\w\.]+)",
            r"Process\s+([\w\.]+)\s+has died",
            r"Killing\s+([\w\.]+):",
            r"Killing\s+\d+:\s+([\w\.]+)/",
        ):
            m = re.search(pat, msg, re.IGNORECASE)
            if m:
                pid_map[pid] = m.group(1)
                return m.group(1)

    if pid in pid_map:
        return pid_map[pid]

    # Package names in message for common targets
    m = re.search(r"([a-zA-Z][\w]*\.)+[a-zA-Z][\w]*", msg)
    if m and "http" not in m.group(0).lower():
        proc = m.group(0)
        if proc in COMMON_TARGETS:
            return proc
    return None


# ---------------------------------------------------------------------------
# Anchors and context extraction
# ---------------------------------------------------------------------------

MODULE_LOG_TAGS = ("LSPosed-Bridge", "LSPosed", "CustoMIUIzer")


def module_evidence(text: str, tag: str, source_package: str, application_id: str) -> str:
    """How strongly a piece of text implicates the module's own code.

    The distinction that matters: the ROM writes our applicationId into its own logs
    constantly — activity starts, SmartPower, the package manager, recents. Treating
    that as evidence buries the real findings under hundreds of routine lines, which
    is what made triage slow. Our *code* being named is evidence; our *package* being
    mentioned is only context.

    Returns one of "code", "log", "mention", "none", strongest first.
    """
    if re.search(r"\bat\s+" + re.escape(source_package) + r"[\w.$]*", text):
        return "code"
    if re.search(r"\b" + re.escape(source_package) + r"\.[A-Za-z]\w*\.[A-Za-z]\w*\s*\(", text):
        return "code"
    if any(t in tag for t in MODULE_LOG_TAGS) and (source_package in text or "CustoMIUIzer" in text):
        return "log"
    if source_package in text or application_id in text:
        return "mention"
    return "none"


def is_anchor(rec: dict, module_markers: List[str]) -> Tuple[bool, bool]:
    msg = rec["message"]
    tag = rec["tag"]
    module_hit = any(m in msg or m in tag for m in module_markers)
    severe_hit = any(a in msg or a in tag for a in SEVERE_ANCHORS)
    hook_hit = any(a in msg or a in tag for a in HOOK_ANCHORS)
    exc_hit = any(a in msg for a in EXCEPTION_CLASSES)
    return module_hit or severe_hit or hook_hit or exc_hit, module_hit


def extract_exception_type(lines: List[str]) -> str:
    for line in lines:
        # Android logcat exception lines often contain: java.lang.FooException: msg
        m = re.search(r"([\w\.]+(?:Exception|Error|Death|Crash))", line)
        if m:
            return m.group(1).split(".")[-1]
    return ""


def extract_module_stack(lines: List[str], module_pkg: str) -> str:
    for line in lines:
        if f"at {module_pkg}" in line:
            # keep method signature only
            m = re.search(r"at\s+([\w\.]+)\.(\w+)\s*\(", line)
            if m:
                return f"{m.group(1)}.{m.group(2)}"
            m = re.search(r"at\s+([\w\.]+)", line)
            if m:
                return m.group(1)
    return ""


def extract_first_stack(lines: List[str]) -> str:
    for line in lines:
        m = re.search(r"at\s+([\w\.]+)\.(\w+)\s*\(", line)
        if m:
            return f"{m.group(1)}.{m.group(2)}"
    return ""


def normalize_message(text: str) -> str:
    text = UUID_RE.sub("UUID", text)
    text = HEX_RE.sub("HEX", text)
    text = OBJHASH_RE.sub("@HEX", text)
    text = DEC_RE.sub("NUM", text)
    text = re.sub(r"\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+", "DATETIME", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:160]


def fingerprint_from_context(ctx: List[str], rec: dict, module_pkg: str) -> str:
    exc = extract_exception_type(ctx)
    mod_stack = extract_module_stack(ctx, module_pkg)
    first_stack = extract_first_stack(ctx) if not mod_stack else ""
    tag = rec["tag"]
    proc = rec.get("process") or "unknown"
    if mod_stack:
        return f"{proc}|{exc or 'module'}|{mod_stack}"
    if exc:
        stack = first_stack or normalize_message(ctx[0] if ctx else rec["message"])
        return f"{proc}|{exc}|{stack}"
    return f"{proc}|{tag}|{normalize_message(rec['message'])}"


# ---------------------------------------------------------------------------
# Scoring and candidate
# ---------------------------------------------------------------------------

@dataclass
class Candidate:
    fingerprint: str
    priority: str
    score: int
    count: int = 0
    first_time: str = ""
    last_time: str = ""
    process: Set[str] = field(default_factory=set)
    pids: Set[int] = field(default_factory=set)
    tag: str = ""
    exception: str = ""
    module_related: bool = False
    module_stack: str = ""
    sample_offsets: List[int] = field(default_factory=list)
    sample_messages: List[str] = field(default_factory=list)
    classification: str = "待分类"

    def to_tsv(self) -> str:
        return (
            f"{self.priority}\t{self.score}\t{self.count}\t{self.first_time}\t{self.last_time}\t"
            f"{','.join(sorted(self.process))}\t{','.join(str(p) for p in sorted(self.pids))}\t"
            f"{self.tag}\t{self.exception}\t{self.fingerprint}\t{self.module_related}\t"
            f"{','.join(str(o) for o in self.sample_offsets)}\t{self.classification}"
        )


def score_context(ctx: List[str], rec: dict, module_markers: List[str], profile: dict) -> int:
    score = 0
    msg = rec["message"]
    tag = rec["tag"]
    proc = rec.get("process") or "unknown"
    source_package = profile["source_package"]
    application_id = profile["application_id"]

    severe = any(a in msg or a in tag for a in SEVERE_ANCHORS)
    hook = any(a in msg or a in tag for a in HOOK_ANCHORS)
    exc = any(a in msg for a in EXCEPTION_CLASSES)

    # Strongest evidence wins, taken over the whole context so a stack trace a few
    # lines below the anchor still counts.
    evidence = module_evidence(msg, tag, source_package, application_id)
    if evidence != "code":
        for line in ctx:
            if module_evidence(line, tag, source_package, application_id) == "code":
                evidence = "code"
                break

    if evidence == "code":
        score += 140
    elif evidence == "log":
        score += 80
    elif evidence == "mention":
        # Our applicationId appearing in someone else's message is context, not a
        # finding. It must not on its own reach any actionable priority.
        score += 10

    if severe:
        score += 90
    if hook:
        score += 25
    if exc:
        score += 40

    if proc in COMMON_TARGETS:
        if exc:
            score += 20
        if proc in ("system_server", "com.android.systemui", "com.miui.home"):
            score += 40

    # suppress plain E/W without exception/severity/module
    if rec["level"] in ("E", "W") and not (evidence in ("code", "log") or severe or exc or hook):
        score -= 60
    if tag in NOISE_TAGS:
        score -= 50
    if any(ns in msg for ns in NOISE_MESSAGES):
        score -= 30

    return max(score, 0)


def priority_from_score(score: int) -> str:
    if score >= 150:
        return "P0"
    if score >= 110:
        return "P1"
    if score >= 70:
        return "P2"
    if score >= 30:
        return "P3"
    return "P4"


# ---------------------------------------------------------------------------
# Main streaming analyzer
# ---------------------------------------------------------------------------

def analyze(log_path: str, profile: dict, args: argparse.Namespace) -> Tuple[Dict[str, Candidate], List[str], dict, List[dict]]:
    marker_set = set(profile["module_markers"])
    pid_map: Dict[int, str] = {}
    stats = {
        "sha256": file_sha256(log_path),
        "size": os.path.getsize(log_path),
        "total_lines": 0,
        "parsed_lines": 0,
        "first_time": None,
        "last_time": None,
        "module_loads": [],
    }

    window: deque = deque(maxlen=args.context_before + 1)
    after_buffer: List[Tuple[dict, int]] = []
    pending_anchor: Optional[Tuple[dict, int, int]] = None
    pending_ctx: List[str] = []

    candidates: Dict[str, Candidate] = {}
    contexts: List[Tuple[str, List[str]]] = []

    with open(log_path, "r", encoding="utf-8", errors="replace") as f:
        for line_no, raw in enumerate(f, start=1):
            stats["total_lines"] += 1
            if not raw.strip():
                continue
            rec = parse_line(raw, line_no)
            if not rec:
                continue
            stats["parsed_lines"] += 1
            if stats["first_time"] is None:
                stats["first_time"] = rec["time"]
            stats["last_time"] = rec["time"]

            rec["process"] = discover_process(rec, pid_map)

            # module load tracking
            if ("Loading module" in rec["message"] or "Loaded module" in rec["message"]) and any(m in rec["message"] for m in marker_set):
                stats["module_loads"].append({
                    "time": rec["time"], "pid": rec["pid"], "process": rec.get("process"), "message": rec["message"],
                })

            # anchor detection with context window
            is_anch, module_hit = is_anchor(rec, profile["module_markers"])

            if pending_anchor is not None:
                # continue collecting context after anchor
                pending_ctx.append(rec["raw"])
                after_buffer.append((rec, line_no))
                # stop collecting if we have enough lines and next line is a new anchor or log record break
                anchor_rec, anchor_off, collected = pending_anchor
                if len(pending_ctx) - len(window) >= args.context_after:
                    # finalize
                    _finalize_candidate(
                        anchor_rec, anchor_off, pending_ctx, window, candidates, profile["source_package"], stats, contexts, profile
                    )
                    pending_anchor = None
                    pending_ctx = []
                    after_buffer = []
                elif is_anch and not rec["raw"].startswith(" ") and rec["level"] in "EW":
                    # new anchor, finalize previous
                    _finalize_candidate(
                        anchor_rec, anchor_off, pending_ctx, window, candidates, profile["source_package"], stats, contexts, profile
                    )
                    pending_anchor = None
                    pending_ctx = []
                    after_buffer = []

            if is_anch:
                if pending_anchor is None:
                    pending_anchor = (rec, line_no, 0)
                    # build context: previous window lines + anchor line
                    pending_ctx = [r["raw"] for r in window] + [rec["raw"]]
                    # reset after buffer
                    after_buffer = []

            window.append(rec)

    # finalize any pending anchor at EOF
    if pending_anchor is not None:
        anchor_rec, anchor_off, _ = pending_anchor
        _finalize_candidate(
            anchor_rec, anchor_off, pending_ctx, window, candidates, profile["source_package"], stats, contexts, profile
        )

    # Priority is severity, not frequency. A repeated benign line is still benign and
    # a crash that happened once is still a crash; `count` carries the frequency.
    # Repetition only breaks ties between findings of the same severity.
    for c in candidates.values():
        c.score = max(0, c.score)
        c.priority = priority_from_score(c.score)

    return candidates, contexts, stats, stats["module_loads"]


def _finalize_candidate(
    rec: dict, offset: int, ctx: List[str], window: deque,
    candidates: Dict[str, Candidate], module_pkg: str, stats: dict,
    contexts: List[Tuple[str, List[str]]],
    profile: dict
) -> None:
    fp = fingerprint_from_context(ctx, rec, module_pkg)
    if fp not in candidates:
        candidates[fp] = Candidate(
            fingerprint=fp,
            priority="P0",
            score=0,
            tag=rec["tag"],
            exception=extract_exception_type(ctx),
            module_related=module_pkg in "\n".join(ctx),
            module_stack=extract_module_stack(ctx, module_pkg),
        )
    c = candidates[fp]
    c.count += 1
    # Worst single observation, not the sum over occurrences. Summing let a benign
    # line that repeats 200 times outrank a crash that happened once; how often a
    # finding occurred is reported in `count`, it is not evidence of severity.
    c.score = max(c.score, score_context(ctx, rec, profile["module_markers"], profile))
    if not c.first_time:
        c.first_time = rec["time"]
    c.last_time = rec["time"]
    if rec.get("process"):
        c.process.add(rec["process"])
    c.pids.add(rec["pid"])
    if len(c.sample_offsets) < 3:
        c.sample_offsets.append(offset)
        c.sample_messages.append(rec["raw"])
        # Keep the raw context; format it at write time. The priority is not known
        # until every occurrence of this fingerprint has been scored.
        contexts.append((fp, list(ctx)))


def _format_context(fp: str, c: Candidate, ctx: List[str]) -> str:
    header = (
        f"\n--- context [{c.priority}] score={c.score} count={c.count} ---\n"
        f"process={','.join(c.process)} pids={','.join(str(p) for p in sorted(c.pids))}\n"
        f"fingerprint: {fp}\n"
    )
    return header + "\n".join(ctx)


# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------

def write_outputs(
    output_dir: str, log_path: str, profile_name: str,
    candidates: Dict[str, Candidate], contexts: List[Tuple[str, List[str]]], stats: dict, module_loads: List[dict]
) -> None:
    os.makedirs(output_dir, exist_ok=True)

    loaded = any("Loaded module" in m["message"] for m in module_loads)

    p_counts = Counter(c.priority for c in candidates.values())

    # crash flags require P0 + specific evidence
    p0 = [c for c in candidates.values() if c.priority == "P0"]
    system_server_crash = any(
        c.priority == "P0" and "system_server" in c.process and
        (c.exception in ("FATAL EXCEPTION", "AndroidRuntime") or "crash" in c.fingerprint.lower() or "died" in c.fingerprint.lower())
        for c in candidates.values()
    )
    systemui_crash = any(
        c.priority == "P0" and "com.android.systemui" in c.process and
        (c.exception in ("FATAL EXCEPTION", "AndroidRuntime") or "crash" in c.fingerprint.lower())
        for c in candidates.values()
    )
    launcher_crash = any(
        c.priority == "P0" and (c.process & {"com.miui.home", "com.mi.android.globallauncher"}) and
        (c.exception in ("FATAL EXCEPTION", "AndroidRuntime") or "crash" in c.fingerprint.lower())
        for c in candidates.values()
    )
    hook_failed = any("Failed to hook" in c.fingerprint or "Hook failed" in c.fingerprint for c in candidates.values())
    rp_issue = any("RemotePreferences" in c.fingerprint for c in candidates.values())

    module_top = "无"
    module_cands = [c for c in candidates.values() if c.module_related]
    if module_cands:
        top = max(module_cands, key=lambda c: c.count)
        module_top = f"{top.fingerprint[:120]} (count={top.count})"

    if not loaded and not p0 and not any(c.priority == "P1" for c in candidates.values()):
        conclusion = "未检测到模块加载及高优先级问题，需要确认测试时是否已启用模块并重启目标进程。"
    elif not p0 and not any(c.priority == "P1" for c in candidates.values()):
        conclusion = "模块加载正常，未发现 P0/P1 级别异常。"
    else:
        conclusion = f"发现 {len(p0)} 个 P0、{sum(1 for c in candidates.values() if c.priority == 'P1')} 个 P1 候选，需要人工归因。"

    summary = f"""# LSPosed 日志分析摘要

- **日志文件**: `{log_path}`
- **SHA-256**: `{stats['sha256']}`
- **文件大小**: {stats['size']} bytes
- **总行数**: {stats['total_lines']}
- **可解析行数**: {stats['parsed_lines']}
- **不可解析行数**: {stats['total_lines'] - stats['parsed_lines']}
- **时间范围**: {stats['first_time']} - {stats['last_time']}
- **Profile**: {profile_name}
- **模块加载**: {'成功' if loaded else '未检测到'} ({len(module_loads)} 条加载事件)
- **P0**: {p_counts.get('P0', 0)}
- **P1**: {p_counts.get('P1', 0)}
- **P2**: {p_counts.get('P2', 0)}
- **P3**: {p_counts.get('P3', 0)}
- **P4**: {p_counts.get('P4', 0)}
- **system_server 崩溃**: {'是' if system_server_crash else '否'}
- **SystemUI 崩溃**: {'是' if systemui_crash else '否'}
- **Launcher 崩溃**: {'是' if launcher_crash else '否'}
- **Hook 失败**: {'是' if hook_failed else '否'}
- **RemotePreferences 异常**: {'是' if rp_issue else '否'}
- **重复最多的模块相关异常**: {module_top}
- **最终结论**: {conclusion}
"""
    with open(os.path.join(output_dir, "summary.md"), "w", encoding="utf-8") as f:
        f.write(summary)

    with open(os.path.join(output_dir, "candidates.tsv"), "w", encoding="utf-8") as f:
        f.write("priority\tscore\tcount\tfirst_time\tlast_time\tprocess\tpid\ttag\texception\tfingerprint\tmodule_related\tcontext_offset\tclassification\n")
        for c in sorted(candidates.values(), key=lambda x: (x.priority, -x.score, -x.count)):
            f.write(c.to_tsv() + "\n")

    # Only actionable priorities get a context dump. Emitting one for every P3/P4
    # line produced a file too large to read, which pushes the reader back to the raw
    # log - the exact thing this tool exists to prevent.
    with open(os.path.join(output_dir, "contexts.log"), "w", encoding="utf-8") as f:
        written = 0
        for fp, ctx in contexts:
            c = candidates.get(fp)
            if c is None or c.priority not in ("P0", "P1", "P2"):
                continue
            f.write(_format_context(fp, c, ctx))
            f.write(chr(10))
            written += 1
        if written == 0:
            f.write("No P0/P1/P2 candidates: nothing needs to be read by hand." + chr(10))

    noise = sorted((c for c in candidates.values() if c.priority in ("P3", "P4")), key=lambda x: -x.count)[:50]
    with open(os.path.join(output_dir, "noise-stats.tsv"), "w", encoding="utf-8") as f:
        f.write("priority\tscore\tcount\tprocess\ttag\tfingerprint\n")
        for c in noise:
            f.write(f"{c.priority}\t{c.score}\t{c.count}\t{','.join(c.process)}\t{c.tag}\t{c.fingerprint}\n")

    sigs = {}
    for fp, c in candidates.items():
        sigs[fp] = {
            "fingerprint": fp,
            "priority": c.priority,
            "score": c.score,
            "count": c.count,
            "first_time": c.first_time,
            "last_time": c.last_time,
            "process": sorted(c.process),
            "pids": sorted(c.pids),
            "exception": c.exception,
            "module_related": c.module_related,
            "classification": c.classification,
        }
    with open(os.path.join(output_dir, "signatures.json"), "w", encoding="utf-8") as f:
        json.dump(sigs, f, ensure_ascii=False, indent=2)

    with open(os.path.join(output_dir, "parser-stats.json"), "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2, default=str)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description="Analyze LSPosed full.log")
    parser.add_argument("log", help="Path to full.log")
    parser.add_argument("--profile", choices=["a14", "a13"], default="a14")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--output", required=True)
    parser.add_argument("--context-before", type=int, default=25)
    parser.add_argument("--context-after", type=int, default=60)
    parser.add_argument("--max-stack-lines", type=int, default=150)
    args = parser.parse_args()

    profile = PROFILES[args.profile]
    log_path = os.path.abspath(args.log)
    output_dir = os.path.abspath(args.output)

    print("Analyzing ...", file=sys.stderr)
    candidates, contexts, stats, module_loads = analyze(log_path, profile, args)
    write_outputs(output_dir, log_path, args.profile, candidates, contexts, stats, module_loads)
    print(f"Done. Output in {output_dir}", file=sys.stderr)
    print(f"Candidates: {len(candidates)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
