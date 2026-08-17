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
CONFIRM_APIM_WRITE="${CONFIRM_APIM_WRITE:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/admin-operational-investigations/authenticated-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "APIM policy template is missing"
[[ "${CONFIRM_APIM_WRITE,,}" == "true" ]] || fail "Set CONFIRM_APIM_WRITE=true for the controlled APIM write"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
[[ -n "$SUBSCRIPTION_ID" ]] || fail "Azure subscription could not be resolved"

ready_backend() {
  local APP="$1" LABEL="$2" APP_JSON FQDN LATEST READY RUNNING
  APP_JSON=$(az containerapp show -g "$RG" -n "$APP" -o json)
  FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
  LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
  READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
  RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
  [[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "$LABEL is not ready"
  curl --silent --show-error --fail --max-time 30 "https://$FQDN/actuator/health" >/dev/null
  printf 'https://%s' "$FQDN"
}

ORDER_BASE=$(ready_backend "$ORDER_APP" "Order Service")
INTEGRATION_BASE=$(ready_backend "$INTEGRATION_APP" "Integration Service")
ORDER_OPERATION_BASE="${ORDER_BASE}/api/v1/admin/operations"
INTEGRATION_OPERATION_BASE="${INTEGRATION_BASE}/api/v1/admin/operations"

API_MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
GLOBAL_POLICY_URL="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}"

GLOBAL_POLICY=$(az rest --method get --url "$GLOBAL_POLICY_URL" --query properties.value -o tsv 2>/dev/null || true)
[[ "$GLOBAL_POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited global backend-id policy blocks safe operation routing"

mapfile -t PATH_OWNERS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#PATH_OWNERS[@]} <= 1 )) || fail "Multiple APIM APIs own ${API_PATH}"

if (( ${#PATH_OWNERS[@]} == 0 )); then
  az apim api create \
    -g "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --display-name "Craves Admin Operational Investigations" \
    --path "$API_PATH" \
    --service-url "$INTEGRATION_OPERATION_BASE" \
    --protocols https \
    --subscription-required false \
    -o none
else
  [[ "${PATH_OWNERS[0]}" == "$API_ID" ]] || fail "Existing path owner ${PATH_OWNERS[0]} is not the approved API ${API_ID}"
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing API requires a subscription key; this script will not relax it"
fi

API_POLICY=$(az rest --method get --url "${API_MGMT}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv 2>/dev/null || true)
[[ "$API_POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited API backend-id policy blocks safe operation routing"

put_operation() {
  local ID="$1" METHOD="$2" TEMPLATE="$3" DISPLAY="$4" BACKEND="$5"
  local BODY RENDERED POLICY_BODY
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  jq -n \
    --arg display "$DISPLAY" \
    --arg method "$METHOD" \
    --arg template "$TEMPLATE" \
    '{properties:{displayName:$display,method:$method,urlTemplate:$template,templateParameters:[{name:"resourceId",type:"string",required:true}],responses:[{statusCode:200,description:"Audited investigation evidence"},{statusCode:400,description:"Invalid request"},{statusCode:401,description:"Authentication required"},{statusCode:403,description:"ADMIN access required"},{statusCode:404,description:"Resource not found"}]}}' >"$BODY"
  az rest --method put --url "${API_MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${API_MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}

put_operation "get-admin-order-investigation" "GET" "/orders/{resourceId}" "Investigate order" "$ORDER_OPERATION_BASE"
put_operation "get-admin-payment-investigation" "GET" "/payments/{resourceId}" "Investigate payment" "$INTEGRATION_OPERATION_BASE"
put_operation "get-admin-refund-investigation" "GET" "/refunds/{resourceId}" "Investigate refund" "$INTEGRATION_OPERATION_BASE"
put_operation "get-admin-delivery-command-investigation" "GET" "/delivery-commands/{resourceId}" "Investigate delivery command" "$INTEGRATION_OPERATION_BASE"

verify_operation() {
  local ID="$1" EXPECTED_BACKEND="$2" POLICY OPERATION
  OPERATION=$(az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o json)
  [[ "$(jq -r '.method' <<<"$OPERATION")" == "GET" ]] || fail "$ID is not GET"
  POLICY=$(az rest --method get --url "${API_MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$EXPECTED_BACKEND"* ]] || fail "$ID backend read-back failed"
  [[ "$POLICY" == *"Authorization"* && "$POLICY" == *"Bearer"* ]] || fail "$ID Bearer guard is missing"
  [[ "$POLICY" == *"no-store"* && "$POLICY" == *"nosniff"* ]] || fail "$ID response hardening is missing"
  [[ "$POLICY" != *'backend-id='* ]] || fail "$ID unexpectedly uses backend-id"
}

verify_operation "get-admin-order-investigation" "$ORDER_OPERATION_BASE"
verify_operation "get-admin-payment-investigation" "$INTEGRATION_OPERATION_BASE"
verify_operation "get-admin-refund-investigation" "$INTEGRATION_OPERATION_BASE"
verify_operation "get-admin-delivery-command-investigation" "$INTEGRATION_OPERATION_BASE"

GATEWAY_URL=$(az apim show -g "$RG" -n "$APIM" --query gatewayUrl -o tsv)
[[ "$GATEWAY_URL" == https://* ]] || fail "APIM HTTPS gateway URL was not returned"
SMOKE_ID="00000000-0000-4000-8000-000000000001"
HTTP_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 30 "${GATEWAY_URL%/}/${API_PATH}/orders/${SMOKE_ID}")
[[ "$HTTP_STATUS" == "401" ]] || fail "Unauthenticated gateway guard returned HTTP $HTTP_STATUS instead of 401"

echo "SUCCESS: Admin operational investigation APIM operations configured and verified."
