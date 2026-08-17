#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
INTEGRATION_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
API_VERSION="${API_VERSION:-2022-08-01}"
SUBSCRIPTION_ID=$(az account show --query id -o tsv)

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl; do command -v "$tool" >/dev/null || fail "$tool is required"; done

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

APP_JSON=$(az containerapp show -g "$RG" -n "$INTEGRATION_APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Integration Service is not ready"
probe_integration_health
SUBSCRIPTION_PAYMENT_BACKEND="https://${FQDN}/api/v1/subscription-payments"
PAYMENT_BACKEND="https://${FQDN}/api/v1/payments"

api_id_for_path() {
  local PATH_VALUE="$1"
  local -a IDS
  mapfile -t IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${PATH_VALUE}'].name" -o tsv)
  (( ${#IDS[@]} == 1 )) || fail "Expected exactly one APIM API for ${PATH_VALUE}"
  printf '%s' "${IDS[0]}"
}

route_id() {
  local API_ID="$1" METHOD="$2" TEMPLATE="$3"
  local -a MATCHES
  mapfile -t MATCHES < <(
    az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json \
      | jq -r --arg method "${METHOD^^}" --arg template "$TEMPLATE" \
          '.[] | select((.method | ascii_upcase) == $method and .urlTemplate == $template) | .name'
  )
  (( ${#MATCHES[@]} == 1 )) || fail "Expected exactly one ${METHOD^^} ${TEMPLATE} operation in API $API_ID"
  printf '%s' "${MATCHES[0]}"
}

verify_route() {
  local API_ID="$1" BACKEND="$2" METHOD="$3" TEMPLATE="$4" AUTH_REQUIRED="$5"
  local ID MGMT POLICY
  ID=$(route_id "$API_ID" "$METHOD" "$TEMPLATE")
  MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"no-store"* && "$POLICY" == *"nosniff"* ]] \
    || fail "Policy verification failed for ${METHOD^^} ${TEMPLATE}"
  if [[ "$AUTH_REQUIRED" == "true" ]]; then
    [[ "$POLICY" == *"Authorization"* && "$POLICY" == *"Bearer"* ]] \
      || fail "Bearer guard missing for ${METHOD^^} ${TEMPLATE}"
  else
    [[ "$POLICY" != *"A Bearer access token is required"* ]] \
      || fail "Cashfree webhook unexpectedly requires Bearer authentication"
    [[ "$POLICY" != *"set-body"* ]] \
      || fail "Cashfree webhook policy must not transform the raw request body"
  fi
  echo "OK: ${METHOD^^} ${TEMPLATE} -> ${ID}"
}

SUB_PAYMENT_API=$(api_id_for_path "api/v1/subscription-payments")
PAYMENT_API=$(api_id_for_path "api/v1/payments")

verify_route "$SUB_PAYMENT_API" "$SUBSCRIPTION_PAYMENT_BACKEND" GET "/subscriptions/{subscriptionId}" true
verify_route "$SUB_PAYMENT_API" "$SUBSCRIPTION_PAYMENT_BACKEND" GET "/invoices/{invoiceId}" true
verify_route "$SUB_PAYMENT_API" "$SUBSCRIPTION_PAYMENT_BACKEND" POST "/invoices/{invoiceId}/orders" true
verify_route "$PAYMENT_API" "$PAYMENT_BACKEND" POST "/webhooks/cashfree" false

echo "SUCCESS: Subscription payment and Cashfree webhook APIM status is valid."
