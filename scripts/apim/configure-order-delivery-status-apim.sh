#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
API_ID="${API_ID:-}"
DEFAULT_API_ID="${DEFAULT_API_ID:-craves-order-customer-v1}"
API_PATH="${API_PATH:-api/v1/orders}"
OPERATION_ID="${OPERATION_ID:-get-order-delivery-status}"
API_VERSION="${API_VERSION:-2022-08-01}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POLICY_TEMPLATE="${POLICY_TEMPLATE:-$REPO_ROOT/infra/apim/order-delivery-status/order-delivery-status-policy.xml}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command is not installed: $1"
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
    fail "$scope_name contains an inherited set-backend-service backend-id policy. This module will not override it with base-url; use an approved APIM backend entity design instead."
  fi
}

require_command az
require_command curl
require_command jq
require_command sed
require_command grep

[[ -f "$POLICY_TEMPLATE" ]] || fail "APIM policy template not found: $POLICY_TEMPLATE"
[[ "$API_PATH" =~ ^[A-Za-z0-9/_-]+$ ]] || fail "API_PATH contains unsupported characters."
[[ "$DEFAULT_API_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "DEFAULT_API_ID contains unsupported characters."
[[ -z "$API_ID" || "$API_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "API_ID contains unsupported characters."
[[ "$OPERATION_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "OPERATION_ID contains unsupported characters."

SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
[[ -n "$SUBSCRIPTION_ID" ]] || fail "No active Azure subscription is selected."

az apim show \
  --resource-group "$RG" \
  --name "$APIM" \
  -o none

APP_JSON="$(az containerapp show \
  --resource-group "$RG" \
  --name "$ORDER_APP" \
  -o json)"

ORDER_FQDN="$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")"
LATEST_REVISION="$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")"
READY_REVISION="$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")"
RUNNING_STATUS="$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")"
DELIVERY_STATUS_ENV_COUNT="$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED")] | length' <<<"$APP_JSON")"

[[ -n "$ORDER_FQDN" ]] || fail "Order Container App ingress FQDN could not be resolved."
[[ -n "$LATEST_REVISION" ]] || fail "Order Container App has no latest revision."
[[ "$LATEST_REVISION" == "$READY_REVISION" ]] || fail "Order latest revision is not the latest ready revision."
[[ "$RUNNING_STATUS" == "Running" ]] || fail "Order Container App is not running. Current state: ${RUNNING_STATUS:-<empty>}"
[[ "$DELIVERY_STATUS_ENV_COUNT" == "1" ]] || fail "Order delivery-status code has not been deployed yet; CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED is absent."

REVISION_HEALTH="$(az containerapp revision show \
  --resource-group "$RG" \
  --name "$ORDER_APP" \
  --revision "$LATEST_REVISION" \
  --query properties.healthState \
  -o tsv 2>/dev/null || true)"
[[ "$REVISION_HEALTH" == "Healthy" ]] || fail "Order latest revision is not healthy. Current health: ${REVISION_HEALTH:-<empty>}"

ORDER_BASE_URL="https://${ORDER_FQDN}"
ORDER_DELIVERY_STATUS_BACKEND_URL="${ORDER_BASE_URL}/api/v1/orders"

curl -sS --fail --max-time 30 "${ORDER_BASE_URL}/actuator/health" >/dev/null

echo "Order backend health verified."
echo "Order revision: $LATEST_REVISION"

SERVICE_MGMT_BASE="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}"
MGMT_BASE="${SERVICE_MGMT_BASE}/apis"

mapfile -t PATH_API_IDS < <(az apim api list \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --query "[?path=='${API_PATH}'].name" \
  -o tsv)

if (( ${#PATH_API_IDS[@]} > 1 )); then
  printf 'Conflicting APIM APIs at path %s:\n' "$API_PATH" >&2
  printf '  %s\n' "${PATH_API_IDS[@]}" >&2
  fail "Multiple APIs already use the intended public path. No APIM change was made."
fi

if [[ -n "$API_ID" ]]; then
  if (( ${#PATH_API_IDS[@]} == 1 )) && [[ "${PATH_API_IDS[0]}" != "$API_ID" ]]; then
    fail "Public path $API_PATH is already owned by API ${PATH_API_IDS[0]}, not requested API $API_ID."
  fi
else
  if (( ${#PATH_API_IDS[@]} == 1 )); then
    API_ID="${PATH_API_IDS[0]}"
  else
    API_ID="$DEFAULT_API_ID"
  fi
fi

API_EXISTS=false
if az apim api show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  >/dev/null 2>&1; then
  API_EXISTS=true
  EXISTING_PATH="$(az apim api show \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --query path \
    -o tsv)"
  [[ "$EXISTING_PATH" == "$API_PATH" ]] || fail "API $API_ID exists at path $EXISTING_PATH, not $API_PATH."

  SUBSCRIPTION_REQUIRED="$(az apim api show \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --query subscriptionRequired \
    -o tsv)"
  [[ "${SUBSCRIPTION_REQUIRED,,}" == "false" ]] || fail "API $API_ID requires an APIM subscription key. This script will not relax an existing API-wide security setting."
else
  az apim api create \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --display-name "Craves Customer Orders API" \
    --path "$API_PATH" \
    --service-url "$ORDER_BASE_URL" \
    --protocols https \
    --subscription-required false \
    -o none
fi

GLOBAL_POLICY_VALUE="$(read_policy_value "${SERVICE_MGMT_BASE}/policies/policy?api-version=${API_VERSION}")"
API_POLICY_VALUE="$(read_policy_value "${MGMT_BASE}/${API_ID}/policies/policy?api-version=${API_VERSION}")"
reject_incompatible_backend_inheritance "APIM global policy" "$GLOBAL_POLICY_VALUE"
reject_incompatible_backend_inheritance "API $API_ID policy" "$API_POLICY_VALUE"

OPERATION_BODY="$(mktemp)"
RENDERED_POLICY="$(mktemp)"
POLICY_BODY="$(mktemp)"
cleanup() {
  rm -f "$OPERATION_BODY" "$RENDERED_POLICY" "$POLICY_BODY"
}
trap cleanup EXIT

cat >"$OPERATION_BODY" <<'JSON'
{
  "properties": {
    "displayName": "Get customer order delivery status",
    "method": "GET",
    "urlTemplate": "/{orderId}/delivery-status",
    "templateParameters": [
      {
        "name": "orderId",
        "type": "string",
        "required": true
      }
    ],
    "responses": [
      { "statusCode": 200, "description": "Current provider-neutral delivery status" },
      { "statusCode": 401, "description": "Authentication required" },
      { "statusCode": 403, "description": "Customer role is required" },
      { "statusCode": 404, "description": "Owned order was not found" }
    ]
  }
}
JSON

az rest \
  --method put \
  --url "${MGMT_BASE}/${API_ID}/operations/${OPERATION_ID}?api-version=${API_VERSION}" \
  --body @"$OPERATION_BODY" \
  -o none

sed \
  "s|__ORDER_DELIVERY_STATUS_BACKEND_URL__|${ORDER_DELIVERY_STATUS_BACKEND_URL}|g" \
  "$POLICY_TEMPLATE" >"$RENDERED_POLICY"

if grep -q '__ORDER_DELIVERY_STATUS_BACKEND_URL__' "$RENDERED_POLICY"; then
  fail "The backend URL placeholder was not fully rendered."
fi

jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED_POLICY" >"$POLICY_BODY"

az rest \
  --method put \
  --url "${MGMT_BASE}/${API_ID}/operations/${OPERATION_ID}/policies/policy?api-version=${API_VERSION}" \
  --body @"$POLICY_BODY" \
  -o none

CONFIGURED_OPERATION="$(az apim api operation show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  --operation-id "$OPERATION_ID" \
  -o json)"

[[ "$(jq -r '.method // ""' <<<"$CONFIGURED_OPERATION")" == "GET" ]] || fail "Configured APIM operation method is not GET."
[[ "$(jq -r '.urlTemplate // ""' <<<"$CONFIGURED_OPERATION")" == "/{orderId}/delivery-status" ]] || fail "Configured APIM operation template is incorrect."

POLICY_VALUE="$(az rest \
  --method get \
  --url "${MGMT_BASE}/${API_ID}/operations/${OPERATION_ID}/policies/policy?api-version=${API_VERSION}" \
  --query properties.value \
  -o tsv)"

[[ "$POLICY_VALUE" == *"Authorization"* ]] || fail "Configured operation policy does not enforce an Authorization header."
[[ "$POLICY_VALUE" == *"Bearer "* ]] || fail "Configured operation policy does not enforce Bearer authentication syntax."
[[ "$POLICY_VALUE" == *"${ORDER_DELIVERY_STATUS_BACKEND_URL}"* ]] || fail "Configured operation policy does not target the Order delivery-status backend."
[[ "$POLICY_VALUE" == *"no-store"* ]] || fail "Configured operation policy does not disable response caching."

echo
echo "========== Delivery Status APIM Configuration =========="
echo "API ID:       $API_ID"
echo "Public path:  /$API_PATH/{orderId}/delivery-status"
echo "Operation ID: $OPERATION_ID"
echo "Backend:      $ORDER_DELIVERY_STATUS_BACKEND_URL"
echo "API existed:  $API_EXISTS"
echo
echo "SUCCESS: Delivery-status APIM operation configured without changing provider or delivery execution flags."
