#!/usr/bin/env bash
set -euo pipefail
set +x

RG=${1:?resource group required}
INTEGRATION_APP=${2:?integration container app required}
ORDER_APP=${3:?order container app required}
NS=${4:?service bus namespace required}
FQNS=${5:?service bus fully-qualified namespace required}
TOPIC=${6:?topic name required}
ORDER_SUB=${7:?order subscription required}
ORDER_RULE=${8:?order subscription rule required}

EXPECTED_FILTER="eventType = 'DELIVERY_STATUS_CHANGED' OR event_type = 'DELIVERY_STATUS_CHANGED'"
TARGET='CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED'

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for cmd in az jq sha256sum curl; do
  command -v "$cmd" >/dev/null 2>&1 || fail "$cmd is required"
done

[[ "$FQNS" == "$NS.servicebus.windows.net" ]] || fail "Fully-qualified namespace must be $NS.servicebus.windows.net"

integration_json() {
  az containerapp show --resource-group "$RG" --name "$INTEGRATION_APP" --output json --only-show-errors
}

order_json() {
  az containerapp show --resource-group "$RG" --name "$ORDER_APP" --output json --only-show-errors
}

secret_meta_json() {
  az containerapp secret list --resource-group "$RG" --name "$INTEGRATION_APP" --output json --only-show-errors \
    | jq -S '[.[] | {name, keyVaultUrl:(.keyVaultUrl // null), identity:(.identity // null)}] | sort_by(.name)'
}

hash_stdin() {
  sha256sum | awk '{print $1}'
}

unrelated_env_hash() {
  jq -S --arg target "$TARGET" '
    [
      .properties.template.containers[] as $container
      | ($container.env // [])[]
      | . as $env
      | select($env.name != $target)
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

wait_for_revision() {
  local previous_latest=$1
  local expect_publisher=$2
  local allow_same=${3:-false}
  local attempt final latest ready running health publisher

  for attempt in $(seq 1 60); do
    final=$(integration_json)
    latest=$(jq -r '.properties.latestRevisionName // ""' <<<"$final")
    ready=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$final")
    running=$(jq -r '.properties.runningStatus // ""' <<<"$final")
    publisher=$(value_of "$final" "$TARGET")
    health=''
    if [[ -n "$latest" ]]; then
      health=$(az containerapp revision show \
        --resource-group "$RG" --name "$INTEGRATION_APP" --revision "$latest" \
        --query properties.healthState --output tsv --only-show-errors 2>/dev/null || true)
    fi

    echo "Attempt $attempt/60: latest=$latest ready=$ready health=$health running=$running statusPublisher=${publisher:-<empty>}" >&2

    if [[ "$latest" == "$ready" && "$health" == 'Healthy' && "$running" == 'Running' ]]; then
      if [[ "$allow_same" == true || "$latest" != "$previous_latest" ]]; then
        if [[ "${publisher,,}" == "${expect_publisher,,}" ]]; then
          printf '%s' "$final"
          return 0
        fi
      fi
    fi
    sleep 10
  done
  return 1
}

rollback_publisher() {
  local previous=$1
  echo 'Rolling back Integration delivery-status publisher control.' >&2
  if [[ "$previous" == '__ABSENT__' ]]; then
    az containerapp update --resource-group "$RG" --name "$INTEGRATION_APP" \
      --remove-env-vars "$TARGET" --no-wait --output none --only-show-errors || true
  else
    az containerapp update --resource-group "$RG" --name "$INTEGRATION_APP" \
      --set-env-vars "$TARGET=$previous" --no-wait --output none --only-show-errors || true
  fi
}

echo '============================================================'
echo '1. VERIFY ORDER CONSUMER AND INTEGRATION FAIL-CLOSED BASELINE'
echo '============================================================'

BEFORE=$(integration_json)
ORDER=$(order_json)
PRINCIPAL_ID=$(jq -r '.identity.principalId // ""' <<<"$BEFORE")
PREVIOUS_LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$BEFORE")
PREVIOUS_READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$BEFORE")
RUNNING_BEFORE=$(jq -r '.properties.runningStatus // ""' <<<"$BEFORE")
ORDER_LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$ORDER")
ORDER_READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$ORDER")
ORDER_RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$ORDER")

[[ -n "$PRINCIPAL_ID" ]] || fail 'Integration Container App has no system-assigned managed identity.'
[[ -n "$PREVIOUS_LATEST" && "$PREVIOUS_LATEST" == "$PREVIOUS_READY" && "$RUNNING_BEFORE" == 'Running' ]] || \
  fail 'Integration must be Running with latest revision Ready before publisher activation.'
[[ -n "$ORDER_LATEST" && "$ORDER_LATEST" == "$ORDER_READY" && "$ORDER_RUNNING" == 'Running' ]] || \
  fail 'Order must be Running with latest revision Ready before publisher activation.'
[[ "$(value_of "$ORDER" CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED | tr '[:upper:]' '[:lower:]')" == 'true' ]] || \
  fail 'Order delivery-status consumer must be enabled.'

require_false_or_empty 'Integration delivery command' "$(value_of "$BEFORE" CRAVES_DELIVERY_COMMAND_ENABLED)"
require_false_or_empty 'Integration create reconciliation' "$(value_of "$BEFORE" CRAVES_DELIVERY_RECONCILIATION_ENABLED)"
require_false_or_empty 'Integration webhook processing' "$(value_of "$BEFORE" CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED)"
require_false_or_empty 'Integration tracking reconciliation' "$(value_of "$BEFORE" CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED)"
require_false_or_empty 'Borzo API' "$(value_of "$BEFORE" BORZO_API_ENABLED)"

CURRENT_PUBLISHER=$(value_of "$BEFORE" "$TARGET")
if [[ -n "$CURRENT_PUBLISHER" && "${CURRENT_PUBLISHER,,}" != 'false' && "${CURRENT_PUBLISHER,,}" != 'true' ]]; then
  fail "Unexpected publisher flag value: $CURRENT_PUBLISHER"
fi

CURRENT_FQNS=$(value_of "$BEFORE" SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE)
CURRENT_TOPIC=$(value_of "$BEFORE" SERVICE_BUS_TOPIC_NAME)
[[ "$CURRENT_FQNS" == "$FQNS" ]] || fail "Integration SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE must already equal $FQNS before activation. Current: ${CURRENT_FQNS:-<empty>}"
if [[ -n "$CURRENT_TOPIC" && "$CURRENT_TOPIC" != "$TOPIC" ]]; then
  fail "Integration SERVICE_BUS_TOPIC_NAME differs from expected topic. Current: $CURRENT_TOPIC"
fi

echo 'Order consumer: ENABLED'
echo 'Integration provider/worker controls: DISABLED'
echo 'Integration Service Bus namespace/topic configuration: PASS'

echo ''
echo '============================================================'
echo '2. VERIFY SERVICE BUS SUBSCRIPTION / FILTER / QUEUE STATE'
echo '============================================================'

SUB_JSON=$(az servicebus topic subscription show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$ORDER_SUB" \
  --output json --only-show-errors)
[[ "$(jq -r '.status // ""' <<<"$SUB_JSON")" == 'Active' ]] || fail 'Order delivery-status subscription must be Active.'
[[ "$(jq -r '.countDetails.activeMessageCount // 0' <<<"$SUB_JSON")" == '0' ]] || fail 'Order delivery-status subscription must have zero active messages before publisher activation.'
[[ "$(jq -r '.countDetails.deadLetterMessageCount // 0' <<<"$SUB_JSON")" == '0' ]] || fail 'Order delivery-status DLQ must be empty before publisher activation.'

RULES=$(az servicebus topic subscription rule list \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --subscription-name "$ORDER_SUB" \
  --output json --only-show-errors)
[[ "$(jq 'length' <<<"$RULES")" == '1' ]] || fail 'Order delivery-status subscription must contain exactly one rule.'
[[ "$(jq -r '.[0].name // ""' <<<"$RULES")" == "$ORDER_RULE" ]] || fail 'Unexpected delivery-status rule name.'
[[ "$(jq -r '.[0].filterType // ""' <<<"$RULES")" == 'SqlFilter' ]] || fail 'Delivery-status rule must be SqlFilter.'
[[ "$(jq -r '.[0].sqlFilter.sqlExpression // ""' <<<"$RULES")" == "$EXPECTED_FILTER" ]] || fail 'Delivery-status SQL filter does not match approved expression.'

echo 'Service Bus subscription/filter/queue state: PASS'

echo ''
echo '============================================================'
echo '3. VERIFY ACTIVE INTEGRATION SECRETREFS ARE KEY VAULT-BACKED'
echo '============================================================'

SECRET_META=$(secret_meta_json)
SECRET_META_HASH=$(hash_stdin <<<"$SECRET_META")
while IFS= read -r ref; do
  [[ -n "$ref" ]] || continue
  kv_url=$(jq -r --arg ref "$ref" '[.[] | select(.name == $ref)][0].keyVaultUrl // ""' <<<"$SECRET_META")
  [[ "$kv_url" == https://*.vault.azure.net/secrets/* ]] || fail "Active Integration secretRef '$ref' is not Key Vault-backed."
done < <(jq -r '.properties.template.containers[].env[]? | .secretRef // empty' <<<"$BEFORE" | sort -u)
[[ -z "$(secret_ref_of "$BEFORE" "$TARGET")" ]] || fail "$TARGET unexpectedly uses secretRef."

echo 'All active Integration secretRefs are Key Vault-backed: PASS'

echo ''
echo '============================================================'
echo '4. VERIFY INTEGRATION MANAGED IDENTITY SENDER RBAC'
echo '============================================================'

TOPIC_SCOPE=$(az servicebus topic show \
  --resource-group "$RG" --namespace-name "$NS" --name "$TOPIC" \
  --query id --output tsv --only-show-errors)
SENDER_COUNT=$(az role assignment list \
  --assignee-object-id "$PRINCIPAL_ID" \
  --scope "$TOPIC_SCOPE" \
  --role 'Azure Service Bus Data Sender' \
  --include-inherited \
  --fill-principal-name false \
  --query 'length(@)' \
  --output tsv \
  --only-show-errors)

if [[ "${SENDER_COUNT:-0}" == '0' ]]; then
  echo 'ERROR: Integration managed identity is missing Azure Service Bus Data Sender.' >&2
  echo "PRINCIPAL_ID=$PRINCIPAL_ID"
  echo "TOPIC_SCOPE=$TOPIC_SCOPE"
  echo 'Grant the role at this topic scope or a parent scope, then rerun.' >&2
  exit 2
fi

echo 'Azure Service Bus Data Sender (direct or inherited): PASS'

echo ''
echo '============================================================'
echo '5. CAPTURE RUNTIME PRESERVATION FINGERPRINTS'
echo '============================================================'

PREV_PUBLISHER=$(previous_value_of "$BEFORE" "$TARGET")
UNRELATED_ENV_BEFORE=$(unrelated_env_hash <<<"$BEFORE")
CONFIGURATION_BEFORE=$(configuration_hash <<<"$BEFORE")
TEMPLATE_BEFORE=$(template_non_env_hash <<<"$BEFORE")
IDENTITY_BEFORE=$(identity_hash <<<"$BEFORE")

echo 'Runtime/identity/secret metadata fingerprints captured: PASS'

if [[ "${CURRENT_PUBLISHER,,}" == 'true' ]]; then
  echo 'Publisher is already enabled. All safety prerequisites passed; no runtime mutation required.'
  exit 0
fi

echo ''
echo '============================================================'
echo '6. ENABLE ONLY DELIVERY STATUS PUBLISHER'
echo '============================================================'

az containerapp update \
  --resource-group "$RG" --name "$INTEGRATION_APP" \
  --set-env-vars "$TARGET=true" \
  --no-wait --output none --only-show-errors \
  || fail 'Unable to update Integration publisher flag.'

FINAL=''
if ! FINAL=$(wait_for_revision "$PREVIOUS_LATEST" true false); then
  rollback_publisher "$PREV_PUBLISHER"
  fail 'Publisher activation revision did not become healthy within 10 minutes.'
fi

echo ''
echo '============================================================'
echo '7. VERIFY PRESERVATION AND HEALTH'
echo '============================================================'

UNRELATED_ENV_AFTER=$(unrelated_env_hash <<<"$FINAL")
CONFIGURATION_AFTER=$(configuration_hash <<<"$FINAL")
TEMPLATE_AFTER=$(template_non_env_hash <<<"$FINAL")
IDENTITY_AFTER=$(identity_hash <<<"$FINAL")
SECRET_META_AFTER=$(secret_meta_json)
SECRET_META_HASH_AFTER=$(hash_stdin <<<"$SECRET_META_AFTER")

check_equal() {
  local label=$1 before=$2 after=$3
  if [[ "$before" != "$after" ]]; then
    echo "ERROR: $label changed during publisher activation." >&2
    rollback_publisher "$PREV_PUBLISHER"
    exit 1
  fi
  echo "$label: PRESERVED"
}

check_equal 'Unrelated Integration environment' "$UNRELATED_ENV_BEFORE" "$UNRELATED_ENV_AFTER"
check_equal 'Integration configuration excluding ingress traffic' "$CONFIGURATION_BEFORE" "$CONFIGURATION_AFTER"
check_equal 'Integration template excluding environment/revision metadata' "$TEMPLATE_BEFORE" "$TEMPLATE_AFTER"
check_equal 'Integration managed identity' "$IDENTITY_BEFORE" "$IDENTITY_AFTER"
check_equal 'Integration Key Vault secret metadata' "$SECRET_META_HASH" "$SECRET_META_HASH_AFTER"

[[ "$(value_of "$FINAL" "$TARGET" | tr '[:upper:]' '[:lower:]')" == 'true' ]] || { rollback_publisher "$PREV_PUBLISHER"; fail 'Publisher flag is not true after activation.'; }
require_false_or_empty 'Integration delivery command' "$(value_of "$FINAL" CRAVES_DELIVERY_COMMAND_ENABLED)"
require_false_or_empty 'Integration create reconciliation' "$(value_of "$FINAL" CRAVES_DELIVERY_RECONCILIATION_ENABLED)"
require_false_or_empty 'Integration webhook processing' "$(value_of "$FINAL" CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED)"
require_false_or_empty 'Integration tracking reconciliation' "$(value_of "$FINAL" CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED)"
require_false_or_empty 'Borzo API' "$(value_of "$FINAL" BORZO_API_ENABLED)"
[[ "$(value_of "$FINAL" SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE)" == "$FQNS" ]] || { rollback_publisher "$PREV_PUBLISHER"; fail 'Service Bus namespace changed during activation.'; }

FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$FINAL")
if [[ -n "$FQDN" ]]; then
  for path in actuator/health/liveness actuator/health/readiness; do
    code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "https://$FQDN/$path" || true)
    [[ "$code" == '200' ]] || { rollback_publisher "$PREV_PUBLISHER"; fail "/$path returned HTTP $code"; }
    echo "/$path -> HTTP 200"
  done
fi

echo ''
echo '============================================================'
echo 'INTEGRATION DELIVERY STATUS PUBLISHER ACTIVATION: PASS'
echo '============================================================'
echo 'Order delivery-status consumer:              ENABLED'
echo 'Integration delivery-status publisher:       ENABLED'
echo 'Integration delivery command:                DISABLED'
echo 'Integration reconciliation:                  DISABLED'
echo 'Integration webhook processing:              DISABLED'
echo 'Integration tracking reconciliation:         DISABLED'
echo 'Borzo/provider execution:                    DISABLED'
echo 'Unrelated Integration runtime changed:       NO'
echo 'Integration identity changed:                NO'
echo 'Key Vault secret metadata changed:           NO'
echo 'Credential values read or changed:           NO'
