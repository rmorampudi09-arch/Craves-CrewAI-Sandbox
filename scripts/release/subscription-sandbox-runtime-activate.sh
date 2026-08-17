#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
SUB_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
INT_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
SB_NS="${SERVICE_BUS_NAMESPACE:-sb-craves-prodlow-l3ing6}"
SB_FQNS="${SERVICE_BUS_FQNS:-sb-craves-prodlow-l3ing6.servicebus.windows.net}"
SB_TOPIC="${SERVICE_BUS_TOPIC:-craves-domain-events}"
APIM_HOST="${APIM_HOST:-api.craves.in}"
CONFIRM_ACTIVATION="${CONFIRM_SUBSCRIPTION_SANDBOX_ACTIVATION:-false}"
ACTIVATION_ID="${ACTIVATION_ID:-${BUILD_BUILDID:-manual-$(date -u +%Y%m%d%H%M%S)}}"
SNAPSHOT_FILE="${SNAPSHOT_FILE:-}"
MAX_ATTEMPTS="${CRAVES_SUBSCRIPTION_ACTIVATION_MAX_ATTEMPTS:-150}"
POLL_SECONDS="${CRAVES_SUBSCRIPTION_ACTIVATION_POLL_SECONDS:-10}"
HEALTH_ATTEMPTS="${CRAVES_RUNTIME_HEALTH_ATTEMPTS:-6}"
HEALTH_SLEEP_SECONDS="${CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS:-10}"
HEALTH_MAX_TIME_SECONDS="${CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS:-30}"

PAYMENT_REQUEST_SUB="integration-service-subscription-payment-requested"
PAYMENT_STATUS_SUB="subscription-service-payment-status-changed"
ORDER_REQUEST_SUB="order-service-subscription-order-requested"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl bash; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ "${CONFIRM_ACTIVATION,,}" == "true" ]] || fail "Set CONFIRM_SUBSCRIPTION_SANDBOX_ACTIVATION=true for this controlled sandbox activation"
[[ "$MAX_ATTEMPTS" =~ ^[0-9]+$ && "$MAX_ATTEMPTS" -ge 1 && "$MAX_ATTEMPTS" -le 300 ]] || fail "Invalid activation max-attempts"
[[ "$POLL_SECONDS" =~ ^[0-9]+$ && "$POLL_SECONDS" -ge 2 && "$POLL_SECONDS" -le 60 ]] || fail "Invalid activation poll interval"
[[ "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_RUNTIME_HEALTH_ATTEMPTS must be a positive integer"
[[ "$HEALTH_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || fail "CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS must be a non-negative integer"
[[ "$HEALTH_MAX_TIME_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS must be a positive integer"

export RESOURCE_GROUP="$RG" SUBSCRIPTION_APP="$SUB_APP" INTEGRATION_APP="$INT_APP" ORDER_APP="$ORDER_APP"
export SERVICE_BUS_NAMESPACE="$SB_NS" SERVICE_BUS_TOPIC="$SB_TOPIC" APIM_HOST="$APIM_HOST"
export APIM_NAME="${APIM_NAME:-apim-craves-prodlow-l3ing6}"
export CRAVES_RUNTIME_HEALTH_ATTEMPTS="$HEALTH_ATTEMPTS"
export CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS="$HEALTH_SLEEP_SECONDS"
export CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS="$HEALTH_MAX_TIME_SECONDS"
bash scripts/release/subscription-sandbox-runtime-preflight.sh

app_json() { az containerapp show -g "$RG" -n "$1" -o json; }
app_fqdn() { app_json "$1" | jq -r '.properties.configuration.ingress.fqdn // ""'; }

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

snapshot() {
  [[ -n "$SNAPSHOT_FILE" ]] || return 0
  mkdir -p "$(dirname "$SNAPSHOT_FILE")"
  local SUB_JSON INT_JSON ORDER_JSON
  SUB_JSON=$(app_json "$SUB_APP")
  INT_JSON=$(app_json "$INT_APP")
  ORDER_JSON=$(app_json "$ORDER_APP")
  jq -n \
    --arg activationId "$ACTIVATION_ID" \
    --argjson sub "$SUB_JSON" \
    --argjson integration "$INT_JSON" \
    --argjson order "$ORDER_JSON" \
    '{
      activationId:$activationId,
      capturedAt:(now|todate),
      apps:[
        {name:"subscription",revision:$sub.properties.latestReadyRevisionName,image:$sub.properties.template.containers[0].image,env:[($sub.properties.template.containers[0].env // [])[] | select(.name|IN(
          "CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_ENABLED","CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED","CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED","CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED","CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED","CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED","CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED"
        )) | {name,value:(.value // "<secret-ref>")}]},
        {name:"integration",revision:$integration.properties.latestReadyRevisionName,image:$integration.properties.template.containers[0].image,env:[($integration.properties.template.containers[0].env // [])[] | select(.name|IN(
          "PAYMENT_PROVIDER_ENVIRONMENT","CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED","CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED","CRAVES_CASHFREE_WEBHOOK_INGRESS_ENABLED","CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED","CRAVES_SUBSCRIPTION_PAYMENT_CONSUMER_ENABLED","CRAVES_SUBSCRIPTION_PAYMENT_STATUS_PUBLISHER_ENABLED","CRAVES_PAYMENT_ORDER_API_ENABLED"
        )) | {name,value:(.value // "<secret-ref>")}]},
        {name:"order",revision:$order.properties.latestReadyRevisionName,image:$order.properties.template.containers[0].image,env:[($order.properties.template.containers[0].env // [])[] | select(.name|IN(
          "CRAVES_SUBSCRIPTION_ORDER_CONSUMER_ENABLED","CRAVES_SUBSCRIPTION_ORDER_CALLBACK_WORKER_ENABLED"
        )) | {name,value:(.value // "<secret-ref>")}]}
      ]
    }' >"$SNAPSHOT_FILE"
  echo "Pre-activation non-secret snapshot written: $SNAPSHOT_FILE"
}

ensure_subscription_rule() {
  local SUB="$1" RULE="$2" FILTER="$3"
  if ! az servicebus topic subscription show -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" -n "$SUB" -o none 2>/dev/null; then
    az servicebus topic subscription create \
      -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" -n "$SUB" \
      --max-delivery-count 10 \
      --enable-dead-lettering-on-message-expiration true \
      --enable-batched-operations true -o none
    echo "CREATED: Service Bus subscription $SUB"
  fi

  local CURRENT=""
  CURRENT=$(az servicebus topic subscription rule show \
    -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" \
    --subscription-name "$SUB" -n "$RULE" --query sqlFilter.sqlExpression -o tsv 2>/dev/null || true)
  if [[ "$CURRENT" != "$FILTER" ]]; then
    if [[ -n "$CURRENT" ]]; then
      az servicebus topic subscription rule delete \
        -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" \
        --subscription-name "$SUB" -n "$RULE" -o none
    fi
    az servicebus topic subscription rule create \
      -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" \
      --subscription-name "$SUB" -n "$RULE" --filter-sql-expression "$FILTER" -o none
    echo "CONFIGURED: $SUB rule $RULE"
  fi
  az servicebus topic subscription rule delete \
    -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" \
    --subscription-name "$SUB" -n '$Default' -o none 2>/dev/null || true
}

wait_target_revision() {
  local APP="$1" LABEL="$2" PREVIOUS="$3"
  local ATTEMPT JSON LATEST READY APP_RUNNING REV_JSON MARKER HEALTH PROVISIONING REV_RUNNING FQDN
  for ((ATTEMPT=1; ATTEMPT<=MAX_ATTEMPTS; ATTEMPT++)); do
    JSON=$(app_json "$APP")
    LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$JSON")
    READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$JSON")
    APP_RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$JSON")
    if [[ -z "$LATEST" || "$LATEST" == "$PREVIOUS" ]]; then
      echo "WAIT: $LABEL new revision not visible yet attempt=$ATTEMPT/$MAX_ATTEMPTS"
      sleep "$POLL_SECONDS"
      continue
    fi
    REV_JSON=$(az containerapp revision show -g "$RG" -n "$APP" --revision "$LATEST" -o json 2>/dev/null || echo '{}')
    MARKER=$(jq -r --arg name CRAVES_SUBSCRIPTION_SANDBOX_ACTIVATION_ID \
      '(.properties.template.containers[0].env // []) | map(select(.name==$name)) | last | .value // ""' <<<"$REV_JSON")
    HEALTH=$(jq -r '.properties.healthState // ""' <<<"$REV_JSON")
    PROVISIONING=$(jq -r '.properties.provisioningState // ""' <<<"$REV_JSON")
    REV_RUNNING=$(jq -r '.properties.runningState // ""' <<<"$REV_JSON")
    if [[ "$MARKER" != "$ACTIVATION_ID" ]]; then
      fail "$LABEL latest revision $LATEST was changed by another rollout (activation marker mismatch)"
    fi
    echo "WAIT: $LABEL revision=$LATEST ready=$READY health=$HEALTH provisioning=$PROVISIONING revisionRunning=$REV_RUNNING appRunning=$APP_RUNNING attempt=$ATTEMPT/$MAX_ATTEMPTS"
    if [[ "$PROVISIONING" == "Failed" || "$REV_RUNNING" == "Failed" || "$REV_RUNNING" == "ActivationFailed" ]]; then
      fail "$LABEL activation entered a terminal failure state"
    fi
    if [[ "$LATEST" == "$READY" && "$HEALTH" == "Healthy" && "$PROVISIONING" == "Provisioned" && "$APP_RUNNING" == "Running" ]]; then
      FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$JSON")
      [[ -n "$FQDN" ]] || fail "$LABEL target revision FQDN is missing"
      probe_up "$FQDN" "$LABEL" "/actuator/health/liveness"
      probe_up "$FQDN" "$LABEL" "/actuator/health/readiness"
      echo "PASS: $LABEL target revision healthy: $LATEST"
      return
    fi
    sleep "$POLL_SECONDS"
  done
  fail "$LABEL did not become ready within $((MAX_ATTEMPTS * POLL_SECONDS)) seconds"
}

update_app() {
  local APP="$1" LABEL="$2"; shift 2
  local PREVIOUS
  PREVIOUS=$(app_json "$APP" | jq -r '.properties.latestRevisionName // ""')
  az containerapp update -g "$RG" -n "$APP" \
    --set-env-vars "CRAVES_SUBSCRIPTION_SANDBOX_ACTIVATION_ID=${ACTIVATION_ID}" "$@" \
    --only-show-errors -o none
  wait_target_revision "$APP" "$LABEL" "$PREVIOUS"
}

snapshot

ensure_subscription_rule "$PAYMENT_REQUEST_SUB" subscription-payment-requested \
  "event_type = 'SUBSCRIPTION_PAYMENT_REQUESTED' OR eventType = 'SUBSCRIPTION_PAYMENT_REQUESTED'"
ensure_subscription_rule "$PAYMENT_STATUS_SUB" subscription-payment-status \
  "event_type = 'SUBSCRIPTION_PAYMENT_STATUS_CHANGED' OR eventType = 'SUBSCRIPTION_PAYMENT_STATUS_CHANGED'"
ensure_subscription_rule "$ORDER_REQUEST_SUB" subscription-order-requested \
  "event_type = 'SUBSCRIPTION_ORDER_REQUESTED' OR eventType = 'SUBSCRIPTION_ORDER_REQUESTED'"
echo "PASS: Service Bus subscription-payment topology is ready"

SUB_FQDN=$(app_fqdn "$SUB_APP")
[[ -n "$SUB_FQDN" ]] || fail "Subscription Service FQDN was not resolved"
SUB_BASE="https://${SUB_FQDN}"

# Stage 1: ingress and request consumer first. Provider execution remains unavailable.
update_app "$INT_APP" "Integration downstream" \
  "PAYMENT_PROVIDER_ENVIRONMENT=sandbox" \
  "PAYMENT_PROVIDER_SANDBOX_BASE_URL=https://sandbox.cashfree.com" \
  "PAYMENT_PROVIDER_API_VERSION=2025-01-01" \
  "PAYMENT_PROVIDER_WEBHOOK_URL=https://${APIM_HOST}/api/v1/payments/webhooks/cashfree" \
  "CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED=false" \
  "CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false" \
  "SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=${SB_FQNS}" \
  "SERVICE_BUS_TOPIC_NAME=${SB_TOPIC}" \
  "SERVICE_BUS_SUBSCRIPTION_PAYMENT_REQUESTED_SUBSCRIPTION=${PAYMENT_REQUEST_SUB}" \
  "CRAVES_SUBSCRIPTION_SERVICE_BASE_URL=${SUB_BASE}" \
  "CRAVES_CASHFREE_WEBHOOK_INGRESS_ENABLED=true" \
  "CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_PAYMENT_CONSUMER_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_PAYMENT_STATUS_PUBLISHER_ENABLED=false" \
  "CRAVES_PAYMENT_ORDER_API_ENABLED=false"

# Stage 2: payment-status downstream consumer before the Integration publisher.
update_app "$SUB_APP" "Subscription payment-status downstream" \
  "CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=${SB_FQNS}" \
  "CRAVES_DOMAIN_EVENTS_TOPIC_NAME=${SB_TOPIC}" \
  "SERVICE_BUS_SUBSCRIPTION_PAYMENT_STATUS_SUBSCRIPTION=${PAYMENT_STATUS_SUB}" \
  "CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED=false"

# Stage 3: Order consumer/callback before Subscription publishes order requests.
update_app "$ORDER_APP" "Order subscription downstream" \
  "CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=${SB_FQNS}" \
  "CRAVES_DOMAIN_EVENTS_TOPIC_NAME=${SB_TOPIC}" \
  "SERVICE_BUS_SUBSCRIPTION_ORDER_REQUESTED_SUBSCRIPTION=${ORDER_REQUEST_SUB}" \
  "CRAVES_SUBSCRIPTION_INTERNAL_BASE_URL=${SUB_BASE}" \
  "CRAVES_SUBSCRIPTION_ORDER_CONSUMER_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_ORDER_CALLBACK_WORKER_ENABLED=true"

# Stage 4: only after webhook + downstream status consumer are healthy, expose sandbox order creation and publish status.
update_app "$INT_APP" "Integration sandbox payment execution" \
  "PAYMENT_PROVIDER_ENVIRONMENT=sandbox" \
  "CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED=false" \
  "CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_PAYMENT_STATUS_PUBLISHER_ENABLED=true" \
  "CRAVES_PAYMENT_ORDER_API_ENABLED=true"

# Stage 5: activate all Subscription upstream schedulers/publishers last.
update_app "$SUB_APP" "Subscription upstream runtime" \
  "CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED=true" \
  "CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED=true"

echo "============================================================"
echo "SUCCESS: SUBSCRIPTION SANDBOX RUNTIME ACTIVATED"
echo "Cashfree environment: sandbox"
echo "Cashfree production activation: false"
echo "Cashfree production payment execution: false"
echo "Activation ID: $ACTIVATION_ID"
echo "============================================================"