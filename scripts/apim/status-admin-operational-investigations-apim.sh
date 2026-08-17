#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
INTEGRATION_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
API_ID="${API_ID:-craves-admin-operational-investigations-v1}"
API_PATH="${API_PATH:-api/v1/admin/operations}"
API_VERSION="${API_VERSION:-2022-08-01}"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl; do command -v "$tool" >/dev/null || fail "$tool is required"; done
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
API_MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"

api_json=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json)
[[ "$(jq -r '.path' <<<"$api_json")" == "$API_PATH" ]] || fail "API path mismatch"
[[ "$(jq -r '.subscriptionRequired' <<<"$api_json")" == "false" ]] || fail "API unexpectedly requires a subscription key"

backend_url() {
  local APP="$1" APP_JSON FQDN LATEST READY RUNNING
  APP_JSON=$(az containerapp show -g "$RG" -n "$APP" -o json)
  FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
  LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
  READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
  RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
  [[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "$APP is not ready"
  curl --silent --show-error --fail --max-time 30 "https://$FQDN/actuator/health" >/dev/null
  printf 'https://%s/api/v1/admin/operations' "$FQDN"
}

ORDER_BACKEND=$(backend_url "$ORDER_APP")
INTEGRATION_BACKEND=$(backend_url "$INTEGRATION_APP")

check_operation() {
  local ID="$1" TEMPLATE="$2" EXPECTED_BACKEND="$3" OP POLICY
  OP=$(az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o json)
  [[ "$(jq -r '.method' <<<"$OP")" == "GET" ]] || fail "$ID method mismatch"
  [[ "$(jq -r '.urlTemplate' <<<"$OP")" == "$TEMPLATE" ]] || fail "$ID URL template mismatch"
  POLICY=$(az rest --method get --url "${API_MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$EXPECTED_BACKEND"* ]] || fail "$ID backend mismatch"
  [[ "$POLICY" == *"Authorization"* && "$POLICY" == *"Bearer"* ]] || fail "$ID authentication guard missing"
  [[ "$POLICY" == *"no-store"* && "$POLICY" == *"nosniff"* && "$POLICY" == *"DENY"* ]] || fail "$ID hardening headers missing"
  [[ "$POLICY" != *'backend-id='* ]] || fail "$ID contains an unsafe backend-id override"
}

check_operation "get-admin-order-investigation" "/orders/{resourceId}" "$ORDER_BACKEND"
check_operation "get-admin-payment-investigation" "/payments/{resourceId}" "$INTEGRATION_BACKEND"
check_operation "get-admin-refund-investigation" "/refunds/{resourceId}" "$INTEGRATION_BACKEND"
check_operation "get-admin-delivery-command-investigation" "/delivery-commands/{resourceId}" "$INTEGRATION_BACKEND"

GATEWAY_URL=$(az apim show -g "$RG" -n "$APIM" --query gatewayUrl -o tsv)
SMOKE_ID="00000000-0000-4000-8000-000000000001"
for RESOURCE in orders payments refunds delivery-commands; do
  CODE=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 30 "${GATEWAY_URL%/}/${API_PATH}/${RESOURCE}/${SMOKE_ID}")
  [[ "$CODE" == "401" ]] || fail "$RESOURCE unauthenticated guard returned HTTP $CODE"
done

az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query "[].{id:name,method:method,path:urlTemplate}" -o table

echo "SUCCESS: Admin operational investigation APIM status is healthy."
