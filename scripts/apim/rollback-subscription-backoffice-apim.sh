#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
CONFIRM_APIM_ROLLBACK="${CONFIRM_APIM_ROLLBACK:-false}"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ "${CONFIRM_APIM_ROLLBACK,,}" == "true" ]] || fail "Set CONFIRM_APIM_ROLLBACK=true for the controlled rollback"

resolve_api_optional() {
  local PATH_VALUE="$1"
  local -a IDS
  mapfile -t IDS < <(az apim api list \
    -g "$RG" \
    --service-name "$APIM" \
    --query "[?path=='${PATH_VALUE}'].name" \
    -o tsv \
    --only-show-errors)

  (( ${#IDS[@]} <= 1 )) || fail "Multiple APIM APIs own ${PATH_VALUE}"
  if (( ${#IDS[@]} == 0 )); then
    printf ''
    return
  fi
  printf '%s' "${IDS[0]}"
}

delete_operation_by_route() {
  local API_ID="$1" PATH_VALUE="$2" METHOD="$3" TEMPLATE="$4"
  local OPS_JSON OP_ID
  local -a MATCH_IDS

  [[ -n "$API_ID" ]] || {
    echo "SKIP: no API owns ${PATH_VALUE}; ${METHOD} ${TEMPLATE} is absent"
    return
  }

  OPS_JSON=$(az apim api operation list \
    -g "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    -o json \
    --only-show-errors)

  mapfile -t MATCH_IDS < <(
    jq -r \
      --arg method "$METHOD" \
      --arg template "$TEMPLATE" \
      '.[] | select((.method | ascii_upcase) == ($method | ascii_upcase) and .urlTemplate == $template) | .name' \
      <<<"$OPS_JSON"
  )

  (( ${#MATCH_IDS[@]} <= 1 )) || fail "Multiple operations own ${METHOD} ${TEMPLATE} in API ${API_ID}"

  if (( ${#MATCH_IDS[@]} == 0 )); then
    echo "SKIP: ${PATH_VALUE} ${METHOD} ${TEMPLATE} is absent"
    return
  fi

  OP_ID="${MATCH_IDS[0]}"
  az apim api operation delete \
    -g "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --operation-id "$OP_ID" \
    --yes \
    --only-show-errors

  echo "REMOVED: ${PATH_VALUE} ${METHOD} ${TEMPLATE} (${OP_ID})"
}

SUB_API=$(resolve_api_optional "api/v1/subscriptions")
CHEF_PLAN_API=$(resolve_api_optional "api/v1/chef/subscription-plans")
ADMIN_PLAN_API=$(resolve_api_optional "api/v1/admin/subscription-plans")
ADMIN_SUB_API=$(resolve_api_optional "api/v1/admin/subscriptions")
CHEF_CAPACITY_API=$(resolve_api_optional "api/v1/chef/subscription-capacity")
ADMIN_CAPACITY_API=$(resolve_api_optional "api/v1/admin/subscription-capacity")
CHEF_REVIEW_API=$(resolve_api_optional "api/v1/backoffice/chef-reviews")

delete_operation_by_route "$SUB_API" "api/v1/subscriptions" GET "/plans"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" GET "/plans/{planId}"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" GET "/plans/{planId}/schedule"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" GET "/plans/{planId}/policy"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" POST "/"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" GET "/"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" GET "/{subscriptionId}"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" GET "/{subscriptionId}/occurrences"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" PATCH "/{subscriptionId}/pause"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" PATCH "/{subscriptionId}/resume"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" PATCH "/{subscriptionId}/cancel"
delete_operation_by_route "$SUB_API" "api/v1/subscriptions" POST "/{subscriptionId}/skips"

delete_operation_by_route "$CHEF_PLAN_API" "api/v1/chef/subscription-plans" GET "/"
delete_operation_by_route "$CHEF_PLAN_API" "api/v1/chef/subscription-plans" POST "/"
delete_operation_by_route "$CHEF_PLAN_API" "api/v1/chef/subscription-plans" GET "/{planId}"
delete_operation_by_route "$CHEF_PLAN_API" "api/v1/chef/subscription-plans" PUT "/{planId}"
delete_operation_by_route "$CHEF_PLAN_API" "api/v1/chef/subscription-plans" GET "/{planId}/schedule"
delete_operation_by_route "$CHEF_PLAN_API" "api/v1/chef/subscription-plans" PUT "/{planId}/schedule"
delete_operation_by_route "$CHEF_PLAN_API" "api/v1/chef/subscription-plans" POST "/{planId}/submit"

delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" GET "/"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" POST "/"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" PATCH "/{planId}/status"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" GET "/{planId}/schedule"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" PUT "/{planId}/schedule"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" POST "/{planId}/schedule/activate"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" POST "/{planId}/review"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" GET "/{planId}/policy"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" PUT "/{planId}/policy"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" POST "/{planId}/policy/activate"
delete_operation_by_route "$ADMIN_PLAN_API" "api/v1/admin/subscription-plans" GET "/{planId}/readiness"

delete_operation_by_route "$ADMIN_SUB_API" "api/v1/admin/subscriptions" GET "/"
delete_operation_by_route "$ADMIN_SUB_API" "api/v1/admin/subscriptions" GET "/{subscriptionId}/history"
delete_operation_by_route "$ADMIN_SUB_API" "api/v1/admin/subscriptions" PATCH "/{subscriptionId}/status/{status}"

delete_operation_by_route "$CHEF_CAPACITY_API" "api/v1/chef/subscription-capacity" GET "/"
delete_operation_by_route "$CHEF_CAPACITY_API" "api/v1/chef/subscription-capacity" PUT "/rules/slots"
delete_operation_by_route "$CHEF_CAPACITY_API" "api/v1/chef/subscription-capacity" PUT "/rules/menu-items"
delete_operation_by_route "$CHEF_CAPACITY_API" "api/v1/chef/subscription-capacity" PUT "/overrides/slots"
delete_operation_by_route "$CHEF_CAPACITY_API" "api/v1/chef/subscription-capacity" PUT "/overrides/menu-items"

delete_operation_by_route "$ADMIN_CAPACITY_API" "api/v1/admin/subscription-capacity" GET "/chefs/{chefIdentityId}"
delete_operation_by_route "$ADMIN_CAPACITY_API" "api/v1/admin/subscription-capacity" PATCH "/chefs/{chefIdentityId}/freeze"
delete_operation_by_route "$ADMIN_CAPACITY_API" "api/v1/admin/subscription-capacity" GET "/incidents"
delete_operation_by_route "$ADMIN_CAPACITY_API" "api/v1/admin/subscription-capacity" POST "/subscriptions/{subscriptionId}/reconcile"

delete_operation_by_route "$CHEF_REVIEW_API" "api/v1/backoffice/chef-reviews" GET "/"
delete_operation_by_route "$CHEF_REVIEW_API" "api/v1/backoffice/chef-reviews" GET "/{applicationId}"
delete_operation_by_route "$CHEF_REVIEW_API" "api/v1/backoffice/chef-reviews" POST "/{applicationId}/approve"
delete_operation_by_route "$CHEF_REVIEW_API" "api/v1/backoffice/chef-reviews" POST "/{applicationId}/reject"
delete_operation_by_route "$CHEF_REVIEW_API" "api/v1/backoffice/chef-reviews" GET "/{applicationId}/documents/{documentId}/content"

echo "SUCCESS: Named subscription, Chef meal-plan, capacity, and backoffice routes removed. API containers were retained."
