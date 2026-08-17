#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
API_VERSION="${API_VERSION:-2022-08-01}"
SUBSCRIPTION_ID=$(az account show --query id -o tsv)

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq; do command -v "$tool" >/dev/null || fail "$tool is required"; done

resolve_api() {
  local PATH_VALUE="$1"
  local -a IDS
  mapfile -t IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${PATH_VALUE}'].name" -o tsv --only-show-errors)
  (( ${#IDS[@]} == 1 )) || fail "Expected exactly one API for ${PATH_VALUE}"
  local API_ID="${IDS[0]}" SUB_REQUIRED
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv --only-show-errors)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "API ${API_ID} unexpectedly requires a subscription key"
  printf '%s' "$API_ID"
}

operation_matches() {
  local API_ID="$1" METHOD="$2" TEMPLATE="$3"
  az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json --only-show-errors |
    jq -r --arg method "$METHOD" --arg template "$TEMPLATE" '[.[] | select((.method | ascii_upcase) == ($method | ascii_upcase) and .urlTemplate == $template)] | length'
}

check_absent() {
  local API_ID="$1" METHOD="$2" TEMPLATE="$3" COUNT
  COUNT=$(operation_matches "$API_ID" "$METHOD" "$TEMPLATE")
  [[ "$COUNT" == "0" ]] || fail "Obsolete operation still exists: ${METHOD} ${TEMPLATE} in ${API_ID}"
  echo "ABSENT: ${METHOD} ${TEMPLATE}"
}

check_operation() {
  local API_ID="$1" METHOD="$2" TEMPLATE="$3" AUTH_REQUIRED="$4"
  local OPS_JSON MGMT POLICY OP_ID
  local -a MATCH_IDS
  OPS_JSON=$(az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json --only-show-errors)
  mapfile -t MATCH_IDS < <(jq -r --arg method "$METHOD" --arg template "$TEMPLATE" '.[] | select((.method | ascii_upcase) == ($method | ascii_upcase) and .urlTemplate == $template) | .name' <<<"$OPS_JSON")
  (( ${#MATCH_IDS[@]} == 1 )) || fail "Expected exactly one ${METHOD} ${TEMPLATE} operation in API ${API_ID}; found ${#MATCH_IDS[@]}"
  OP_ID="${MATCH_IDS[0]}"
  MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  POLICY=$(az rest --method get --url "${MGMT}/operations/${OP_ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"no-store"* && "$POLICY" == *"nosniff"* && "$POLICY" == *"set-backend-service base-url="* ]] || fail "Policy verification failed for ${METHOD} ${TEMPLATE} (${OP_ID})"
  if [[ "$AUTH_REQUIRED" == "true" ]]; then
    [[ "$POLICY" == *"Authorization"* && "$POLICY" == *"Bearer"* ]] || fail "Bearer guard missing for ${METHOD} ${TEMPLATE} (${OP_ID})"
  else
    [[ "$POLICY" != *"A Bearer access token is required"* ]] || fail "Public operation ${METHOD} ${TEMPLATE} unexpectedly requires Bearer authentication"
  fi
  echo "OK: ${METHOD} ${TEMPLATE} -> ${OP_ID}"
}

SUB_API=$(resolve_api "api/v1/subscriptions")
CHEF_PLAN_API=$(resolve_api "api/v1/chef/subscription-plans")
ADMIN_PLAN_API=$(resolve_api "api/v1/admin/subscription-plans")
ADMIN_SUB_API=$(resolve_api "api/v1/admin/subscriptions")
CHEF_CAPACITY_API=$(resolve_api "api/v1/chef/subscription-capacity")
ADMIN_CAPACITY_API=$(resolve_api "api/v1/admin/subscription-capacity")
CHEF_REVIEW_API=$(resolve_api "api/v1/backoffice/chef-reviews")

check_operation "$SUB_API" GET "/plans" false
check_operation "$SUB_API" GET "/plans/{planId}" false
check_operation "$SUB_API" GET "/plans/{planId}/schedule" false
check_operation "$SUB_API" GET "/plans/{planId}/policy" false
check_operation "$SUB_API" POST "/" true
check_operation "$SUB_API" GET "/" true
check_operation "$SUB_API" GET "/{subscriptionId}" true
check_operation "$SUB_API" GET "/{subscriptionId}/occurrences" true
check_operation "$SUB_API" PATCH "/{subscriptionId}/pause" true
check_operation "$SUB_API" PATCH "/{subscriptionId}/resume" true
check_operation "$SUB_API" PATCH "/{subscriptionId}/cancel" true
check_operation "$SUB_API" POST "/{subscriptionId}/skips" true

check_operation "$CHEF_PLAN_API" GET "/" true
check_operation "$CHEF_PLAN_API" POST "/" true
check_operation "$CHEF_PLAN_API" GET "/{planId}" true
check_operation "$CHEF_PLAN_API" PUT "/{planId}" true
check_operation "$CHEF_PLAN_API" GET "/{planId}/schedule" true
check_operation "$CHEF_PLAN_API" PUT "/{planId}/schedule" true
check_operation "$CHEF_PLAN_API" POST "/{planId}/submit" true

check_operation "$ADMIN_PLAN_API" GET "/" true
check_operation "$ADMIN_PLAN_API" PATCH "/{planId}/status" true
check_operation "$ADMIN_PLAN_API" GET "/{planId}/schedule" true
check_operation "$ADMIN_PLAN_API" POST "/{planId}/review" true
check_operation "$ADMIN_PLAN_API" GET "/{planId}/policy" true
check_operation "$ADMIN_PLAN_API" PUT "/{planId}/policy" true
check_operation "$ADMIN_PLAN_API" POST "/{planId}/policy/activate" true
check_operation "$ADMIN_PLAN_API" GET "/{planId}/readiness" true
check_absent "$ADMIN_PLAN_API" POST "/"
check_absent "$ADMIN_PLAN_API" PUT "/{planId}/schedule"
check_absent "$ADMIN_PLAN_API" POST "/{planId}/schedule/activate"

check_operation "$ADMIN_SUB_API" GET "/" true
check_operation "$ADMIN_SUB_API" GET "/{subscriptionId}/history" true
check_operation "$ADMIN_SUB_API" PATCH "/{subscriptionId}/status/{status}" true

check_operation "$CHEF_CAPACITY_API" GET "/" true
check_operation "$CHEF_CAPACITY_API" PUT "/rules/slots" true
check_operation "$CHEF_CAPACITY_API" PUT "/rules/menu-items" true
check_operation "$CHEF_CAPACITY_API" PUT "/overrides/slots" true
check_operation "$CHEF_CAPACITY_API" PUT "/overrides/menu-items" true

check_operation "$ADMIN_CAPACITY_API" GET "/chefs/{chefIdentityId}" true
check_operation "$ADMIN_CAPACITY_API" PATCH "/chefs/{chefIdentityId}/freeze" true
check_operation "$ADMIN_CAPACITY_API" GET "/incidents" true
check_operation "$ADMIN_CAPACITY_API" POST "/subscriptions/{subscriptionId}/reconcile" true

check_operation "$CHEF_REVIEW_API" GET "/" true
check_operation "$CHEF_REVIEW_API" GET "/{applicationId}" true
check_operation "$CHEF_REVIEW_API" POST "/{applicationId}/approve" true
check_operation "$CHEF_REVIEW_API" POST "/{applicationId}/reject" true
check_operation "$CHEF_REVIEW_API" GET "/{applicationId}/documents/{documentId}/content" true

echo "SUCCESS: Chef-owned subscription plans, capacity, customer subscriptions, and backoffice APIM status are valid."
