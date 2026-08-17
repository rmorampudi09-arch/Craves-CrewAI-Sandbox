#!/usr/bin/env bash

# Configures the existing Order Container App to publish transactional outbox
# records to the existing craves-domain-events Service Bus topic.
#
# This script does not create Azure resources, role assignments, topics, or
# secrets. It validates prerequisites and updates only Container App runtime
# environment variables.

RG="${RG:-rg-craves-prodlow-centralindia}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
SERVICE_BUS_NAMESPACE="${SERVICE_BUS_NAMESPACE:-}"
DOMAIN_EVENTS_TOPIC="${DOMAIN_EVENTS_TOPIC:-craves-domain-events}"

if [ -z "$SERVICE_BUS_NAMESPACE" ]; then
  echo "ERROR: Set SERVICE_BUS_NAMESPACE to the fully qualified namespace, for example namespace.servicebus.windows.net."
  exit 1
fi

PRINCIPAL_ID=$(az containerapp identity show \
  --resource-group "$RG" \
  --name "$ORDER_APP" \
  --query principalId \
  -o tsv 2>/dev/null)

if [ -z "$PRINCIPAL_ID" ] || [ "$PRINCIPAL_ID" = "null" ]; then
  echo "ERROR: Order Container App does not have a system-assigned managed identity."
  exit 1
fi

NAMESPACE_NAME="${SERVICE_BUS_NAMESPACE%%.*}"
TOPIC_ID=$(az servicebus topic show \
  --resource-group "$RG" \
  --namespace-name "$NAMESPACE_NAME" \
  --name "$DOMAIN_EVENTS_TOPIC" \
  --query id \
  -o tsv 2>/dev/null)

if [ -z "$TOPIC_ID" ]; then
  echo "ERROR: Existing Service Bus topic '$DOMAIN_EVENTS_TOPIC' was not found in namespace '$NAMESPACE_NAME'."
  exit 1
fi

ROLE_COUNT=$(az role assignment list \
  --assignee-object-id "$PRINCIPAL_ID" \
  --scope "$TOPIC_ID" \
  --query "[?roleDefinitionName=='Azure Service Bus Data Sender'] | length(@)" \
  -o tsv 2>/dev/null)

if [ "${ROLE_COUNT:-0}" = "0" ]; then
  NAMESPACE_ID=$(az servicebus namespace show \
    --resource-group "$RG" \
    --name "$NAMESPACE_NAME" \
    --query id \
    -o tsv)

  ROLE_COUNT=$(az role assignment list \
    --assignee-object-id "$PRINCIPAL_ID" \
    --scope "$NAMESPACE_ID" \
    --query "[?roleDefinitionName=='Azure Service Bus Data Sender'] | length(@)" \
    -o tsv 2>/dev/null)
fi

if [ "${ROLE_COUNT:-0}" = "0" ]; then
  echo "ERROR: Order managed identity does not have Azure Service Bus Data Sender on the topic or namespace."
  echo "Assign the role manually, wait for Azure RBAC propagation, and rerun this script."
  exit 1
fi

az containerapp update \
  --resource-group "$RG" \
  --name "$ORDER_APP" \
  --set-env-vars \
    CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED=true \
    CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED=true \
    CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE="$SERVICE_BUS_NAMESPACE" \
    CRAVES_DOMAIN_EVENTS_TOPIC_NAME="$DOMAIN_EVENTS_TOPIC" \
  --only-show-errors \
  -o none

LATEST_REVISION=$(az containerapp show \
  --resource-group "$RG" \
  --name "$ORDER_APP" \
  --query properties.latestRevisionName \
  -o tsv)

for attempt in $(seq 1 36); do
  READY=$(az containerapp show \
    --resource-group "$RG" \
    --name "$ORDER_APP" \
    --query properties.latestReadyRevisionName \
    -o tsv)

  HEALTH=$(az containerapp revision show \
    --resource-group "$RG" \
    --name "$ORDER_APP" \
    --revision "$LATEST_REVISION" \
    --query properties.healthState \
    -o tsv 2>/dev/null)

  RUNNING=$(az containerapp revision show \
    --resource-group "$RG" \
    --name "$ORDER_APP" \
    --revision "$LATEST_REVISION" \
    --query properties.runningState \
    -o tsv 2>/dev/null)

  echo "Attempt $attempt/36: latest=$LATEST_REVISION ready=$READY health=$HEALTH running=$RUNNING"

  if [ "$LATEST_REVISION" = "$READY" ] && [ "$HEALTH" = "Healthy" ] && [[ "$RUNNING" == Running* ]]; then
    az containerapp show \
      --resource-group "$RG" \
      --name "$ORDER_APP" \
      --query "properties.template.containers[0].env[?starts_with(name, 'CRAVES_DOMAIN_EVENT') || name=='CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE' || name=='CRAVES_DOMAIN_EVENTS_TOPIC_NAME'].{name:name,value:value,secretRef:secretRef}" \
      -o table
    exit 0
  fi

  sleep 10
done

echo "ERROR: Timed out waiting for a healthy Order Service revision."
exit 1
