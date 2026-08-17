#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
API_ID="${API_ID:-}"
API_PATH="${API_PATH:-api/v1/orders}"
OPERATION_ID="${OPERATION_ID:-get-order-delivery-status}"
API_VERSION="${API_VERSION:-2022-08-01}"
TEST_ORDER_ID="${TEST_ORDER_ID:-}"
CRAVES_ACCESS_TOKEN="${CRAVES_ACCESS_TOKEN:-}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

read_policy_value() {
  local url="$1"
  az rest \
    --method get \
    --url "$url" \
    --query properties.value \
    -o tsv 2>/dev/null || true
}

reject_incompatible_backend_inheritance() {
  local scope_name="$1"
  local policy_value="$2"
  if grep -Eqi '<set-backend-service[^>]+backend-id=' <<<"$policy_value"; then
    fail "$scope_name contains an inherited set-backend-service backend-id policy that is incompatible with this operation base-url override."
  fi
}

for command_name in az curl jq grep; do
  command -v "$command_name" >/dev/null 2>&1 || fail "Required command is not installed: $command_name"
done

[[ "$API_PATH" =~ ^[A-Za-z0-9/_-]+$ ]] || fail "API_PATH contains unsupported characters."
[[ -z "$API_ID" || "$API_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "API_ID contains unsupported characters."
[[ "$OPERATION_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "OPERATION_ID contains unsupported characters."

SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
[[ -n "$SUBSCRIPTION_ID" ]] || fail "No active Azure subscription is selected."

APP_JSON="$(az containerapp show \
  --resource-group "$RG" \
  --name "$ORDER_APP" \
  -o json)"
ORDER_FQDN="$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")"
LATEST_REVISION="$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")"
READY_REVISION="$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")"
RUNNING_STATUS="$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")"
CONSUMER_ENV_COUNT="$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED")] | length' <<<"$APP_JSON")"

[[ -n "$ORDER_FQDN" ]] || fail "Order Container App FQDN could not be resolved."
[[ "$LATEST_REVISION" == "$READY_REVISION" ]] || fail "Order latest revision is not ready."
[[ "$RUNNING_STATUS" == "Running" ]] || fail "Order Container App is not running."
[[ "$CONSUMER_ENV_COUNT" == "1" ]] || fail "Order delivery-status deployment marker is absent."

REVISION_HEALTH="$(az containerapp revision show \
  --resource-group "$RG" \
  --name "$ORDER_APP" \
  --revision "$LATEST_REVISION" \
  --query properties.healthState \
  -o tsv 2>/dev/null || true)"
[[ "$REVISION_HEALTH" == "Healthy" ]] || fail "Order latest revision is not healthy."

ORDER_BASE_URL="https://${ORDER_FQDN}"
ORDER_DELIVERY_STATUS_BACKEND_URL="${ORDER_BASE_URL}/api/v1/orders"
curl -sS --fail --max-time 30 "${ORDER_BASE_URL}/actuator/health" >/dev/null

if [[ -z "$API_ID" ]]; then
  mapfile -t PATH_API_IDS < <(az apim api list \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --query "[?path=='${API_PATH}'].name" \
    -o tsv)
  (( ${#PATH_API_IDS[@]} == 1 )) || fail "Expected exactly one APIM API at path $API_PATH; found ${#PATH_API_IDS[@]}."
  API_ID="${PATH_API_IDS[0]}"
fi

API_JSON="$(az apim api show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  -o json)"
[[ "$(jq -r '.path // ""' <<<"$API_JSON")" == "$API_PATH" ]] || fail "APIM API path does not match $API_PATH."
[[ "$(jq -r '.subscriptionRequired' <<<"$API_JSON")" == "false" ]] || fail "APIM API unexpectedly requires a subscription key."

SERVICE_MGMT_BASE="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}"
MGMT_BASE="${SERVICE_MGMT_BASE}/apis"
GLOBAL_POLICY_VALUE="$(read_policy_value "${SERVICE_MGMT_BASE}/policies/policy?api-version=${API_VERSION}")"
API_POLICY_VALUE="$(read_policy_value "${MGMT_BASE}/${API_ID}/policies/policy?api-version=${API_VERSION}")"
reject_incompatible_backend_inheritance "APIM global policy" "$GLOBAL_POLICY_VALUE"
reject_incompatible_backend_inheritance "API $API_ID policy" "$API_POLICY_VALUE"

OPERATION_JSON="$(az apim api operation show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  --operation-id "$OPERATION_ID" \
  -o json)"
[[ "$(jq -r '.method // ""' <<<"$OPERATION_JSON")" == "GET" ]] || fail "Delivery-status APIM operation is not GET."
[[ "$(jq -r '.urlTemplate // ""' <<<"$OPERATION_JSON")" == "/{orderId}/delivery-status" ]] || fail "Delivery-status APIM URL template is incorrect."
[[ "$(jq -r '[.templateParameters[]? | select(.name == "orderId" and .required == true)] | length' <<<"$OPERATION_JSON")" == "1" ]] || fail "Required orderId path parameter is missing."

POLICY_VALUE="$(az rest \
  --method get \
  --url "${MGMT_BASE}/${API_ID}/operations/${OPERATION_ID}/policies/policy?api-version=${API_VERSION}" \
  --query properties.value \
  -o tsv)"

[[ "$POLICY_VALUE" == *"Authorization"* ]] || fail "Authorization header enforcement is missing from the operation policy."
[[ "$POLICY_VALUE" == *"Bearer "* ]] || fail "Bearer syntax enforcement is missing from the operation policy."
[[ "$POLICY_VALUE" == *"${ORDER_DELIVERY_STATUS_BACKEND_URL}"* ]] || fail "Operation backend target does not match the Order Container App."
[[ "$POLICY_VALUE" == *"no-store"* ]] || fail "Delivery response caching is not disabled."
[[ "$POLICY_VALUE" != *"allow-credentials=\"true\""* ]] || fail "Unexpected permissive credential CORS policy detected."

GATEWAY_URL="$(az apim show \
  --resource-group "$RG" \
  --name "$APIM" \
  --query gatewayUrl \
  -o tsv)"
[[ -n "$GATEWAY_URL" ]] || fail "APIM gateway URL could not be resolved."

UNAUTH_URL="${GATEWAY_URL%/}/${API_PATH}/00000000-0000-0000-0000-000000000000/delivery-status"
UNAUTH_BODY="$(mktemp)"
cleanup() {
  rm -f "$UNAUTH_BODY"
}
trap cleanup EXIT

UNAUTH_CODE=""
for attempt in $(seq 1 18); do
  UNAUTH_CODE="$(curl -sS \
    --max-time 30 \
    --output "$UNAUTH_BODY" \
    --write-out '%{http_code}' \
    "$UNAUTH_URL" || true)"
  if [[ "$UNAUTH_CODE" == "401" ]] && grep -q 'AUTHENTICATION_REQUIRED' "$UNAUTH_BODY"; then
    break
  fi
  if (( attempt == 18 )); then
    echo "Last unauthenticated response code: ${UNAUTH_CODE:-<empty>}" >&2
    sed -n '1,20p' "$UNAUTH_BODY" >&2 || true
    fail "APIM delivery-status policy did not become active with the expected 401 response."
  fi
  sleep 10
done

if [[ -n "$CRAVES_ACCESS_TOKEN" || -n "$TEST_ORDER_ID" ]]; then
  [[ -n "$CRAVES_ACCESS_TOKEN" && -n "$TEST_ORDER_ID" ]] || fail "CRAVES_ACCESS_TOKEN and TEST_ORDER_ID must be supplied together for an authenticated smoke test."
  [[ "$TEST_ORDER_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "TEST_ORDER_ID is not a UUID."

  AUTH_BODY="$(mktemp)"
  AUTH_CODE="$(curl -sS \
    --max-time 30 \
    --output "$AUTH_BODY" \
    --write-out '%{http_code}' \
    -H "Authorization: Bearer ${CRAVES_ACCESS_TOKEN}" \
    "${GATEWAY_URL%/}/${API_PATH}/${TEST_ORDER_ID}/delivery-status" || true)"

  if [[ "$AUTH_CODE" != "200" ]]; then
    echo "Authenticated smoke response code: $AUTH_CODE" >&2
    sed -n '1,20p' "$AUTH_BODY" >&2 || true
    rm -f "$AUTH_BODY"
    fail "Authenticated delivery-status smoke test failed."
  fi

  jq -e --arg id "$TEST_ORDER_ID" '
    .orderId == $id
    and (.history | type == "array")
    and ((.deliveryJobId == null) or (.deliveryJobId | type == "string"))
    and ((.providerId == null) or (.providerId | type == "string"))
    and ((.status == null) or (.status | type == "string"))
    and ((.trackingUrl == null) or (.trackingUrl | type == "string"))
  ' "$AUTH_BODY" >/dev/null \
    || { rm -f "$AUTH_BODY"; fail "Authenticated delivery-status response does not match the public contract."; }
  rm -f "$AUTH_BODY"
  echo "Authenticated customer delivery-status smoke test passed."
else
  echo "Authenticated smoke test skipped because no token/order pair was supplied."
fi

echo
echo "========== Delivery Status APIM Verification =========="
echo "API ID:       $API_ID"
echo "Public URL:   ${GATEWAY_URL%/}/${API_PATH}/{orderId}/delivery-status"
echo "Order revision: $LATEST_REVISION"
echo "Unauthenticated guard: HTTP 401 verified"
echo
echo "SUCCESS: Delivery-status APIM route, policy, backend mapping, inherited-policy compatibility, and no-cache controls are valid."
