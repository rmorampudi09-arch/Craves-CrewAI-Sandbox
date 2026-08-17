#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
SUB_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
INT_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
SB_NS="${SERVICE_BUS_NAMESPACE:-sb-craves-prodlow-l3ing6}"
SB_TOPIC="${SERVICE_BUS_TOPIC:-craves-domain-events}"
APIM_HOST="${APIM_HOST:-api.craves.in}"
HEALTH_ATTEMPTS="${CRAVES_RUNTIME_HEALTH_ATTEMPTS:-6}"
HEALTH_SLEEP_SECONDS="${CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS:-10}"
HEALTH_MAX_TIME_SECONDS="${CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS:-30}"

fail() { echo "ERROR: $*" >&2; exit 1; }
warn() { echo "WARNING: $*" >&2; }
for tool in az jq curl; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_RUNTIME_HEALTH_ATTEMPTS must be a positive integer"
[[ "$HEALTH_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || fail "CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS must be a non-negative integer"
[[ "$HEALTH_MAX_TIME_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS must be a positive integer"

app_json() {
  az containerapp show -g "$RG" -n "$1" -o json
}

probe_up() {
  local FQDN="$1" LABEL="$2" HEALTH_PATH="$3" ATTEMPT BODY CODE STATUS
  local SAFE_PATH="${HEALTH_PATH//\//_}"
  BODY="/tmp/craves-runtime-health-${BASHPID}-${SAFE_PATH}.json"
  for ((ATTEMPT=1; ATTEMPT<=HEALTH_ATTEMPTS; ATTEMPT++)); do
    : >"$BODY"
    CODE=$(curl \
      --silent \
      --show-error \
      --connect-timeout 10 \
      --max-time "$HEALTH_MAX_TIME_SECONDS" \
      --output "$BODY" \
      --write-out '%{http_code}' \
      "https://${FQDN}${HEALTH_PATH}" || true)
    STATUS=$(jq -r '.status // empty' "$BODY" 2>/dev/null || true)
    if [[ "$CODE" == "200" && "$STATUS" == "UP" ]]; then
      rm -f "$BODY"
      echo "PASS: $LABEL ${HEALTH_PATH} -> UP attempt=$ATTEMPT/$HEALTH_ATTEMPTS"
      return 0
    fi
    echo "WAIT: $LABEL ${HEALTH_PATH} attempt=$ATTEMPT/$HEALTH_ATTEMPTS HTTP=${CODE:-curl-error} status=${STATUS:-unavailable}" >&2
    if (( ATTEMPT < HEALTH_ATTEMPTS )); then
      sleep "$HEALTH_SLEEP_SECONDS"
    fi
  done
  rm -f "$BODY"
  fail "$LABEL ${HEALTH_PATH} did not return HTTP 200 with status UP after $HEALTH_ATTEMPTS attempts"
}

healthy_app() {
  local APP="$1" LABEL="$2" JSON LATEST READY RUNNING HEALTH FQDN
  JSON=$(app_json "$APP")
  LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$JSON")
  READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$JSON")
  RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$JSON")
  FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$JSON")
  [[ -n "$LATEST" && "$LATEST" == "$READY" && "$RUNNING" == "Running" && -n "$FQDN" ]] \
    || fail "$LABEL is not ready: latest=$LATEST ready=$READY running=$RUNNING"
  HEALTH=$(az containerapp revision show -g "$RG" -n "$APP" --revision "$LATEST" --query properties.healthState -o tsv 2>/dev/null || true)
  [[ "$HEALTH" == "Healthy" ]] || fail "$LABEL latest revision is not Healthy: $HEALTH"

  probe_up "$FQDN" "$LABEL" "/actuator/health/liveness"
  probe_up "$FQDN" "$LABEL" "/actuator/health/readiness"
  echo "PASS: $LABEL healthy revision=$LATEST"
}

get_env_entry() {
  local APP="$1" NAME="$2"
  app_json "$APP" | jq -c --arg name "$NAME" \
    '(.properties.template.containers[0].env // []) | map(select(.name == $name)) | last // {}'
}

get_env_value() {
  local APP="$1" NAME="$2"
  get_env_entry "$APP" "$NAME" | jq -r '.value // ""'
}

get_env_secret_ref() {
  local APP="$1" NAME="$2"
  get_env_entry "$APP" "$NAME" | jq -r '.secretRef // ""'
}

require_env_present() {
  local APP="$1" NAME="$2" LABEL="$3" ENTRY
  ENTRY=$(get_env_entry "$APP" "$NAME")
  if [[ "$(jq -r '(.value // "") != "" or (.secretRef // "") != ""' <<<"$ENTRY")" != "true" ]]; then
    fail "$LABEL is missing required environment binding $NAME"
  fi
  echo "PASS: $LABEL has $NAME binding (value not displayed)"
}

require_secret_ref() {
  local APP="$1" NAME="$2" LABEL="$3" REF
  REF=$(get_env_secret_ref "$APP" "$NAME")
  [[ -n "$REF" ]] || fail "$LABEL $NAME must use a Container App secret reference; do not place the secret value directly in an environment variable"
  echo "PASS: $LABEL $NAME uses secretRef=$REF"
}

secret_keyvault_url() {
  local APP="$1" REF="$2"
  app_json "$APP" | jq -r --arg ref "$REF" \
    '(.properties.configuration.secrets // []) | map(select(.name == $ref)) | last | .keyVaultUrl // ""'
}

assert_not_true() {
  local APP="$1" NAME="$2" LABEL="$3" VALUE
  VALUE=$(get_env_value "$APP" "$NAME")
  [[ "${VALUE,,}" != "true" ]] || fail "$LABEL must not have $NAME=true for the sandbox rollout"
  echo "PASS: $LABEL $NAME is not enabled"
}

has_service_bus_connection_secret() {
  local APP="$1"
  [[ -n "$(get_env_secret_ref "$APP" SERVICE_BUS_CONNECTION_STRING)" ]]
}

require_bus_role() {
  local APP="$1" ROLE="$2" LABEL="$3" PRINCIPAL NS_SCOPE COUNT
  if has_service_bus_connection_secret "$APP"; then
    echo "PASS: $LABEL uses a Service Bus connection-string secret reference; managed-identity $ROLE check not required"
    return
  fi
  PRINCIPAL=$(app_json "$APP" | jq -r '.identity.principalId // ""')
  [[ -n "$PRINCIPAL" && "$PRINCIPAL" != "null" ]] || fail "$LABEL needs a system-assigned identity or SERVICE_BUS_CONNECTION_STRING secretRef"
  NS_SCOPE=$(az servicebus namespace show -g "$RG" -n "$SB_NS" --query id -o tsv)
  COUNT=$(az role assignment list --assignee-object-id "$PRINCIPAL" --all -o json \
    | jq --arg role "$ROLE" --arg scope "$NS_SCOPE" \
      '[.[] | select(.roleDefinitionName == $role and (.scope == $scope or (.scope | startswith($scope + "/"))))] | length')
  [[ "$COUNT" -gt 0 ]] || fail "$LABEL managed identity is missing $ROLE on the Service Bus namespace/topic scope"
  echo "PASS: $LABEL managed identity has $ROLE"
}

healthy_app "$SUB_APP" "Subscription Service"
healthy_app "$INT_APP" "Integration Service"
healthy_app "$ORDER_APP" "Order Service"

az servicebus namespace show -g "$RG" -n "$SB_NS" -o none
az servicebus topic show -g "$RG" --namespace-name "$SB_NS" -n "$SB_TOPIC" -o none
echo "PASS: existing Service Bus namespace/topic are reachable"

require_bus_role "$SUB_APP" "Azure Service Bus Data Sender" "Subscription Service"
require_bus_role "$SUB_APP" "Azure Service Bus Data Receiver" "Subscription Service"
require_bus_role "$INT_APP" "Azure Service Bus Data Sender" "Integration Service"
require_bus_role "$INT_APP" "Azure Service Bus Data Receiver" "Integration Service"
require_bus_role "$ORDER_APP" "Azure Service Bus Data Receiver" "Order Service"

require_env_present "$INT_APP" PAYMENT_PROVIDER_CLIENT_ID "Integration Service"
require_secret_ref "$INT_APP" PAYMENT_PROVIDER_CLIENT_KEY "Integration Service"
require_secret_ref "$SUB_APP" CRAVES_INTERNAL_SERVICE_SECRET "Subscription Service"
require_secret_ref "$ORDER_APP" CRAVES_INTERNAL_SERVICE_SECRET "Order Service"

ORDER_LEAD_HOURS=$(get_env_value "$SUB_APP" CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS)
[[ "$ORDER_LEAD_HOURS" =~ ^[0-9]+$ ]] \
  || fail "Subscription Service requires an approved CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS value before the recurring-order worker can be enabled"
(( ORDER_LEAD_HOURS >= 0 && ORDER_LEAD_HOURS <= 168 )) \
  || fail "CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS must be between 0 and 168"
echo "PASS: approved recurring-order dispatch lead is configured: ${ORDER_LEAD_HOURS} hour(s)"

SUB_SECRET_REF=$(get_env_secret_ref "$SUB_APP" CRAVES_INTERNAL_SERVICE_SECRET)
ORDER_SECRET_REF=$(get_env_secret_ref "$ORDER_APP" CRAVES_INTERNAL_SERVICE_SECRET)
SUB_KV_URL=$(secret_keyvault_url "$SUB_APP" "$SUB_SECRET_REF")
ORDER_KV_URL=$(secret_keyvault_url "$ORDER_APP" "$ORDER_SECRET_REF")
if [[ -n "$SUB_KV_URL" && -n "$ORDER_KV_URL" ]]; then
  [[ "$SUB_KV_URL" == "$ORDER_KV_URL" ]] || fail "Subscription and Order internal-secret references point to different Key Vault secret URLs"
  echo "PASS: Subscription and Order callbacks reference the same Key Vault internal secret"
else
  warn "Internal-secret values are hidden and at least one local Container App secret is not a Key Vault reference; equality cannot be proven read-only. The activation status checks will still verify worker health."
fi

ENVIRONMENT=$(get_env_value "$INT_APP" PAYMENT_PROVIDER_ENVIRONMENT)
[[ -z "$ENVIRONMENT" || "${ENVIRONMENT,,}" == "sandbox" ]] \
  || fail "Integration Service PAYMENT_PROVIDER_ENVIRONMENT must be sandbox (current configured value is not sandbox)"
assert_not_true "$INT_APP" CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED "Integration Service"
assert_not_true "$INT_APP" CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED "Integration Service"

echo "PASS: Cashfree rollout is sandbox-only; production activation/execution are off"

if command -v bash >/dev/null && [[ -f scripts/apim/status-subscription-payments-apim.sh ]]; then
  RG="$RG" APIM="${APIM_NAME:-apim-craves-prodlow-l3ing6}" INTEGRATION_APP="$INT_APP" \
    bash scripts/apim/status-subscription-payments-apim.sh
else
  warn "APIM status helper was not available in the current working directory"
fi

echo "============================================================"
echo "SUCCESS: SUBSCRIPTION SANDBOX RUNTIME PREFLIGHT PASSED"
echo "No secret value was printed or changed."
echo "No Container App flag was enabled by this preflight."
echo "============================================================"