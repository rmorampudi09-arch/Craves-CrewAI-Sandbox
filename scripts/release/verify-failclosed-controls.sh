#!/usr/bin/env bash
set -euo pipefail

RG="${1:-}"
APP="${2:-}"
CONFIG="services/integration-service/src/main/resources/application.yml"
[[ -f "$CONFIG" ]] || { echo "ERROR: $CONFIG not found." >&2; exit 1; }

required_false=(
  CRAVES_DELIVERY_COMMAND_ENABLED
  CRAVES_DELIVERY_RECONCILIATION_ENABLED
  CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED
  CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED
  CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED
  BORZO_API_ENABLED
)
required_true=(CRAVES_DELIVERY_INTELLIGENCE_ENABLED)

failures=0
for name in "${required_false[@]}"; do
  key=$(tr '[:upper:]_' '[:lower:].' <<<"$name")
  if ! grep -Eq "\$\{$name:false\}|$key:[[:space:]]*false" "$CONFIG"; then
    echo "ERROR: $name is not visibly fail-closed in $CONFIG." >&2
    failures=$((failures+1))
  fi
done

if [[ -n "$RG" || -n "$APP" ]]; then
  [[ -n "$RG" && -n "$APP" ]] || { echo 'ERROR: resource group and app must be supplied together.' >&2; exit 1; }
  json=$(az containerapp show -g "$RG" -n "$APP" --only-show-errors -o json)
  env_value() { jq -r --arg n "$1" '[.properties.template.containers[0].env[]? | select(.name==$n)][0].value // ""' <<<"$json"; }
  for name in "${required_false[@]}"; do
    value=$(env_value "$name")
    [[ "$value" == "false" ]] || { echo "ERROR: runtime $name=$value; expected false." >&2; failures=$((failures+1)); }
  done
  for name in "${required_true[@]}"; do
    value=$(env_value "$name")
    [[ "$value" == "true" ]] || { echo "ERROR: runtime $name=$value; expected true." >&2; failures=$((failures+1)); }
  done
fi

(( failures == 0 )) || { echo "FAILED: $failures fail-closed control issue(s)." >&2; exit 1; }
echo 'SUCCESS: delivery/provider execution remains fail-closed and intelligence remains enabled.'
