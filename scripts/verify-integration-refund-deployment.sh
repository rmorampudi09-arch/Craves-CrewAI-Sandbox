#!/usr/bin/env bash
set -euo pipefail

RG="${RG:-rg-craves-prodlow-centralindia}"
APP="${APP:-ca-craves-integration-service-pr}"
SB_NAMESPACE="${SB_NAMESPACE:-sb-craves-prodlow-l3ing6}"
SB_TOPIC="${SB_TOPIC:-craves-domain-events}"
REFUND_SUBSCRIPTION="${REFUND_SUBSCRIPTION:-integration-service-refund-requested}"
EXPECTED_IMAGE_PART="craves/integration-service"

echo ""
echo "============================================================"
echo "VERIFY INTEGRATION REFUND DEPLOYMENT"
echo "============================================================"

APP_JSON=$(az containerapp show \
  --resource-group "$RG" \
  --name "$APP" \
  -o json)

LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
IMAGE=$(jq -r '.properties.template.containers[0].image // ""' <<<"$APP_JSON")
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")

printf '%-24s %s\n' "Running status:" "$RUNNING"
printf '%-24s %s\n' "Latest revision:" "$LATEST"
printf '%-24s %s\n' "Ready revision:" "$READY"
printf '%-24s %s\n' "Image:" "$IMAGE"

if [[ "$RUNNING" != "Running" ]]; then
  echo "ERROR: Integration Service is not Running."
  exit 1
fi

if [[ -z "$LATEST" || "$LATEST" != "$READY" ]]; then
  echo "ERROR: Latest revision is not the latest ready revision."
  exit 1
fi

if [[ "$IMAGE" != *"$EXPECTED_IMAGE_PART"* ]]; then
  echo "ERROR: Unexpected Integration Service image."
  exit 1
fi

HEALTH=$(az containerapp revision show \
  --resource-group "$RG" \
  --name "$APP" \
  --revision "$LATEST" \
  --query properties.healthState \
  -o tsv 2>/dev/null || true)

printf '%-24s %s\n' "Revision health:" "${HEALTH:-unknown}"
if [[ "$HEALTH" != "Healthy" ]]; then
  echo "ERROR: Latest Integration Service revision is not Healthy."
  exit 1
fi

if [[ -z "$FQDN" ]]; then
  echo "ERROR: Integration Service FQDN is missing."
  exit 1
fi

echo ""
echo "==================== HEALTH ENDPOINT ===================="
curl -fsS --max-time 30 "https://$FQDN/actuator/health"
echo ""

env_value() {
  local name="$1"
  jq -r --arg name "$name" '
    .properties.template.containers[0].env[]?
    | select(.name == $name)
    | (.value // "")
  ' <<<"$APP_JSON" | head -n 1
}

assert_disabled() {
  local name="$1"
  local value
  value=$(env_value "$name")

  if [[ -z "$value" ]]; then
    echo "OK: $name is absent; application default is false."
    return
  fi

  if [[ "${value,,}" == "false" ]]; then
    echo "OK: $name=false"
    return
  fi

  echo "ERROR: $name is '$value'; expected false or absent."
  exit 1
}

echo ""
echo "==================== SAFETY SWITCHES ===================="
assert_disabled "CRAVES_REFUND_CONSUMER_ENABLED"
assert_disabled "CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED"
assert_disabled "CRAVES_REFUND_RECONCILIATION_ENABLED"
assert_disabled "CRAVES_REFUND_STATUS_PUBLISHER_ENABLED"
assert_disabled "CRAVES_DELIVERY_COMMAND_ENABLED"
assert_disabled "BORZO_API_ENABLED"

echo ""
echo "==================== FLYWAY EVIDENCE ===================="
LOG_MATCHES=$(az containerapp logs show \
  --resource-group "$RG" \
  --name "$APP" \
  --revision "$LATEST" \
  --type console \
  --tail 500 2>/dev/null \
  | grep -Ei "V100|refund_workflow_foundation|Successfully applied.*100|Schema.*up to date" \
  || true)

if [[ -n "$LOG_MATCHES" ]]; then
  printf '%s\n' "$LOG_MATCHES"
else
  echo "No V100 line remains in the retained log window."
  echo "The healthy deployment proves Flyway did not block startup; use DB inspection only if stronger evidence is required."
fi

echo ""
echo "==================== REFUND SUBSCRIPTION ===================="
if az servicebus topic subscription show \
  --resource-group "$RG" \
  --namespace-name "$SB_NAMESPACE" \
  --topic-name "$SB_TOPIC" \
  --name "$REFUND_SUBSCRIPTION" \
  >/dev/null 2>&1; then

  az servicebus topic subscription show \
    --resource-group "$RG" \
    --namespace-name "$SB_NAMESPACE" \
    --topic-name "$SB_TOPIC" \
    --name "$REFUND_SUBSCRIPTION" \
    --query "{name:name,active:countDetails.activeMessageCount,deadLetter:countDetails.deadLetterMessageCount,maxDeliveryCount:maxDeliveryCount}" \
    -o table
else
  echo "Refund subscription does not exist yet. This is expected before the controlled consumer-enablement pipeline."
fi

echo ""
echo "============================================================"
echo "SUCCESS: Refund code is deployed and all execution paths remain disabled."
echo "No secrets were printed."
echo "============================================================"
