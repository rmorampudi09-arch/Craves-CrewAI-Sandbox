#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
INTEGRATION_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_WRITE="${CONFIRM_APIM_WRITE:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
AUTH_POLICY="$ROOT/infra/apim/subscription-payments/authenticated-policy.xml"
WEBHOOK_POLICY="$ROOT/infra/apim/subscription-payments/cashfree-webhook-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$AUTH_POLICY" && -f "$WEBHOOK_POLICY" ]] || fail "Subscription payment APIM policy templates are missing"
[[ "${CONFIRM_APIM_WRITE,,}" == "true" ]] || fail "Set CONFIRM_APIM_WRITE=true for the controlled APIM write"

probe_integration_health() {
  local path body code
  for path in /actuator/health/liveness /actuator/health/readiness; do
    body=$(mktemp)
    code=$(curl \
      --silent \
      --show-error \
      --connect-timeout 10 \
      --max-time 30 \
      --output "$body" \
      --write-out '%{http_code}' \
      "https://${FQDN}${path}" || true)
    if [[ "$code" != "200" ]] || ! jq -e '.status == "UP"' "$body" >/dev/null 2>&1; then
      rm -f "$body"
      fail "Integration Service ${path} is not UP (HTTP ${code:-curl-error})"
    fi
    rm -f "$body"
    echo "PASS: Integration Service ${path} -> UP"
  done
}

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$INTEGRATION_APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Integration Service is not ready"
probe_integration_health

SUBSCRIPTION_PAYMENT_BACKEND="https://${FQDN}/api/v1/subscription-payments"
PAYMENT_BACKEND="https://${FQDN}/api/v1/payments"

check_inherited_backend_policy() {
  local API_ID="$1" MGMT="$2" POLICY SCOPE_URL
  for SCOPE_URL in \
    "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}" \
    "${MGMT}/policies/policy?api-version=${API_VERSION}"; do
    POLICY=$(az rest --method get --url "$SCOPE_URL" --query properties.value -o tsv 2>/dev/null || true)
    [[ "$POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited backend-id policy blocks safe base-url override for API $API_ID"
  done
}

ensure_api() {
  local PATH_VALUE="$1" NEW_ID="$2" DISPLAY="$3" SERVICE_URL="$4"
  local -a IDS
  mapfile -t IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${PATH_VALUE}'].name" -o tsv)
  (( ${#IDS[@]} <= 1 )) || fail "Multiple APIM APIs own ${PATH_VALUE}"
  local API_ID
  if (( ${#IDS[@]} == 0 )); then
    az apim api create \
      -g "$RG" --service-name "$APIM" --api-id "$NEW_ID" --display-name "$DISPLAY" \
      --path "$PATH_VALUE" --service-url "$SERVICE_URL" --protocols https \
      --subscription-required false -o none
    API_ID="$NEW_ID"
  else
    API_ID="${IDS[0]}"
    local SUB_REQUIRED
    SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
    [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing API $API_ID requires a subscription key; this script will not relax it"
  fi
  local MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  check_inherited_backend_policy "$API_ID" "$MGMT"
  printf '%s' "$API_ID"
}

find_route_id() {
  local API_ID="$1" METHOD="$2" TEMPLATE="$3"
  local -a MATCHES
  mapfile -t MATCHES < <(
    az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json \
      | jq -r --arg method "${METHOD^^}" --arg template "$TEMPLATE" \
          '.[] | select((.method | ascii_upcase) == $method and .urlTemplate == $template) | .name'
  )
  (( ${#MATCHES[@]} <= 1 )) || fail "Multiple operations own ${METHOD^^} ${TEMPLATE} in API $API_ID"
  if (( ${#MATCHES[@]} == 1 )); then
    printf '%s' "${MATCHES[0]}"
  fi
}

choose_operation_id() {
  local API_ID="$1" DESIRED_ID="$2" METHOD="$3" TEMPLATE="$4"
  local ROUTE_ID
  ROUTE_ID=$(find_route_id "$API_ID" "$METHOD" "$TEMPLATE")
  if [[ -n "$ROUTE_ID" ]]; then
    if [[ "$ROUTE_ID" != "$DESIRED_ID" ]]; then
      echo "ADOPT: ${METHOD^^} ${TEMPLATE} already exists as operation ${ROUTE_ID}; preserving that management ID." >&2
    fi
    printf '%s' "$ROUTE_ID"
    return
  fi

  if az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$DESIRED_ID" -o json >/tmp/craves-apim-operation.json 2>/dev/null; then
    local EXISTING_METHOD EXISTING_TEMPLATE
    EXISTING_METHOD=$(jq -r '.method // "" | ascii_upcase' /tmp/craves-apim-operation.json)
    EXISTING_TEMPLATE=$(jq -r '.urlTemplate // ""' /tmp/craves-apim-operation.json)
    rm -f /tmp/craves-apim-operation.json
    [[ "$EXISTING_METHOD" == "${METHOD^^}" && "$EXISTING_TEMPLATE" == "$TEMPLATE" ]] \
      || fail "Operation ID $DESIRED_ID already owns a different route; refusing to overwrite it"
  fi
  printf '%s' "$DESIRED_ID"
}

put_operation() {
  local API_ID="$1" BACKEND="$2" POLICY_TEMPLATE="$3" PLACEHOLDER="$4"
  local DESIRED_ID="$5" METHOD="$6" TEMPLATE="$7" DISPLAY="$8" PARAMS="$9" AUTH_REQUIRED="${10}"
  local MGMT ID BODY RENDERED POLICY_BODY POLICY
  MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  ID=$(choose_operation_id "$API_ID" "$DESIRED_ID" "$METHOD" "$TEMPLATE")
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  jq -n \
    --arg display "$DISPLAY" \
    --arg method "${METHOD^^}" \
    --arg template "$TEMPLATE" \
    --argjson params "$PARAMS" \
    '{properties:{displayName:$display,method:$method,urlTemplate:$template,templateParameters:$params,responses:[{statusCode:200,description:"Craves response"},{statusCode:201,description:"Created"},{statusCode:400,description:"Invalid request"},{statusCode:401,description:"Authentication required"},{statusCode:404,description:"Not found"},{statusCode:409,description:"Conflict"},{statusCode:503,description:"Execution disabled"}]}}' >"$BODY"
  az rest --method put --url "${MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|${PLACEHOLDER}|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none

  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"no-store"* && "$POLICY" == *"nosniff"* ]] \
    || fail "Operation $ID policy verification failed"
  if [[ "$AUTH_REQUIRED" == "true" ]]; then
    [[ "$POLICY" == *"Authorization"* && "$POLICY" == *"Bearer"* ]] \
      || fail "Operation $ID is missing the Bearer guard"
  else
    [[ "$POLICY" != *"A Bearer access token is required"* ]] \
      || fail "Webhook operation $ID unexpectedly requires Bearer authentication"
    [[ "$POLICY" != *"set-body"* ]] || fail "Webhook operation $ID must not transform the Cashfree raw request body"
  fi
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
  echo "OK: ${METHOD^^} ${TEMPLATE} -> ${ID}"
}

SUB_PAYMENT_API=$(ensure_api \
  "api/v1/subscription-payments" \
  "craves-subscription-payments-v1" \
  "Craves Subscription Payments API" \
  "$SUBSCRIPTION_PAYMENT_BACKEND")
PAYMENT_API=$(ensure_api \
  "api/v1/payments" \
  "craves-customer-payments-v1" \
  "Craves Customer Payments API" \
  "$PAYMENT_BACKEND")

INVOICE_PARAM='[{"name":"invoiceId","type":"string","required":true}]'
SUBSCRIPTION_PARAM='[{"name":"subscriptionId","type":"string","required":true}]'
put_operation "$SUB_PAYMENT_API" "$SUBSCRIPTION_PAYMENT_BACKEND" "$AUTH_POLICY" "__SUBSCRIPTION_PAYMENT_BACKEND_URL__" \
  "get-subscription-payment-for-subscription" "GET" "/subscriptions/{subscriptionId}" "Get latest owned subscription payment" "$SUBSCRIPTION_PARAM" true
put_operation "$SUB_PAYMENT_API" "$SUBSCRIPTION_PAYMENT_BACKEND" "$AUTH_POLICY" "__SUBSCRIPTION_PAYMENT_BACKEND_URL__" \
  "get-subscription-payment-invoice" "GET" "/invoices/{invoiceId}" "Get owned subscription payment invoice" "$INVOICE_PARAM" true
put_operation "$SUB_PAYMENT_API" "$SUBSCRIPTION_PAYMENT_BACKEND" "$AUTH_POLICY" "__SUBSCRIPTION_PAYMENT_BACKEND_URL__" \
  "create-subscription-payment-order" "POST" "/invoices/{invoiceId}/orders" "Create Cashfree subscription payment order" "$INVOICE_PARAM" true
put_operation "$PAYMENT_API" "$PAYMENT_BACKEND" "$WEBHOOK_POLICY" "__PAYMENT_BACKEND_URL__" \
  "cashfree-payment-webhook" "POST" "/webhooks/cashfree" "Cashfree signed payment webhook" '[]' false

echo "SUCCESS: Subscription payment and Cashfree webhook APIM operations configured and verified."
