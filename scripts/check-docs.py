#!/usr/bin/env python3
"""
Checks that the app's Kotlin code is described in plain words: every file has at least one
/** ... */ description, and every top-level class, object, interface and function that isn't
private has one directly above it (annotations like @Composable may sit in between).

Prints what's missing and exits with 1 if anything is, so CI can fail the build.
Run from the repo root: python3 scripts/check-docs.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path("app/src/main/java")
DECLARATION = re.compile(
    r"^(?:(?:internal|public|abstract|open|sealed|data|enum|suspend|inline|value|fun|annotation)\s+)*"
    r"(?:class|object|interface|fun)\b"
)

missing = []
for path in sorted(ROOT.rglob("*.kt")):
    lines = path.read_text(encoding="utf-8").splitlines()
    if not any("/**" in line for line in lines):
        missing.append(f"{path}: no description in this file")
        continue
    for i, line in enumerate(lines):
        if not DECLARATION.match(line):
            continue
        j = i - 1
        while j >= 0 and lines[j].startswith("@"):
            j -= 1
        if j < 0 or not lines[j].rstrip().endswith("*/"):
            missing.append(f"{path}:{i + 1}: {line.strip()[:80]}")

if missing:
    print("Code without a description (see CONTRIBUTING.md, Documentation):")
    print("\n".join(missing))
    sys.exit(1)
print("All code is described.")
