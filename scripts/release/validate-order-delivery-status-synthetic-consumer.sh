#!/usr/bin/env bash
set -Eeuo pipefail
set +x

RG=${1:?resource group required}
ORDER_APP=${2:?order container app required}
INTEGRATION_APP=${3:?integration container app required}
NS=${4:?service bus namespace required}
TOPIC=${5:?service bus topic required}
SUB=${6:?order delivery-status subscription required}

EXPECTED_RULE='delivery-status-changed-only'
EXPECTED_FILTER="eventType = 'DELIVERY_STATUS_CHANGED' OR event_type = 'DELIVERY_STATUS_CHANGED'"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for cmd in az jq python3 sha256sum; do
  command -v "$cmd" >/dev/null 2>&1 || fail "$cmd is required."
done

ORDER_JSON=''
INTEGRATION_JSON=''
SECRET_META=''
DB_URL=''
DB_USER=''
DB_PASSWORD=''
SB_CONNECTION=''
VENV=''
TEST_ROW_CREATED=false
SENT_ANY=false
TEST_SUCCEEDED=false

CHECKOUT_ID=''
CUSTOMER_ID=''
ORDER_ID=''
KITCHEN_ID=''
DELIVERY_JOB_ID=''
EVENT_A=''
EVENT_B=''
EVENT_C=''
EVENT_D=''
EVENT_E=''
PROVIDER_DELIVERY_ID=''
T0=''
T_NO_CHANGE=''
T_STALE=''
T_DELIVERED=''
T_TERMINAL=''

cleanup_secrets() {
  unset DB_PASSWORD PGPASSWORD SB_CONNECTION EVENT_JSON 2>/dev/null || true
  if [[ -n "${VENV:-}" && -d "$VENV" ]]; then
    rm -rf "$VENV" || true
  fi
}

resolve_order_database() {
  local password_ref password_kv_url

  DB_URL=$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "SPRING_DATASOURCE_URL")][0].value // ""' <<<"$ORDER_JSON")
  DB_USER=$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "SPRING_DATASOURCE_USERNAME")][0].value // ""' <<<"$ORDER_JSON")

  [[ -n "$DB_URL" && -n "$DB_USER" ]] || \
    fail 'Order datasource URL/username are not available as runtime environment values.'

  if [[ -n "$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "SPRING_DATASOURCE_PASSWORD")][0].value // ""' <<<"$ORDER_JSON")" ]]; then
    fail 'Order datasource password is unexpectedly a plaintext environment value. Synthetic validation refused.'
  fi

  password_ref=$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "SPRING_DATASOURCE_PASSWORD")][0].secretRef // ""' <<<"$ORDER_JSON")
  [[ -n "$password_ref" ]] || fail 'Order datasource password secretRef is missing.'

  password_kv_url=$(jq -r --arg ref "$password_ref" '[.[] | select(.name == $ref)][0].keyVaultUrl // ""' <<<"$SECRET_META")
  [[ "$password_kv_url" == https://*.vault.azure.net/secrets/* ]] || \
    fail 'Order datasource password is not backed by Azure Key Vault.'

  DB_PASSWORD=$(az keyvault secret show \
    --id "$password_kv_url" \
    --query value \
    --output tsv \
    --only-show-errors)

  [[ -n "$DB_PASSWORD" ]] || fail 'Order datasource password could not be resolved from Key Vault.'
}

ensure_psql() {
  if command -v psql >/dev/null 2>&1; then
    return
  fi
  echo 'Installing PostgreSQL client on the ephemeral Azure DevOps agent.'
  sudo apt-get update -qq >/dev/null
  sudo apt-get install -y -qq postgresql-client >/dev/null
  command -v psql >/dev/null 2>&1 || fail 'PostgreSQL client installation failed.'
}

parse_jdbc() {
  local jdbc host_port db_part
  jdbc=${DB_URL#jdbc:postgresql://}
  [[ "$jdbc" != "$DB_URL" ]] || fail 'Order datasource URL is not PostgreSQL JDBC format.'

  host_port=${jdbc%%/*}
  db_part=${jdbc#*/}
  DB_HOST=${host_port%%:*}
  DB_NAME=${db_part%%\?*}
  if [[ "$host_port" == *:* ]]; then
    DB_PORT=${host_port##*:}
  else
    DB_PORT=5432
  fi

  [[ -n "$DB_HOST" && -n "$DB_NAME" ]] || fail 'Order PostgreSQL host/database could not be parsed.'
}

run_psql() {
  PGPASSWORD="$DB_PASSWORD" psql \
    "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER sslmode=require" \
    --set=ON_ERROR_STOP=1 \
    --pset=pager=off \
    "$@"
}

query_scalar() {
  local sql=$1
  run_psql --tuples-only --no-align --quiet --command="$sql" | sed '/^[[:space:]]*$/d' | tail -n 1
}

order_env_value() {
  local name=$1
  jq -r --arg name "$name" '[.properties.template.containers[0].env[]? | select(.name == $name)][0].value // ""' <<<"$ORDER_JSON"
}

integration_env_value() {
  local name=$1
  jq -r --arg name "$name" '[.properties.template.containers[0].env[]? | select(.name == $name)][0].value // ""' <<<"$INTEGRATION_JSON"
}

require_false_or_empty() {
  local label=$1
  local value=$2
  if [[ -n "$value" && "${value,,}" != 'false' ]]; then
    fail "$label must remain false/absent. Current value: $value"
  fi
}

subscription_metric() {
  local query=$1
  az servicebus topic subscription show \
    --resource-group "$RG" \
    --namespace-name "$NS" \
    --topic-name "$TOPIC" \
    --name "$SUB" \
    --query "$query" \
    --output tsv \
    --only-show-errors
}

wait_subscription_empty() {
  local attempt active
  for attempt in $(seq 1 30); do
    active=$(subscription_metric countDetails.activeMessageCount)
    if [[ "${active:-0}" == '0' ]]; then
      return 0
    fi
    echo "Waiting for delivery-status subscription to drain: attempt $attempt/30 active=$active"
    sleep 2
  done
  return 1
}

wait_inbox_status() {
  local event_id=$1
  local expected=$2
  local attempt actual
  for attempt in $(seq 1 30); do
    actual=$(query_scalar "
      SELECT COALESCE(processing_status, '')
      FROM order_schema.delivery_status_inbox
      WHERE event_id = '$event_id'::uuid;
    ")
    if [[ "$actual" == "$expected" ]]; then
      echo "Inbox $event_id -> $expected"
      return 0
    fi
    echo "Waiting for inbox result $expected: attempt $attempt/30 current=${actual:-<missing>}"
    sleep 2
  done
  return 1
}

resolve_sender_connection() {
  local rules rule
  rules=$(az servicebus namespace authorization-rule list \
    --resource-group "$RG" \
    --namespace-name "$NS" \
    --output json \
    --only-show-errors)

  rule=$(jq -r '
    [
      .[]
      | select(
          (((.rights // []) | index("Send")) != null)
          or (((.rights // []) | index("Manage")) != null)
        )
    ][0].name // ""
  ' <<<"$rules")

  [[ -n "$rule" ]] || fail 'No existing Service Bus namespace authorization rule grants Send/Manage. No RBAC or auth rule was created.'

  SB_CONNECTION=$(az servicebus namespace authorization-rule keys list \
    --resource-group "$RG" \
    --namespace-name "$NS" \
    --name "$rule" \
    --query primaryConnectionString \
    --output tsv \
    --only-show-errors)

  [[ -n "$SB_CONNECTION" ]] || fail 'Existing Service Bus sender connection string could not be resolved.'
}

ensure_servicebus_python() {
  VENV=$(mktemp -d)
  python3 -m venv "$VENV/venv"
  "$VENV/venv/bin/python" -m pip install --quiet --disable-pip-version-check 'azure-servicebus>=7.12,<8'
}

send_event() {
  local event_id=$1
  local status=$2
  local observed_at=$3
  local event_json

  event_json=$(jq -cn \
    --arg eventId "$event_id" \
    --arg occurredAt "$observed_at" \
    --arg correlationId "$CHECKOUT_ID" \
    --arg subject "delivery-job/$DELIVERY_JOB_ID" \
    --arg deliveryJobId "$DELIVERY_JOB_ID" \
    --arg orderId "$CHECKOUT_ID" \
    --arg chefSubOrderId "$ORDER_ID" \
    --arg providerId 'synthetic-craves-validation' \
    --arg providerDeliveryId "$PROVIDER_DELIVERY_ID" \
    --arg status "$status" \
    --arg observedAt "$observed_at" \
    '{
      eventId: $eventId,
      eventType: "DELIVERY_STATUS_CHANGED",
      eventVersion: "1.0",
      occurredAt: $occurredAt,
      correlationId: $correlationId,
      causationId: null,
      source: "integration-service",
      subject: $subject,
      data: {
        deliveryJobId: $deliveryJobId,
        orderId: $orderId,
        chefSubOrderId: $chefSubOrderId,
        providerId: $providerId,
        providerDeliveryId: $providerDeliveryId,
        status: $status,
        trackingUrl: null,
        observedAt: $observedAt
      }
    }')

  SB_CONNECTION="$SB_CONNECTION" \
  TOPIC="$TOPIC" \
  EVENT_ID="$event_id" \
  CORRELATION_ID="$CHECKOUT_ID" \
  EVENT_JSON="$event_json" \
  "$VENV/venv/bin/python" - <<'PY'
import os
from azure.servicebus import ServiceBusClient, ServiceBusMessage

message = ServiceBusMessage(
    os.environ["EVENT_JSON"],
    content_type="application/json",
    message_id=os.environ["EVENT_ID"],
    correlation_id=os.environ["CORRELATION_ID"],
    application_properties={
        "event_type": "DELIVERY_STATUS_CHANGED",
        "eventType": "DELIVERY_STATUS_CHANGED",
    },
)

with ServiceBusClient.from_connection_string(os.environ["SB_CONNECTION"]) as client:
    with client.get_topic_sender(topic_name=os.environ["TOPIC"]) as sender:
        sender.send_messages(message)
PY

  SENT_ANY=true
  echo "Published synthetic DELIVERY_STATUS_CHANGED event=$event_id status=$status"
}

remove_synthetic_rows() {
  [[ -n "$ORDER_ID" ]] || return 0

  run_psql --quiet --command="
    BEGIN;

    DELETE FROM order_schema.notification_outbox
    WHERE aggregate_id = '$ORDER_ID'::uuid
      AND event_key LIKE 'delivery-status-%';

    DELETE FROM order_schema.order_delivery_status_history
    WHERE order_id = '$ORDER_ID'::uuid;

    DELETE FROM order_schema.delivery_status_inbox
    WHERE chef_sub_order_id = '$ORDER_ID'::uuid;

    DELETE FROM order_schema.customer_order
    WHERE id = '$ORDER_ID'::uuid
      AND kitchen_name_snapshot = 'Craves Synthetic Delivery Validator';

    COMMIT;
  " >/dev/null
}

failure_handler() {
  local rc=$?
  set +e

  if [[ "$TEST_SUCCEEDED" != true ]]; then
    echo '' >&2
    echo 'SYNTHETIC DELIVERY-STATUS VALIDATION DID NOT COMPLETE.' >&2
    if [[ "$TEST_ROW_CREATED" == true && "$SENT_ANY" != true ]]; then
      echo 'No Service Bus event was sent. Removing the isolated synthetic Order row.' >&2
      remove_synthetic_rows >/dev/null 2>&1 || true
    elif [[ "$TEST_ROW_CREATED" == true ]]; then
      echo 'At least one Service Bus event was sent. Synthetic rows are being preserved for forensic review rather than deleted under a potentially in-flight message.' >&2
      echo "SYNTHETIC_ORDER_ID=$ORDER_ID"
      echo "SYNTHETIC_CHECKOUT_ID=$CHECKOUT_ID"
      echo "SYNTHETIC_DELIVERY_JOB_ID=$DELIVERY_JOB_ID"
      echo "SYNTHETIC_EVENT_A=$EVENT_A"
      echo "SYNTHETIC_EVENT_B=$EVENT_B"
      echo "SYNTHETIC_EVENT_C=$EVENT_C"
      echo "SYNTHETIC_EVENT_D=$EVENT_D"
      echo "SYNTHETIC_EVENT_E=$EVENT_E"
    fi
  fi

  cleanup_secrets
  exit "$rc"
}
trap failure_handler EXIT

echo '============================================================'
echo '1. VERIFY FAIL-CLOSED RUNTIME BASELINE'
echo '============================================================'

ORDER_JSON=$(az containerapp show --resource-group "$RG" --name "$ORDER_APP" --output json --only-show-errors)
INTEGRATION_JSON=$(az containerapp show --resource-group "$RG" --name "$INTEGRATION_APP" --output json --only-show-errors)

ORDER_LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$ORDER_JSON")
ORDER_READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$ORDER_JSON")
ORDER_RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$ORDER_JSON")
INTEGRATION_LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$INTEGRATION_JSON")
INTEGRATION_READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$INTEGRATION_JSON")
INTEGRATION_RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$INTEGRATION_JSON")

[[ -n "$ORDER_LATEST" && "$ORDER_LATEST" == "$ORDER_READY" && "$ORDER_RUNNING" == 'Running' ]] || \
  fail 'Order Container App is not on one ready/running latest revision.'
[[ -n "$INTEGRATION_LATEST" && "$INTEGRATION_LATEST" == "$INTEGRATION_READY" && "$INTEGRATION_RUNNING" == 'Running' ]] || \
  fail 'Integration Container App is not on one ready/running latest revision.'
[[ "$(order_env_value CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED | tr '[:upper:]' '[:lower:]')" == 'true' ]] || \
  fail 'Order delivery-status consumer is not enabled.'

require_false_or_empty 'Order notification outbox dispatcher' "$(order_env_value CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)"
require_false_or_empty 'Order direct notification dispatch' "$(order_env_value CRAVES_NOTIFICATION_DIRECT_DISPATCH_ENABLED)"
require_false_or_empty 'Integration delivery-status publisher' "$(integration_env_value CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED)"
require_false_or_empty 'Integration delivery command worker' "$(integration_env_value CRAVES_DELIVERY_COMMAND_ENABLED)"
require_false_or_empty 'Integration delivery reconciliation' "$(integration_env_value CRAVES_DELIVERY_RECONCILIATION_ENABLED)"
require_false_or_empty 'Integration delivery webhook processing' "$(integration_env_value CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED)"
require_false_or_empty 'Integration delivery tracking reconciliation' "$(integration_env_value CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED)"
require_false_or_empty 'Borzo provider execution' "$(integration_env_value BORZO_API_ENABLED)"

echo "Order revision:       $ORDER_LATEST"
echo "Integration revision: $INTEGRATION_LATEST"
echo 'Fail-closed provider/publisher state: PASS'

echo ''
echo '============================================================'
echo '2. VERIFY SERVICE BUS SUBSCRIPTION AND FILTER'
echo '============================================================'

SUB_JSON=$(az servicebus topic subscription show \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --name "$SUB" \
  --output json --only-show-errors)

[[ "$(jq -r '.status // ""' <<<"$SUB_JSON")" == 'Active' ]] || fail 'Delivery-status Service Bus subscription is not Active.'
[[ "$(jq -r '.countDetails.activeMessageCount // 0' <<<"$SUB_JSON")" == '0' ]] || fail 'Delivery-status subscription already has active messages. Test refused.'
[[ "$(jq -r '.countDetails.deadLetterMessageCount // 0' <<<"$SUB_JSON")" == '0' ]] || fail 'Delivery-status subscription DLQ is not empty. Test refused.'

RULES_JSON=$(az servicebus topic subscription rule list \
  --resource-group "$RG" --namespace-name "$NS" --topic-name "$TOPIC" --subscription-name "$SUB" \
  --output json --only-show-errors)

[[ "$(jq 'length' <<<"$RULES_JSON")" == '1' ]] || fail 'Expected exactly one delivery-status Service Bus rule.'
[[ "$(jq -r '.[0].name // ""' <<<"$RULES_JSON")" == "$EXPECTED_RULE" ]] || fail 'Unexpected delivery-status rule name.'
[[ "$(jq -r '.[0].filterType // ""' <<<"$RULES_JSON")" == 'SqlFilter' ]] || fail 'Delivery-status rule is not SqlFilter.'
[[ "$(jq -r '.[0].sqlFilter.sqlExpression // ""' <<<"$RULES_JSON")" == "$EXPECTED_FILTER" ]] || fail 'Delivery-status SQL filter differs from the approved expression.'

echo 'Service Bus subscription/filter baseline: PASS'

echo ''
echo '============================================================'
echo '3. VERIFY KEY VAULT-BACKED ORDER DATABASE ACCESS'
echo '============================================================'

SECRET_META=$(az containerapp secret list --resource-group "$RG" --name "$ORDER_APP" --output json --only-show-errors)

while IFS= read -r REF; do
  [[ -n "$REF" ]] || continue
  KV_URL=$(jq -r --arg ref "$REF" '[.[] | select(.name == $ref)][0].keyVaultUrl // ""' <<<"$SECRET_META")
  KV_IDENTITY=$(jq -r --arg ref "$REF" '[.[] | select(.name == $ref)][0].identity // ""' <<<"$SECRET_META")
  [[ "$KV_URL" == https://*.vault.azure.net/secrets/* ]] || fail "Active Order secretRef '$REF' is not Key Vault-backed."
  [[ "$KV_IDENTITY" == 'system' || "$KV_IDENTITY" == /subscriptions/* ]] || fail "Active Order secretRef '$REF' has no supported managed identity."
done < <(jq -r '.properties.template.containers[].env[]? | .secretRef // empty' <<<"$ORDER_JSON" | sort -u)

resolve_order_database
ensure_psql
parse_jdbc

if ! query_scalar 'SELECT 1;' >/dev/null 2>&1; then
  fail 'Azure DevOps hosted agent cannot reach the Order PostgreSQL database. No synthetic data was created; firewall/networking was not changed.'
fi

echo 'Order database connectivity: PASS'
echo 'Credential values printed: NO'

echo ''
echo '============================================================'
echo '4. VERIFY CURRENT ORDER SCHEMA SHAPE'
echo '============================================================'

for TABLE in customer_order delivery_status_inbox order_delivery_status_history notification_outbox; do
  EXISTS=$(query_scalar "SELECT CASE WHEN to_regclass('order_schema.$TABLE') IS NULL THEN '0' ELSE '1' END;")
  [[ "$EXISTS" == '1' ]] || fail "Required Order table is missing: order_schema.$TABLE"
done

for COLUMN in accepted_at ready_at delivery_job_id delivery_status delivery_status_observed_at delivery_status_event_id; do
  EXISTS=$(query_scalar "
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema='order_schema'
      AND table_name='customer_order'
      AND column_name='$COLUMN';
  ")
  [[ "$EXISTS" == '1' ]] || fail "Required customer_order column is missing: $COLUMN"
done

UNEXPECTED_REQUIRED=$(run_psql --tuples-only --no-align --quiet --command="
  SELECT column_name
  FROM information_schema.columns
  WHERE table_schema='order_schema'
    AND table_name='customer_order'
    AND is_nullable='NO'
    AND column_default IS NULL
    AND column_name NOT IN (
      'id','checkout_id','customer_identity_id','kitchen_id','status','currency',
      'food_subtotal','platform_fee','tax_amount','delivery_fee','grand_total'
    )
  ORDER BY ordinal_position;
" | sed '/^[[:space:]]*$/d')

[[ -z "$UNEXPECTED_REQUIRED" ]] || fail "Unexpected required customer_order columns without defaults: $UNEXPECTED_REQUIRED"

echo 'Synthetic row compatibility check: PASS'

echo ''
echo '============================================================'
echo '5. GENERATE ISOLATED SYNTHETIC IDENTIFIERS'
echo '============================================================'

readarray -t GENERATED < <(python3 - <<'PY'
import uuid
for _ in range(10):
    print(uuid.uuid4())
PY
)

CHECKOUT_ID=${GENERATED[0]}
CUSTOMER_ID=${GENERATED[1]}
ORDER_ID=${GENERATED[2]}
KITCHEN_ID=${GENERATED[3]}
DELIVERY_JOB_ID=${GENERATED[4]}
EVENT_A=${GENERATED[5]}
EVENT_B=${GENERATED[6]}
EVENT_C=${GENERATED[7]}
EVENT_D=${GENERATED[8]}
EVENT_E=${GENERATED[9]}
PROVIDER_DELIVERY_ID="synthetic-$DELIVERY_JOB_ID"

readarray -t TIMES < <(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
base = datetime.now(timezone.utc) - timedelta(minutes=5)
for value in (
    base,
    base + timedelta(seconds=30),
    base - timedelta(seconds=60),
    base + timedelta(seconds=60),
    base + timedelta(seconds=120),
):
    print(value.isoformat().replace('+00:00', 'Z'))
PY
)

T0=${TIMES[0]}
T_NO_CHANGE=${TIMES[1]}
T_STALE=${TIMES[2]}
T_DELIVERED=${TIMES[3]}
T_TERMINAL=${TIMES[4]}

echo "Synthetic checkout ID:     $CHECKOUT_ID"
echo "Synthetic chef sub-order:  $ORDER_ID"
echo "Synthetic delivery job:    $DELIVERY_JOB_ID"
echo 'Real customer order used:  NO'
echo 'Real PII used:             NO'

echo ''
echo '============================================================'
echo '6. INSERT ONE SYNTHETIC ACCEPTED ORDER'
echo '============================================================'

run_psql --quiet --command="
  INSERT INTO order_schema.customer_order (
    id,
    checkout_id,
    customer_identity_id,
    kitchen_id,
    kitchen_name_snapshot,
    status,
    currency,
    food_subtotal,
    platform_fee,
    tax_amount,
    delivery_fee,
    grand_total,
    prep_time_minutes,
    accepted_at,
    ready_at,
    created_at,
    updated_at
  ) VALUES (
    '$ORDER_ID'::uuid,
    '$CHECKOUT_ID'::uuid,
    '$CUSTOMER_ID'::uuid,
    '$KITCHEN_ID'::uuid,
    'Craves Synthetic Delivery Validator',
    'CHEF_ACCEPTED',
    'INR',
    1.00,
    0.00,
    0.00,
    0.00,
    1.00,
    30,
    now() - interval '10 minutes',
    now() + interval '20 minutes',
    now(),
    now()
  );
" >/dev/null
TEST_ROW_CREATED=true

ROW_COUNT=$(query_scalar "
  SELECT COUNT(*)
  FROM order_schema.customer_order
  WHERE id='$ORDER_ID'::uuid
    AND checkout_id='$CHECKOUT_ID'::uuid
    AND status='CHEF_ACCEPTED'
    AND accepted_at IS NOT NULL
    AND ready_at > accepted_at
    AND kitchen_name_snapshot='Craves Synthetic Delivery Validator';
")
[[ "$ROW_COUNT" == '1' ]] || fail 'Synthetic accepted Order row was not created exactly once.'

echo 'Synthetic Order row: CREATED'

echo ''
echo '============================================================'
echo '7. PREPARE EXISTING SERVICE BUS SENDER ACCESS'
echo '============================================================'

resolve_sender_connection
ensure_servicebus_python

echo 'Existing Service Bus sender authorization resolved without printing credentials.'
echo 'New role/auth rule created: NO'

echo ''
echo '============================================================'
echo '8. VALIDATE APPLY + DUPLICATE IDEMPOTENCY'
echo '============================================================'

send_event "$EVENT_A" 'PENDING' "$T0"
wait_inbox_status "$EVENT_A" 'PROCESSED' || fail 'Initial PENDING event was not processed.'
wait_subscription_empty || fail 'Subscription did not drain after initial PENDING event.'

PROJECTION=$(query_scalar "
  SELECT CONCAT_WS('|', delivery_status, delivery_job_id::text, delivery_status_event_id::text)
  FROM order_schema.customer_order
  WHERE id='$ORDER_ID'::uuid;
")
[[ "$PROJECTION" == "PENDING|$DELIVERY_JOB_ID|$EVENT_A" ]] || fail "Initial delivery projection mismatch: $PROJECTION"

HISTORY_COUNT=$(query_scalar "SELECT COUNT(*) FROM order_schema.order_delivery_status_history WHERE order_id='$ORDER_ID'::uuid;")
[[ "$HISTORY_COUNT" == '1' ]] || fail 'Initial PENDING event did not create exactly one history row.'

send_event "$EVENT_A" 'PENDING' "$T0"
wait_subscription_empty || fail 'Subscription did not drain after duplicate event.'
sleep 2

INBOX_A_COUNT=$(query_scalar "SELECT COUNT(*) FROM order_schema.delivery_status_inbox WHERE event_id='$EVENT_A'::uuid;")
HISTORY_COUNT=$(query_scalar "SELECT COUNT(*) FROM order_schema.order_delivery_status_history WHERE order_id='$ORDER_ID'::uuid;")
[[ "$INBOX_A_COUNT" == '1' && "$HISTORY_COUNT" == '1' ]] || fail 'Duplicate event changed inbox/history cardinality.'

echo 'Duplicate event-id idempotency: PASS'

echo ''
echo '============================================================'
echo '9. VALIDATE NO_CHANGE AND STALE PROTECTION'
echo '============================================================'

send_event "$EVENT_B" 'PENDING' "$T_NO_CHANGE"
wait_inbox_status "$EVENT_B" 'NO_CHANGE' || fail 'Newer same-state event was not classified NO_CHANGE.'

send_event "$EVENT_C" 'SEARCHING' "$T_STALE"
wait_inbox_status "$EVENT_C" 'STALE' || fail 'Older delivery event was not classified STALE.'
wait_subscription_empty || fail 'Subscription did not drain after NO_CHANGE/STALE tests.'

HISTORY_COUNT=$(query_scalar "SELECT COUNT(*) FROM order_schema.order_delivery_status_history WHERE order_id='$ORDER_ID'::uuid;")
[[ "$HISTORY_COUNT" == '1' ]] || fail 'NO_CHANGE or STALE event incorrectly added delivery history.'

echo 'NO_CHANGE protection: PASS'
echo 'STALE protection: PASS'

echo ''
echo '============================================================'
echo '10. VALIDATE TERMINAL PROJECTION + TERMINAL PROTECTION'
echo '============================================================'

send_event "$EVENT_D" 'DELIVERED' "$T_DELIVERED"
wait_inbox_status "$EVENT_D" 'PROCESSED' || fail 'DELIVERED event was not processed.'

DELIVERED_MATCH=$(query_scalar "
  SELECT COUNT(*)
  FROM order_schema.customer_order
  WHERE id='$ORDER_ID'::uuid
    AND status='CHEF_ACCEPTED'
    AND delivery_status='DELIVERED'
    AND delivery_job_id='$DELIVERY_JOB_ID'::uuid
    AND delivery_status_event_id='$EVENT_D'::uuid;
")
[[ "$DELIVERED_MATCH" == '1' ]] || fail 'DELIVERED projection was not applied while preserving commercial Order status.'

HISTORY_COUNT=$(query_scalar "SELECT COUNT(*) FROM order_schema.order_delivery_status_history WHERE order_id='$ORDER_ID'::uuid;")
[[ "$HISTORY_COUNT" == '2' ]] || fail 'DELIVERED event did not create the second expected history row.'

NOTIFICATION_MATCH=$(query_scalar "
  SELECT COUNT(*)
  FROM order_schema.notification_outbox
  WHERE event_key='delivery-status-$EVENT_D'
    AND aggregate_id='$ORDER_ID'::uuid
    AND event_type='DELIVERY_DELIVERED'
    AND status='PENDING';
")
[[ "$NOTIFICATION_MATCH" == '1' ]] || fail 'DELIVERED did not create exactly one pending synthetic notification outbox row.'

send_event "$EVENT_E" 'IN_TRANSIT' "$T_TERMINAL"
wait_inbox_status "$EVENT_E" 'TERMINAL_PROTECTED' || fail 'Post-terminal event was not classified TERMINAL_PROTECTED.'
wait_subscription_empty || fail 'Subscription did not drain after terminal-protection test.'

FINAL_DELIVERY_STATUS=$(query_scalar "SELECT COALESCE(delivery_status,'') FROM order_schema.customer_order WHERE id='$ORDER_ID'::uuid;")
HISTORY_COUNT=$(query_scalar "SELECT COUNT(*) FROM order_schema.order_delivery_status_history WHERE order_id='$ORDER_ID'::uuid;")
NOTIFICATION_COUNT=$(query_scalar "SELECT COUNT(*) FROM order_schema.notification_outbox WHERE aggregate_id='$ORDER_ID'::uuid AND event_key LIKE 'delivery-status-%';")

[[ "$FINAL_DELIVERY_STATUS" == 'DELIVERED' ]] || fail 'Terminal projection regressed after post-terminal event.'
[[ "$HISTORY_COUNT" == '2' ]] || fail 'Terminal-protected event incorrectly added delivery history.'
[[ "$NOTIFICATION_COUNT" == '1' ]] || fail 'Synthetic notification outbox cardinality changed unexpectedly.'

echo 'DELIVERED projection: PASS'
echo 'Commercial Order status preserved: PASS'
echo 'Pending notification outbox evidence: PASS'
echo 'TERMINAL_PROTECTED behavior: PASS'

echo ''
echo '============================================================'
echo '11. VERIFY FINAL SERVICE BUS / INTEGRATION SAFETY STATE'
echo '============================================================'

wait_subscription_empty || fail 'Delivery-status subscription still has active messages.'
FINAL_DLQ=$(subscription_metric countDetails.deadLetterMessageCount)
[[ "${FINAL_DLQ:-0}" == '0' ]] || fail "Delivery-status DLQ is not empty after synthetic test: $FINAL_DLQ"

INTEGRATION_JSON=$(az containerapp show --resource-group "$RG" --name "$INTEGRATION_APP" --output json --only-show-errors)
require_false_or_empty 'Integration delivery-status publisher' "$(integration_env_value CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED)"
require_false_or_empty 'Integration delivery command worker' "$(integration_env_value CRAVES_DELIVERY_COMMAND_ENABLED)"
require_false_or_empty 'Integration delivery reconciliation' "$(integration_env_value CRAVES_DELIVERY_RECONCILIATION_ENABLED)"
require_false_or_empty 'Integration delivery webhook processing' "$(integration_env_value CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED)"
require_false_or_empty 'Integration delivery tracking reconciliation' "$(integration_env_value CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED)"
require_false_or_empty 'Borzo provider execution' "$(integration_env_value BORZO_API_ENABLED)"

echo 'Service Bus active messages: 0'
echo 'Service Bus dead letters:    0'
echo 'Integration publisher:       DISABLED'
echo 'Borzo/provider execution:    DISABLED'

echo ''
echo '============================================================'
echo '12. REMOVE ALL SYNTHETIC DATABASE RECORDS'
echo '============================================================'

remove_synthetic_rows

REMAINING=$(query_scalar "
  SELECT
      (SELECT COUNT(*) FROM order_schema.customer_order WHERE id='$ORDER_ID'::uuid)
    + (SELECT COUNT(*) FROM order_schema.delivery_status_inbox WHERE chef_sub_order_id='$ORDER_ID'::uuid)
    + (SELECT COUNT(*) FROM order_schema.order_delivery_status_history WHERE order_id='$ORDER_ID'::uuid)
    + (SELECT COUNT(*) FROM order_schema.notification_outbox WHERE aggregate_id='$ORDER_ID'::uuid AND event_key LIKE 'delivery-status-%');
")
[[ "$REMAINING" == '0' ]] || fail "Synthetic cleanup incomplete. Remaining rows: $REMAINING"

TEST_SUCCEEDED=true
trap - EXIT
cleanup_secrets

echo ''
echo '============================================================'
echo 'ORDER DELIVERY STATUS SYNTHETIC CONSUMER VALIDATION: PASS'
echo '============================================================'
echo 'Real customer order used:                 NO'
echo 'Real PII used:                            NO'
echo 'Initial status APPLY:                     PASS'
echo 'Duplicate event idempotency:              PASS'
echo 'NO_CHANGE protection:                     PASS'
echo 'STALE protection:                         PASS'
echo 'DELIVERED terminal projection:            PASS'
echo 'TERMINAL_PROTECTED behavior:              PASS'
echo 'Commercial Order status mutated:          NO'
echo 'Synthetic notification externally sent:   NO'
echo 'Delivery-status DLQ:                      0'
echo 'Integration delivery-status publisher:    DISABLED'
echo 'Borzo/provider execution:                 DISABLED'
echo 'Synthetic database rows remaining:        0'
echo 'Credential values printed:                NO'
echo 'Credential rotation performed:            NO'
echo 'Azure firewall/networking changed:        NO'
echo 'New Service Bus role/auth rule created:   NO'
echo '============================================================'
