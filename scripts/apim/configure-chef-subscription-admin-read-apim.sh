#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
SUBSCRIPTION_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_WRITE="${CONFIRM_APIM_WRITE:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
AUTH_POLICY="$ROOT/infra/apim/subscription-backoffice/authenticated-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$AUTH_POLICY" ]] || fail "Authenticated APIM policy template is missing"
[[ "${CONFIRM_APIM_WRITE,,}" == "true" ]] || fail "Set CONFIRM_APIM_WRITE=true for the controlled APIM write"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$SUBSCRIPTION_APP" -o json --only-show-errors)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Subscription Service is not ready"
SUB_BASE="https://${FQDN}"
HEALTH=$(curl -fsS --max-time 30 "${SUB_BASE}/actuator/health")
[[ "$(jq -r '.status // empty' <<<"$HEALTH")" == "UP" ]] || fail "Subscription Service aggregate health is not UP"

ensure_api() {
  local PATH_VALUE="$1" API_ID="$2" DISPLAY="$3" BACKEND="$4"
  local EXISTING COUNT SUB_REQUIRED
  EXISTING=$(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${PATH_VALUE}'].name | [0]" -o tsv)
  if [[ -z "$EXISTING" ]]; then
    az apim api create -g "$RG" --service-name "$APIM" --api-id "$API_ID" --display-name "$DISPLAY" \
      --path "$PATH_VALUE" --service-url "$BACKEND" --protocols https --subscription-required false -o none --only-show-errors
    printf '%s' "$API_ID"
    return
  fi
  COUNT=$(az apim api list -g "$RG" --service-name "$APIM" --query "length([?path=='${PATH_VALUE}'])" -o tsv)
  [[ "$COUNT" == "1" ]] || fail "Multiple APIM APIs own ${PATH_VALUE}"
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$EXISTING" --query subscriptionRequired -o tsv)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing API ${EXISTING} unexpectedly requires a subscription key"
  az apim api update -g "$RG" --service-name "$APIM" --api-id "$EXISTING" --set serviceUrl="$BACKEND" -o none --only-show-errors
  printf '%s' "$EXISTING"
}

put_read() {
  local API_ID="$1" BACKEND="$2" OP_ID="$3" TEMPLATE="$4" DISPLAY="$5" PARAMS="$6"
  local MGMT OPS_JSON EFFECTIVE_ID BODY RENDERED POLICY_BODY POLICY MATCH_COUNT
  MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  OPS_JSON=$(az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json --only-show-errors)
  MATCH_COUNT=$(jq -r --arg template "$TEMPLATE" '[.[] | select((.method | ascii_upcase) == "GET" and .urlTemplate == $template)] | length' <<<"$OPS_JSON")
  (( MATCH_COUNT <= 1 )) || fail "Multiple GET operations own ${TEMPLATE} in ${API_ID}"
  EFFECTIVE_ID=$(jq -r --arg template "$TEMPLATE" '[.[] | select((.method | ascii_upcase) == "GET" and .urlTemplate == $template) | .name][0] // empty' <<<"$OPS_JSON")
  [[ -n "$EFFECTIVE_ID" ]] || EFFECTIVE_ID="$OP_ID"

  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  jq -n --arg display "$DISPLAY" --arg template "$TEMPLATE" --argjson params "$PARAMS" \
    '{properties:{displayName:$display,method:"GET",urlTemplate:$template,templateParameters:$params,responses:[{statusCode:200,description:"Craves response"},{statusCode:401,description:"Authentication required"},{statusCode:403,description:"Access denied"},{statusCode:404,description:"Not found"}]}}' >"$BODY"
  az rest --method put --url "${MGMT}/operations/${EFFECTIVE_ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__BACKEND_URL__|${BACKEND}|g" "$AUTH_POLICY" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${EFFECTIVE_ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  POLICY=$(az rest --method get --url "${MGMT}/operations/${EFFECTIVE_ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Bearer"* && "$POLICY" == *"no-store"* ]] || fail "Policy verification failed for GET ${TEMPLATE}"
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}

PLAN_PARAM='[{"name":"planId","type":"string","required":true}]'
CHEF_PARAM='[{"name":"chefIdentityId","type":"string","required":true}]'
ADMIN_PLAN_API=$(ensure_api "api/v1/admin/subscription-plans" "craves-admin-subscription-plans-v1" "Craves Admin Subscription Plans API" "${SUB_BASE}/api/v1/admin/subscription-plans")
ADMIN_CAPACITY_API=$(ensure_api "api/v1/admin/subscription-capacity" "craves-admin-subscription-capacity-v1" "Craves Admin Subscription Capacity API" "${SUB_BASE}/api/v1/admin/subscription-capacity")

put_read "$ADMIN_PLAN_API" "${SUB_BASE}/api/v1/admin/subscription-plans" "list-admin-meal-plans" "/" "List meal plans for review" '[]'
put_read "$ADMIN_PLAN_API" "${SUB_BASE}/api/v1/admin/subscription-plans" "read-admin-meal-plan-schedule" "/{planId}/schedule" "Read Chef meal schedule" "$PLAN_PARAM"
put_read "$ADMIN_CAPACITY_API" "${SUB_BASE}/api/v1/admin/subscription-capacity" "read-admin-chef-capacity" "/chefs/{chefIdentityId}" "Read Chef subscription capacity" "$CHEF_PARAM"

echo "SUCCESS: Admin meal-plan review reads are configured for plan list, Chef schedule, and Chef capacity."
