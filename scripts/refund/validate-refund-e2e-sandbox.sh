#!/usr/bin/env bash
set -euo pipefail
set +x

: "${RESOURCE_GROUP:?RESOURCE_GROUP is required}"
: "${ORDER_APP:?ORDER_APP is required}"
: "${INTEGRATION_APP:?INTEGRATION_APP is required}"
: "${NOTIFICATION_APP:?NOTIFICATION_APP is required}"
: "${SERVICE_BUS_NAMESPACE:?SERVICE_BUS_NAMESPACE is required}"
: "${SERVICE_BUS_TOPIC:?SERVICE_BUS_TOPIC is required}"
: "${INTEGRATION_REFUND_SUBSCRIPTION:?INTEGRATION_REFUND_SUBSCRIPTION is required}"
: "${ORDER_REFUND_STATUS_SUBSCRIPTION:?ORDER_REFUND_STATUS_SUBSCRIPTION is required}"
: "${SYNTHETIC_CHEF_SUB_ORDER_ID:?SYNTHETIC_CHEF_SUB_ORDER_ID is required}"
: "${CONFIRM_SYNTHETIC_SANDBOX_REFUND:?CONFIRM_SYNTHETIC_SANDBOX_REFUND is required}"
: "${FORCE_ACCEPTANCE_EXPIRY:?FORCE_ACCEPTANCE_EXPIRY is required}"
: "${EXPECTED_FINAL_ORDER_STATUS:?EXPECTED_FINAL_ORDER_STATUS is required}"
: "${MAX_WAIT_SECONDS:?MAX_WAIT_SECONDS is required}"
: "${POLL_INTERVAL_SECONDS:?POLL_INTERVAL_SECONDS is required}"

UUID_PATTERN='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
ZERO_UUID='00000000-0000-0000-0000-000000000000'

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_true() {
  local label="$1"
  local value="${2:-}"
  [[ "${value,,}" == "true" ]] || fail "$label must be true. Actual: ${value:-<empty>}"
}

require_false_or_empty() {
  local label="$1"
  local value="${2:-}"
  [[ -z "$value" || "${value,,}" == "false" ]] || fail "$label must remain false. Actual: $value"
}

require_count() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  [[ "$actual" == "$expected" ]] || fail "$label count mismatch. Expected: $expected Actual: $actual"
}

read_env() {
  local app="$1"
  local name="$2"
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$app" \
    --query "properties.template.containers[0].env[?name=='$name'].value | [0]" \
    --output tsv
}

env_secret_ref() {
  local app="$1"
  local name="$2"
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$app" \
    --query "properties.template.containers[0].env[?name=='$name'].secretRef | [0]" \
    --output tsv
}

resolve_env() {
  local app="$1"
  local name="$2"
  local value secret_ref
  value=$(read_env "$app" "$name")
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
    return
  fi
  secret_ref=$(env_secret_ref "$app" "$name")
  if [[ -n "$secret_ref" ]]; then
    az containerapp secret list \
      --resource-group "$RESOURCE_GROUP" \
      --name "$app" \
      --show-values \
      --query "[?name=='$secret_ref'].value | [0]" \
      --only-show-errors \
      --output tsv
  fi
}

ensure_psql() {
  if command -v psql >/dev/null 2>&1; then
    return
  fi
  sudo apt-get update -qq
  sudo apt-get install -y -qq postgresql-client
}

query_app_database() {
  local app="$1"
  local sql="$2"
  local jdbc_url username password jdbc host_port db_query db_name db_host db_port

  jdbc_url=$(resolve_env "$app" SPRING_DATASOURCE_URL)
  username=$(resolve_env "$app" SPRING_DATASOURCE_USERNAME)
  password=$(resolve_env "$app" SPRING_DATASOURCE_PASSWORD)
  [[ -n "$jdbc_url" && -n "$username" && -n "$password" ]] || fail "Database connection settings are incomplete for $app."

  jdbc="${jdbc_url#jdbc:postgresql://}"
  host_port="${jdbc%%/*}"
  db_query="${jdbc#*/}"
  db_name="${db_query%%\?*}"
  db_host="${host_port%%:*}"
  if [[ "$host_port" == *:* ]]; then
    db_port="${host_port##*:}"
  else
    db_port=5432
  fi

  ensure_psql
  PGPASSWORD="$password" psql \
    "host=$db_host port=$db_port dbname=$db_name user=$username sslmode=require" \
    --set=ON_ERROR_STOP=1 \
    --tuples-only \
    --no-align \
    --quiet \
    --field-separator='|' \
    --command="$sql" | sed '/^[[:space:]]*$/d'
}

subscription_dlq() {
  local subscription="$1"
  az servicebus topic subscription show \
    --resource-group "$RESOURCE_GROUP" \
    --namespace-name "$SERVICE_BUS_NAMESPACE" \
    --topic-name "$SERVICE_BUS_TOPIC" \
    --name "$subscription" \
    --query countDetails.deadLetterMessageCount \
    --output tsv
}

normalized_event_types() {
  printf '%s' "$1" | tr ',' '\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed '/^$/d' | sort | paste -sd, -
}

verify_stage5_runtime() {
  local actual_types expected_types payment_environment simulation_status
  actual_types=$(normalized_event_types "$(read_env "$ORDER_APP" CRAVES_DOMAIN_EVENT_ENABLED_TYPES)")
  expected_types=$(normalized_event_types 'CHEF_ACCEPTED_ORDER,REFUND_REQUESTED')
  [[ "$actual_types" == "$expected_types" ]] || fail "Order event allow-list is not Stage 5 compatible. Expected: $expected_types Actual: ${actual_types:-<empty>}"

  require_true "Chef timeout worker" "$(read_env "$ORDER_APP" CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED)"
  require_true "Order refund-status consumer" "$(read_env "$ORDER_APP" CRAVES_REFUND_STATUS_CONSUMER_ENABLED)"
  require_true "Order domain-event outbox" "$(read_env "$ORDER_APP" CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED)"
  require_true "Order Service Bus publisher" "$(read_env "$ORDER_APP" CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED)"
  require_true "Notification dispatcher" "$(read_env "$ORDER_APP" CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)"

  require_true "Integration refund consumer" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_CONSUMER_ENABLED)"
  require_true "Integration refund-status publisher" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_STATUS_PUBLISHER_ENABLED)"
  require_true "Cashfree refund execution" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED)"
  require_true "Cashfree reconciliation" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_RECONCILIATION_ENABLED)"
  require_false_or_empty "Delivery command execution" "$(read_env "$INTEGRATION_APP" CRAVES_DELIVERY_COMMAND_ENABLED)"
  require_false_or_empty "Borzo API execution" "$(read_env "$INTEGRATION_APP" BORZO_API_ENABLED)"

  payment_environment=$(read_env "$INTEGRATION_APP" PAYMENT_PROVIDER_ENVIRONMENT)
  [[ "${payment_environment,,}" == "sandbox" ]] || fail "Cashfree execution is not confined to sandbox. Actual environment: ${payment_environment:-<empty>}"
  [[ -n "$(env_secret_ref "$INTEGRATION_APP" PAYMENT_PROVIDER_CLIENT_ID)" ]] || fail "Cashfree client ID is not configured through a Container App secret reference."
  [[ -n "$(env_secret_ref "$INTEGRATION_APP" PAYMENT_PROVIDER_CLIENT_KEY)" ]] || fail "Cashfree client key is not configured through a Container App secret reference."

  simulation_status=$(read_env "$INTEGRATION_APP" CRAVES_CASHFREE_SANDBOX_REFUND_SIMULATION_STATUS)
  case "$simulation_status" in
    PENDING) [[ "$EXPECTED_FINAL_ORDER_STATUS" == "REFUND_PENDING" ]] || fail "Current sandbox simulation PENDING requires expectedFinalOrderStatus=REFUND_PENDING." ;;
    SUCCESS) [[ "$EXPECTED_FINAL_ORDER_STATUS" == "REFUNDED" ]] || fail "Current sandbox simulation SUCCESS requires expectedFinalOrderStatus=REFUNDED." ;;
    FAILED) [[ "$EXPECTED_FINAL_ORDER_STATUS" == "REFUND_FAILED" ]] || fail "Current sandbox simulation FAILED requires expectedFinalOrderStatus=REFUND_FAILED." ;;
    *) fail "Unsupported Cashfree sandbox simulation status: ${simulation_status:-<empty>}" ;;
  esac

  require_count "REFUND_REQUESTED DLQ" "$(subscription_dlq "$INTEGRATION_REFUND_SUBSCRIPTION")" 0
  require_count "REFUND_STATUS_CHANGED DLQ" "$(subscription_dlq "$ORDER_REFUND_STATUS_SUBSCRIPTION")" 0
}

expected_provider_status() {
  case "$EXPECTED_FINAL_ORDER_STATUS" in
    REFUND_PENDING) printf 'PENDING' ;;
    REFUNDED) printf 'SUCCESS' ;;
    REFUND_FAILED) printf 'FAILED' ;;
    *) fail "Unsupported EXPECTED_FINAL_ORDER_STATUS: $EXPECTED_FINAL_ORDER_STATUS" ;;
  esac
}

poll_until_complete() {
  local started now elapsed expected_provider
  local order_state request_outbox refund_row status_outbox inbox_row notification_row
  local order_status refund_id refund_reference provider_status status_event_id
  local request_count request_status refund_count integration_refund_id integration_refund_status integration_provider_status
  local integration_refund_reference integration_idempotency integration_cf_refund_id
  local status_count status_outbox_status status_event
  local inbox_count inbox_status inbox_normalized inbox_provider
  local notification_count notification_status notification_attempts unsafe_payload_count

  expected_provider=$(expected_provider_status)
  started=$(date +%s)

  while true; do
    order_state=$(query_app_database "$ORDER_APP" "
      SELECT status,
             COALESCE(refund_id::text, ''),
             COALESCE(refund_reference, ''),
             COALESCE(refund_provider_status, ''),
             COALESCE(refund_status_event_id::text, '')
      FROM order_schema.customer_order
      WHERE id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid;
    ")
    IFS='|' read -r order_status refund_id refund_reference provider_status status_event_id <<< "$order_state"

    request_outbox=$(query_app_database "$ORDER_APP" "
      SELECT COUNT(*), COALESCE(MAX(status), '')
      FROM order_schema.domain_event_outbox
      WHERE aggregate_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid
        AND event_type = 'REFUND_REQUESTED';
    ")
    IFS='|' read -r request_count request_status <<< "$request_outbox"

    refund_row=$(query_app_database "$INTEGRATION_APP" "
      SELECT COUNT(*),
             COALESCE(MAX(id::text), ''),
             COALESCE(MAX(status), ''),
             COALESCE(MAX(provider_status), ''),
             COALESCE(MAX(refund_ref), ''),
             COALESCE(MAX(idempotency_key::text), ''),
             COALESCE(MAX(cf_refund_id), '')
      FROM payment_schema.refund
      WHERE chef_sub_order_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid;
    ")
    IFS='|' read -r refund_count integration_refund_id integration_refund_status integration_provider_status integration_refund_reference integration_idempotency integration_cf_refund_id <<< "$refund_row"

    status_outbox='0||'
    if [[ -n "$integration_refund_id" ]]; then
      status_outbox=$(query_app_database "$INTEGRATION_APP" "
        SELECT COUNT(*), COALESCE(MAX(status), ''), COALESCE(MAX(id::text), '')
        FROM payment_schema.refund_status_outbox
        WHERE aggregate_id = '$integration_refund_id'::uuid
          AND payload -> 'data' ->> 'status' = '$EXPECTED_FINAL_ORDER_STATUS';
      ")
    fi
    IFS='|' read -r status_count status_outbox_status status_event <<< "$status_outbox"

    inbox_row=$(query_app_database "$ORDER_APP" "
      SELECT COUNT(*),
             COALESCE(MAX(processing_status), ''),
             COALESCE(MAX(normalized_status), ''),
             COALESCE(MAX(provider_status), '')
      FROM order_schema.refund_status_inbox
      WHERE subject = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid
        AND normalized_status = '$EXPECTED_FINAL_ORDER_STATUS';
    ")
    IFS='|' read -r inbox_count inbox_status inbox_normalized inbox_provider <<< "$inbox_row"

    notification_row=$(query_app_database "$ORDER_APP" "
      SELECT COUNT(*), COALESCE(MAX(status), ''), COALESCE(MAX(attempt_count), 0)
      FROM order_schema.notification_outbox
      WHERE aggregate_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid
        AND event_type = '$EXPECTED_FINAL_ORDER_STATUS';
    ")
    IFS='|' read -r notification_count notification_status notification_attempts <<< "$notification_row"

    if [[ "$order_status" == "$EXPECTED_FINAL_ORDER_STATUS" \
          && "$request_count" == "1" && "$request_status" == "PUBLISHED" \
          && "$refund_count" == "1" \
          && "$integration_provider_status" == "$expected_provider" \
          && "$status_count" == "1" && "$status_outbox_status" == "PUBLISHED" \
          && "$inbox_count" == "1" && "$inbox_status" == "PROCESSED" \
          && "$inbox_normalized" == "$EXPECTED_FINAL_ORDER_STATUS" \
          && "$inbox_provider" == "$expected_provider" \
          && "$notification_count" == "1" && "$notification_status" == "SENT" ]]; then
      break
    fi

    if [[ "$request_status" == "DEAD" \
          || "$integration_refund_status" == "DEAD_LETTER" \
          || "$status_outbox_status" == "DEAD_LETTER" ]]; then
      fail "Synthetic refund entered a terminal local failure state. Order outbox=$request_status refund=$integration_refund_status statusOutbox=$status_outbox_status"
    fi

    now=$(date +%s)
    elapsed=$((now - started))
    if (( elapsed >= MAX_WAIT_SECONDS )); then
      echo "Last observed state: order=$order_status requestOutbox=$request_status refund=$integration_refund_status/$integration_provider_status statusOutbox=$status_outbox_status inbox=$inbox_status/$inbox_normalized notification=$notification_status"
      fail "Timed out after ${MAX_WAIT_SECONDS}s waiting for the Stage 5 synthetic refund flow."
    fi

    echo "Waiting: order=$order_status requestOutbox=${request_status:-none} refund=${integration_refund_status:-none}/${integration_provider_status:-none} statusOutbox=${status_outbox_status:-none} inbox=${inbox_status:-none} notification=${notification_status:-none}"
    sleep "$POLL_INTERVAL_SECONDS"
  done

  [[ "$refund_reference" == "CRV${SYNTHETIC_CHEF_SUB_ORDER_ID//-/}" ]] || fail "Order refund reference is not deterministic."
  [[ "$integration_refund_reference" == "$refund_reference" ]] || fail "Order and Integration refund references do not match."
  [[ -n "$integration_idempotency" ]] || fail "Integration idempotency key is missing."
  [[ -n "$refund_id" && "$refund_id" == "$integration_refund_id" ]] || fail "Order and Integration refund IDs do not match."
  [[ -n "$status_event_id" && "$status_event_id" == "$status_event" ]] || fail "Order status event ID does not match the published Integration status event."
  [[ "$provider_status" == "$expected_provider" ]] || fail "Order provider status does not match the expected sandbox result."
  if [[ "$EXPECTED_FINAL_ORDER_STATUS" != "REFUND_FAILED" && -z "$integration_cf_refund_id" ]]; then
    fail "Cashfree refund ID is missing for the successful sandbox provider response."
  fi

  unsafe_payload_count=$(query_app_database "$ORDER_APP" "
    SELECT COUNT(*)
    FROM order_schema.notification_outbox
    WHERE aggregate_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid
      AND event_type = '$EXPECTED_FINAL_ORDER_STATUS'
      AND payload::text ~* '(client[_-]?secret|client[_-]?key|x-client|password|pickup[_ -]?(address|contact)|accesskey)';
  ")
  require_count "Unsafe notification payload" "$unsafe_payload_count" 0

  require_count "REFUND_REQUESTED DLQ after validation" "$(subscription_dlq "$INTEGRATION_REFUND_SUBSCRIPTION")" 0
  require_count "REFUND_STATUS_CHANGED DLQ after validation" "$(subscription_dlq "$ORDER_REFUND_STATUS_SUBSCRIPTION")" 0

  echo "============================================================"
  echo "CRAVES REFUND E2E SANDBOX VALIDATION PASSED"
  echo "Chef sub-order: $SYNTHETIC_CHEF_SUB_ORDER_ID"
  echo "Order status: $order_status"
  echo "Provider status: $provider_status"
  echo "Order REFUND_REQUESTED outbox: exactly 1 / PUBLISHED"
  echo "Integration refund: exactly 1 / idempotency key present"
  echo "Integration REFUND_STATUS_CHANGED outbox: exactly 1 / PUBLISHED"
  echo "Order refund-status inbox: exactly 1 / PROCESSED"
  echo "Customer notification outbox: exactly 1 / SENT / attempts=$notification_attempts"
  echo "Refund Service Bus DLQs: 0"
  echo "Production Cashfree was not enabled or contacted by this validator."
  echo "Durable financial and audit rows were intentionally retained."
  echo "============================================================"
}

[[ "$SYNTHETIC_CHEF_SUB_ORDER_ID" =~ $UUID_PATTERN ]] || fail "syntheticChefSubOrderId must be a UUID."
[[ "$SYNTHETIC_CHEF_SUB_ORDER_ID" != "$ZERO_UUID" ]] || fail "Replace the placeholder syntheticChefSubOrderId with the real sandbox chef sub-order UUID."
[[ "${CONFIRM_SYNTHETIC_SANDBOX_REFUND,,}" == "true" ]] || fail "Set confirmSyntheticSandboxRefund=true only for an explicitly approved Cashfree sandbox test order."
[[ "${FORCE_ACCEPTANCE_EXPIRY,,}" == "true" || "${FORCE_ACCEPTANCE_EXPIRY,,}" == "false" ]] || fail "forceAcceptanceExpiry must be true or false."
[[ "$MAX_WAIT_SECONDS" =~ ^[0-9]+$ && "$MAX_WAIT_SECONDS" -ge 60 && "$MAX_WAIT_SECONDS" -le 3600 ]] || fail "maxWaitSeconds must be between 60 and 3600."
[[ "$POLL_INTERVAL_SECONDS" =~ ^[0-9]+$ && "$POLL_INTERVAL_SECONDS" -ge 5 && "$POLL_INTERVAL_SECONDS" -le 60 ]] || fail "pollIntervalSeconds must be between 5 and 60."
case "$EXPECTED_FINAL_ORDER_STATUS" in
  REFUND_PENDING|REFUNDED|REFUND_FAILED) ;;
  *) fail "expectedFinalOrderStatus must be REFUND_PENDING, REFUNDED or REFUND_FAILED." ;;
esac

verify_stage5_runtime

ORDER_ROW=$(query_app_database "$ORDER_APP" "
  SELECT checkout_id::text,
         customer_identity_id::text,
         status,
         currency,
         grand_total::text,
         COALESCE(chef_acceptance_expires_at::text, ''),
         COALESCE(refund_requested_at::text, ''),
         COALESCE(refund_id::text, '')
  FROM order_schema.customer_order
  WHERE id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid;
")
[[ -n "$ORDER_ROW" ]] || fail "The synthetic chef sub-order was not found in Order Service."
IFS='|' read -r CHECKOUT_ID CUSTOMER_ID ORDER_STATUS ORDER_CURRENCY ORDER_AMOUNT ACCEPTANCE_EXPIRES_AT REFUND_REQUESTED_AT EXISTING_REFUND_ID <<< "$ORDER_ROW"
[[ "$CHECKOUT_ID" =~ $UUID_PATTERN && "$CUSTOMER_ID" =~ $UUID_PATTERN ]] || fail "Synthetic order has invalid checkout/customer identifiers."
[[ "$ORDER_STATUS" == "CHEF_ACCEPTANCE_PENDING" ]] || fail "Synthetic order must be CHEF_ACCEPTANCE_PENDING. Actual: $ORDER_STATUS"
[[ "$ORDER_CURRENCY" == "INR" ]] || fail "Synthetic order currency must be INR. Actual: $ORDER_CURRENCY"
awk -v amount="$ORDER_AMOUNT" 'BEGIN { exit !(amount > 0) }' || fail "Synthetic order amount must be greater than zero."
[[ -z "$REFUND_REQUESTED_AT" && -z "$EXISTING_REFUND_ID" ]] || fail "Synthetic order already contains refund metadata. Use a new sandbox test order."

PAYMENT_ROW=$(query_app_database "$INTEGRATION_APP" "
  SELECT id::text, status, currency, amount::text, COALESCE(cashfree_order_id, '')
  FROM payment_schema.payment_order
  WHERE checkout_id = '$CHECKOUT_ID'::uuid
  ORDER BY created_at DESC
  LIMIT 1;
")
[[ -n "$PAYMENT_ROW" ]] || fail "No Integration payment order exists for the synthetic checkout. Complete a real Cashfree sandbox payment first."
IFS='|' read -r PAYMENT_ORDER_ID PAYMENT_STATUS PAYMENT_CURRENCY PAYMENT_AMOUNT CASHFREE_ORDER_ID <<< "$PAYMENT_ROW"
[[ "$PAYMENT_STATUS" == "PAID" ]] || fail "The latest Integration payment order must be PAID. Actual: $PAYMENT_STATUS"
[[ "$PAYMENT_CURRENCY" == "$ORDER_CURRENCY" ]] || fail "Order and payment currencies do not match."
[[ -n "$CASHFREE_ORDER_ID" ]] || fail "Cashfree order ID is missing. Complete the sandbox payment normally; do not fabricate payment data."
awk -v paid="$PAYMENT_AMOUNT" -v refund="$ORDER_AMOUNT" 'BEGIN { exit !(paid >= refund) }' || fail "Chef sub-order refund amount exceeds the captured checkout payment."

RESERVED_AMOUNT=$(query_app_database "$INTEGRATION_APP" "SELECT COALESCE(SUM(amount), 0)::text FROM payment_schema.refund WHERE payment_order_id = '$PAYMENT_ORDER_ID'::uuid;")
awk -v paid="$PAYMENT_AMOUNT" -v reserved="$RESERVED_AMOUNT" -v refund="$ORDER_AMOUNT" 'BEGIN { exit !(paid >= reserved + refund) }' || fail "The captured payment does not have enough unreserved value for this chef sub-order refund."

require_count "Existing Integration refund" "$(query_app_database "$INTEGRATION_APP" "SELECT COUNT(*) FROM payment_schema.refund WHERE chef_sub_order_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid;")" 0
require_count "Existing Integration refund-request inbox" "$(query_app_database "$INTEGRATION_APP" "SELECT COUNT(*) FROM payment_schema.refund_request_inbox WHERE subject = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid;")" 0
require_count "Existing Order REFUND_REQUESTED outbox" "$(query_app_database "$ORDER_APP" "SELECT COUNT(*) FROM order_schema.domain_event_outbox WHERE aggregate_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid AND event_type = 'REFUND_REQUESTED';")" 0
require_count "Existing refund-status inbox" "$(query_app_database "$ORDER_APP" "SELECT COUNT(*) FROM order_schema.refund_status_inbox WHERE subject = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid;")" 0
require_count "Existing refund-status notification" "$(query_app_database "$ORDER_APP" "SELECT COUNT(*) FROM order_schema.notification_outbox WHERE aggregate_id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid AND event_type IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED');")" 0

if [[ "${FORCE_ACCEPTANCE_EXPIRY,,}" == "true" ]]; then
  UPDATED=$(query_app_database "$ORDER_APP" "
    UPDATE order_schema.customer_order
    SET chef_acceptance_expires_at = now() - INTERVAL '1 second',
        updated_at = now()
    WHERE id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid
      AND status = 'CHEF_ACCEPTANCE_PENDING'
      AND refund_requested_at IS NULL
      AND refund_id IS NULL
    RETURNING id::text;
  ")
  [[ "$UPDATED" == "$SYNTHETIC_CHEF_SUB_ORDER_ID" ]] || fail "The approved synthetic order could not be expired safely."
  echo "Synthetic chef acceptance deadline accelerated for the named paid sandbox order only."
else
  ALREADY_EXPIRED=$(query_app_database "$ORDER_APP" "
    SELECT CASE WHEN chef_acceptance_expires_at IS NOT NULL AND chef_acceptance_expires_at <= now() THEN 1 ELSE 0 END
    FROM order_schema.customer_order
    WHERE id = '$SYNTHETIC_CHEF_SUB_ORDER_ID'::uuid;
  ")
  [[ "$ALREADY_EXPIRED" == "1" ]] || fail "The synthetic chef acceptance deadline has not expired. Run with forceAcceptanceExpiry=true or wait for the normal deadline."
fi

poll_until_complete
