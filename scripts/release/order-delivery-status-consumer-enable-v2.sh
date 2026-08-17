#!/usr/bin/env bash
set -euo pipefail
set +x

RG=${1:?resource group required}
APP=${2:?order container app required}
INTEGRATION_APP=${3:?integration container app required}
NS=${4:?service bus namespace required}
FQNS=${5:?service bus fully-qualified namespace required}
TOPIC=${6:?topic name required}
SUB=${7:?subscription name required}

RULE='delivery-status-changed-only'
EXPECTED_FILTER="eventType = 'DELIVERY_STATUS_CHANGED' OR event_type = 'DELIVERY_STATUS_CHANGED'"
TARGETS='["CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED","CRAVES_DELIVERY_STATUS_SUBSCRIPTION","CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE","CRAVES_DOMAIN_EVENTS_TOPIC_NAME"]'

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for cmd in az jq sha256sum curl; do
  command -v "$cmd" >/dev/null 2>&1 || fail "$cmd is required"
done

[[ "$FQNS" == "$NS.servicebus.windows.net" ]] || \
  fail "Fully-qualified namespace must be $NS.servicebus.windows.net"

app_json() {
  az containerapp show --resource-group "$RG" --name "$APP" --output json --only-show-errors
}

integration_json() {
  az containerapp show --resource-group "$RG" --name "$INTEGRATION_APP" --output json --only-show-errors
}

secret_meta_json() {
  az containerapp secret list --resource-group "$RG" --name "$APP" --output json --only-show-errors \
    | jq -S '[.[] | {name, keyVaultUrl:(.keyVaultUrl // null), identity:(.identity // null)}] | sort_by(.name)'
}

hash_stdin() {
  sha256sum | awk '{print $1}'
}

unrelated_env_hash() {
  jq -S --argjson targets "$TARGETS" '
    [
      .properties.template.containers[] as $container
      | ($container.env // [])[]
      | . as $env
      | select(($targets | index($env.name)) == null)
      | {
          container: $container.name,
          name: $env.name,
          value: ($env.value // null),
          secretRef: ($env.secretRef // null)
        }
    ] | sort_by(.container, .name)
  ' | hash_stdin
}

configuration_hash() {
  jq -S '
    (.properties.configuration // {})
    | if .ingress then .ingress |= del(.traffic) else . end
  ' | hash_stdin
}

template_non_env_hash() {
  jq -S '
    (.properties.template // {})
    | del(.revisionSuffix)
    | (.containers // []) |= map(del(.env))
  ' | hash_stdin
}

identity_hash() {
  jq -S '.identity // {}' | hash_stdin
}

value_of() {
  local json=$1
  local name=$2
  jq -r --arg name "$name" '[.properties.template.containers[0].env[]? | select(.name == $name)][0].value // ""' <<<"$json"
}

previous_value_of() {
  local json=$1
  local name=$2
  jq -r --arg name "$name" '[.properties.template.containers[0].env[]? | select(.name == $name)][0].value // "__ABSENT__"' <<<"$json"
}

secret_ref_of() {
  local json=$1
  local name=$2
  jq -r --arg name "$name" '[.properties.template.containers[0].env[]? | select(.name == $name)][0].secretRef // ""' <<<"$json"
}

require_false_or_empty() {
  local label=$1
  local value=$2
  if [[ -n "$value" && "${value,,}" != 'false' ]]; then
    fail "$label must remain false/absent. Current value: $value"
  fi
}

verify_integration_safe() {
  local json=$1
  require_false_or_empty 'Integration delivery-status publisher' "$(value_of "$json" CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED)"
  require_false_or_empty 'Integration delivery command worker' "$(value_of "$json" CRAVES_DELIVERY_COMMAND_ENABLED)"
  require_false_or_empty 'Integration delivery reconciliation' "$(value_of "$json" CRAVES_DELIVERY_RECONCILIATION_ENABLED)"
  require_false_or_empty 'Integration delivery webhook processing' "$(value_of "$json" CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED)"
  require_false_or_empty 'Integration delivery tracking reconciliation' "$(value_of "$json" CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED)"
  require_false_or_empty 'Borzo provider execution' "$(value_of "$json" BORZO_API_ENABLED)"
}

echo '============================================================'
echo '1. VERIFY ORDER / INTEGRATION SAFETY STATE'
echo '============================================================'

BEFORE=$(app_json)
INTEGRATION_BEFORE=$(integration_json)
PRINCIPAL_ID=$(jq -r '.identity.principalId // ""' <<<"$BEFORE")
PREVIOUS_LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$BEFORE")
PREVIOUS_READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$BEFORE")
RUNNING_BEFORE=$(jq -r '.properties.runningStatus // ""' <<<"$BEFORE")

[[ -n "$PRINCIPAL_ID" ]] || fail 'Order Container App has no system-assigned managed identity.'
[[ -n "$PREVIOUS_LATEST" && "$PREVIOUS_LATEST" == "$PREVIOUS_READY" && "$RUNNING_BEFORE" == 'Running' ]] || \
  fail 'Order Container App must be Running with latest revision Ready before activation.'
verify_integration_safe "$INTEGRATION_BEFORE"

echo 'Order identity/revision state: PASS'
echo 'Integration publisher/provider safety state: PASS'

echo ''
echo '============================================================'
echo '2. VERIFY ACTIVE ORDER SECRETREFS ARE KEY VAULT-BACKED'
echo '============================================================'

SECRET_META=$(secret_meta_json)
SECRET_META_HASH=$(hash_stdin <<<"$SECRET_META")

while IFS= read -r REF; do
  [[ -n "$REF" ]] || continue
  KV_URL=$(jq -r --arg ref "$REF" '[.[] | select(.name == $ref)][0].keyVaultUrl // ""' <<<"$SECRET_META")
  [[ "$KV_URL" == https://*.vault.azure.net/secrets/* ]] || \
    fail "Active Order secretRef '$REF' is not Key Vault-backed."
done < <(jq -r '.properties.template.containers[].env[]? | .secretRef // empty' <<<"$BEFORE" | sort -u)

echo 'All active Order secretRefs are Key Vault-backed: PASS'

for TARGET in \
  CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED \
  CRAVES_DELIVERY_STATUS_SUBSCRIPTION \
  CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE \
  CRAVES_DOMAIN_EVENTS_TOPIC_NAME
do
  [[ -z "$(secret_ref_of "$BEFORE" "$TARGET")" ]] || fail "$TARGET unexpectedly uses secretRef."
done

PREV_ENABLED=$(previous_value_of "$BEFORE" CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED)
PREV_SUB=$(previous_value_of "$BEFORE" CRAVES_DELIVERY_STATUS_SUBSCRIPTION)
PREV_FQNS=$(previous_value_of "$BEFORE" CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE)
PREV_TOPIC=$(previous_value_of "$BEFORE" CRAVES_DOMAIN_EVENTS_TOPIC_NAME)

UNRELATED_ENV_BEFORE=$(unrelated_env_hash <<<"$BEFORE")
IDENTITY_BEFORE=$(identity_hash <<<"$BEFORE")
CONFIGURATION_BEFORE=$(configuration_hash <<<"$BEFORE")
TEMPLATE_NON_ENV_BEFORE=$(template_non_env_hash <<<"$BEFORE")

echo ''
echo '============================================================'
echo '3. CREATE / VERIFY FILTERED SERVICE BUS SUBSCRIPTION'
echo '============================================================'

az servicebus topic show --resource-group "$RG" --namespace-name "$NS" --name "$TOPIC" --output none --only-show-errors \
  || fail "Service Bus topic $TOPIC does not exist."

SUB_CREATED=false
if ! az servicebus topic subscription show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
  --output none --only-show-errors 2>/dev/null; then
  echo 'Subscription missing; creating it Disabled until filter and RBAC checks pass.'
  az servicebus topic subscription create \
    --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
    --max-delivery-count 10 --lock-duration PT1M --status Disabled --output none --only-show-errors
  SUB_CREATED=true
fi

SUB_JSON=$(az servicebus topic subscription show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
  --output json --only-show-errors)
PREVIOUS_SUB_STATUS=$(jq -r '.status // "Active"' <<<"$SUB_JSON")

if ! az servicebus topic subscription rule show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --subscription-name "$SUB" \
  --name "$RULE" --output none --only-show-errors 2>/dev/null; then
  [[ "$SUB_CREATED" == true ]] || fail "Existing subscription $SUB lacks expected rule $RULE; refusing automatic rewrite."
  az servicebus topic subscription rule create \
    --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --subscription-name "$SUB" \
    --name "$RULE" --filter-type SqlFilter --filter-sql-expression "$EXPECTED_FILTER" --output none --only-show-errors
fi

RULE_JSON=$(az servicebus topic subscription rule show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --subscription-name "$SUB" \
  --name "$RULE" --output json --only-show-errors)
[[ "$(jq -r '.filterType // ""' <<<"$RULE_JSON")" == 'SqlFilter' ]] || fail 'Unexpected Service Bus filter type.'
[[ "$(jq -r '.sqlFilter.sqlExpression // ""' <<<"$RULE_JSON")" == "$EXPECTED_FILTER" ]] || fail 'Unexpected Service Bus SQL filter.'

az servicebus topic subscription rule delete \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --subscription-name "$SUB" \
  --name '$Default' --output none --only-show-errors 2>/dev/null || true
RULE_COUNT=$(az servicebus topic subscription rule list \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --subscription-name "$SUB" \
  --query 'length(@)' --output tsv --only-show-errors)
[[ "$RULE_COUNT" == '1' ]] || fail "Expected exactly one filter rule, found $RULE_COUNT."

echo "Subscription: $SUB"
echo "Filter rule:  $RULE"
echo 'Filter verification: PASS'

echo ''
echo '============================================================'
echo '4. VERIFY ORDER MANAGED IDENTITY RECEIVER RBAC'
echo '============================================================'

SUBSCRIPTION_SCOPE=$(az servicebus topic subscription show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
  --query id --output tsv --only-show-errors)
RECEIVER_COUNT=$(az role assignment list \
  --assignee-object-id "$PRINCIPAL_ID" \
  --scope "$SUBSCRIPTION_SCOPE" \
  --role 'Azure Service Bus Data Receiver' \
  --include-inherited \
  --fill-principal-name false \
  --query 'length(@)' \
  --output tsv \
  --only-show-errors)

if [[ "${RECEIVER_COUNT:-0}" == '0' ]]; then
  az servicebus topic subscription update \
    --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
    --status Disabled --output none --only-show-errors || true
  echo 'ERROR: Order managed identity is missing Azure Service Bus Data Receiver.' >&2
  echo "PRINCIPAL_ID=$PRINCIPAL_ID"
  echo "SUBSCRIPTION_SCOPE=$SUBSCRIPTION_SCOPE"
  echo 'Subscription remains Disabled. Grant the role at this scope or a parent scope, then rerun.' >&2
  exit 2
fi

echo 'Azure Service Bus Data Receiver (direct or inherited): PASS'

echo ''
echo '============================================================'
echo '5. ACTIVATE SUBSCRIPTION AND ORDER CONSUMER'
echo '============================================================'

rollback_order() {
  echo 'Rolling back delivery-status consumer controls.' >&2
  local set_args=()
  local remove_args=()

  add_previous() {
    local name=$1
    local value=$2
    if [[ "$value" == '__ABSENT__' ]]; then
      remove_args+=("$name")
    else
      set_args+=("$name=$value")
    fi
  }

  add_previous CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED "$PREV_ENABLED"
  add_previous CRAVES_DELIVERY_STATUS_SUBSCRIPTION "$PREV_SUB"
  add_previous CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE "$PREV_FQNS"
  add_previous CRAVES_DOMAIN_EVENTS_TOPIC_NAME "$PREV_TOPIC"

  if (( ${#set_args[@]} > 0 )); then
    az containerapp update \
      --resource-group "$RG" --name "$APP" --set-env-vars "${set_args[@]}" \
      --output none --only-show-errors || true
  fi
  if (( ${#remove_args[@]} > 0 )); then
    az containerapp update \
      --resource-group "$RG" --name "$APP" --remove-env-vars "${remove_args[@]}" \
      --output none --only-show-errors || true
  fi
  az servicebus topic subscription update \
    --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
    --status "$PREVIOUS_SUB_STATUS" --output none --only-show-errors || true
}

CURRENT_ENABLED=$(value_of "$BEFORE" CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED)
CURRENT_SUB=$(value_of "$BEFORE" CRAVES_DELIVERY_STATUS_SUBSCRIPTION)
CURRENT_FQNS=$(value_of "$BEFORE" CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE)
CURRENT_TOPIC=$(value_of "$BEFORE" CRAVES_DOMAIN_EVENTS_TOPIC_NAME)

ALREADY_CONFIGURED=false
if [[ "${CURRENT_ENABLED,,}" == 'true' && "$CURRENT_SUB" == "$SUB" && "$CURRENT_FQNS" == "$FQNS" && "$CURRENT_TOPIC" == "$TOPIC" ]]; then
  ALREADY_CONFIGURED=true
fi

az servicebus topic subscription update \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
  --status Active --output none --only-show-errors

if [[ "$ALREADY_CONFIGURED" != true ]]; then
  az containerapp update \
    --resource-group "$RG" --name "$APP" \
    --set-env-vars \
      CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=true \
      CRAVES_DELIVERY_STATUS_SUBSCRIPTION="$SUB" \
      CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE="$FQNS" \
      CRAVES_DOMAIN_EVENTS_TOPIC_NAME="$TOPIC" \
    --no-wait --output none --only-show-errors \
    || { rollback_order; fail 'Unable to update Order delivery-status consumer controls.'; }
fi

READY=false
FINAL=''
for ATTEMPT in $(seq 1 60); do
  FINAL=$(app_json)
  LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$FINAL")
  READY_REV=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$FINAL")
  RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$FINAL")
  ENABLED=$(value_of "$FINAL" CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED)
  HEALTH=''
  if [[ -n "$LATEST" ]]; then
    HEALTH=$(az containerapp revision show \
      --resource-group "$RG" --name "$APP" --revision "$LATEST" \
      --query properties.healthState --output tsv --only-show-errors 2>/dev/null || true)
  fi
  echo "Attempt $ATTEMPT/60: latest=$LATEST ready=$READY_REV health=$HEALTH running=$RUNNING deliveryStatusConsumer=$ENABLED"

  REVISION_OK=false
  if [[ "$ALREADY_CONFIGURED" == true || "$LATEST" != "$PREVIOUS_LATEST" ]]; then
    REVISION_OK=true
  fi
  if [[ "$REVISION_OK" == true && -n "$LATEST" && "$LATEST" == "$READY_REV" && "$HEALTH" == 'Healthy' && "$RUNNING" == 'Running' && "${ENABLED,,}" == 'true' ]]; then
    READY=true
    break
  fi
  if [[ "$HEALTH" == 'Unhealthy' || "$RUNNING" == 'Failed' ]]; then
    break
  fi
  sleep 10
done

[[ "$READY" == true ]] || { rollback_order; fail 'Delivery status consumer revision did not become healthy within 10 minutes.'; }

echo ''
echo '============================================================'
echo '6. VERIFY RUNTIME PRESERVATION AND HEALTH'
echo '============================================================'

FINAL_ENABLED=$(value_of "$FINAL" CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED)
FINAL_SUB=$(value_of "$FINAL" CRAVES_DELIVERY_STATUS_SUBSCRIPTION)
FINAL_FQNS=$(value_of "$FINAL" CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE)
FINAL_TOPIC=$(value_of "$FINAL" CRAVES_DOMAIN_EVENTS_TOPIC_NAME)

[[ "${FINAL_ENABLED,,}" == 'true' ]] || { rollback_order; fail 'Consumer is not enabled.'; }
[[ "$FINAL_SUB" == "$SUB" ]] || { rollback_order; fail 'Subscription binding mismatch.'; }
[[ "$FINAL_FQNS" == "$FQNS" ]] || { rollback_order; fail 'Namespace binding mismatch.'; }
[[ "$FINAL_TOPIC" == "$TOPIC" ]] || { rollback_order; fail 'Topic binding mismatch.'; }

UNRELATED_ENV_AFTER=$(unrelated_env_hash <<<"$FINAL")
IDENTITY_AFTER=$(identity_hash <<<"$FINAL")
CONFIGURATION_AFTER=$(configuration_hash <<<"$FINAL")
TEMPLATE_NON_ENV_AFTER=$(template_non_env_hash <<<"$FINAL")
FINAL_SECRET_META=$(secret_meta_json)
FINAL_SECRET_META_HASH=$(hash_stdin <<<"$FINAL_SECRET_META")

DRIFT=false
compare_hash() {
  local label=$1
  local before=$2
  local after=$3
  if [[ "$before" == "$after" ]]; then
    echo "$label: PRESERVED"
  else
    echo "$label: CHANGED" >&2
    DRIFT=true
  fi
}

compare_hash 'Unrelated environment' "$UNRELATED_ENV_BEFORE" "$UNRELATED_ENV_AFTER"
compare_hash 'Managed identity' "$IDENTITY_BEFORE" "$IDENTITY_AFTER"
compare_hash 'Configuration excluding ingress traffic' "$CONFIGURATION_BEFORE" "$CONFIGURATION_AFTER"
compare_hash 'Template excluding target env/revision metadata' "$TEMPLATE_NON_ENV_BEFORE" "$TEMPLATE_NON_ENV_AFTER"
compare_hash 'Key Vault secret metadata' "$SECRET_META_HASH" "$FINAL_SECRET_META_HASH"

if [[ "$DRIFT" == true ]]; then
  rollback_order
  fail 'Real unrelated Order runtime drift detected during activation.'
fi

FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$FINAL")
[[ -n "$FQDN" ]] || { rollback_order; fail 'Order FQDN is missing.'; }
for PATHNAME in /actuator/health/liveness /actuator/health/readiness; do
  CODE=$(curl -sS --connect-timeout 10 --max-time 30 \
    -o /dev/null -w '%{http_code}' "https://$FQDN$PATHNAME" || true)
  echo "$PATHNAME -> HTTP ${CODE:-unavailable}"
  [[ "$CODE" == '200' ]] || { rollback_order; fail "$PATHNAME did not return HTTP 200."; }
done

INTEGRATION_AFTER=$(integration_json)
verify_integration_safe "$INTEGRATION_AFTER"

SUB_FINAL=$(az servicebus topic subscription show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
  --output json --only-show-errors)
[[ "$(jq -r '.status // ""' <<<"$SUB_FINAL")" == 'Active' ]] || \
  { rollback_order; fail 'Service Bus subscription is not Active.'; }

echo ''
echo '============================================================'
echo 'ORDER DELIVERY STATUS CONSUMER ACTIVATION: PASS'
echo '============================================================'
echo "Subscription: $SUB"
echo "Rule:         $RULE"
echo 'Service Bus receiver RBAC: PASS'
echo 'Order delivery-status consumer: ENABLED'
echo 'Order liveness/readiness: PASS'
echo 'Unrelated Order runtime configuration changed: NO'
echo 'Ingress traffic revision bookkeeping ignored: YES'
echo 'Secret metadata changed: NO'
echo 'Credential values read or changed: NO'
echo 'Integration delivery-status publisher changed: NO'
echo 'Borzo/provider execution changed: NO'
echo '============================================================'
