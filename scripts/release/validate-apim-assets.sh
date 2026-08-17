#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"
command -v xmllint >/dev/null 2>&1 || { echo 'ERROR: xmllint is required.' >&2; exit 1; }

mapfile -t POLICIES < <(find infra/apim -type f -name '*.xml' 2>/dev/null | sort)
mapfile -t SCRIPTS < <(find scripts/apim -type f -name '*.sh' 2>/dev/null | sort)
((${#POLICIES[@]} > 0)) || { echo 'ERROR: no APIM policy XML files found.' >&2; exit 1; }

WRITE_CONFIRM_PATTERN=$(cat <<'REGEX'
confirm[^=]*=["']?(true|yes)
REGEX
)

failures=0
for file in "${POLICIES[@]}"; do
  echo "Checking $file"
  xmllint --noout "$file" || { failures=$((failures+1)); continue; }
  grep -q '<policies' "$file" || { echo "ERROR: $file lacks policies root." >&2; failures=$((failures+1)); }
  grep -q '<base[[:space:]]*/>' "$file" || { echo "ERROR: $file does not inherit a parent policy with <base />." >&2; failures=$((failures+1)); }
  if grep -Eq '<allowed-origin>[[:space:]]*\*[[:space:]]*</allowed-origin>|<origin>[[:space:]]*\*[[:space:]]*</origin>' "$file"; then
    echo "ERROR: wildcard CORS origin in $file." >&2; failures=$((failures+1))
  fi
  if grep -Eiq '(accesskey=|client-secret|Authorization:[[:space:]]*Bearer[[:space:]]+[A-Za-z0-9._-]{20,})' "$file"; then
    echo "ERROR: possible credential material in $file." >&2; failures=$((failures+1))
  fi
  if grep -q 'backend-id=' "$file" && grep -q 'base-url=' "$file"; then
    echo "ERROR: $file mixes backend-id and base-url routing, which can fail under inherited policies." >&2
    failures=$((failures+1))
  fi
done

for script in "${SCRIPTS[@]}"; do
  bash -n "$script" || failures=$((failures+1))
  if grep -Eiq -- "$WRITE_CONFIRM_PATTERN" "$script"; then
    echo "ERROR: $script appears to default a write confirmation to true." >&2
    failures=$((failures+1))
  fi
done

(( failures == 0 )) || { echo "FAILED: $failures APIM asset issue(s)." >&2; exit 1; }
echo "SUCCESS: ${#POLICIES[@]} policies and ${#SCRIPTS[@]} scripts passed APIM safety checks."
