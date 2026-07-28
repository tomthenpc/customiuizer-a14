#!/usr/bin/env python3
"""Repoints MainModule's calls at a hook object that was just split out.

Hook registration order is a property of the sequence of calls MainModule makes,
not of which file the callee lives in. This script rewrites only the receiver
type and then proves the ordered sequence of called hook names is identical to
what it was before, so a split cannot silently reorder registration.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

CALL = re.compile(r"\b([A-Z]\w*)\.([A-Za-z]\w*)\s*\(")


def call_sequence(text: str) -> list[str]:
    """The ordered method names called on any capitalised receiver."""
    return [match.group(2) for match in CALL.finditer(text)]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", required=True)
    parser.add_argument("--from-object", required=True)
    parser.add_argument("--to-object", required=True)
    parser.add_argument("--members", required=True)
    parser.add_argument("--package", default="tv.withaibuild.customiuizer.mods")
    args = parser.parse_args()

    path = Path(args.file)
    before = path.read_text(encoding="utf-8")
    members = [name.strip() for name in args.members.split(",") if name.strip()]

    after = before
    rewritten = 0
    for name in members:
        pattern = re.compile(r"\b" + re.escape(args.from_object) + r"\." + re.escape(name) + r"\s*\(")
        after, count = pattern.subn(f"{args.to_object}.{name}(", after)
        rewritten += count

    if rewritten == 0:
        raise SystemExit("no call sites rewritten; nothing to do")

    import_line = f"import {args.package}.{args.to_object};"
    if import_line not in after:
        anchor = f"import {args.package}.{args.from_object};"
        if anchor in after:
            after = after.replace(anchor, f"{import_line}\n{anchor}", 1)
        else:
            raise SystemExit(f"could not place import: {anchor} not found")

    if call_sequence(before) != call_sequence(after):
        raise SystemExit("call sequence changed; registration order would differ")

    path.write_text(after, encoding="utf-8", newline="\n")
    print(f"{path.name}: repointed {rewritten} call sites to {args.to_object}")
    print("  call sequence verified identical")
    return 0


if __name__ == "__main__":
    sys.exit(main())
