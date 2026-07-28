#!/usr/bin/env python3
"""Moves one functional domain out of a large hook object into its own file.

Usage:
    python tools/split-hook-domain.py --source app/.../mods/System.kt \
        --target app/.../mods/SystemShareMenuHooks.kt --object SystemShareMenuHooks \
        --members CleanShareMenuHook,hideMimeType,...

The move is mechanical and verified: every moved member's text must appear
byte-identical in the target, and the sequence of hook calls in MainModule.java
must be unchanged apart from the receiver type. Hook registration order is a
property of that call sequence, not of file layout, which is what makes the
move safe to do without a device.

The script refuses to run if a moved member is still referenced from what stays
behind, or vice versa, so a domain can only be extracted once it is genuinely
self-contained.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

DECL = re.compile(
    r"^    (?:@JvmStatic\s+)?"
    r"(?:private |internal |public )?"
    r"(?:const )?"
    r"(fun|val|var|class|object|enum class|interface) (\w+)"
)
ANNOTATION_OR_COMMENT = re.compile(r"^\s*(@\w|//|/\*|\*)")


class Chunk:
    def __init__(self, name: str, kind: str, start: int, end: int, lines: list[str]) -> None:
        self.name = name
        self.kind = kind
        self.start = start
        self.end = end
        self.text = "\n".join(lines[start:end])

    def body_signature(self) -> str:
        """Whitespace-insensitive-at-the-edges text used to prove the move changed nothing."""
        return self.text.strip("\n")


def parse_chunks(lines: list[str]) -> tuple[int, list[Chunk], int]:
    """Returns (preamble_end, chunks, object_close_line)."""
    decl_lines = []
    for index, line in enumerate(lines):
        match = DECL.match(line)
        if match:
            decl_lines.append((index, match.group(1), match.group(2)))

    if not decl_lines:
        raise SystemExit("no top-level members found; is the indentation four spaces?")

    # A chunk starts at its leading annotation/comment block, not at the declaration.
    starts = []
    for index, _, _ in decl_lines:
        start = index
        while start > 0 and ANNOTATION_OR_COMMENT.match(lines[start - 1]):
            start -= 1
        starts.append(start)

    close = len(lines) - 1
    while close > 0 and lines[close].strip() != "}":
        close -= 1

    chunks = []
    for position, (index, kind, name) in enumerate(decl_lines):
        start = starts[position]
        end = starts[position + 1] if position + 1 < len(starts) else close
        chunks.append(Chunk(name, kind, start, end, lines))

    return starts[0], chunks, close


def referenced_names(text: str, candidates: set[str]) -> set[str]:
    return {name for name in candidates if re.search(r"\b" + re.escape(name) + r"\b", text)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--target", required=True)
    parser.add_argument("--object", required=True, dest="object_name")
    parser.add_argument("--members", required=True)
    parser.add_argument("--kdoc", default="")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    source = Path(args.source)
    lines = source.read_text(encoding="utf-8").split("\n")
    preamble_end, chunks, close = parse_chunks(lines)

    wanted = [name.strip() for name in args.members.split(",") if name.strip()]
    by_name: dict[str, list[Chunk]] = {}
    for chunk in chunks:
        by_name.setdefault(chunk.name, []).append(chunk)

    missing = [name for name in wanted if name not in by_name]
    if missing:
        raise SystemExit(f"not found in {source.name}: {missing}")

    moving = [chunk for chunk in chunks if chunk.name in wanted]
    staying = [chunk for chunk in chunks if chunk.name not in wanted]

    moving_names = {chunk.name for chunk in moving}
    staying_names = {chunk.name for chunk in staying}

    # A domain is only extractable when nothing crosses the boundary in either
    # direction, other than the public entry points MainModule calls.
    stay_text = "\n".join(chunk.text for chunk in staying)
    leaks_in = referenced_names(stay_text, moving_names)
    if leaks_in:
        raise SystemExit(f"members left behind still reference the domain: {sorted(leaks_in)}")

    move_text = "\n".join(chunk.text for chunk in moving)
    private_stayers = {
        chunk.name for chunk in staying if re.search(r"^\s*private ", chunk.text, re.MULTILINE)
    }
    leaks_out = referenced_names(move_text, private_stayers & staying_names)
    if leaks_out:
        raise SystemExit(f"domain references private members that stay behind: {sorted(leaks_out)}")

    package = next(line for line in lines if line.startswith("package "))
    imports = [line for line in lines[:preamble_end] if line.startswith("import ")]
    used_imports = [
        line for line in imports
        if re.search(r"\b" + re.escape(line.rstrip(";").split(".")[-1].strip()) + r"\b", move_text)
    ]

    header = [package, ""]
    header += used_imports
    header += [""]
    if args.kdoc:
        header += ["/**"] + [f" * {piece}" for piece in args.kdoc.split("\n")] + [" */"]
    header += [f"object {args.object_name} {{", ""]

    target_lines = header + [chunk.text.strip("\n") + "\n" for chunk in moving] + ["}", ""]
    target_text = "\n".join(target_lines)

    # Rebuild the source by deleting the moved line ranges verbatim rather than
    # re-joining what is left. Re-joining would renormalise the spacing between
    # retained members — separating property groups that were written adjacent —
    # and the diff would no longer be a pure deletion.
    moved_lines = set()
    for chunk in moving:
        moved_lines.update(range(chunk.start, chunk.end))
    remaining = [line for index, line in enumerate(lines) if index not in moved_lines]

    # A deleted chunk can leave the blank line that preceded it next to the blank
    # line that followed the previous one.
    collapsed = []
    for line in remaining:
        if line.strip() == "" and collapsed and collapsed[-1].strip() == "":
            continue
        collapsed.append(line)
    remaining_text = "\n".join(collapsed)

    # Proof that the move did not edit anything.
    for chunk in moving:
        if chunk.body_signature() not in target_text:
            raise SystemExit(f"moved member '{chunk.name}' is not byte-identical in the target")
    for chunk in staying:
        if chunk.body_signature() not in remaining_text:
            raise SystemExit(f"retained member '{chunk.name}' is not byte-identical in the source")

    print(f"{source.name}: {len(chunks)} members -> move {len(moving)}, keep {len(staying)}")
    print(f"  moved: {', '.join(sorted(moving_names))}")
    print(f"  imports carried: {len(used_imports)} of {len(imports)}")

    if args.dry_run:
        return 0

    Path(args.target).write_text(target_text, encoding="utf-8", newline="\n")
    source.write_text(remaining_text, encoding="utf-8", newline="\n")
    print(f"  wrote {args.target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
