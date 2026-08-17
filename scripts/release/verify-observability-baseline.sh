#!/usr/bin/env bash
set -euo pipefail

RG="${1:-}"
APPS_CSV="${2:-}"
failures=0

mapfile -t CONFIGS < <(find services -path '*/src/main/resources/application.yml' -print | sort)
((${#CONFIGS[@]} > 0)) || { echo 'ERROR: no Spring application.yml files found.' >&2; exit 1; }
for config in "${CONFIGS[@]}"; do
  if ! grep -Eq '^management:|management\.endpoint|management\.endpoints' "$config"; then
    echo "ERROR: $config has no visible Actuator management configuration." >&2
    failures=$((failures+1))
  fi
  if ! grep -Eqi 'health|probes-enabled|readiness|liveness' "$config"; then
    echo "ERROR: $config has no visible health/probe configuration." >&2
    failures=$((failures+1))
  fi
done

if [[ -n "$RG" || -n "$APPS_CSV" ]]; then
  [[ -n "$RG" && -n "$APPS_CSV" ]] || { echo 'ERROR: resource group and app list must be supplied together.' >&2; exit 1; }
  IFS=',' read -r -a APPS <<<"$APPS_CSV"
  declare -A ENVIRONMENTS=()
  for app in "${APPS[@]}"; do
    app="${app//[[:space:]]/}"
    [[ -n "$app" ]] || continue
    env_id=$(az containerapp show -g "$RG" -n "$app" --query properties.managedEnvironmentId -o tsv --only-show-errors)
    [[ -n "$env_id" ]] || { echo "ERROR: $app has no managed environment ID." >&2; failures=$((failures+1)); continue; }
    ENVIRONMENTS["$env_id"]=1
  done
  for env_id in "${!ENVIRONMENTS[@]}"; do
    destination=$(az containerapp env show --ids "$env_id" --query properties.appLogsConfiguration.destination -o tsv --only-show-errors)
    case "${destination,,}" in
      log-analytics|azure-monitor) echo "SUCCESS: $env_id logs destination=$destination" ;;
      *) echo "ERROR: $env_id logs destination='$destination'; expected log-analytics or azure-monitor." >&2; failures=$((failures+1)) ;;
    esac
  done
fi

(( failures == 0 )) || { echo "FAILED: $failures observability baseline issue(s)." >&2; exit 1; }
echo 'SUCCESS: source probes and Azure logging baseline are present.'
