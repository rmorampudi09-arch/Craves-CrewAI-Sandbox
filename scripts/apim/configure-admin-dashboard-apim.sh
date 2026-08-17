#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
API_ID="${API_ID:-craves-admin-dashboard-v1}"
API_PATH="${API_PATH:-api/v1/admin/dashboard}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_WRITE="${CONFIRM_APIM_WRITE:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/admin-dashboard/authenticated-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "APIM policy template is missing"
[[ "${CONFIRM_APIM_WRITE,,}" == "true" ]] || fail "Set CONFIRM_APIM_WRITE=true for the controlled APIM write"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$ORDER_APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Order Service is not ready"
curl --silent --show-error --fail --max-time 30 "https://$FQDN/actuator/health" >/dev/null
BACKEND="https://${FQDN}/api/v1/admin/dashboard"

API_MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
mapfile -t OWNERS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#OWNERS[@]} <= 1 )) || fail "Multiple APIM APIs own ${API_PATH}"
if (( ${#OWNERS[@]} == 0 )); then
  az apim api create -g "$RG" --service-name "$APIM" --api-id "$API_ID" \
    --display-name "Craves Admin Dashboard" --path "$API_PATH" --service-url "$BACKEND" \
    --protocols https --subscription-required false -o none
else
  [[ "${OWNERS[0]}" == "$API_ID" ]] || fail "A different API owns ${API_PATH}"
fi

BODY=$(mktemp)
RENDERED=$(mktemp)
POLICY_BODY=$(mktemp)
trap 'rm -f "$BODY" "$RENDERED" "$POLICY_BODY"' EXIT
jq -n '{properties:{displayName:"Read admin dashboard summary",method:"GET",urlTemplate:"/summary",responses:[{statusCode:200,description:"Operational summary"},{statusCode:401,description:"Authentication required"},{statusCode:403,description:"ADMIN access required"}]}}' >"$BODY"
az rest --method put --url "${API_MGMT}/operations/get-admin-dashboard-summary?api-version=${API_VERSION}" --body @"$BODY" -o none
sed "s|__BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
az rest --method put --url "${API_MGMT}/operations/get-admin-dashboard-summary/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none

POLICY=$(az rest --method get --url "${API_MGMT}/operations/get-admin-dashboard-summary/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
[[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Authorization"* && "$POLICY" == *"no-store"* ]] || fail "Dashboard policy read-back failed"
GATEWAY_URL=$(az apim show -g "$RG" -n "$APIM" --query gatewayUrl -o tsv)
HTTP_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 30 "${GATEWAY_URL%/}/${API_PATH}/summary")
[[ "$HTTP_STATUS" == "401" ]] || fail "Unauthenticated gateway guard returned HTTP $HTTP_STATUS instead of 401"
echo "SUCCESS: Admin dashboard APIM operation configured and verified."
