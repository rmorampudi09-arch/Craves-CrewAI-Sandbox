#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
API_ID="${API_ID:-craves-admin-notification-recovery-v1}"
API_VERSION="${API_VERSION:-2022-08-01}"

fail() { echo "ERROR: $*" >&2; exit 1; }
command -v az >/dev/null || fail "az is required"
command -v jq >/dev/null || fail "jq is required"

if ! az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o none 2>/dev/null; then
  echo "NOT_CONFIGURED: $API_ID"
  exit 2
fi

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
[[ -n "$SUBSCRIPTION_ID" ]] || fail "Azure subscription could not be resolved"
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"

check_operation() {
  local ID="$1" EXPECTED_METHOD="$2" EXPECTED_TEMPLATE="$3"
  local OP POLICY METHOD TEMPLATE
  OP=$(az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o json)
  METHOD=$(jq -r '.method // ""' <<<"$OP")
  TEMPLATE=$(jq -r '.urlTemplate // ""' <<<"$OP")
  [[ "$METHOD" == "$EXPECTED_METHOD" ]] || fail "$ID method mismatch: expected $EXPECTED_METHOD, found $METHOD"
  [[ "$TEMPLATE" == "$EXPECTED_TEMPLATE" ]] || fail "$ID URL template mismatch: expected $EXPECTED_TEMPLATE, found $TEMPLATE"
  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"Bearer"* && "$POLICY" == *"no-store"* && "$POLICY" != *'backend-id='* ]] || fail "$ID policy is unsafe or incomplete"
  echo "$ID method=$METHOD urlTemplate=$TEMPLATE policy=READY"
}

check_operation "get-admin-notification-recovery-backlog" "GET" "/backlog"
check_operation "post-admin-notification-recovery-retry" "POST" "/{requestId}/retry"

echo "SUCCESS: Admin notification recovery APIM status is ready."
