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
APIM_NAME="${APIM_NAME:-apim-craves-prodlow-l3ing6}"
HEALTH_ATTEMPTS="${CRAVES_RUNTIME_HEALTH_ATTEMPTS:-6}"
HEALTH_SLEEP_SECONDS="${CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS:-10}"
HEALTH_MAX_TIME_SECONDS="${CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS:-30}"

PAYMENT_REQUEST_SUB="integration-service-subscription-payment-requested"
PAYMENT_STATUS_SUB="subscription-service-payment-status-changed"
ORDER_REQUEST_SUB="order-service-subscription-order-requested"

fail() { echo "ERROR: $*" >&2; exit 1; }
warn() { echo "WARNING: $*" >&2; }
for tool in az jq curl bash; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_RUNTIME_HEALTH_ATTEMPTS must be a positive integer"
[[ "$HEALTH_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || fail "CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS must be a non-negative integer"
[[ "$HEALTH_MAX_TIME_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS must be a positive integer"

app_json() { az containerapp show -g "$RG" -n "$1" -o json; }

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

flag_value() {
  local APP="$1" NAME="$2"
  app_json "$APP" | jq -r --arg name "$NAME" \
    '(.properties.template.containers[0].env // []) | map(select(.name==$name)) | last | .value // ""'
}

expect_flag() {
  local APP="$1" NAME="$2" EXPECTED="$3" LABEL="$4" ACTUAL
  ACTUAL=$(flag_value "$APP" "$NAME")
  [[ "${ACTUAL,,}" == "${EXPECTED,,}" ]] || fail "$LABEL expected $NAME=$EXPECTED but found '${ACTUAL:-<unset>}'"
  echo "PASS: $LABEL $NAME=$EXPECTED"
}

verify_bus_subscription() {
  local SUB="$1" RULE="$2" FILTER="$3" JSON DLQ CURRENT
  JSON=$(az servicebus topic subscription show -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" -n "$SUB" -o json)
  DLQ=$(jq -r '.countDetails.deadLetterMessageCount // 0' <<<"$JSON")
  CURRENT=$(az servicebus topic subscription rule show \
    -g "$RG" --namespace-name "$SB_NS" --topic-name "$SB_TOPIC" \
    --subscription-name "$SUB" -n "$RULE" --query sqlFilter.sqlExpression -o tsv)
  [[ "$CURRENT" == "$FILTER" ]] || fail "$SUB rule filter does not match the expected event type"
  [[ "$DLQ" == "0" ]] || fail "$SUB has $DLQ dead-letter message(s); stop activation validation and investigate"
  echo "PASS: $SUB filter correct, deadLetterMessageCount=0, activeMessageCount=$(jq -r '.countDetails.activeMessageCount // 0' <<<"$JSON")"
}

healthy_app "$SUB_APP" "Subscription Service"
healthy_app "$INT_APP" "Integration Service"
healthy_app "$ORDER_APP" "Order Service"

expect_flag "$INT_APP" PAYMENT_PROVIDER_ENVIRONMENT sandbox "Integration Service"
expect_flag "$INT_APP" CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED false "Integration Service"
expect_flag "$INT_APP" CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED false "Integration Service"
expect_flag "$INT_APP" CRAVES_CASHFREE_WEBHOOK_INGRESS_ENABLED true "Integration Service"
expect_flag "$INT_APP" CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED true "Integration Service"
expect_flag "$INT_APP" CRAVES_SUBSCRIPTION_PAYMENT_CONSUMER_ENABLED true "Integration Service"
expect_flag "$INT_APP" CRAVES_SUBSCRIPTION_PAYMENT_STATUS_PUBLISHER_ENABLED true "Integration Service"
expect_flag "$INT_APP" CRAVES_PAYMENT_ORDER_API_ENABLED true "Integration Service"

expect_flag "$SUB_APP" CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_ENABLED true "Subscription Service"
expect_flag "$SUB_APP" CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED true "Subscription Service"
expect_flag "$SUB_APP" CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED true "Subscription Service"
expect_flag "$SUB_APP" CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED true "Subscription Service"
expect_flag "$SUB_APP" CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED true "Subscription Service"
expect_flag "$SUB_APP" CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED true "Subscription Service"
expect_flag "$SUB_APP" CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED true "Subscription Service"

expect_flag "$ORDER_APP" CRAVES_SUBSCRIPTION_ORDER_CONSUMER_ENABLED true "Order Service"
expect_flag "$ORDER_APP" CRAVES_SUBSCRIPTION_ORDER_CALLBACK_WORKER_ENABLED true "Order Service"

verify_bus_subscription "$PAYMENT_REQUEST_SUB" subscription-payment-requested \
  "event_type = 'SUBSCRIPTION_PAYMENT_REQUESTED' OR eventType = 'SUBSCRIPTION_PAYMENT_REQUESTED'"
verify_bus_subscription "$PAYMENT_STATUS_SUB" subscription-payment-status \
  "event_type = 'SUBSCRIPTION_PAYMENT_STATUS_CHANGED' OR eventType = 'SUBSCRIPTION_PAYMENT_STATUS_CHANGED'"
verify_bus_subscription "$ORDER_REQUEST_SUB" subscription-order-requested \
  "event_type = 'SUBSCRIPTION_ORDER_REQUESTED' OR eventType = 'SUBSCRIPTION_ORDER_REQUESTED'"

RG="$RG" APIM="$APIM_NAME" INTEGRATION_APP="$INT_APP" bash scripts/apim/status-subscription-payments-apim.sh

INVOICE_ANON_CODE=$(curl -sS -o /tmp/craves-subscription-payment-anon.out -w '%{http_code}' \
  "https://${APIM_HOST}/api/v1/subscription-payments/invoices/00000000-0000-0000-0000-000000000000")
[[ "$INVOICE_ANON_CODE" == "401" ]] || fail "Anonymous subscription-payment route expected HTTP 401 but received $INVOICE_ANON_CODE"
echo "PASS: subscription-payment APIM anonymous guard returns HTTP 401"

WEBHOOK_CODE=$(curl -sS -o /tmp/craves-cashfree-webhook-smoke.out -w '%{http_code}' \
  -X POST "https://${APIM_HOST}/api/v1/payments/webhooks/cashfree" \
  -H 'Content-Type: application/json' \
  --data '{}')
[[ "$WEBHOOK_CODE" == "400" ]] || fail "Unsigned Cashfree webhook expected backend HTTP 400 but received $WEBHOOK_CODE"
echo "PASS: public Cashfree webhook route reaches the signature/header validator and rejects unsigned input with HTTP 400"

check_start_log() {
  local APP="$1" PATTERN="$2" LABEL="$3" READY LOGS
  READY=$(app_json "$APP" | jq -r '.properties.latestReadyRevisionName // ""')
  LOGS=$(az containerapp logs show -g "$RG" -n "$APP" --revision "$READY" --type console --tail 300 --format text 2>/dev/null || true)
  if grep -q "$PATTERN" <<<"$LOGS"; then
    echo "PASS: $LABEL startup log observed"
  else
    warn "$LABEL startup log was not present in the last 300 console lines; health/flag/topology checks passed, so this is retained as a non-blocking observability warning"
  fi
}

check_start_log "$INT_APP" "SUBSCRIPTION_PAYMENT_REQUESTED processor started" "Integration subscription-payment consumer"
check_start_log "$SUB_APP" "SUBSCRIPTION_PAYMENT_STATUS_CHANGED processor started" "Subscription payment-status consumer"
check_start_log "$ORDER_APP" "SUBSCRIPTION_ORDER_REQUESTED processor started" "Order subscription-order consumer"

echo "============================================================"
echo "SUCCESS: SUBSCRIPTION SANDBOX RUNTIME STATUS IS VALID"
echo "All requested Subscription workers are enabled."
echo "Cashfree remains sandbox-only and rejects unsigned webhooks."
echo "No subscription-specific Service Bus dead letters are present."
echo "============================================================"