#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
FILES = sorted(ROOT.glob("services/*/src/main/resources/db/migration/V*__*.sql"))
if not FILES:
    raise SystemExit("ERROR: no Flyway migrations found")

pattern = re.compile(r"^V(?P<version>[0-9]+(?:_[0-9]+)*)__[a-z0-9_]+\.sql$")
by_dir: dict[pathlib.Path, list[tuple[tuple[int, ...], pathlib.Path]]] = {}
errors: list[str] = []

for path in FILES:
    match = pattern.match(path.name)
    if not match:
        errors.append(f"{path.relative_to(ROOT)}: invalid Flyway filename")
        continue
    version = tuple(int(part) for part in match.group("version").split("_"))
    by_dir.setdefault(path.parent, []).append((version, path))
    text = path.read_text(encoding="utf-8")
    destructive = re.search(r"\b(DROP\s+(TABLE|SCHEMA|COLUMN)|TRUNCATE\s+TABLE)\b", text, re.IGNORECASE)
    if destructive and "CRAVES-REVIEWED-DESTRUCTIVE-MIGRATION" not in text:
        errors.append(f"{path.relative_to(ROOT)}: destructive SQL requires explicit review marker")

for directory, items in by_dir.items():
    seen: set[tuple[int, ...]] = set()
    previous: tuple[int, ...] | None = None
    for version, path in sorted(items):
        if version in seen:
            errors.append(f"{directory.relative_to(ROOT)}: duplicate migration version {version}")
        seen.add(version)
        if previous is not None and version <= previous:
            errors.append(f"{path.relative_to(ROOT)}: version is not strictly increasing")
        previous = version

if errors:
    print("\n".join(f"ERROR: {item}" for item in errors), file=sys.stderr)
    raise SystemExit(1)
print(f"SUCCESS: validated {len(FILES)} Flyway migrations across {len(by_dir)} services")
