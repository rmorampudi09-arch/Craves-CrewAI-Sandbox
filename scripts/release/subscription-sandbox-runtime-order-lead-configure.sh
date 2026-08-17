#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
SUB_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
APPROVED_LEAD_HOURS="${CRAVES_APPROVED_SUBSCRIPTION_ORDER_LEAD_HOURS:-2}"
CONFIRM_WRITE="${CONFIRM_SUBSCRIPTION_ORDER_LEAD_WRITE:-false}"
MAX_ATTEMPTS="${CRAVES_ORDER_LEAD_CONFIG_MAX_ATTEMPTS:-90}"
POLL_SECONDS="${CRAVES_ORDER_LEAD_CONFIG_POLL_SECONDS:-10}"
HEALTH_ATTEMPTS="${CRAVES_RUNTIME_HEALTH_ATTEMPTS:-6}"
HEALTH_SLEEP_SECONDS="${CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS:-10}"
HEALTH_MAX_TIME_SECONDS="${CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS:-30}"

fail() { echo "ERROR: $*" >&2; exit 1; }

for tool in az jq curl; do
  command -v "$tool" >/dev/null || fail "$tool is required"
done

[[ "${CONFIRM_WRITE,,}" == "true" ]] \
  || fail "Set CONFIRM_SUBSCRIPTION_ORDER_LEAD_WRITE=true for this guarded configuration change"
[[ "$APPROVED_LEAD_HOURS" =~ ^[0-9]+$ ]] \
  || fail "CRAVES_APPROVED_SUBSCRIPTION_ORDER_LEAD_HOURS must be an integer"
(( APPROVED_LEAD_HOURS >= 0 && APPROVED_LEAD_HOURS <= 168 )) \
  || fail "Approved subscription order lead hours must be between 0 and 168"
[[ "$MAX_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "Invalid max attempts"
[[ "$POLL_SECONDS" =~ ^[0-9]+$ && "$POLL_SECONDS" -ge 2 ]] || fail "Invalid poll interval"
[[ "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "Invalid health attempts"
[[ "$HEALTH_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || fail "Invalid health sleep interval"
[[ "$HEALTH_MAX_TIME_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail "Invalid health max-time"

app_json() {
  az containerapp show -g "$RG" -n "$SUB_APP" -o json
}

current_lead_hours() {
  app_json | jq -r --arg name CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS \
    '(.properties.template.containers[0].env // []) | map(select(.name==$name)) | last | .value // ""'
}

probe_up() {
  local FQDN="$1" LABEL="$2" HEALTH_PATH="$3" ATTEMPT BODY CODE STATUS SAFE_PATH
  SAFE_PATH="${HEALTH_PATH//\//_}"
  BODY="/tmp/craves-order-lead-health-${BASHPID}-${SAFE_PATH}.json"
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
  return 1
}

wait_revision() {
  local PREVIOUS="$1" ATTEMPT JSON LATEST READY RUNNING FQDN HEALTH PROVISIONING REV_RUNNING
  for ((ATTEMPT=1; ATTEMPT<=MAX_ATTEMPTS; ATTEMPT++)); do
    JSON=$(app_json)
    LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$JSON")
    READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$JSON")
    RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$JSON")
    FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$JSON")

    if [[ -z "$LATEST" || "$LATEST" == "$PREVIOUS" ]]; then
      echo "WAIT: Subscription Service new revision not visible yet attempt=$ATTEMPT/$MAX_ATTEMPTS"
      sleep "$POLL_SECONDS"
      continue
    fi

    HEALTH=$(az containerapp revision show -g "$RG" -n "$SUB_APP" --revision "$LATEST" --query properties.healthState -o tsv 2>/dev/null || true)
    PROVISIONING=$(az containerapp revision show -g "$RG" -n "$SUB_APP" --revision "$LATEST" --query properties.provisioningState -o tsv 2>/dev/null || true)
    REV_RUNNING=$(az containerapp revision show -g "$RG" -n "$SUB_APP" --revision "$LATEST" --query properties.runningState -o tsv 2>/dev/null || true)

    echo "WAIT: Subscription Service revision=$LATEST ready=$READY health=$HEALTH provisioning=$PROVISIONING revisionRunning=$REV_RUNNING appRunning=$RUNNING attempt=$ATTEMPT/$MAX_ATTEMPTS"

    if [[ "$PROVISIONING" == "Failed" || "$REV_RUNNING" == "Failed" || "$REV_RUNNING" == "ActivationFailed" ]]; then
      return 1
    fi

    if [[ "$LATEST" == "$READY" && "$HEALTH" == "Healthy" && "$PROVISIONING" == "Provisioned" && "$RUNNING" == "Running" && -n "$FQDN" ]]; then
      probe_up "$FQDN" "Subscription Service" "/actuator/health/liveness" || return 1
      probe_up "$FQDN" "Subscription Service" "/actuator/health/readiness" || return 1
      echo "PASS: Subscription Service order-lead revision healthy: $LATEST"
      return 0
    fi

    sleep "$POLL_SECONDS"
  done
  return 1
}

CURRENT=$(current_lead_hours)
if [[ "$CURRENT" == "$APPROVED_LEAD_HOURS" ]]; then
  echo "PASS: CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS is already ${APPROVED_LEAD_HOURS}; no write required"
  exit 0
fi

PREVIOUS=$(app_json | jq -r '.properties.latestRevisionName // ""')

echo "WRITE: setting CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS=${APPROVED_LEAD_HOURS} on Subscription Service"
az containerapp update \
  -g "$RG" \
  -n "$SUB_APP" \
  --set-env-vars "CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS=${APPROVED_LEAD_HOURS}" \
  --only-show-errors \
  -o none

wait_revision "$PREVIOUS" \
  || fail "Subscription Service did not become healthy after configuring CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS"

ACTUAL=$(current_lead_hours)
[[ "$ACTUAL" == "$APPROVED_LEAD_HOURS" ]] \
  || fail "Subscription Service order lead hours verification failed: expected ${APPROVED_LEAD_HOURS}"

echo "============================================================"
echo "SUCCESS: SUBSCRIPTION ORDER LEAD HOURS CONFIGURED"
echo "CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS=${APPROVED_LEAD_HOURS}"
echo "No other Container App runtime flag was changed by this script."
echo "============================================================"
