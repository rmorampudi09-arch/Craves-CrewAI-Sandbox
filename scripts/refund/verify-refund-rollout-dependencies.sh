#!/usr/bin/env bash
set -euo pipefail
set +x

required_vars=(
  RESOURCE_GROUP
  ORDER_APP
  INTEGRATION_APP
  NOTIFICATION_APP
  SERVICE_BUS_NAMESPACE
  SERVICE_BUS_TOPIC
)

for name in "${required_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: Required variable $name is missing."
    exit 1
  fi
done

verify_healthy_app() {
  local app="$1"
  local latest ready running health

  latest=$(az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$app" \
    --query properties.latestRevisionName \
    --output tsv)
  ready=$(az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$app" \
    --query properties.latestReadyRevisionName \
    --output tsv)
  running=$(az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$app" \
    --query properties.runningStatus \
    --output tsv)
  health=$(az containerapp revision show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$app" \
    --revision "$latest" \
    --query properties.healthState \
    --output tsv 2>/dev/null || true)

  if [[ -z "$latest" \
        || "$latest" != "$ready" \
        || "$running" != "Running" \
        || "$health" != "Healthy" ]]; then
    echo "ERROR: Container App is not healthy: $app"
    echo "latest=$latest ready=$ready running=$running health=$health"
    exit 1
  fi

  echo "Healthy dependency: $app revision=$latest"
}

verify_healthy_app "$ORDER_APP"
verify_healthy_app "$INTEGRATION_APP"
verify_healthy_app "$NOTIFICATION_APP"

ORDER_PRINCIPAL_ID=$(az containerapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$ORDER_APP" \
  --query identity.principalId \
  --output tsv)

if [[ -z "$ORDER_PRINCIPAL_ID" || "$ORDER_PRINCIPAL_ID" == "null" ]]; then
  echo "ERROR: Order Container App does not have a system-assigned managed identity."
  exit 1
fi

NAMESPACE_SCOPE=$(az servicebus namespace show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$SERVICE_BUS_NAMESPACE" \
  --query id \
  --output tsv)

TOPIC_SCOPE=$(az servicebus topic show \
  --resource-group "$RESOURCE_GROUP" \
  --namespace-name "$SERVICE_BUS_NAMESPACE" \
  --name "$SERVICE_BUS_TOPIC" \
  --query id \
  --output tsv)

ORDER_SENDER_COUNT=$(az role assignment list \
  --assignee-object-id "$ORDER_PRINCIPAL_ID" \
  --all \
  --query "[?roleDefinitionName=='Azure Service Bus Data Sender' && (scope=='$NAMESPACE_SCOPE' || scope=='$TOPIC_SCOPE')] | length(@)" \
  --output tsv)

if [[ "${ORDER_SENDER_COUNT:-0}" == "0" ]]; then
  echo "ERROR: Order managed identity is missing Azure Service Bus Data Sender."
  echo "ORDER_PRINCIPAL_ID=$ORDER_PRINCIPAL_ID"
  echo "NAMESPACE_SCOPE=$NAMESPACE_SCOPE"
  echo "TOPIC_SCOPE=$TOPIC_SCOPE"
  echo "Grant the role once with an authorized Azure account, then rerun."
  exit 2
fi

INTEGRATION_PRINCIPAL_ID=$(az containerapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$INTEGRATION_APP" \
  --query identity.principalId \
  --output tsv)

if [[ -z "$INTEGRATION_PRINCIPAL_ID" || "$INTEGRATION_PRINCIPAL_ID" == "null" ]]; then
  echo "ERROR: Integration Container App does not have a system-assigned managed identity."
  exit 1
fi

INTEGRATION_RECEIVER_COUNT=$(az role assignment list \
  --assignee-object-id "$INTEGRATION_PRINCIPAL_ID" \
  --all \
  --query "[?roleDefinitionName=='Azure Service Bus Data Receiver'] | length(@)" \
  --output tsv)

INTEGRATION_SENDER_COUNT=$(az role assignment list \
  --assignee-object-id "$INTEGRATION_PRINCIPAL_ID" \
  --all \
  --query "[?roleDefinitionName=='Azure Service Bus Data Sender' && (scope=='$NAMESPACE_SCOPE' || scope=='$TOPIC_SCOPE')] | length(@)" \
  --output tsv)

if [[ "${INTEGRATION_RECEIVER_COUNT:-0}" == "0" ]]; then
  echo "ERROR: Integration managed identity is missing Azure Service Bus Data Receiver."
  echo "INTEGRATION_PRINCIPAL_ID=$INTEGRATION_PRINCIPAL_ID"
  exit 2
fi

if [[ "${INTEGRATION_SENDER_COUNT:-0}" == "0" ]]; then
  echo "ERROR: Integration managed identity is missing Azure Service Bus Data Sender."
  echo "INTEGRATION_PRINCIPAL_ID=$INTEGRATION_PRINCIPAL_ID"
  echo "NAMESPACE_SCOPE=$NAMESPACE_SCOPE"
  echo "TOPIC_SCOPE=$TOPIC_SCOPE"
  exit 2
fi

echo "============================================================"
echo "SUCCESS: REFUND ROLLOUT DEPENDENCIES VERIFIED"
echo "Order, Integration and Notification Container Apps are healthy."
echo "Order managed identity has Service Bus Data Sender."
echo "Integration managed identity has Service Bus Data Receiver and Sender."
echo "No role assignment or Azure resource was created."
echo "============================================================"
