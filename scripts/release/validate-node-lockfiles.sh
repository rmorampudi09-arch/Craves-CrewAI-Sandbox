#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

command -v node >/dev/null 2>&1 || { echo 'ERROR: Node.js is required.' >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo 'ERROR: npm is required.' >&2; exit 1; }

NODE_MAJOR=$(node -p 'process.versions.node.split(".")[0]')
(( NODE_MAJOR >= 24 )) || { echo "ERROR: Node.js 24 or newer required; found $(node --version)." >&2; exit 1; }

mapfile -t PACKAGES < <(find apps -name package.json -not -path '*/node_modules/*' -print | sort)
((${#PACKAGES[@]} > 0)) || { echo 'ERROR: no application package.json files found.' >&2; exit 1; }

failures=0
for package in "${PACKAGES[@]}"; do
  module=$(dirname "$package")
  lock="$module/package-lock.json"
  echo "========== $module =========="
  if [[ ! -f "$lock" ]]; then
    echo "ERROR: missing reviewed package-lock.json in $module." >&2
    failures=$((failures+1))
    continue
  fi
  if ! (cd "$module" && npm ci --ignore-scripts --no-audit --fund=false); then
    echo "ERROR: npm ci failed in $module." >&2
    failures=$((failures+1))
    continue
  fi
  for script in typecheck test build; do
    if node -e "const p=require('./$package'); process.exit(p.scripts?.$script ? 0 : 1)"; then
      (cd "$module" && npm run "$script") || { echo "ERROR: npm run $script failed in $module." >&2; failures=$((failures+1)); }
    fi
  done
done

(( failures == 0 )) || { echo "FAILED: $failures Node application check(s) failed." >&2; exit 1; }
echo "SUCCESS: ${#PACKAGES[@]} Node applications have lockfiles and pass npm ci/build gates."
