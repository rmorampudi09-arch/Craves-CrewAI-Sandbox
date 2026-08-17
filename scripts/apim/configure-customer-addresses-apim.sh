#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
USER_APP="${USER_APP:-ca-craves-user-chef-service-prod}"
API_PATH="${API_PATH:-api/v1/customer}"
NEW_API_ID="${NEW_API_ID:-craves-customer-profile-v1}"
API_VERSION="${API_VERSION:-2022-08-01}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/customer-addresses/customer-address-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "Policy template is missing"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$USER_APP" -o json)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "User/Chef Service is not ready"
curl \
  --silent \
  --show-error \
  --fail \
  --retry 6 \
  --retry-delay 5 \
  --retry-all-errors \
  --max-time 20 \
  "https://$FQDN/actuator/health/readiness" \
  >/dev/null

mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} <= 1 )) || fail "Multiple APIM APIs own $API_PATH"
BACKEND="https://${FQDN}/api/v1/customer"
if (( ${#API_IDS[@]} == 0 )); then
  az apim api create -g "$RG" --service-name "$APIM" --api-id "$NEW_API_ID" --display-name "Craves Customer Profile API" --path "$API_PATH" --service-url "$BACKEND" --protocols https --subscription-required false -o none
  API_ID="$NEW_API_ID"
else
  API_ID="${API_IDS[0]}"
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing customer API requires a subscription key; this script will not relax it"
fi

MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
for SCOPE_URL in \
  "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}" \
  "${MGMT}/policies/policy?api-version=${API_VERSION}"; do
  POLICY=$(az rest --method get --url "$SCOPE_URL" --query properties.value -o tsv 2>/dev/null || true)
  [[ "$POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited backend-id policy cannot be safely overridden"
done

resolve_operation_id() {
  local DESIRED_ID="$1" METHOD="$2" TEMPLATE="$3"
  local OPERATIONS_JSON ACTUAL_ID
  local -a MATCHING_IDS DESIRED_RECORDS

  OPERATIONS_JSON=$(az apim api operation list \
    -g "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    -o json)

  mapfile -t MATCHING_IDS < <(
    jq -r \
      --arg method "$METHOD" \
      --arg template "$TEMPLATE" '
        .[]
        | select(((.method // .properties.method // "") | ascii_upcase) == ($method | ascii_upcase))
        | select(
            ("/" + ((.urlTemplate // .properties.urlTemplate // "") | ltrimstr("/")))
            ==
            ("/" + ($template | ltrimstr("/")))
          )
        | (.name // ((.id // "") | split("/")[-1]))
      ' <<<"$OPERATIONS_JSON"
  )

  (( ${#MATCHING_IDS[@]} <= 1 )) || fail "Multiple APIM operations already use $METHOD $TEMPLATE"

  mapfile -t DESIRED_RECORDS < <(
    jq -r \
      --arg id "$DESIRED_ID" '
        .[]
        | select((.name // ((.id // "") | split("/")[-1])) == $id)
        | [(.method // .properties.method // ""), (.urlTemplate // .properties.urlTemplate // "")]
        | @tsv
      ' <<<"$OPERATIONS_JSON"
  )

  (( ${#DESIRED_RECORDS[@]} <= 1 )) || fail "APIM returned duplicate records for operation ID $DESIRED_ID"

  if (( ${#MATCHING_IDS[@]} == 1 )); then
    ACTUAL_ID="${MATCHING_IDS[0]}"
    [[ -n "$ACTUAL_ID" ]] || fail "Existing APIM operation for $METHOD $TEMPLATE has no operation ID"

    if (( ${#DESIRED_RECORDS[@]} == 1 )) && [[ "$ACTUAL_ID" != "$DESIRED_ID" ]]; then
      fail "Operation ID $DESIRED_ID already belongs to another route; refusing to overwrite it"
    fi

    echo "Reusing existing APIM operation $ACTUAL_ID for $METHOD $TEMPLATE" >&2
    printf '%s\n' "$ACTUAL_ID"
    return
  fi

  if (( ${#DESIRED_RECORDS[@]} == 1 )); then
    fail "Operation ID $DESIRED_ID already exists with a different method or URL template; refusing to overwrite it"
  fi

  echo "Creating APIM operation $DESIRED_ID for $METHOD $TEMPLATE" >&2
  printf '%s\n' "$DESIRED_ID"
}

CONFIGURED_OPERATION_IDS=()

put_operation() {
  local DESIRED_ID="$1" METHOD="$2" TEMPLATE="$3" DISPLAY="$4" PARAMS="$5" STATUS="$6"
  local ACTUAL_ID BODY RENDERED POLICY_BODY

  ACTUAL_ID=$(resolve_operation_id "$DESIRED_ID" "$METHOD" "$TEMPLATE")
  BODY=$(mktemp)
  RENDERED=$(mktemp)
  POLICY_BODY=$(mktemp)

  cat >"$BODY" <<JSON
{"properties":{"displayName":"$DISPLAY","method":"$METHOD","urlTemplate":"$TEMPLATE","templateParameters":$PARAMS,"responses":[{"statusCode":$STATUS,"description":"Customer address response"},{"statusCode":401,"description":"Authentication required"},{"statusCode":404,"description":"Address not found"}]}}
JSON

  az rest --method put --url "${MGMT}/operations/${ACTUAL_ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__CUSTOMER_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${ACTUAL_ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none

  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
  CONFIGURED_OPERATION_IDS+=("$ACTUAL_ID")
}

ADDRESS_PARAM='[{"name":"addressId","type":"string","required":true}]'
put_operation "list-customer-addresses" "GET" "/addresses" "List customer addresses" '[]' 200
put_operation "create-customer-address" "POST" "/addresses" "Create customer address" '[]' 200
put_operation "get-customer-address" "GET" "/addresses/{addressId}" "Get customer address" "$ADDRESS_PARAM" 200
put_operation "update-customer-address" "PUT" "/addresses/{addressId}" "Update customer address" "$ADDRESS_PARAM" 200
put_operation "delete-customer-address" "DELETE" "/addresses/{addressId}" "Delete customer address" "$ADDRESS_PARAM" 204
put_operation "recommend-customer-location" "GET" "/addresses/recommendation" "Recommend customer location" '[]' 200
put_operation "reverse-geocode-customer-address" "POST" "/addresses/reverse-geocode" "Reverse geocode customer address" '[]' 200

for ID in "${CONFIGURED_OPERATION_IDS[@]}"; do
  az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o none
  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Authorization"* && "$POLICY" == *"no-store"* ]] || fail "Operation $ID policy verification failed"
done

echo "SUCCESS: Customer address operations configured on APIM API $API_ID."
