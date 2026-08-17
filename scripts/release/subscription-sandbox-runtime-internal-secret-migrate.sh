#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
SUB_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
ENV_NAME="CRAVES_INTERNAL_SERVICE_SECRET"
LOCAL_SECRET_NAME="${CRAVES_INTERNAL_SERVICE_SECRET_NAME:-craves-internal-svc}"
CONFIRM_MIGRATION="${CONFIRM_INTERNAL_SECRET_MIGRATION:-false}"
MAX_ATTEMPTS="${CRAVES_INTERNAL_SECRET_MIGRATION_MAX_ATTEMPTS:-90}"
POLL_SECONDS="${CRAVES_INTERNAL_SECRET_MIGRATION_POLL_SECONDS:-10}"
HEALTH_ATTEMPTS="${CRAVES_RUNTIME_HEALTH_ATTEMPTS:-6}"
HEALTH_SLEEP_SECONDS="${CRAVES_RUNTIME_HEALTH_SLEEP_SECONDS:-10}"
HEALTH_MAX_TIME_SECONDS="${CRAVES_RUNTIME_HEALTH_MAX_TIME_SECONDS:-30}"

fail() { echo "ERROR: $*" >&2; exit 1; }
warn() { echo "WARNING: $*" >&2; }

for tool in az jq curl; do
  command -v "$tool" >/dev/null || fail "$tool is required"
done

[[ "${CONFIRM_MIGRATION,,}" == "true" ]] \
  || fail "Set CONFIRM_INTERNAL_SECRET_MIGRATION=true for this guarded internal-secret migration"
[[ "$LOCAL_SECRET_NAME" =~ ^[a-z][a-z0-9-]{0,18}[a-z0-9]$ ]] \
  || fail "CRAVES_INTERNAL_SERVICE_SECRET_NAME must be a lower-case Container App secret name of 2-20 characters"
[[ "$MAX_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "Invalid migration max attempts"
[[ "$POLL_SECONDS" =~ ^[0-9]+$ && "$POLL_SECONDS" -ge 2 ]] || fail "Invalid migration poll interval"
[[ "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "Invalid health attempts"
[[ "$HEALTH_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || fail "Invalid health sleep interval"
[[ "$HEALTH_MAX_TIME_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail "Invalid health max-time"

app_json() {
  az containerapp show -g "$RG" -n "$1" -o json
}

env_entry() {
  local APP="$1"
  app_json "$APP" | jq -c --arg name "$ENV_NAME" \
    '(.properties.template.containers[0].env // []) | map(select(.name == $name)) | last // {}'
}

env_kind() {
  local ENTRY="$1" VALUE REF
  VALUE=$(jq -r '.value // ""' <<<"$ENTRY")
  REF=$(jq -r '.secretRef // ""' <<<"$ENTRY")
  if [[ -n "$REF" ]]; then
    printf 'secretRef'
  elif [[ -n "$VALUE" ]]; then
    printf 'inline'
  else
    printf 'missing'
  fi
}

secret_value_from_ref() {
  local APP="$1" REF="$2" SECRET_JSON VALUE KV_URL
  SECRET_JSON=$(az containerapp secret list \
    -g "$RG" \
    -n "$APP" \
    --show-values \
    -o json 2>/dev/null || echo '[]')
  VALUE=$(jq -r --arg ref "$REF" \
    'map(select(.name == $ref)) | last | .value // ""' <<<"$SECRET_JSON")
  if [[ -n "$VALUE" ]]; then
    printf '%s' "$VALUE"
    return 0
  fi

  KV_URL=$(jq -r --arg ref "$REF" \
    'map(select(.name == $ref)) | last | .keyVaultUrl // ""' <<<"$SECRET_JSON")
  if [[ -n "$KV_URL" ]]; then
    VALUE=$(az keyvault secret show --id "$KV_URL" --query value -o tsv 2>/dev/null || true)
    if [[ -n "$VALUE" ]]; then
      printf '%s' "$VALUE"
      return 0
    fi
    return 2
  fi

  return 1
}

resolved_value() {
  local APP="$1" LABEL="$2" ENTRY KIND VALUE REF
  ENTRY=$(env_entry "$APP")
  KIND=$(env_kind "$ENTRY")
  case "$KIND" in
    inline)
      VALUE=$(jq -r '.value // ""' <<<"$ENTRY")
      [[ -n "$VALUE" ]] || return 1
      printf '%s' "$VALUE"
      ;;
    secretRef)
      REF=$(jq -r '.secretRef // ""' <<<"$ENTRY")
      if VALUE=$(secret_value_from_ref "$APP" "$REF"); then
        printf '%s' "$VALUE"
      else
        case "$?" in
          2) fail "$LABEL uses Key Vault-backed secretRef '$REF', but this pipeline cannot read the Key Vault value to prove Subscription/Order secret equality. No migration write was attempted." ;;
          *) fail "$LABEL secretRef '$REF' could not be resolved read-only. No migration write was attempted." ;;
        esac
      fi
      ;;
    missing)
      return 1
      ;;
  esac
}

probe_up() {
  local FQDN="$1" LABEL="$2" HEALTH_PATH="$3" ATTEMPT BODY CODE STATUS SAFE_PATH
  SAFE_PATH="${HEALTH_PATH//\//_}"
  BODY="/tmp/craves-internal-secret-health-${BASHPID}-${SAFE_PATH}.json"
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
  local APP="$1" LABEL="$2" PREVIOUS="$3" ATTEMPT JSON LATEST READY RUNNING FQDN HEALTH PROVISIONING REV_RUNNING
  for ((ATTEMPT=1; ATTEMPT<=MAX_ATTEMPTS; ATTEMPT++)); do
    JSON=$(app_json "$APP")
    LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$JSON")
    READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$JSON")
    RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$JSON")
    FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$JSON")

    if [[ -z "$LATEST" || "$LATEST" == "$PREVIOUS" ]]; then
      echo "WAIT: $LABEL new revision not visible yet attempt=$ATTEMPT/$MAX_ATTEMPTS"
      sleep "$POLL_SECONDS"
      continue
    fi

    HEALTH=$(az containerapp revision show -g "$RG" -n "$APP" --revision "$LATEST" --query properties.healthState -o tsv 2>/dev/null || true)
    PROVISIONING=$(az containerapp revision show -g "$RG" -n "$APP" --revision "$LATEST" --query properties.provisioningState -o tsv 2>/dev/null || true)
    REV_RUNNING=$(az containerapp revision show -g "$RG" -n "$APP" --revision "$LATEST" --query properties.runningState -o tsv 2>/dev/null || true)

    echo "WAIT: $LABEL revision=$LATEST ready=$READY health=$HEALTH provisioning=$PROVISIONING revisionRunning=$REV_RUNNING appRunning=$RUNNING attempt=$ATTEMPT/$MAX_ATTEMPTS"

    if [[ "$PROVISIONING" == "Failed" || "$REV_RUNNING" == "Failed" || "$REV_RUNNING" == "ActivationFailed" ]]; then
      return 1
    fi

    if [[ "$LATEST" == "$READY" && "$HEALTH" == "Healthy" && "$PROVISIONING" == "Provisioned" && "$RUNNING" == "Running" && -n "$FQDN" ]]; then
      probe_up "$FQDN" "$LABEL" "/actuator/health/liveness" || return 1
      probe_up "$FQDN" "$LABEL" "/actuator/health/readiness" || return 1
      echo "PASS: $LABEL secretRef revision healthy: $LATEST"
      return 0
    fi

    sleep "$POLL_SECONDS"
  done
  return 1
}

set_local_secret_and_reference() {
  local APP="$1" LABEL="$2" VALUE="$3" PREVIOUS
  PREVIOUS=$(app_json "$APP" | jq -r '.properties.latestRevisionName // ""')

  az containerapp secret set \
    -g "$RG" \
    -n "$APP" \
    --secrets "${LOCAL_SECRET_NAME}=${VALUE}" \
    --only-show-errors \
    -o none

  az containerapp update \
    -g "$RG" \
    -n "$APP" \
    --set-env-vars "${ENV_NAME}=secretref:${LOCAL_SECRET_NAME}" \
    --only-show-errors \
    -o none

  if ! wait_revision "$APP" "$LABEL" "$PREVIOUS"; then
    return 1
  fi

  local ENTRY REF INLINE
  ENTRY=$(env_entry "$APP")
  REF=$(jq -r '.secretRef // ""' <<<"$ENTRY")
  INLINE=$(jq -r '.value // ""' <<<"$ENTRY")
  [[ "$REF" == "$LOCAL_SECRET_NAME" && -z "$INLINE" ]] || return 1
  echo "PASS: $LABEL $ENV_NAME now uses secretRef=$LOCAL_SECRET_NAME"
}

SUB_ENTRY=$(env_entry "$SUB_APP")
ORDER_ENTRY=$(env_entry "$ORDER_APP")
SUB_KIND=$(env_kind "$SUB_ENTRY")
ORDER_KIND=$(env_kind "$ORDER_ENTRY")

echo "Current binding types: Subscription=$SUB_KIND Order=$ORDER_KIND"

SUB_VALUE=""
ORDER_VALUE=""
if SUB_VALUE=$(resolved_value "$SUB_APP" "Subscription Service"); then :; fi
if ORDER_VALUE=$(resolved_value "$ORDER_APP" "Order Service"); then :; fi

if [[ -z "$SUB_VALUE" && -z "$ORDER_VALUE" ]]; then
  fail "Neither Subscription nor Order exposes a resolvable existing $ENV_NAME value. A shared internal secret must be established before migration; no secret was generated or changed automatically."
fi

if [[ -z "$SUB_VALUE" ]]; then SUB_VALUE="$ORDER_VALUE"; fi
if [[ -z "$ORDER_VALUE" ]]; then ORDER_VALUE="$SUB_VALUE"; fi

[[ "$SUB_VALUE" == "$ORDER_VALUE" ]] \
  || fail "Subscription and Order currently resolve to different internal-secret values. Migration stopped before any write; rotate/reconcile the shared internal secret first."

SHARED_VALUE="$SUB_VALUE"
unset SUB_VALUE ORDER_VALUE

echo "PASS: Subscription and Order internal-secret equality was proven without printing the secret value"

UPDATED_SUB=false
UPDATED_ORDER=false

if [[ "$SUB_KIND" != "secretRef" ]]; then
  echo "WRITE: migrating Subscription Service $ENV_NAME to Container App secretRef"
  set_local_secret_and_reference "$SUB_APP" "Subscription Service" "$SHARED_VALUE" \
    || fail "Subscription Service secretRef migration did not become healthy. No Order migration was attempted."
  UPDATED_SUB=true
else
  echo "PASS: Subscription Service already uses a secretRef; no write required"
fi

if [[ "$ORDER_KIND" != "secretRef" ]]; then
  echo "WRITE: migrating Order Service $ENV_NAME to Container App secretRef"
  if ! set_local_secret_and_reference "$ORDER_APP" "Order Service" "$SHARED_VALUE"; then
    warn "Order Service secretRef migration failed after Subscription migration. Subscription remains on the same secret value via secretRef; runtime activation must remain blocked until Order is repaired."
    fail "Order Service secretRef migration did not become healthy"
  fi
  UPDATED_ORDER=true
else
  echo "PASS: Order Service already uses a secretRef; no write required"
fi

unset SHARED_VALUE

SUB_ENTRY=$(env_entry "$SUB_APP")
ORDER_ENTRY=$(env_entry "$ORDER_APP")
SUB_REF=$(jq -r '.secretRef // ""' <<<"$SUB_ENTRY")
ORDER_REF=$(jq -r '.secretRef // ""' <<<"$ORDER_ENTRY")
SUB_INLINE=$(jq -r '.value // ""' <<<"$SUB_ENTRY")
ORDER_INLINE=$(jq -r '.value // ""' <<<"$ORDER_ENTRY")

[[ -n "$SUB_REF" && -z "$SUB_INLINE" ]] || fail "Subscription Service is not in secretRef-only state after migration"
[[ -n "$ORDER_REF" && -z "$ORDER_INLINE" ]] || fail "Order Service is not in secretRef-only state after migration"

echo "============================================================"
echo "SUCCESS: INTERNAL SERVICE SECRET BINDINGS ARE SECRETREF-ONLY"
echo "Subscription secretRef: $SUB_REF"
echo "Order secretRef: $ORDER_REF"
echo "Secret values were not printed."
echo "Subscription changed: $UPDATED_SUB"
echo "Order changed: $UPDATED_ORDER"
echo "============================================================"
