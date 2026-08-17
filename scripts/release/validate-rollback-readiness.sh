#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"
mapfile -t PIPELINES < <(find . -maxdepth 1 -type f -name 'azure-pipelines*.yml' -print | sort)
((${#PIPELINES[@]} > 0)) || { echo 'ERROR: no Azure pipeline YAML files found.' >&2; exit 1; }

MUTABLE_LATEST_PATTERN=$(cat <<'REGEX'
:[[:space:]]*latest([[:space:]"']|$)
REGEX
)

failures=0
checked=0
for file in "${PIPELINES[@]}"; do
  name=$(basename "$file")
  case "$name" in
    *-ci.yml|*-status.yml|*-rollback.yml|azure-pipelines-release-*) continue ;;
  esac
  if ! grep -Eq 'az[[:space:]]+containerapp[[:space:]]+update|docker[[:space:]]+push|az[[:space:]]+deployment' "$file"; then
    continue
  fi
  checked=$((checked+1))
  echo "Checking $name"

  if grep -Eq -- "$MUTABLE_LATEST_PATTERN" "$file"; then
    echo "ERROR: $name uses a mutable latest image tag." >&2
    failures=$((failures+1))
  fi

  if grep -Eq 'az[[:space:]]+containerapp[[:space:]]+update' "$file"; then
    if ! grep -Eq 'az[[:space:]]+containerapp[[:space:]]+show.*image|CURRENT_IMAGE|PREVIOUS_IMAGE|ROLLBACK_IMAGE' "$file"; then
      echo "ERROR: $name deploys a Container App without visibly recording the previous image." >&2
      failures=$((failures+1))
    fi
  fi

  stem=${name#azure-pipelines-}
  stem=${stem%.yml}
  if ! find . -maxdepth 1 -type f -name "azure-pipelines-*${stem}*rollback*.yml" -print -quit | grep -q . \
     && ! grep -Eqi 'rollback' "$file"; then
    echo "ERROR: $name has no matching rollback pipeline or rollback stage." >&2
    failures=$((failures+1))
  fi
done

(( checked > 0 )) || { echo 'ERROR: no deployment-capable pipelines were found.' >&2; exit 1; }
(( failures == 0 )) || { echo "FAILED: $failures rollback-readiness issue(s) across $checked deployment pipelines." >&2; exit 1; }
echo "SUCCESS: $checked deployment pipelines have immutable images and visible rollback controls."
