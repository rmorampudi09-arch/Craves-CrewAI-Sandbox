#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
SUBSCRIPTION_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_WRITE="${CONFIRM_APIM_WRITE:-false}"
HEALTH_ATTEMPTS="${CRAVES_APIM_BACKEND_HEALTH_ATTEMPTS:-12}"
HEALTH_SLEEP_SECONDS="${CRAVES_APIM_BACKEND_HEALTH_SLEEP_SECONDS:-10}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
AUTH_POLICY="$ROOT/infra/apim/subscription-backoffice/authenticated-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$AUTH_POLICY" ]] || fail "Authenticated APIM policy template is missing"
[[ "${CONFIRM_APIM_WRITE,,}" == "true" ]] || fail "Set CONFIRM_APIM_WRITE=true for the controlled APIM write"
[[ "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_APIM_BACKEND_HEALTH_ATTEMPTS must be a positive integer"
[[ "$HEALTH_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || fail "CRAVES_APIM_BACKEND_HEALTH_SLEEP_SECONDS must be a non-negative integer"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$SUBSCRIPTION_APP" -o json --only-show-errors)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Subscription Service is not ready: latest=$LATEST ready=$READY running=$RUNNING"
SUB_BASE="https://${FQDN}"

wait_for_backend_health() {
  local ATTEMPT BODY CODE STATUS
  BODY="/tmp/craves-chef-subscription-apim-health-${BASHPID}.json"
  for ((ATTEMPT=1; ATTEMPT<=HEALTH_ATTEMPTS; ATTEMPT++)); do
    : >"$BODY"
    CODE=$(curl \
      --silent \
      --show-error \
      --connect-timeout 10 \
      --max-time 30 \
      --output "$BODY" \
      --write-out '%{http_code}' \
      "${SUB_BASE}/actuator/health" || true)
    STATUS=$(jq -r '.status // empty' "$BODY" 2>/dev/null || true)

    if [[ "$CODE" == "200" && "$STATUS" == "UP" ]]; then
      rm -f "$BODY"
      echo "PASS: Subscription Service aggregate health is UP attempt=$ATTEMPT/$HEALTH_ATTEMPTS"
      return 0
    fi

    echo "WAIT: Subscription Service aggregate health attempt=$ATTEMPT/$HEALTH_ATTEMPTS HTTP=${CODE:-curl-error} status=${STATUS:-unavailable}" >&2
    if (( ATTEMPT < HEALTH_ATTEMPTS )); then
      sleep "$HEALTH_SLEEP_SECONDS"
    fi
  done
  rm -f "$BODY"
  return 1
}

wait_for_backend_health || fail "Subscription Service aggregate health did not become UP; APIM write was not attempted"

ensure_api() {
  local PATH_VALUE="$1" NEW_ID="$2" DISPLAY="$3" SERVICE_URL="$4"
  local -a IDS
  mapfile -t IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${PATH_VALUE}'].name" -o tsv)
  (( ${#IDS[@]} <= 1 )) || fail "Multiple APIM APIs own ${PATH_VALUE}"
  if (( ${#IDS[@]} == 0 )); then
    az apim api create -g "$RG" --service-name "$APIM" --api-id "$NEW_ID" \
      --display-name "$DISPLAY" --path "$PATH_VALUE" --service-url "$SERVICE_URL" \
      --protocols https --subscription-required false -o none --only-show-errors
    printf '%s' "$NEW_ID"
  else
    local ID="${IDS[0]}"
    local SUB_REQUIRED
    SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$ID" --query subscriptionRequired -o tsv)
    [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing API $ID unexpectedly requires a subscription key"
    az apim api update -g "$RG" --service-name "$APIM" --api-id "$ID" --set serviceUrl="$SERVICE_URL" -o none --only-show-errors
    printf '%s' "$ID"
  fi
}

put_operation() {
  local API_ID="$1" BACKEND="$2" ID="$3" METHOD="$4" TEMPLATE="$5" DISPLAY="$6" PARAMS="$7"
  local MGMT OPS_JSON EFFECTIVE_ID BODY RENDERED POLICY_BODY POLICY
  local -a MATCH_IDS
  MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  OPS_JSON=$(az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json --only-show-errors)
  mapfile -t MATCH_IDS < <(jq -r --arg method "$METHOD" --arg template "$TEMPLATE" '.[] | select((.method | ascii_upcase) == ($method | ascii_upcase) and .urlTemplate == $template) | .name' <<<"$OPS_JSON")
  (( ${#MATCH_IDS[@]} <= 1 )) || fail "Multiple operations own ${METHOD} ${TEMPLATE} in ${API_ID}"
  EFFECTIVE_ID="${MATCH_IDS[0]:-$ID}"

  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  jq -n --arg display "$DISPLAY" --arg method "$METHOD" --arg template "$TEMPLATE" --argjson params "$PARAMS" \
    '{properties:{displayName:$display,method:$method,urlTemplate:$template,templateParameters:$params,responses:[{statusCode:200,description:"Craves response"},{statusCode:201,description:"Created"},{statusCode:400,description:"Invalid request"},{statusCode:401,description:"Authentication required"},{statusCode:403,description:"Access denied"},{statusCode:404,description:"Not found"},{statusCode:409,description:"State conflict"}]}}' >"$BODY"
  az rest --method put --url "${MGMT}/operations/${EFFECTIVE_ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__BACKEND_URL__|${BACKEND}|g" "$AUTH_POLICY" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${EFFECTIVE_ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  POLICY=$(az rest --method get --url "${MGMT}/operations/${EFFECTIVE_ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Bearer"* && "$POLICY" == *"no-store"* ]] || fail "Policy verification failed for ${METHOD} ${TEMPLATE}"
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}

delete_operation_by_route() {
  local API_ID="$1" METHOD="$2" TEMPLATE="$3"
  local MGMT OPS_JSON
  local -a MATCH_IDS
  MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  OPS_JSON=$(az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json --only-show-errors)
  mapfile -t MATCH_IDS < <(jq -r --arg method "$METHOD" --arg template "$TEMPLATE" '.[] | select((.method | ascii_upcase) == ($method | ascii_upcase) and .urlTemplate == $template) | .name' <<<"$OPS_JSON")
  (( ${#MATCH_IDS[@]} <= 1 )) || fail "Multiple stale operations own ${METHOD} ${TEMPLATE}"
  if (( ${#MATCH_IDS[@]} == 1 )); then
    az rest --method delete --url "${MGMT}/operations/${MATCH_IDS[0]}?api-version=${API_VERSION}" -o none
    echo "Removed obsolete operation: ${METHOD} ${TEMPLATE}"
  fi
}

PLAN_PARAM='[{"name":"planId","type":"string","required":true}]'
CHEF_API=$(ensure_api "api/v1/chef/subscription-plans" "craves-chef-subscription-plans-v1" "Craves Chef Subscription Meal Plans API" "${SUB_BASE}/api/v1/chef/subscription-plans")
ADMIN_PLAN_API=$(ensure_api "api/v1/admin/subscription-plans" "craves-admin-subscription-plans-v1" "Craves Admin Subscription Plans API" "${SUB_BASE}/api/v1/admin/subscription-plans")

put_operation "$CHEF_API" "${SUB_BASE}/api/v1/chef/subscription-plans" "list-chef-meal-plans" "GET" "/" "List my meal plans" '[]'
put_operation "$CHEF_API" "${SUB_BASE}/api/v1/chef/subscription-plans" "create-chef-meal-plan" "POST" "/" "Create meal plan draft" '[]'
put_operation "$CHEF_API" "${SUB_BASE}/api/v1/chef/subscription-plans" "get-chef-meal-plan" "GET" "/{planId}" "Get my meal plan" "$PLAN_PARAM"
put_operation "$CHEF_API" "${SUB_BASE}/api/v1/chef/subscription-plans" "update-chef-meal-plan" "PUT" "/{planId}" "Update my meal plan" "$PLAN_PARAM"
put_operation "$CHEF_API" "${SUB_BASE}/api/v1/chef/subscription-plans" "get-chef-meal-plan-schedule" "GET" "/{planId}/schedule" "Get my meal schedule" "$PLAN_PARAM"
put_operation "$CHEF_API" "${SUB_BASE}/api/v1/chef/subscription-plans" "put-chef-meal-plan-schedule" "PUT" "/{planId}/schedule" "Save my meal schedule" "$PLAN_PARAM"
put_operation "$CHEF_API" "${SUB_BASE}/api/v1/chef/subscription-plans" "submit-chef-meal-plan" "POST" "/{planId}/submit" "Submit meal plan for approval" "$PLAN_PARAM"
put_operation "$ADMIN_PLAN_API" "${SUB_BASE}/api/v1/admin/subscription-plans" "review-chef-meal-plan" "POST" "/{planId}/review" "Approve or reject Chef meal plan" "$PLAN_PARAM"

# Remove the previous authorship model from the gateway. Admin remains review/policy/operations only.
delete_operation_by_route "$ADMIN_PLAN_API" "POST" "/"
delete_operation_by_route "$ADMIN_PLAN_API" "PUT" "/{planId}/schedule"
delete_operation_by_route "$ADMIN_PLAN_API" "POST" "/{planId}/schedule/activate"

# Read-back: obsolete operations must be absent.
OPS_JSON=$(az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$ADMIN_PLAN_API" -o json --only-show-errors)
for SPEC in 'POST|/' 'PUT|/{planId}/schedule' 'POST|/{planId}/schedule/activate'; do
  METHOD="${SPEC%%|*}"; TEMPLATE="${SPEC#*|}"
  COUNT=$(jq -r --arg method "$METHOD" --arg template "$TEMPLATE" '[.[] | select((.method | ascii_upcase) == ($method | ascii_upcase) and .urlTemplate == $template)] | length' <<<"$OPS_JSON")
  [[ "$COUNT" == "0" ]] || fail "Obsolete Admin authorship operation still exists: ${METHOD} ${TEMPLATE}"
done

echo "SUCCESS: Chef-owned subscription meal plan APIM workflow configured; obsolete Admin authoring routes removed."
