#!/usr/bin/env bash
set -euo pipefail
set +x
RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
API_PATH="api/v1/chef/orders"
API_ID_DEFAULT="craves-chef-orders-v1"
API_VERSION="${API_VERSION:-2022-08-01}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/chef-orders/chef-orders-policy.xml"
fail(){ echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Order Service is not ready"
curl -sS --fail --max-time 30 "https://$FQDN/actuator/health" >/dev/null
mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} <= 1 )) || fail "Multiple APIM APIs own $API_PATH"
if (( ${#API_IDS[@]} == 0 )); then API_ID="$API_ID_DEFAULT"; az apim api create -g "$RG" --service-name "$APIM" --api-id "$API_ID" --display-name "Craves Chef Orders API" --path "$API_PATH" --protocols https --subscription-required false -o none; else API_ID="${API_IDS[0]}"; fi
SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
[[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing Chef Orders API requires a subscription key"
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
for SCOPE_URL in "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}" "${MGMT}/policies/policy?api-version=${API_VERSION}"; do POLICY=$(az rest --method get --url "$SCOPE_URL" --query properties.value -o tsv 2>/dev/null || true); [[ "$POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited backend-id policy cannot be safely overridden"; done
BACKEND="https://${FQDN}/api/v1/chef/orders"
put_operation(){
  local ID="$1" METHOD="$2" TEMPLATE="$3" DISPLAY="$4" PARAMS="$5"; local BODY RENDERED POLICY_BODY
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  printf '%s' "{\"properties\":{\"displayName\":\"$DISPLAY\",\"method\":\"$METHOD\",\"urlTemplate\":\"$TEMPLATE\",\"templateParameters\":$PARAMS,\"responses\":[{\"statusCode\":200,\"description\":\"Chef order response\"},{\"statusCode\":401,\"description\":\"Authentication required\"},{\"statusCode\":403,\"description\":\"Chef role required\"}]}}" >"$BODY"
  az rest --method put --url "${MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__CHEF_ORDERS_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}
put_operation "list-my-chef-orders" "GET" "/" "List my chef orders" '[]'
put_operation "get-my-chef-order" "GET" "/{orderId}" "Get my chef order" '[{"name":"orderId","type":"string","required":true}]'
for ID in list-my-chef-orders get-my-chef-order; do az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o none; done
echo "SUCCESS: Chef order read operations configured on API $API_ID."
