#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
INTEGRATION_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
API_PATH="${API_PATH:-api/v1/payments}"
API_VERSION="${API_VERSION:-2022-08-01}"
OPERATION_ID="${OPERATION_ID:-razorpay-payment-webhook}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/customer-payments/razorpay-webhook-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "Razorpay webhook policy template is missing"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$INTEGRATION_APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" ]] || fail "Integration Service is not ready"
payments_healthy=false
for _ in $(seq 1 12); do
  if curl -sS --fail --max-time 30 "https://$FQDN/actuator/health/payments" >/dev/null; then
    payments_healthy=true
    break
  fi
  sleep 5
done
[[ "$payments_healthy" == true ]] || fail "Integration Service payments health group is not ready"

mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} == 1 )) || fail "Expected exactly one APIM API to own $API_PATH"
API_ID="${API_IDS[0]}"
BACKEND="https://${FQDN}/api/v1/payments"
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
trap 'rm -f "$BODY" "$RENDERED" "$POLICY_BODY"' EXIT

printf '%s' '{"properties":{"displayName":"Razorpay payment webhook","method":"POST","urlTemplate":"/webhooks/razorpay","templateParameters":[],"responses":[{"statusCode":200,"description":"Webhook accepted"},{"statusCode":400,"description":"Signature or payload invalid"},{"statusCode":401,"description":"Razorpay signature invalid"}]}}' >"$BODY"
az rest --method put --url "${MGMT}/operations/${OPERATION_ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
sed "s|__PAYMENT_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
az rest --method put --url "${MGMT}/operations/${OPERATION_ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none

OP_JSON=$(az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$OPERATION_ID" -o json)
[[ "$(jq -r '.method' <<<"$OP_JSON")" == "POST" && "$(jq -r '.urlTemplate' <<<"$OP_JSON")" == "/webhooks/razorpay" ]] || fail "Razorpay webhook operation verification failed"
POLICY=$(az rest --method get --url "${MGMT}/operations/${OPERATION_ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
[[ "$POLICY" == *"X-Razorpay-Signature"* && "$POLICY" != *"validate-jwt"* ]] || fail "Razorpay webhook policy verification failed"
PUBLIC_URL="${PUBLIC_URL:-https://api.craves.in/${API_PATH}/webhooks/razorpay}"
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 30 -X POST "$PUBLIC_URL" -H 'Content-Type: application/json' --data '{}' || true)
[[ "$STATUS" == "400" ]] || fail "Razorpay headerless probe expected HTTP 400, got $STATUS"
echo "SUCCESS: Razorpay webhook APIM operation is reachable at $PUBLIC_URL."
