#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-rmorampudi09-arch/Craves-Build-platform}"
START_PR="${2:-25}"
END_PR="${3:-68}"

command -v gh >/dev/null 2>&1 || { echo 'ERROR: GitHub CLI (gh) is required.' >&2; exit 1; }
[[ "$START_PR" =~ ^[0-9]+$ && "$END_PR" =~ ^[0-9]+$ && "$START_PR" -le "$END_PR" ]] || { echo 'ERROR: invalid PR range.' >&2; exit 1; }

previous_head=""
failures=0
printf '%-6s %-8s %-8s %-45s %-45s\n' PR STATE DRAFT BASE HEAD
for pr in $(seq "$START_PR" "$END_PR"); do
  json=$(gh api "repos/$REPO/pulls/$pr" 2>/dev/null || true)
  if [[ -z "$json" ]]; then
    echo "WARN: PR #$pr not found; skipped."
    continue
  fi
  state=$(jq -r '.state' <<<"$json")
  draft=$(jq -r '.draft' <<<"$json")
  base=$(jq -r '.base.ref' <<<"$json")
  head=$(jq -r '.head.ref' <<<"$json")
  mergeable=$(jq -r '.mergeable // "pending"' <<<"$json")
  printf '#%-5s %-8s %-8s %-45s %-45s\n' "$pr" "$state" "$draft" "$base" "$head"

  if [[ "$state" != "open" || "$draft" != "true" ]]; then
    echo "ERROR: PR #$pr must remain open and draft before controlled rollout." >&2
    failures=$((failures+1))
  fi
  if [[ "$mergeable" == "false" ]]; then
    echo "ERROR: PR #$pr is not mergeable." >&2
    failures=$((failures+1))
  fi
  if [[ -n "$previous_head" && "$base" != "$previous_head" ]]; then
    echo "ERROR: PR #$pr base '$base' does not match previous head '$previous_head'." >&2
    failures=$((failures+1))
  fi
  previous_head="$head"
done

if (( failures > 0 )); then
  echo "FAILED: $failures stacked-PR issue(s) found." >&2
  exit 1
fi

echo 'SUCCESS: stacked PR chain is open, draft, mergeable and correctly ordered.'
