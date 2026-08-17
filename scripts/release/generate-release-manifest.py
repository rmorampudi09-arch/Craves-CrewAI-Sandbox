#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import pathlib
import subprocess
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT = pathlib.Path(os.environ.get("CRAVES_RELEASE_MANIFEST", ROOT / "artifacts/release/release-manifest.json"))


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def digest(path: pathlib.Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()

tracked = [ROOT / item for item in git("ls-files").splitlines() if item]
selected = [
    path for path in tracked
    if path.name in {"pom.xml", "package.json", "package-lock.json", "Dockerfile"}
    or path.name.startswith("azure-pipelines")
    or "/db/migration/" in path.as_posix()
    or path.as_posix().startswith((str(ROOT / "infra"), str(ROOT / "scripts/release")))
]

source_epoch = os.environ.get("SOURCE_DATE_EPOCH")
created_at = (
    datetime.fromtimestamp(int(source_epoch), tz=timezone.utc)
    if source_epoch
    else datetime.now(timezone.utc)
).isoformat()

manifest = {
    "schemaVersion": 1,
    "repository": git("config", "--get", "remote.origin.url"),
    "commit": git("rev-parse", "HEAD"),
    "tree": git("rev-parse", "HEAD^{tree}"),
    "createdAt": created_at,
    "dirty": bool(git("status", "--porcelain")),
    "files": [
        {
            "path": path.relative_to(ROOT).as_posix(),
            "sha256": digest(path),
            "bytes": path.stat().st_size,
        }
        for path in sorted(selected)
    ],
}
manifest["manifestContentSha256"] = hashlib.sha256(
    json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8")
).hexdigest()

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"SUCCESS: wrote {OUTPUT} with {len(manifest['files'])} hashed release inputs")
