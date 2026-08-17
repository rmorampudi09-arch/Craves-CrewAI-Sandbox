#!/usr/bin/env bash
set -euo pipefail
set +x

SOURCE_SCRIPT="scripts/refund/validate-refund-e2e-sandbox.sh"

[[ -f "$SOURCE_SCRIPT" ]] || {
  echo "ERROR: Refund E2E validator was not found: $SOURCE_SCRIPT" >&2
  exit 1
}

PATCHED_SCRIPT=$(mktemp)
cleanup() {
  rm -f "$PATCHED_SCRIPT"
}
trap cleanup EXIT

python3 - "$SOURCE_SCRIPT" "$PATCHED_SCRIPT" <<'PY'
from pathlib import Path
import sys

source_path = Path(sys.argv[1])
target_path = Path(sys.argv[2])
text = source_path.read_text(encoding="utf-8")

old = """WHERE aggregate_id = '$integration_refund_id'::uuid
          AND payload -> 'data' ->> 'status' = '$EXPECTED_FINAL_ORDER_STATUS';"""
new = """WHERE aggregate_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid
          AND payload -> 'data' ->> 'refundId' = '$integration_refund_id'
          AND payload -> 'data' ->> 'status' = '$EXPECTED_FINAL_ORDER_STATUS';"""

occurrences = text.count(old)
if occurrences != 1:
    raise SystemExit(
        "ERROR: Expected exactly one known refund-status outbox lookup to patch; "
        f"found {occurrences}. Review the validator before running it."
    )

target_path.write_text(text.replace(old, new, 1), encoding="utf-8")
PY

chmod 700 "$PATCHED_SCRIPT"
bash -n "$PATCHED_SCRIPT"
exec bash "$PATCHED_SCRIPT"
