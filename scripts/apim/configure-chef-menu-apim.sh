#!/usr/bin/env bash
set -euo pipefail
set +x
RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
APP="${CATALOG_APP:-ca-craves-catalog-service-prodlo}"
API_PATH="api/v1/kitchens/me"
API_VERSION="${API_VERSION:-2022-08-01}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/chef-kitchen/chef-kitchen-policy.xml"
fail(){ echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Catalog Service is not ready"
curl -sS --fail --max-time 30 "https://$FQDN/actuator/health" >/dev/null
mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} == 1 )) || fail "Expected exactly one Chef Kitchen API at $API_PATH; run profile APIM first"
API_ID="${API_IDS[0]}"
SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
[[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing Chef Kitchen API requires a subscription key"
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
BACKEND="https://${FQDN}/api/v1/kitchens/me"
put_operation(){
  local ID="$1" METHOD="$2" TEMPLATE="$3" DISPLAY="$4" PARAMS="$5"
  local BODY RENDERED POLICY_BODY
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  printf '%s' "{\"properties\":{\"displayName\":\"$DISPLAY\",\"method\":\"$METHOD\",\"urlTemplate\":\"$TEMPLATE\",\"templateParameters\":$PARAMS,\"responses\":[{\"statusCode\":200,\"description\":\"Chef menu response\"},{\"statusCode\":401,\"description\":\"Authentication required\"},{\"statusCode\":403,\"description\":\"Chef role required\"}]}}" >"$BODY"
  az rest --method put --url "${MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__CHEF_KITCHEN_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}
put_operation "list-my-menu-items" "GET" "/menu-items" "List my menu items" '[]'
put_operation "create-my-menu-item" "POST" "/menu-items" "Create my menu item" '[]'
put_operation "update-my-menu-item" "PUT" "/menu-items/{menuItemId}" "Update my menu item" '[{"name":"menuItemId","type":"string","required":true}]'
for ID in list-my-menu-items create-my-menu-item update-my-menu-item; do az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o none; done
echo "SUCCESS: Chef menu operations configured on API $API_ID."
