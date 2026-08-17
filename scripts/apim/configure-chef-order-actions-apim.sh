#!/usr/bin/env bash
set -euo pipefail
set +x
RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
API_PATH="api/v1/chef/orders"
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
(( ${#API_IDS[@]} == 1 )) || fail "Expected exactly one Chef Orders API; run chef-order-read APIM first"
API_ID="${API_IDS[0]}"
SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
[[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing Chef Orders API requires a subscription key"
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
BACKEND="https://${FQDN}/api/v1/chef/orders"
put_operation(){
  local ID="$1" TEMPLATE="$2" DISPLAY="$3"; local BODY RENDERED POLICY_BODY
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  printf '%s' "{\"properties\":{\"displayName\":\"$DISPLAY\",\"method\":\"POST\",\"urlTemplate\":\"$TEMPLATE\",\"templateParameters\":[{\"name\":\"orderId\",\"type\":\"string\",\"required\":true}],\"responses\":[{\"statusCode\":200,\"description\":\"Chef order response\"},{\"statusCode\":400,\"description\":\"Invalid action\"},{\"statusCode\":401,\"description\":\"Authentication required\"},{\"statusCode\":409,\"description\":\"Order transition conflict\"}]}}" >"$BODY"
  az rest --method put --url "${MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__CHEF_ORDERS_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}
put_operation "accept-my-chef-order" "/{orderId}/accept" "Accept my chef order"
put_operation "reject-my-chef-order" "/{orderId}/reject" "Reject my chef order"
put_operation "ready-my-chef-order" "/{orderId}/ready-for-pickup" "Mark my chef order ready for pickup"
for ID in accept-my-chef-order reject-my-chef-order ready-my-chef-order; do az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o none; done
echo "SUCCESS: Chef order action operations configured on API $API_ID."
