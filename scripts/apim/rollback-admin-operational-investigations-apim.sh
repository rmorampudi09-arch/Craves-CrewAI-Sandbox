#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
API_ID="${API_ID:-craves-admin-operational-investigations-v1}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_ROLLBACK="${CONFIRM_APIM_ROLLBACK:-false}"
DELETE_EMPTY_API="${DELETE_EMPTY_API:-false}"

fail() { echo "ERROR: $*" >&2; exit 1; }
command -v az >/dev/null || fail "az is required"
[[ "${CONFIRM_APIM_ROLLBACK,,}" == "true" ]] || fail "Set CONFIRM_APIM_ROLLBACK=true for the controlled rollback"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"

if ! az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o none 2>/dev/null; then
  echo "SUCCESS: Approved admin investigation API is already absent."
  exit 0
fi

APPROVED_OPERATIONS=(
  get-admin-order-investigation
  get-admin-payment-investigation
  get-admin-refund-investigation
  get-admin-delivery-command-investigation
)

for OPERATION_ID in "${APPROVED_OPERATIONS[@]}"; do
  if az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$OPERATION_ID" -o none 2>/dev/null; then
    az rest --method delete --url "${MGMT}/operations/${OPERATION_ID}?api-version=${API_VERSION}" -o none
    echo "Removed operation: $OPERATION_ID"
  fi
done

mapfile -t REMAINING < <(az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query '[].name' -o tsv)
if (( ${#REMAINING[@]} == 0 )) && [[ "${DELETE_EMPTY_API,,}" == "true" ]]; then
  az rest --method delete --url "${MGMT}?api-version=${API_VERSION}" -o none
  echo "Removed empty API: $API_ID"
elif (( ${#REMAINING[@]} > 0 )); then
  echo "Preserved API because non-module operations remain: ${REMAINING[*]}"
else
  echo "Preserved empty API. Set DELETE_EMPTY_API=true only after confirming no other owner depends on it."
fi

echo "SUCCESS: Admin operational investigation APIM rollback completed without touching backend services or data."
