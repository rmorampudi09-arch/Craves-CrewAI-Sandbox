#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
SUB_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
INT_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
CONFIRM_ROLLBACK="${CONFIRM_SUBSCRIPTION_SANDBOX_ROLLBACK:-false}"
ROLLBACK_ID="${ROLLBACK_ID:-${BUILD_BUILDID:-manual-$(date -u +%Y%m%d%H%M%S)}}-rollback"
MAX_ATTEMPTS="${CRAVES_SUBSCRIPTION_ACTIVATION_MAX_ATTEMPTS:-150}"
POLL_SECONDS="${CRAVES_SUBSCRIPTION_ACTIVATION_POLL_SECONDS:-10}"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ "${CONFIRM_ROLLBACK,,}" == "true" ]] || fail "Set CONFIRM_SUBSCRIPTION_SANDBOX_ROLLBACK=true for the controlled rollback"

app_json() { az containerapp show -g "$RG" -n "$1" -o json; }

wait_target_revision() {
  local APP="$1" LABEL="$2" PREVIOUS="$3"
  local ATTEMPT JSON LATEST READY APP_RUNNING REV_JSON MARKER HEALTH PROVISIONING REV_RUNNING
  for ((ATTEMPT=1; ATTEMPT<=MAX_ATTEMPTS; ATTEMPT++)); do
    JSON=$(app_json "$APP")
    LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$JSON")
    READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$JSON")
    APP_RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$JSON")
    if [[ -z "$LATEST" || "$LATEST" == "$PREVIOUS" ]]; then
      echo "WAIT: $LABEL rollback revision not visible yet attempt=$ATTEMPT/$MAX_ATTEMPTS"
      sleep "$POLL_SECONDS"
      continue
    fi
    REV_JSON=$(az containerapp revision show -g "$RG" -n "$APP" --revision "$LATEST" -o json 2>/dev/null || echo '{}')
    MARKER=$(jq -r --arg name CRAVES_SUBSCRIPTION_SANDBOX_ACTIVATION_ID \
      '(.properties.template.containers[0].env // []) | map(select(.name==$name)) | last | .value // ""' <<<"$REV_JSON")
    HEALTH=$(jq -r '.properties.healthState // ""' <<<"$REV_JSON")
    PROVISIONING=$(jq -r '.properties.provisioningState // ""' <<<"$REV_JSON")
    REV_RUNNING=$(jq -r '.properties.runningState // ""' <<<"$REV_JSON")
    [[ "$MARKER" == "$ROLLBACK_ID" ]] || fail "$LABEL latest revision was changed by another rollout during rollback"
    echo "WAIT: $LABEL rollback revision=$LATEST ready=$READY health=$HEALTH provisioning=$PROVISIONING revisionRunning=$REV_RUNNING appRunning=$APP_RUNNING attempt=$ATTEMPT/$MAX_ATTEMPTS"
    if [[ "$PROVISIONING" == "Failed" || "$REV_RUNNING" == "Failed" || "$REV_RUNNING" == "ActivationFailed" ]]; then
      fail "$LABEL rollback revision entered a terminal failure state"
    fi
    if [[ "$LATEST" == "$READY" && "$HEALTH" == "Healthy" && "$PROVISIONING" == "Provisioned" && "$APP_RUNNING" == "Running" ]]; then
      local FQDN
      FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$JSON")
      curl -sS --fail --max-time 30 "https://${FQDN}/actuator/health" >/dev/null \
        || fail "$LABEL rollback revision actuator health failed"
      echo "PASS: $LABEL rollback revision healthy: $LATEST"
      return
    fi
    sleep "$POLL_SECONDS"
  done
  fail "$LABEL rollback revision did not become ready within $((MAX_ATTEMPTS * POLL_SECONDS)) seconds"
}

update_app() {
  local APP="$1" LABEL="$2"; shift 2
  local PREVIOUS
  PREVIOUS=$(app_json "$APP" | jq -r '.properties.latestRevisionName // ""')
  az containerapp update -g "$RG" -n "$APP" \
    --set-env-vars "CRAVES_SUBSCRIPTION_SANDBOX_ACTIVATION_ID=${ROLLBACK_ID}" "$@" \
    --only-show-errors -o none
  wait_target_revision "$APP" "$LABEL" "$PREVIOUS"
}

# Stop upstream generation and publishing first. Keep the payment-status consumer briefly available.
update_app "$SUB_APP" "Subscription upstream rollback" \
  "CRAVES_SUBSCRIPTION_CAPACITY_PROJECTION_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_OCCURRENCE_GENERATOR_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_BILLING_GENERATOR_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_BILLING_PUBLISHER_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED=false"

# Stop new hosted sandbox payment orders and downstream status publication.
update_app "$INT_APP" "Integration upstream rollback" \
  "CRAVES_PAYMENT_ORDER_API_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_PAYMENT_STATUS_PUBLISHER_ENABLED=false" \
  "CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED=false" \
  "CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false"

# Stop recurring order consumption/callback after the upstream publisher is off.
update_app "$ORDER_APP" "Order subscription rollback" \
  "CRAVES_SUBSCRIPTION_ORDER_CONSUMER_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_ORDER_CALLBACK_WORKER_ENABLED=false"

# Stop the Subscription payment-status consumer after publishers are off.
update_app "$SUB_APP" "Subscription downstream rollback" \
  "CRAVES_SUBSCRIPTION_PAYMENT_STATUS_CONSUMER_ENABLED=false"

# Finally stop payment-request consumption and Cashfree webhook processing/ingress.
update_app "$INT_APP" "Integration downstream rollback" \
  "CRAVES_SUBSCRIPTION_PAYMENT_CONSUMER_ENABLED=false" \
  "CRAVES_CASHFREE_WEBHOOK_WORKER_ENABLED=false" \
  "CRAVES_CASHFREE_WEBHOOK_INGRESS_ENABLED=false" \
  "CRAVES_PAYMENT_ORDER_API_ENABLED=false" \
  "CRAVES_SUBSCRIPTION_PAYMENT_STATUS_PUBLISHER_ENABLED=false" \
  "PAYMENT_PROVIDER_ENVIRONMENT=sandbox" \
  "CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED=false" \
  "CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED=false"

echo "============================================================"
echo "SUCCESS: SUBSCRIPTION SANDBOX RUNTIME ROLLED BACK TO FAIL-CLOSED"
echo "Service Bus subscriptions, APIM operations and durable database evidence were retained."
echo "Cashfree production flags remain false."
echo "Rollback ID: $ROLLBACK_ID"
echo "============================================================"
