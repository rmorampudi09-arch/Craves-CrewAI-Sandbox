#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
API_ID="${API_ID:-}"
DEFAULT_API_ID="${DEFAULT_API_ID:-craves-order-customer-v1}"
API_PATH="${API_PATH:-api/v1/orders}"
OPERATION_ID="${OPERATION_ID:-get-order-delivery-status}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_OPERATION_ROLLBACK="${CONFIRM_OPERATION_ROLLBACK:-false}"
DELETE_EMPTY_DEDICATED_API="${DELETE_EMPTY_DEDICATED_API:-false}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v az >/dev/null 2>&1 || fail "Azure CLI is required."

[[ "${CONFIRM_OPERATION_ROLLBACK,,}" == "true" ]] || fail "Set CONFIRM_OPERATION_ROLLBACK=true to remove the APIM delivery-status operation."

SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
[[ -n "$SUBSCRIPTION_ID" ]] || fail "No active Azure subscription is selected."

if [[ -z "$API_ID" ]]; then
  mapfile -t PATH_API_IDS < <(az apim api list \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --query "[?path=='${API_PATH}'].name" \
    -o tsv)
  (( ${#PATH_API_IDS[@]} == 1 )) || fail "Expected exactly one API at path $API_PATH; found ${#PATH_API_IDS[@]}."
  API_ID="${PATH_API_IDS[0]}"
fi

az apim api show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  -o none

EXISTING_PATH="$(az apim api show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  --query path \
  -o tsv)"
[[ "$EXISTING_PATH" == "$API_PATH" ]] || fail "API $API_ID is at path $EXISTING_PATH, not $API_PATH."

if ! az apim api operation show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  --operation-id "$OPERATION_ID" \
  >/dev/null 2>&1; then
  echo "Delivery-status APIM operation is already absent."
  exit 0
fi

MGMT_BASE="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis"
az rest \
  --method delete \
  --url "${MGMT_BASE}/${API_ID}/operations/${OPERATION_ID}?api-version=${API_VERSION}" \
  -o none

echo "Removed APIM operation $OPERATION_ID from API $API_ID."

if [[ "${DELETE_EMPTY_DEDICATED_API,,}" == "true" ]]; then
  [[ "$API_ID" == "$DEFAULT_API_ID" ]] || fail "Refusing to delete API $API_ID because it is not the dedicated default API $DEFAULT_API_ID."

  REMAINING_OPERATIONS="$(az apim api operation list \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --query 'length(@)' \
    -o tsv)"
  [[ "${REMAINING_OPERATIONS:-0}" == "0" ]] || fail "API $API_ID still contains $REMAINING_OPERATIONS operation(s); it will not be deleted."

  az apim api delete \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --yes \
    -o none
  echo "Deleted empty dedicated API $API_ID."
else
  echo "API $API_ID was preserved. DELETE_EMPTY_DEDICATED_API is false."
fi

echo "SUCCESS: Delivery-status APIM rollback completed without changing Order, Integration, Service Bus, or provider state."
