#!/usr/bin/env bash
set -euo pipefail

RG="${1:?resource group required}"
APPS_CSV="${2:?comma-separated Container App names required}"
IFS=',' read -r -a APPS <<<"$APPS_CSV"

command -v az >/dev/null 2>&1 || { echo 'ERROR: Azure CLI is required.' >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo 'ERROR: jq is required.' >&2; exit 1; }

failures=0
for app in "${APPS[@]}"; do
  app="${app//[[:space:]]/}"
  [[ -n "$app" ]] || continue
  echo "========== $app =========="
  json=$(az containerapp show -g "$RG" -n "$app" --only-show-errors -o json) || { failures=$((failures+1)); continue; }
  latest=$(jq -r '.properties.latestRevisionName // ""' <<<"$json")
  ready=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$json")
  running=$(jq -r '.properties.runningStatus // ""' <<<"$json")
  image=$(jq -r '.properties.template.containers[0].image // ""' <<<"$json")
  [[ -n "$latest" && -n "$image" ]] || { echo "ERROR: $app lacks latest revision or image." >&2; failures=$((failures+1)); continue; }
  [[ "$running" == "Running" ]] || { echo "ERROR: $app runningStatus=$running." >&2; failures=$((failures+1)); }

  revision=$(az containerapp revision show -g "$RG" -n "$app" --revision "$latest" --only-show-errors -o json)
  active=$(jq -r '.properties.active // false' <<<"$revision")
  provisioning=$(jq -r '.properties.provisioningState // ""' <<<"$revision")
  replicas=$(jq -r '.properties.replicas // 0' <<<"$revision")
  [[ "$active" == "true" && "$provisioning" == "Provisioned" && "$replicas" -ge 1 ]] || {
    echo "ERROR: $app latest revision is not active/provisioned with at least one replica." >&2
    failures=$((failures+1))
  }
  printf 'latest=%s ready=%s running=%s replicas=%s image=%s\n' "$latest" "$ready" "$running" "$replicas" "$image"
done

(( failures == 0 )) || { echo "FAILED: $failures Container App preflight issue(s)." >&2; exit 1; }
echo 'SUCCESS: all requested Container Apps passed read-only preflight.'
