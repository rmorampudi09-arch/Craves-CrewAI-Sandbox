#!/usr/bin/env bash
set -euo pipefail

RG="${RG:-rg-craves-prodlow-centralindia}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
USER_CHEF_APP="${USER_CHEF_APP:-ca-craves-user-chef-service-prod}"
NOTIFICATION_APP="${NOTIFICATION_APP:-ca-craves-notification-service-p}"

printf '\n== Craves Notification Outbox State Check ==\n'
printf 'Resource group: %s\n' "$RG"
printf 'Order app: %s\n' "$ORDER_APP"
printf 'User-Chef app: %s\n' "$USER_CHEF_APP"
printf 'Notification app: %s\n\n' "$NOTIFICATION_APP"

require_az() {
  if ! command -v az >/dev/null 2>&1; then
    echo "Azure CLI not found. Run this in Azure Cloud Shell or a machine with az installed." >&2
    exit 1
  fi
}

show_env() {
  local app_name="$1"
  local label="$2"
  printf '\n-- %s notification env --\n' "$label"
  az containerapp show \
    --name "$app_name" \
    --resource-group "$RG" \
    --query "properties.template.containers[0].env[?starts_with(name, 'CRAVES_NOTIFICATION')].[name,value,secretRef]" \
    -o table
}

show_revision() {
  local app_name="$1"
  local label="$2"
  printf '\n-- %s revision/image --\n' "$label"
  az containerapp show \
    --name "$app_name" \
    --resource-group "$RG" \
    --query "{runningStatus:properties.runningStatus, latestRevision:properties.latestRevisionName, image:properties.template.containers[0].image, targetPort:properties.configuration.ingress.targetPort}" \
    -o table
}

show_recent_outbox_logs() {
  local app_name="$1"
  local label="$2"
  printf '\n-- %s recent outbox logs --\n' "$label"
  set +e
  az containerapp logs show \
    --name "$app_name" \
    --resource-group "$RG" \
    --tail 500 2>/dev/null | grep -iE "outbox|Direct notification dispatch disabled|event sent|dispatcher|Review event buffered|Chef notice" || true
  set -e
}

require_az
show_revision "$ORDER_APP" "Order Service"
show_env "$ORDER_APP" "Order Service"
show_recent_outbox_logs "$ORDER_APP" "Order Service"

show_revision "$USER_CHEF_APP" "User-Chef Service"
show_env "$USER_CHEF_APP" "User-Chef Service"
show_recent_outbox_logs "$USER_CHEF_APP" "User-Chef Service"

printf '\nExpected current state after yesterday/today work:\n'
printf '  Order Service: CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=true\n'
printf '  Order Service: CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED=false\n'
printf '  User-Chef before pipeline/env enablement: dispatcher may still be false and direct dispatch may still be true.\n'
printf '\nDo not print or paste CRAVES_NOTIFICATION_INTERNAL_KEY values. The table should show secretRef for secret-backed values.\n'
