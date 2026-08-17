#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

mapfile -t FILES < <(git ls-files -z | xargs -0 -n1 printf '%s\n' | grep -Ev '(^|/)(node_modules|target|dist|build|coverage|\.git)/' || true)
((${#FILES[@]} > 0)) || { echo 'ERROR: no tracked files found.' >&2; exit 1; }

# Match credential values, not ordinary source identifiers such as getConnectionString().
PATTERN=$(cat <<'REGEX'
^[[:space:]]*-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----[[:space:]]*$|Authorization:[[:space:]]*Bearer[[:space:]]+[A-Za-z0-9._-]{20,}|AIza[0-9A-Za-z_-]{30,}|AKIA[0-9A-Z]{16}|access[_-]?key["']?[[:space:]]*[:=][[:space:]]*["'][A-Za-z0-9+/=]{20,}["']|client[_-]?secret["']?[[:space:]]*[:=][[:space:]]*["'][A-Za-z0-9._~+/-]{16,}["']|connection[_-]?string["']?[[:space:]]*[:=][[:space:]]*["']?(Endpoint=|Server=|Host=|DefaultEndpointsProtocol=)[^"'[:space:]]{16,}
REGEX
)

MATCH_FILE=$(mktemp)
trap 'rm -f "$MATCH_FILE"' EXIT

failures=0
for file in "${FILES[@]}"; do
  [[ -f "$file" ]] || continue
  case "$file" in
    *.png|*.jpg|*.jpeg|*.gif|*.webp|*.pdf|*.zip|*.jar|*.keystore|*.p12) continue ;;
  esac
  grep -Iq . "$file" || continue
  : >"$MATCH_FILE"
  if LC_ALL=C grep -Ein -- "$PATTERN" "$file" >"$MATCH_FILE" 2>/dev/null; then
    echo "ERROR: possible credential material in $file" >&2
    cut -d: -f1 "$MATCH_FILE" | sort -n -u | sed 's/^/  line /' >&2
    failures=$((failures+1))
  fi
done

if (( failures > 0 )); then
  echo "FAILED: $failures file(s) contain possible secret material." >&2
  exit 1
fi

echo "SUCCESS: scanned ${#FILES[@]} tracked files; no obvious credential material found."
