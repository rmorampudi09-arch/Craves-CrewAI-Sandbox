#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
API_ID="${API_ID:-craves-internal-admin-rbac-v1}"
API_VERSION="${API_VERSION:-2022-08-01}"

fail() { echo "ERROR: $*" >&2; exit 1; }
command -v az >/dev/null || fail "az is required"
command -v jq >/dev/null || fail "jq is required"

if ! az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o none 2>/dev/null; then
  echo "NOT_CONFIGURED: $API_ID"
  exit 2
fi

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"

check_operation() {
  local ID="$1" EXPECTED_METHOD="$2" EXPECTED_TEMPLATE="$3"
  local OP POLICY METHOD TEMPLATE
  OP=$(az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o json)
  METHOD=$(jq -r '.method // ""' <<<"$OP")
  TEMPLATE=$(jq -r '.urlTemplate // ""' <<<"$OP")
  [[ "$METHOD" == "$EXPECTED_METHOD" ]] || fail "$ID method mismatch"
  [[ "$TEMPLATE" == "$EXPECTED_TEMPLATE" ]] || fail "$ID URL template mismatch"
  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"Bearer"* && "$POLICY" == *"no-store"* && "$POLICY" != *'backend-id='* ]] || fail "$ID policy is unsafe or incomplete"
  echo "$ID method=$METHOD urlTemplate=$TEMPLATE policy=READY"
}

check_operation "get-internal-admin-role-catalog" "GET" "/roles"
check_operation "get-internal-admin-users" "GET" "/users"
check_operation "get-internal-admin-user" "GET" "/users/{identityId}"
check_operation "put-internal-admin-user-roles" "PUT" "/users/{identityId}/roles"
check_operation "put-internal-admin-staff-role-grants" "PUT" "/staff-role-grants"
check_operation "get-internal-admin-role-changes" "GET" "/role-changes"

echo "SUCCESS: Internal administrator RBAC APIM status is ready."
