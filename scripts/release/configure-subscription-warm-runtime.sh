#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APP="${APP:-ca-craves-subscription-service-p}"
CONFIRM="${CONFIRM_KEEP_SUBSCRIPTION_WARM:-false}"
DESIRED_MIN="${DESIRED_MIN_REPLICAS:-1}"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sha256sum awk; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ "${CONFIRM,,}" == "true" ]] || fail "Set confirmKeepSubscriptionWarm=true to keep one Subscription Service replica running"
[[ "$DESIRED_MIN" == "1" ]] || fail "This guarded pipeline only supports DESIRED_MIN_REPLICAS=1"

app_json() {
  az containerapp show --resource-group "$RG" --name "$APP" -o json --only-show-errors
}

runtime_fingerprint() {
  jq -S '{
    configuration: (.properties.configuration | if .ingress then .ingress |= del(.traffic) else . end),
    identity: .identity,
    template: (.properties.template | del(.revisionSuffix, .scale.minReplicas))
  }' | sha256sum | cut -d' ' -f1
}

secret_metadata_fingerprint() {
  az containerapp secret list \
    --resource-group "$RG" \
    --name "$APP" \
    --query '[].{name:name,keyVaultUrl:keyVaultUrl,identity:identity}' \
    -o json \
    --only-show-errors \
  | jq -S 'sort_by(.name)' \
  | sha256sum \
  | cut -d' ' -f1
}

probe_status_up() {
  local URL="$1" LABEL="$2" ATTEMPT BODY CODE STATUS
  BODY="$(mktemp)"
  for ATTEMPT in $(seq 1 30); do
    : >"$BODY"
    CODE="$(curl --silent --show-error --connect-timeout 8 --max-time 15 \
      --output "$BODY" --write-out '%{http_code}' "$URL" 2>/dev/null || true)"
    STATUS="$(jq -r '.status // empty' "$BODY" 2>/dev/null || true)"
    if [[ "$CODE" == "200" && "$STATUS" == "UP" ]]; then
      rm -f "$BODY"
      echo "PASS: $LABEL -> UP attempt=$ATTEMPT"
      return 0
    fi
    sleep 5
  done
  rm -f "$BODY"
  fail "$LABEL did not become healthy"
}

probe_plans() {
  local URL="$1" LABEL="$2" BODY META RC CODE TOTAL
  BODY="$(mktemp)"
  set +e
  META="$(curl --silent --show-error --connect-timeout 8 --max-time 30 \
    --output "$BODY" --write-out '%{http_code} %{time_total}' "$URL" 2>/dev/null)"
  RC=$?
  set -e
  CODE="${META%% *}"
  TOTAL="${META#* }"
  if [[ "$RC" -ne 0 || "$CODE" != "200" ]]; then
    rm -f "$BODY"
    fail "$LABEL failed: curl_exit=$RC HTTP=${CODE:-000} total=${TOTAL:-unknown}s"
  fi
  jq -e 'type == "array"' "$BODY" >/dev/null || { rm -f "$BODY"; fail "$LABEL did not return a JSON array"; }
  rm -f "$BODY"
  echo "PASS: $LABEL -> HTTP 200 total=${TOTAL}s"
  PROBE_TOTAL="$TOTAL"
}

BEFORE="$(app_json)"
FQDN="$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$BEFORE")"
EXTERNAL="$(jq -r '.properties.configuration.ingress.external // false' <<<"$BEFORE")"
CURRENT_MIN="$(jq -r '.properties.template.scale.minReplicas // 0' <<<"$BEFORE")"
CURRENT_MAX="$(jq -r '.properties.template.scale.maxReplicas // 10' <<<"$BEFORE")"
IMAGE="$(jq -r '.properties.template.containers[0].image // ""' <<<"$BEFORE")"
BEFORE_RUNTIME_HASH="$(runtime_fingerprint <<<"$BEFORE")"
BEFORE_SECRET_HASH="$(secret_metadata_fingerprint)"

[[ "$EXTERNAL" == "true" ]] || fail "Subscription Service ingress is not external"
[[ -n "$FQDN" ]] || fail "Subscription Service FQDN is missing"
[[ "$CURRENT_MAX" =~ ^[0-9]+$ && "$CURRENT_MAX" -ge 1 ]] || fail "Subscription Service maxReplicas is invalid: $CURRENT_MAX"

echo "Subscription Service warm-runtime change"
echo "App: $APP"
echo "Current minReplicas: $CURRENT_MIN"
echo "Desired minReplicas: $DESIRED_MIN"
echo "maxReplicas preserved: $CURRENT_MAX"
echo "Image preserved: $IMAGE"
echo "NOTE: minReplicas=1 keeps one replica available and can incur Azure Container Apps idle/active usage charges."

if [[ "$CURRENT_MIN" != "$DESIRED_MIN" ]]; then
  az containerapp update \
    --resource-group "$RG" \
    --name "$APP" \
    --min-replicas "$DESIRED_MIN" \
    --only-show-errors \
    -o none
else
  echo "INFO: minReplicas is already $DESIRED_MIN; no scale write was needed."
fi

READY=false
for ATTEMPT in $(seq 1 90); do
  AFTER="$(app_json)"
  ACTUAL_MIN="$(jq -r '.properties.template.scale.minReplicas // 0' <<<"$AFTER")"
  LATEST="$(jq -r '.properties.latestRevisionName // ""' <<<"$AFTER")"
  READY_REV="$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$AFTER")"
  RUNNING="$(jq -r '.properties.runningStatus // ""' <<<"$AFTER")"
  HEALTH=""
  if [[ -n "$LATEST" ]]; then
    HEALTH="$(az containerapp revision show --resource-group "$RG" --name "$APP" --revision "$LATEST" \
      --query properties.healthState -o tsv --only-show-errors 2>/dev/null || true)"
  fi
  if [[ "$ACTUAL_MIN" == "$DESIRED_MIN" && -n "$LATEST" && "$LATEST" == "$READY_REV" && "$RUNNING" == "Running" && "$HEALTH" == "Healthy" ]]; then
    READY=true
    break
  fi
  sleep 5
done
[[ "$READY" == "true" ]] || fail "Subscription Service did not become ready with minReplicas=$DESIRED_MIN"

AFTER="$(app_json)"
AFTER_MIN="$(jq -r '.properties.template.scale.minReplicas // 0' <<<"$AFTER")"
AFTER_MAX="$(jq -r '.properties.template.scale.maxReplicas // 10' <<<"$AFTER")"
AFTER_IMAGE="$(jq -r '.properties.template.containers[0].image // ""' <<<"$AFTER")"
AFTER_RUNTIME_HASH="$(runtime_fingerprint <<<"$AFTER")"
AFTER_SECRET_HASH="$(secret_metadata_fingerprint)"

[[ "$AFTER_MIN" == "$DESIRED_MIN" ]] || fail "minReplicas verification failed: $AFTER_MIN"
[[ "$AFTER_MAX" == "$CURRENT_MAX" ]] || fail "maxReplicas changed unexpectedly: before=$CURRENT_MAX after=$AFTER_MAX"
[[ "$AFTER_IMAGE" == "$IMAGE" ]] || fail "Container image changed unexpectedly"
[[ "$AFTER_RUNTIME_HASH" == "$BEFORE_RUNTIME_HASH" ]] || fail "Runtime configuration changed outside minReplicas"
[[ "$AFTER_SECRET_HASH" == "$BEFORE_SECRET_HASH" ]] || fail "Secret metadata changed unexpectedly"

echo "PASS: only Subscription Service minReplicas changed; image/env/ingress/identity/secret metadata are preserved"

BASE="https://${FQDN}"
probe_status_up "$BASE/actuator/health/liveness" "Subscription liveness"
probe_status_up "$BASE/actuator/health/readiness" "Subscription readiness"
probe_plans "$BASE/api/v1/subscriptions/plans" "Direct public plans warm probe 1"
FIRST_TOTAL="$PROBE_TOTAL"
probe_plans "$BASE/api/v1/subscriptions/plans" "Direct public plans warm probe 2"
SECOND_TOTAL="$PROBE_TOTAL"

if ! awk -v t="$SECOND_TOTAL" 'BEGIN { exit !(t < 10.0) }'; then
  fail "Warm public plans latency is still ${SECOND_TOTAL}s, which is above the 10-second steady-state target"
fi

echo "PASS: steady-state direct plans latency is ${SECOND_TOTAL}s (<10s); first warm probe was ${FIRST_TOTAL}s"
echo "SUCCESS: Subscription Service is kept warm with minReplicas=1 and passed health/latency verification."
