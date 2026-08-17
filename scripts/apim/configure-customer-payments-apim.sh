#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
INTEGRATION_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
API_PATH="${API_PATH:-api/v1/payments}"
NEW_API_ID="${NEW_API_ID:-craves-customer-payments-v1}"
API_VERSION="${API_VERSION:-2022-08-01}"
HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-12}"
HEALTH_SLEEP_SECONDS="${HEALTH_SLEEP_SECONDS:-10}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/customer-payments/customer-payment-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "Policy template is missing"

SUBSCRIPTION_ID=$(az account show --query id -o tsv --only-show-errors)
APP_JSON=$(az containerapp show -g "$RG" -n "$INTEGRATION_APP" -o json --only-show-errors)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "Integration Service is not ready"

TOKEN_REVOCATION_ENABLED=$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "CRAVES_TOKEN_REVOCATION_ENABLED")][0].value // "false"' <<<"$APP_JSON")
TOKEN_REVOCATION_ENABLED=${TOKEN_REVOCATION_ENABLED,,}

probe_health() {
  local PATH_SUFFIX="$1" LABEL="$2"
  local attempt body code status

  for attempt in $(seq 1 "$HEALTH_ATTEMPTS"); do
    body=$(mktemp)
    code=$(curl \
      --silent \
      --show-error \
      --connect-timeout 10 \
      --max-time 30 \
      --output "$body" \
      --write-out '%{http_code}' \
      "https://$FQDN$PATH_SUFFIX" || true)
    status=$(jq -r '.status // "UNKNOWN"' "$body" 2>/dev/null || echo "NON_JSON")

    if [[ "$code" == "200" && "$status" == "UP" ]]; then
      rm -f "$body"
      echo "Health check passed: $LABEL ($PATH_SUFFIX) -> UP"
      return 0
    fi

    echo "Health check $LABEL attempt $attempt/$HEALTH_ATTEMPTS -> HTTP ${code:-curl-error}, status=$status" >&2
    if jq -e . "$body" >/dev/null 2>&1; then
      jq -c '{status:(.status // "UNKNOWN"),components:((.components // {}) | with_entries(.value={status:(.value.status // "UNKNOWN")}))}' "$body" >&2 || true
    fi
    rm -f "$body"

    if (( attempt < HEALTH_ATTEMPTS )); then
      sleep "$HEALTH_SLEEP_SECONDS"
    fi
  done

  fail "Required Integration Service health check failed: $LABEL ($PATH_SUFFIX)"
}

# The payment-specific health group is defined by the Integration Service itself and contains
# readinessState + db. This avoids exposing all health components while still proving PostgreSQL
# is available before APIM payment routes are changed.
probe_health "/actuator/health/liveness" "liveness"
probe_health "/actuator/health/readiness" "readiness"
probe_health "/actuator/health/payments" "payments dependency group (readiness + PostgreSQL)"

if [[ "$TOKEN_REVOCATION_ENABLED" == "true" ]]; then
  probe_health "/actuator/health/token-revocation" "token-revocation dependency group (readiness + Redis)"
else
  echo "Token revocation is disabled; Redis is not a required dependency for this payment APIM gate."
fi

mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv --only-show-errors)
(( ${#API_IDS[@]} <= 1 )) || fail "Multiple APIM APIs own $API_PATH"
BACKEND="https://${FQDN}/api/v1/payments"
if (( ${#API_IDS[@]} == 0 )); then
  az apim api create \
    -g "$RG" \
    --service-name "$APIM" \
    --api-id "$NEW_API_ID" \
    --display-name "Craves Customer Payments API" \
    --path "$API_PATH" \
    --service-url "$BACKEND" \
    --protocols https \
    --subscription-required false \
    -o none \
    --only-show-errors
  API_ID="$NEW_API_ID"
else
  API_ID="${API_IDS[0]}"
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv --only-show-errors)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing payment API requires a subscription key; this script will not relax it"
fi

MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
for SCOPE_URL in \
  "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}" \
  "${MGMT}/policies/policy?api-version=${API_VERSION}"; do
  POLICY=$(az rest --method get --url "$SCOPE_URL" --query properties.value -o tsv 2>/dev/null || true)
  [[ "$POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited backend-id policy cannot be safely overridden"
done

operation_inventory() {
  local OPS_JSON
  if ! OPS_JSON=$(az apim api operation list \
    -g "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --only-show-errors \
    -o json); then
    fail "Unable to list APIM operations for API $API_ID; refusing to create or overwrite operations without a reliable inventory"
  fi
  printf '%s\n' "$OPS_JSON"
}

reconcile_operation() {
  local RESULT_VAR="$1" PREFERRED_ID="$2" METHOD="$3" TEMPLATE="$4" DISPLAY="$5" PARAMS="$6"
  local OPS_JSON OP_ID BODY RENDERED POLICY_BODY
  local EXISTING_METHOD EXISTING_TEMPLATE
  local -a MATCHING_IDS=()
  local -a PREFERRED_MATCHES=()

  [[ "$RESULT_VAR" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail "Invalid result variable name: $RESULT_VAR"
  OPS_JSON="$(operation_inventory)"

  mapfile -t MATCHING_IDS < <(
    jq -r \
      --arg method "$METHOD" \
      --arg template "$TEMPLATE" \
      '.[]?
       | select(
           (((.method // .properties.method // "") | ascii_upcase) == ($method | ascii_upcase))
           and ((.urlTemplate // .properties.urlTemplate // "") == $template)
         )
       | (.name // ((.id // "") | split("/") | last) // empty)' \
      <<<"$OPS_JSON"
  )

  if (( ${#MATCHING_IDS[@]} > 1 )); then
    fail "Multiple APIM operations already own $METHOD $TEMPLATE: ${MATCHING_IDS[*]}"
  fi

  if (( ${#MATCHING_IDS[@]} == 1 )); then
    OP_ID="${MATCHING_IDS[0]}"
    if [[ "$OP_ID" != "$PREFERRED_ID" ]]; then
      echo "INFO: Reusing existing APIM operation '$OP_ID' for $METHOD $TEMPLATE; preferred ID '$PREFERRED_ID' will not be created." >&2
    fi
  else
    mapfile -t PREFERRED_MATCHES < <(
      jq -c \
        --arg preferred "$PREFERRED_ID" \
        '.[]?
         | select((.name // ((.id // "") | split("/") | last) // "") == $preferred)' \
        <<<"$OPS_JSON"
    )

    if (( ${#PREFERRED_MATCHES[@]} > 1 )); then
      fail "Multiple APIM operations unexpectedly use preferred operation ID '$PREFERRED_ID'"
    fi

    if (( ${#PREFERRED_MATCHES[@]} == 1 )); then
      EXISTING_METHOD=$(jq -r '(.method // .properties.method // "")' <<<"${PREFERRED_MATCHES[0]}")
      EXISTING_TEMPLATE=$(jq -r '(.urlTemplate // .properties.urlTemplate // "")' <<<"${PREFERRED_MATCHES[0]}")
      if [[ "${EXISTING_METHOD^^}" != "${METHOD^^}" || "$EXISTING_TEMPLATE" != "$TEMPLATE" ]]; then
        fail "Operation ID '$PREFERRED_ID' already exists for $EXISTING_METHOD $EXISTING_TEMPLATE; refusing to overwrite it with $METHOD $TEMPLATE"
      fi
      OP_ID="$PREFERRED_ID"
    else
      OP_ID="$PREFERRED_ID"
      echo "INFO: No APIM operation owns $METHOD $TEMPLATE; creating preferred operation '$OP_ID'." >&2
    fi
  fi

  BODY=$(mktemp)
  RENDERED=$(mktemp)
  POLICY_BODY=$(mktemp)
  cat >"$BODY" <<JSON
{"properties":{"displayName":"$DISPLAY","method":"$METHOD","urlTemplate":"$TEMPLATE","templateParameters":$PARAMS,"responses":[{"statusCode":200,"description":"Owned customer payment response"},{"statusCode":401,"description":"Authentication required"},{"statusCode":404,"description":"Payment not found"}]}}
JSON

  echo "INFO: Reconciling APIM operation '$OP_ID' as $METHOD $TEMPLATE." >&2
  if ! az rest --method put --url "${MGMT}/operations/${OP_ID}?api-version=${API_VERSION}" --body @"$BODY" -o none >/dev/null; then
    rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
    fail "Failed to create/update operation $OP_ID"
  fi

  sed "s|__PAYMENT_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"

  echo "INFO: Applying customer-payment policy to APIM operation '$OP_ID'." >&2
  if ! az rest --method put --url "${MGMT}/operations/${OP_ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none >/dev/null; then
    rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
    fail "Failed to apply policy to operation $OP_ID"
  fi

  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
  printf -v "$RESULT_VAR" '%s' "$OP_ID"
}

PAYMENT_PARAM='[{"name":"paymentOrderId","type":"string","required":true}]'
CREATE_PAYMENT_OP=''
GET_PAYMENT_OP=''
VERIFY_PAYMENT_OP=''

reconcile_operation CREATE_PAYMENT_OP "create-customer-payment-order" "POST" "/orders" "Create customer payment order" '[]'
reconcile_operation GET_PAYMENT_OP "get-customer-payment-order" "GET" "/orders/{paymentOrderId}" "Get customer payment order" "$PAYMENT_PARAM"
reconcile_operation VERIFY_PAYMENT_OP "verify-customer-payment-order" "POST" "/orders/{paymentOrderId}/verify" "Verify customer payment order" "$PAYMENT_PARAM"

for ID in "$CREATE_PAYMENT_OP" "$GET_PAYMENT_OP" "$VERIFY_PAYMENT_OP"; do
  [[ -n "$ID" && "$ID" != *$'\n'* ]] || fail "Resolved APIM payment operation ID is invalid"
  az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o none --only-show-errors
  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Authorization"* && "$POLICY" == *"no-store"* ]] || fail "Operation $ID policy verification failed"
done

echo "SUCCESS: Customer payment operations configured on APIM API $API_ID (actual IDs: $CREATE_PAYMENT_OP, $GET_PAYMENT_OP, $VERIFY_PAYMENT_OP). Webhook operations were not changed."
