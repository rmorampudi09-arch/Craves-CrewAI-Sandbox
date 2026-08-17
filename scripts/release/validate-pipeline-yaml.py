#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import sys
import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
FILES = sorted({*ROOT.glob("azure-pipelines*.yml"), *ROOT.glob(".github/workflows/*.yml"), *ROOT.glob(".github/workflows/*.yaml")})
if not FILES:
    raise SystemExit("ERROR: no pipeline YAML files were found")

errors: list[str] = []
for path in FILES:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    if "\t" in text:
        errors.append(f"{rel}: tab characters are not permitted")
    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError as exc:
        errors.append(f"{rel}: invalid YAML: {exc}")
        continue
    if not isinstance(doc, dict):
        errors.append(f"{rel}: top-level YAML must be a mapping")
        continue
    if rel.name.startswith("azure-pipelines"):
        if "steps" not in doc and "stages" not in doc and "jobs" not in doc:
            errors.append(f"{rel}: Azure pipeline has no steps, jobs or stages")
        if doc.get("trigger") not in (None, "none", {"none": True}):
            errors.append(f"{rel}: automatic CI trigger must be reviewed; expected trigger: none")
        if doc.get("pr") not in (None, "none", {"none": True}):
            errors.append(f"{rel}: automatic PR trigger must be reviewed; expected pr: none")

if errors:
    print("\n".join(f"ERROR: {item}" for item in errors), file=sys.stderr)
    raise SystemExit(1)
print(f"SUCCESS: validated {len(FILES)} pipeline YAML files")
