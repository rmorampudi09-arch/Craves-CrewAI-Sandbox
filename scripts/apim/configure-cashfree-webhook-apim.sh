#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
INTEGRATION_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
API_PATH="${API_PATH:-api/v1/payments}"
API_VERSION="${API_VERSION:-2022-08-01}"
OPERATION_ID="${OPERATION_ID:-cashfree-payment-webhook}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/customer-payments/cashfree-webhook-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "Cashfree webhook policy template is missing"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$INTEGRATION_APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Integration Service is not ready"
curl -sS --fail --max-time 30 "https://$FQDN/actuator/health" >/dev/null

mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} == 1 )) || fail "Expected exactly one APIM API to own $API_PATH; run customer-payment APIM configuration first"
API_ID="${API_IDS[0]}"
BACKEND="https://${FQDN}/api/v1/payments"
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"

BODY=$(mktemp)
RENDERED=$(mktemp)
POLICY_BODY=$(mktemp)
cleanup() { rm -f "$BODY" "$RENDERED" "$POLICY_BODY"; }
trap cleanup EXIT

cat >"$BODY" <<'JSON'
{
  "properties": {
    "displayName": "Cashfree payment webhook",
    "method": "POST",
    "urlTemplate": "/webhooks/cashfree",
    "templateParameters": [],
    "responses": [
      {"statusCode": 200, "description": "Webhook accepted or duplicate delivery acknowledged"},
      {"statusCode": 400, "description": "Webhook headers or payload invalid"},
      {"statusCode": 401, "description": "Cashfree signature invalid"},
      {"statusCode": 409, "description": "Idempotency key reused with different content"}
    ]
  }
}
JSON

az rest --method put --url "${MGMT}/operations/${OPERATION_ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
sed "s|__PAYMENT_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
az rest --method put --url "${MGMT}/operations/${OPERATION_ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none

OP_JSON=$(az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$OPERATION_ID" -o json)
[[ "$(jq -r '.method' <<<"$OP_JSON")" == "POST" ]] || fail "Cashfree webhook operation method verification failed"
[[ "$(jq -r '.urlTemplate' <<<"$OP_JSON")" == "/webhooks/cashfree" ]] || fail "Cashfree webhook path verification failed"

POLICY=$(az rest --method get --url "${MGMT}/operations/${OPERATION_ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
[[ "$POLICY" == *"$BACKEND"* ]] || fail "Cashfree webhook backend verification failed"
[[ "$POLICY" == *"x-webhook-timestamp"* ]] || fail "Cashfree timestamp header guard is missing"
[[ "$POLICY" == *"x-webhook-signature"* ]] || fail "Cashfree signature header guard is missing"
[[ "$POLICY" == *"x-webhook-version"* ]] || fail "Cashfree version header guard is missing"
[[ "$POLICY" != *"Authorization"* ]] || fail "Cashfree webhook policy must not require a customer Bearer token"
[[ "$POLICY" != *"validate-jwt"* ]] || fail "Cashfree webhook policy must not require customer JWT validation"

PUBLIC_URL="https://${APIM}.azure-api.net/${API_PATH}/webhooks/cashfree"
STATUS=$(curl -sS -o /tmp/craves-cashfree-apim-probe.$$ -w '%{http_code}' --max-time 30 -X POST "$PUBLIC_URL" -H 'Content-Type: application/json' --data '{}' || true)
rm -f /tmp/craves-cashfree-apim-probe.$$
[[ "$STATUS" == "400" ]] || fail "Cashfree webhook public probe expected HTTP 400 for missing provider headers, got $STATUS"

echo "SUCCESS: Cashfree webhook APIM operation is reachable at $PUBLIC_URL and preserves provider-signature verification at Integration Service."
