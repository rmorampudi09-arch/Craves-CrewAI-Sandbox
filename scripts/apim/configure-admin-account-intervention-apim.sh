#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
AUTH_APP="${AUTH_APP:-ca-craves-auth-service-prodlow}"
API_ID="${API_ID:-craves-admin-account-intervention-v1}"
API_PATH="${API_PATH:-api/v1/admin/accounts}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_WRITE="${CONFIRM_APIM_WRITE:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/admin-account-intervention/authenticated-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "APIM policy template is missing"
[[ "${CONFIRM_APIM_WRITE,,}" == "true" ]] || fail "Set CONFIRM_APIM_WRITE=true for the controlled APIM write"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
[[ -n "$SUBSCRIPTION_ID" ]] || fail "Azure subscription could not be resolved"
AUTH_JSON=$(az containerapp show -g "$RG" -n "$AUTH_APP" -o json)
AUTH_FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$AUTH_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$AUTH_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$AUTH_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$AUTH_JSON")
[[ -n "$AUTH_FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Auth Service is not ready"
curl --silent --show-error --fail --max-time 30 "https://$AUTH_FQDN/actuator/health" >/dev/null
BACKEND="https://${AUTH_FQDN}/api/v1/admin/accounts"

API_MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
GLOBAL_POLICY_URL="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}"
GLOBAL_POLICY=$(az rest --method get --url "$GLOBAL_POLICY_URL" --query properties.value -o tsv 2>/dev/null || true)
[[ "$GLOBAL_POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited global backend-id policy blocks safe routing"

mapfile -t PATH_OWNERS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#PATH_OWNERS[@]} <= 1 )) || fail "Multiple APIM APIs own ${API_PATH}"
if (( ${#PATH_OWNERS[@]} == 0 )); then
  az apim api create -g "$RG" --service-name "$APIM" --api-id "$API_ID" \
    --display-name "Craves Admin Account Intervention" --path "$API_PATH" \
    --service-url "$BACKEND" --protocols https --subscription-required false -o none
else
  [[ "${PATH_OWNERS[0]}" == "$API_ID" ]] || fail "Existing path owner is not ${API_ID}"
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing API unexpectedly requires a subscription key"
fi

API_POLICY=$(az rest --method get --url "${API_MGMT}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv 2>/dev/null || true)
[[ "$API_POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited API backend-id policy blocks safe routing"

put_operation() {
  local ID="$1" METHOD="$2" TEMPLATE="$3" DISPLAY="$4"
  local BODY RENDERED POLICY_BODY
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  jq -n --arg display "$DISPLAY" --arg method "$METHOD" --arg template "$TEMPLATE" \
    '{properties:{displayName:$display,method:$method,urlTemplate:$template,templateParameters:[{name:"identityId",type:"string",required:true}],responses:[{statusCode:200,description:"Audited account intervention result"},{statusCode:400,description:"Invalid request"},{statusCode:401,description:"Authentication required"},{statusCode:403,description:"ADMIN access required"},{statusCode:404,description:"Identity not found"},{statusCode:409,description:"Intervention conflict"},{statusCode:503,description:"Feature disabled"}]}}' >"$BODY"
  az rest --method put --url "${API_MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${API_MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}

put_operation "get-admin-account-intervention-status" "GET" "/{identityId}/intervention-status" "Get account intervention status"
put_operation "post-admin-account-suspend" "POST" "/{identityId}/suspend" "Suspend account"
put_operation "post-admin-account-reactivate" "POST" "/{identityId}/reactivate" "Reactivate account"

verify_operation() {
  local ID="$1" EXPECTED_METHOD="$2" EXPECTED_TEMPLATE="$3"
  local OPERATION POLICY ACTUAL_METHOD ACTUAL_TEMPLATE
  OPERATION=$(az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o json)
  ACTUAL_METHOD=$(jq -r '.method // ""' <<<"$OPERATION")
  ACTUAL_TEMPLATE=$(jq -r '.urlTemplate // ""' <<<"$OPERATION")
  [[ "$ACTUAL_METHOD" == "$EXPECTED_METHOD" ]] || fail "$ID method read-back failed: expected $EXPECTED_METHOD, found $ACTUAL_METHOD"
  [[ "$ACTUAL_TEMPLATE" == "$EXPECTED_TEMPLATE" ]] || fail "$ID URL template read-back failed: expected $EXPECTED_TEMPLATE, found $ACTUAL_TEMPLATE"
  POLICY=$(az rest --method get --url "${API_MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Bearer"* && "$POLICY" == *"no-store"* ]] || fail "$ID policy read-back failed"
  [[ "$POLICY" != *'backend-id='* ]] || fail "$ID unexpectedly uses backend-id"
}

verify_operation "get-admin-account-intervention-status" "GET" "/{identityId}/intervention-status"
verify_operation "post-admin-account-suspend" "POST" "/{identityId}/suspend"
verify_operation "post-admin-account-reactivate" "POST" "/{identityId}/reactivate"

GATEWAY_URL=$(az apim show -g "$RG" -n "$APIM" --query gatewayUrl -o tsv)
[[ "$GATEWAY_URL" == https://* ]] || fail "APIM HTTPS gateway URL was not returned"
SMOKE_ID="00000000-0000-4000-8000-000000000001"
HTTP_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 30 "${GATEWAY_URL%/}/${API_PATH}/${SMOKE_ID}/intervention-status")
[[ "$HTTP_STATUS" == "401" ]] || fail "Unauthenticated gateway guard returned HTTP $HTTP_STATUS instead of 401"

echo "SUCCESS: Admin account intervention APIM operations configured and verified."
