#!/usr/bin/env bash
set -euo pipefail

RG="${1:?resource group required}"
APPS_CSV="${2:?comma-separated app names required}"
SMOKE_ATTEMPTS="${SMOKE_ATTEMPTS:-18}"
SMOKE_SLEEP_SECONDS="${SMOKE_SLEEP_SECONDS:-10}"
IFS=',' read -r -a APPS <<<"$APPS_CSV"

[[ "$SMOKE_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || {
  echo 'ERROR: SMOKE_ATTEMPTS must be a positive integer.' >&2
  exit 1
}
[[ "$SMOKE_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || {
  echo 'ERROR: SMOKE_SLEEP_SECONDS must be a non-negative integer.' >&2
  exit 1
}

failures=0
for app in "${APPS[@]}"; do
  app="${app//[[:space:]]/}"
  [[ -n "$app" ]] || continue

  fqdn=$(az containerapp show \
    --resource-group "$RG" \
    --name "$app" \
    --query properties.configuration.ingress.fqdn \
    --output tsv \
    --only-show-errors)
  [[ -n "$fqdn" ]] || {
    echo "ERROR: $app has no ingress FQDN." >&2
    failures=$((failures + 1))
    continue
  }

  success=false
  for ((attempt=1; attempt<=SMOKE_ATTEMPTS; attempt++)); do
    declare -a observations=()

    for path in /actuator/health/readiness /actuator/health /health; do
      body_file=$(mktemp)
      code=$(curl \
        --silent \
        --show-error \
        --location \
        --max-time 20 \
        --connect-timeout 5 \
        --output "$body_file" \
        --write-out '%{http_code}' \
        "https://$fqdn$path" 2>/dev/null || true)

      status=$(jq -r '.status // empty' "$body_file" 2>/dev/null || true)
      [[ -n "$status" ]] || status='unavailable'
      observations+=("$path=http:$code,status:$status")

      if [[ "$code" == '200' ]] \
        && jq -e '
          (.status? == "UP")
          or (.status? == "running")
          or (.healthy? == true)
        ' "$body_file" >/dev/null 2>&1; then
        echo "SUCCESS: $app https://$fqdn$path attempt=$attempt"
        success=true
        rm -f "$body_file"
        break
      fi

      rm -f "$body_file"
    done

    if [[ "$success" == 'true' ]]; then
      break
    fi

    echo "Waiting for healthy ingress response from $app ($attempt/$SMOKE_ATTEMPTS): ${observations[*]}" >&2
    if (( attempt < SMOKE_ATTEMPTS )); then
      sleep "$SMOKE_SLEEP_SECONDS"
    fi
  done

  if [[ "$success" != 'true' ]]; then
    echo "ERROR: no supported health endpoint returned a healthy HTTP 200 response for $app after $SMOKE_ATTEMPTS attempts." >&2
    failures=$((failures + 1))
  fi
done

(( failures == 0 )) || {
  echo "FAILED: $failures service health smoke issue(s)." >&2
  exit 1
}

echo 'SUCCESS: all requested service health endpoints passed.'
