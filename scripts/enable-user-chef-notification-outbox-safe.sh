#!/usr/bin/env bash
set -euo pipefail

RG="${RG:-rg-craves-prodlow-centralindia}"
USER_CHEF_APP="${USER_CHEF_APP:-ca-craves-user-chef-service-prod}"
MODE="${1:-parallel}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/enable-user-chef-notification-outbox-safe.sh parallel
  ./scripts/enable-user-chef-notification-outbox-safe.sh outbox-first

Modes:
  parallel
    Enables User-Chef notification outbox dispatcher but keeps direct dispatch enabled.
    Use this immediately after the User-Chef pipeline has deployed the new image.

  outbox-first
    Disables direct dispatch after one successful chef approval/rejection notification test.
    Use only after confirming the dispatcher sent a notification and the chef inbox received it once.
EOF
}

if [[ "$MODE" != "parallel" && "$MODE" != "outbox-first" ]]; then
  usage
  exit 1
fi

if ! command -v az >/dev/null 2>&1; then
  echo "Azure CLI not found. Run this in Azure Cloud Shell or a machine with az installed." >&2
  exit 1
fi

printf '\n== User-Chef Notification Outbox Safe Enablement ==\n'
printf 'Resource group: %s\n' "$RG"
printf 'Container App: %s\n' "$USER_CHEF_APP"
printf 'Mode: %s\n\n' "$MODE"

printf 'Current User-Chef revision/image:\n'
az containerapp show \
  --name "$USER_CHEF_APP" \
  --resource-group "$RG" \
  --query "{runningStatus:properties.runningStatus, latestRevision:properties.latestRevisionName, image:properties.template.containers[0].image}" \
  -o table

if [[ "$MODE" == "parallel" ]]; then
  cat <<'EOF'

This will:
  - enable CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=true
  - keep CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED=true

This is the safe first step because chef approval/rejection notification can still use the existing direct path while the outbox dispatcher is being verified.
EOF
  read -r -p "Proceed with parallel safe enablement? Type YES: " CONFIRM
  if [[ "$CONFIRM" != "YES" ]]; then
    echo "Cancelled."
    exit 0
  fi

  az containerapp update \
    --name "$USER_CHEF_APP" \
    --resource-group "$RG" \
    --set-env-vars \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED="true" \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_FIXED_DELAY_MS="30000" \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_BATCH_SIZE="25" \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_MAX_ATTEMPTS="5" \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_RETRY_BASE_DELAY_SECONDS="60" \
      CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED="true" \
      FORCE_RESTART="$(date +%s)" \
    -o table
else
  cat <<'EOF'

This will:
  - keep the User-Chef outbox dispatcher enabled
  - switch CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED=false

Use this only after one successful chef approval/rejection test confirms:
  - User-Chef logs show Review event buffered / Chef notice outbox event sent
  - Chef in-app inbox receives the notification once
  - No duplicate notification is created
EOF
  read -r -p "Proceed with outbox-first switch? Type YES: " CONFIRM
  if [[ "$CONFIRM" != "YES" ]]; then
    echo "Cancelled."
    exit 0
  fi

  az containerapp update \
    --name "$USER_CHEF_APP" \
    --resource-group "$RG" \
    --set-env-vars \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED="true" \
      CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED="false" \
      FORCE_RESTART="$(date +%s)" \
    -o table
fi

printf '\nUpdated User-Chef notification env:\n'
az containerapp show \
  --name "$USER_CHEF_APP" \
  --resource-group "$RG" \
  --query "properties.template.containers[0].env[?starts_with(name, 'CRAVES_NOTIFICATION')].[name,value,secretRef]" \
  -o table

printf '\nRecent User-Chef logs containing notification/outbox keywords:\n'
set +e
az containerapp logs show \
  --name "$USER_CHEF_APP" \
  --resource-group "$RG" \
  --tail 500 2>/dev/null | grep -iE "outbox|Direct notification dispatch disabled|event sent|dispatcher|Review event buffered|Chef notice" || true
set -e
