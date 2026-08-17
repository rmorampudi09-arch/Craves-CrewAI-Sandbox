#!/usr/bin/env bash
set -euo pipefail

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
SUB_APP="${SUB_APP:-ca-craves-subscription-service-p}"
API_VERSION="2022-08-01"

AZURE_SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
SUB_FQDN="$(az containerapp show \
  --name "$SUB_APP" \
  --resource-group "$RG" \
  --query 'properties.configuration.ingress.fqdn' \
  -o tsv)"

SUB_BASE_URL="https://${SUB_FQDN}"
MGMT_BASE="https://management.azure.com/subscriptions/${AZURE_SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis"

echo "Subscription backend: $SUB_BASE_URL"
curl -k -sS --fail --max-time 30 "$SUB_BASE_URL/actuator/health"
echo

ensure_api() {
  local api_id="$1"
  local display_name="$2"
  local path_value="$3"
  local service_url="$4"

  if az apim api show \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$api_id" \
    >/dev/null 2>&1; then
    az apim api update \
      --resource-group "$RG" \
      --service-name "$APIM" \
      --api-id "$api_id" \
      --set serviceUrl="$service_url" path="$path_value" subscriptionRequired=false \
      -o none
  else
    az apim api create \
      --resource-group "$RG" \
      --service-name "$APIM" \
      --api-id "$api_id" \
      --display-name "$display_name" \
      --path "$path_value" \
      --service-url "$service_url" \
      --protocols https \
      --subscription-required false \
      -o none
  fi
}

put_operation() {
  local api_id="$1"
  local operation_id="$2"
  local method="$3"
  local url_template="$4"
  local display_name="$5"
  local params_json="$6"
  local body_file

  body_file="$(mktemp)"
  trap 'rm -f "$body_file"' RETURN

  cat > "$body_file" <<JSON
{
  "properties": {
    "displayName": "$display_name",
    "method": "$method",
    "urlTemplate": "$url_template",
    "templateParameters": $params_json,
    "responses": []
  }
}
JSON

  az rest \
    --method put \
    --url "${MGMT_BASE}/${api_id}/operations/${operation_id}?api-version=${API_VERSION}" \
    --body @"$body_file" \
    -o none

  rm -f "$body_file"
  trap - RETURN
}

NO_PARAMS='[]'
PLAN_ID='[{"name":"planId","type":"string","required":true}]'
SUBSCRIPTION_ID_PARAM='[{"name":"subscriptionId","type":"string","required":true}]'
SUBSCRIPTION_STATUS_PARAMS='[{"name":"subscriptionId","type":"string","required":true},{"name":"status","type":"string","required":true}]'

ensure_api \
  "craves-subscriptions-v1" \
  "Craves Subscriptions API" \
  "api/v1/subscriptions" \
  "$SUB_BASE_URL/api/v1/subscriptions"

put_operation "craves-subscriptions-v1" "list-plans" "GET" "/plans" "List active subscription plans" "$NO_PARAMS"
put_operation "craves-subscriptions-v1" "get-plan" "GET" "/plans/{planId}" "Get subscription plan" "$PLAN_ID"
put_operation "craves-subscriptions-v1" "create-subscription" "POST" "/" "Create customer subscription" "$NO_PARAMS"
put_operation "craves-subscriptions-v1" "list-my-subscriptions" "GET" "/" "List my subscriptions" "$NO_PARAMS"
put_operation "craves-subscriptions-v1" "get-subscription" "GET" "/{subscriptionId}" "Get subscription" "$SUBSCRIPTION_ID_PARAM"
put_operation "craves-subscriptions-v1" "pause-subscription" "PATCH" "/{subscriptionId}/pause" "Pause subscription" "$SUBSCRIPTION_ID_PARAM"
put_operation "craves-subscriptions-v1" "cancel-subscription" "PATCH" "/{subscriptionId}/cancel" "Cancel subscription" "$SUBSCRIPTION_ID_PARAM"

ensure_api \
  "craves-admin-subscription-plans-v1" \
  "Craves Admin Subscription Plans API" \
  "api/v1/admin/subscription-plans" \
  "$SUB_BASE_URL/api/v1/admin/subscription-plans"

put_operation "craves-admin-subscription-plans-v1" "create-plan" "POST" "/" "Create subscription plan" "$NO_PARAMS"
put_operation "craves-admin-subscription-plans-v1" "list-all-plans" "GET" "/" "List all subscription plans" "$NO_PARAMS"
put_operation "craves-admin-subscription-plans-v1" "update-plan-status" "PATCH" "/{planId}/status" "Update subscription plan status" "$PLAN_ID"

ensure_api \
  "craves-admin-subscriptions-v1" \
  "Craves Admin Subscriptions API" \
  "api/v1/admin/subscriptions" \
  "$SUB_BASE_URL/api/v1/admin/subscriptions"

put_operation "craves-admin-subscriptions-v1" "admin-change-subscription-status" "PATCH" "/{subscriptionId}/status/{status}" "Admin change subscription status" "$SUBSCRIPTION_STATUS_PARAMS"

az apim api list \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --query "[?contains(name, 'subscription') || contains(path, 'subscription')].{name:name,path:path,serviceUrl:serviceUrl,subscriptionRequired:subscriptionRequired}" \
  -o table

echo "Subscription APIM routes are configured."
