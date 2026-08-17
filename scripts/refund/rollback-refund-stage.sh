#!/usr/bin/env bash
set -euo pipefail
set +x

: "${ROLLBACK_TARGET:?ROLLBACK_TARGET is required}"
: "${RESOURCE_GROUP:?RESOURCE_GROUP is required}"
: "${ORDER_APP:?ORDER_APP is required}"
: "${INTEGRATION_APP:?INTEGRATION_APP is required}"

case "$ROLLBACK_TARGET" in
  safe_publisher_only)
    ORDER_EVENT_TYPES="CHEF_ACCEPTED_ORDER"
    TIMEOUT_WORKER=false
    PROVIDER_EXECUTION=false
    RECONCILIATION=false
    NOTIFICATION_DISPATCHER=false
    ;;
  stage1_request_publication)
    ORDER_EVENT_TYPES="CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
    TIMEOUT_WORKER=false
    PROVIDER_EXECUTION=false
    RECONCILIATION=false
    NOTIFICATION_DISPATCHER=false
    ;;
  stage2_timeout_worker)
    ORDER_EVENT_TYPES="CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
    TIMEOUT_WORKER=true
    PROVIDER_EXECUTION=false
    RECONCILIATION=false
    NOTIFICATION_DISPATCHER=false
    ;;
  stage3_cashfree_sandbox_execution)
    ORDER_EVENT_TYPES="CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
    TIMEOUT_WORKER=true
    PROVIDER_EXECUTION=true
    RECONCILIATION=false
    NOTIFICATION_DISPATCHER=false
    ;;
  stage4_reconciliation)
    ORDER_EVENT_TYPES="CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
    TIMEOUT_WORKER=true
    PROVIDER_EXECUTION=true
    RECONCILIATION=true
    NOTIFICATION_DISPATCHER=false
    ;;
  *)
    echo "ERROR: Unsupported rollback target: $ROLLBACK_TARGET"
    exit 1
    ;;
esac

wait_healthy() {
  local app="$1"
  local previous="$2"

  for attempt in $(seq 1 60); do
    local latest ready health running
    latest=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$app" --query properties.latestRevisionName --output tsv)
    ready=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$app" --query properties.latestReadyRevisionName --output tsv)
    running=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$app" --query properties.runningStatus --output tsv)
    health=$(az containerapp revision show --resource-group "$RESOURCE_GROUP" --name "$app" --revision "$latest" --query properties.healthState --output tsv 2>/dev/null || true)

    echo "$app attempt $attempt/60: latest=$latest ready=$ready health=$health running=$running"
    if [[ "$latest" != "$previous" \
          && "$latest" == "$ready" \
          && "$health" == "Healthy" \
          && "$running" == "Running" ]]; then
      return 0
    fi
    if [[ "$health" == "Unhealthy" || "$running" == "Failed" ]]; then
      return 1
    fi
    sleep 10
  done

  return 1
}

ORDER_PREVIOUS=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$ORDER_APP" --query properties.latestRevisionName --output tsv)
INTEGRATION_PREVIOUS=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$INTEGRATION_APP" --query properties.latestRevisionName --output tsv)

# Stop outward notification delivery first, then financial execution, then timeout/event generation.
az containerapp update \
  --resource-group "$RESOURCE_GROUP" \
  --name "$ORDER_APP" \
  --set-env-vars CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=false \
  --output none

az containerapp update \
  --resource-group "$RESOURCE_GROUP" \
  --name "$INTEGRATION_APP" \
  --set-env-vars \
    CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED="$PROVIDER_EXECUTION" \
    CRAVES_REFUND_RECONCILIATION_ENABLED="$RECONCILIATION" \
    CRAVES_REFUND_CONSUMER_ENABLED=true \
    CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true \
    CRAVES_DELIVERY_COMMAND_ENABLED=false \
    BORZO_API_ENABLED=false \
  --output none

az containerapp update \
  --resource-group "$RESOURCE_GROUP" \
  --name "$ORDER_APP" \
  --set-env-vars \
    CRAVES_DOMAIN_EVENT_ENABLED_TYPES="$ORDER_EVENT_TYPES" \
    CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED="$TIMEOUT_WORKER" \
    CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED="$NOTIFICATION_DISPATCHER" \
    CRAVES_REFUND_STATUS_CONSUMER_ENABLED=true \
    CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED=true \
    CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED=true \
  --output none

wait_healthy "$INTEGRATION_APP" "$INTEGRATION_PREVIOUS"
wait_healthy "$ORDER_APP" "$ORDER_PREVIOUS"

echo "============================================================"
echo "SUCCESS: REFUND ROLLBACK APPLIED"
echo "Rollback target: $ROLLBACK_TARGET"
echo "Order event types: $ORDER_EVENT_TYPES"
echo "Chef timeout worker: $TIMEOUT_WORKER"
echo "Cashfree sandbox execution: $PROVIDER_EXECUTION"
echo "Reconciliation: $RECONCILIATION"
echo "Notification dispatcher: $NOTIFICATION_DISPATCHER"
echo "Durable outbox, inbox and refund records were not deleted."
echo "============================================================"
