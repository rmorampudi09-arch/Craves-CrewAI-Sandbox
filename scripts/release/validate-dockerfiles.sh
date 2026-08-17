#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"
mapfile -t DOCKERFILES < <(find . -name Dockerfile -not -path '*/node_modules/*' -not -path '*/target/*' | sort)
((${#DOCKERFILES[@]} > 0)) || { echo 'ERROR: no Dockerfiles found.' >&2; exit 1; }

failures=0
for file in "${DOCKERFILES[@]}"; do
  echo "Checking $file"
  if grep -Eiq '^FROM[[:space:]]+[^[:space:]]+:latest([[:space:]]|$)' "$file"; then
    echo "ERROR: $file uses the mutable latest tag." >&2; failures=$((failures+1))
  fi
  if ! grep -Eq '^USER[[:space:]]+[^[:space:]]+' "$file"; then
    echo "ERROR: $file has no explicit non-root USER." >&2; failures=$((failures+1))
  elif grep -Eiq '^USER[[:space:]]+(root|0)([[:space:]]|$)' "$file"; then
    echo "ERROR: $file ends or runs as root." >&2; failures=$((failures+1))
  fi
  if grep -Eiq '(ARG|ENV)[[:space:]]+[^=]*(PASSWORD|SECRET|TOKEN|ACCESS_KEY|CONNECTION_STRING)' "$file"; then
    echo "ERROR: $file declares a likely secret build argument/environment value." >&2; failures=$((failures+1))
  fi
  if grep -Eiq '^ADD[[:space:]]+https?://' "$file"; then
    echo "ERROR: $file downloads remote content with ADD." >&2; failures=$((failures+1))
  fi
  if ! grep -Eq '^ENTRYPOINT|^CMD' "$file"; then
    echo "ERROR: $file has no ENTRYPOINT or CMD." >&2; failures=$((failures+1))
  fi
done

(( failures == 0 )) || { echo "FAILED: $failures Docker hardening issue(s)." >&2; exit 1; }
echo "SUCCESS: ${#DOCKERFILES[@]} Dockerfiles passed baseline hardening checks."
