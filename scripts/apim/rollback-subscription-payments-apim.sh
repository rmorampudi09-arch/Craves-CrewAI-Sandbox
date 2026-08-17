#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_ROLLBACK="${CONFIRM_APIM_ROLLBACK:-false}"
SUBSCRIPTION_ID=$(az account show --query id -o tsv)

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ "${CONFIRM_APIM_ROLLBACK,,}" == "true" ]] || fail "Set CONFIRM_APIM_ROLLBACK=true for the controlled APIM rollback"

api_id_for_path() {
  local PATH_VALUE="$1"
  local -a IDS
  mapfile -t IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${PATH_VALUE}'].name" -o tsv)
  (( ${#IDS[@]} <= 1 )) || fail "Multiple APIM APIs own ${PATH_VALUE}"
  if (( ${#IDS[@]} == 1 )); then
    printf '%s' "${IDS[0]}"
  fi
}

remove_route() {
  local PATH_VALUE="$1" METHOD="$2" TEMPLATE="$3"
  local API_ID
  API_ID=$(api_id_for_path "$PATH_VALUE")
  if [[ -z "$API_ID" ]]; then
    echo "SKIP: no API owns ${PATH_VALUE}"
    return
  fi
  local -a MATCHES
  mapfile -t MATCHES < <(
    az apim api operation list -g "$RG" --service-name "$APIM" --api-id "$API_ID" -o json \
      | jq -r --arg method "${METHOD^^}" --arg template "$TEMPLATE" \
          '.[] | select((.method | ascii_upcase) == $method and .urlTemplate == $template) | .name'
  )
  (( ${#MATCHES[@]} <= 1 )) || fail "Multiple operations own ${METHOD^^} ${TEMPLATE} in API $API_ID"
  if (( ${#MATCHES[@]} == 0 )); then
    echo "SKIP: ${METHOD^^} ${PATH_VALUE}${TEMPLATE} is absent"
    return
  fi
  local MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
  az rest --method delete --url "${MGMT}/operations/${MATCHES[0]}?api-version=${API_VERSION}" -o none
  echo "REMOVED: ${METHOD^^} ${PATH_VALUE}${TEMPLATE} operation ${MATCHES[0]}"
}

remove_route "api/v1/subscription-payments" GET "/subscriptions/{subscriptionId}"
remove_route "api/v1/subscription-payments" GET "/invoices/{invoiceId}"
remove_route "api/v1/subscription-payments" POST "/invoices/{invoiceId}/orders"
remove_route "api/v1/payments" POST "/webhooks/cashfree"

echo "SUCCESS: Subscription payment/Cashfree webhook APIM operations removed. API containers were retained."
