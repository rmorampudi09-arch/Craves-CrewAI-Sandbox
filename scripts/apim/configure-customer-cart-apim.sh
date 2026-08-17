#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
API_PATH="${API_PATH:-api/v1/cart}"
NEW_API_ID="${NEW_API_ID:-craves-customer-cart-v1}"
API_VERSION="${API_VERSION:-2022-08-01}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/customer-cart/customer-cart-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "Policy template is missing"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$ORDER_APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Order Service is not ready"
curl -sS --fail --max-time 30 "https://$FQDN/actuator/health" >/dev/null

mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} <= 1 )) || fail "Multiple APIM APIs own $API_PATH"
BACKEND="https://${FQDN}/api/v1/cart"
if (( ${#API_IDS[@]} == 0 )); then
  az apim api create -g "$RG" --service-name "$APIM" --api-id "$NEW_API_ID" --display-name "Craves Customer Cart API" --path "$API_PATH" --service-url "$BACKEND" --protocols https --subscription-required false -o none
  API_ID="$NEW_API_ID"
else
  API_ID="${API_IDS[0]}"
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing cart API requires a subscription key; this script will not relax it"
fi

MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
for SCOPE_URL in "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}" "${MGMT}/policies/policy?api-version=${API_VERSION}"; do
  POLICY=$(az rest --method get --url "$SCOPE_URL" --query properties.value -o tsv 2>/dev/null || true)
  [[ "$POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited backend-id policy cannot be safely overridden"
done

put_operation() {
  local ID="$1" METHOD="$2" TEMPLATE="$3" DISPLAY="$4" PARAMS="$5"
  local BODY RENDERED POLICY_BODY
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  cat >"$BODY" <<JSON
{"properties":{"displayName":"$DISPLAY","method":"$METHOD","urlTemplate":"$TEMPLATE","templateParameters":$PARAMS,"responses":[{"statusCode":200,"description":"Customer cart response"},{"statusCode":401,"description":"Authentication required"},{"statusCode":400,"description":"Cart validation failed"}]}}
JSON
  az rest --method put --url "${MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__CART_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}

ITEM_PARAM='[{"name":"cartItemId","type":"string","required":true}]'
put_operation "get-customer-cart" "GET" "/" "Get customer cart" '[]'
put_operation "clear-customer-cart" "DELETE" "/" "Clear customer cart" '[]'
put_operation "add-customer-cart-item" "POST" "/items" "Add customer cart item" '[]'
put_operation "update-customer-cart-item" "PUT" "/items/{cartItemId}" "Update customer cart item" "$ITEM_PARAM"
put_operation "remove-customer-cart-item" "DELETE" "/items/{cartItemId}" "Remove customer cart item" "$ITEM_PARAM"
put_operation "validate-customer-cart" "POST" "/validate" "Validate customer cart" '[]'

for ID in get-customer-cart clear-customer-cart add-customer-cart-item update-customer-cart-item remove-customer-cart-item validate-customer-cart; do
  az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o none
  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Authorization"* && "$POLICY" == *"no-store"* ]] || fail "Operation $ID policy verification failed"
done

echo "SUCCESS: Customer cart operations configured on APIM API $API_ID."
