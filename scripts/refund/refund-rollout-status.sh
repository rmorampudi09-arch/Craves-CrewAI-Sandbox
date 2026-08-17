#!/usr/bin/env bash
set -euo pipefail
set +x

: "${RESOURCE_GROUP:?RESOURCE_GROUP is required}"
: "${ORDER_APP:?ORDER_APP is required}"
: "${INTEGRATION_APP:?INTEGRATION_APP is required}"
: "${SERVICE_BUS_NAMESPACE:?SERVICE_BUS_NAMESPACE is required}"
: "${SERVICE_BUS_TOPIC:?SERVICE_BUS_TOPIC is required}"
: "${INTEGRATION_REFUND_SUBSCRIPTION:?INTEGRATION_REFUND_SUBSCRIPTION is required}"
: "${ORDER_REFUND_STATUS_SUBSCRIPTION:?ORDER_REFUND_STATUS_SUBSCRIPTION is required}"

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
    --pset=pager=off \
    --command="$sql"
}

ORDER_EVENT_TYPES=$(read_env "$ORDER_APP" CRAVES_DOMAIN_EVENT_ENABLED_TYPES)
TIMEOUT_WORKER=$(read_env "$ORDER_APP" CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED)
ORDER_STATUS_CONSUMER=$(read_env "$ORDER_APP" CRAVES_REFUND_STATUS_CONSUMER_ENABLED)
NOTIFICATION_DISPATCHER=$(read_env "$ORDER_APP" CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)
INTEGRATION_CONSUMER=$(read_env "$INTEGRATION_APP" CRAVES_REFUND_CONSUMER_ENABLED)
STATUS_PUBLISHER=$(read_env "$INTEGRATION_APP" CRAVES_REFUND_STATUS_PUBLISHER_ENABLED)
PROVIDER_EXECUTION=$(read_env "$INTEGRATION_APP" CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED)
RECONCILIATION=$(read_env "$INTEGRATION_APP" CRAVES_REFUND_RECONCILIATION_ENABLED)
PAYMENT_ENVIRONMENT=$(read_env "$INTEGRATION_APP" PAYMENT_PROVIDER_ENVIRONMENT)
PAYMENT_API_VERSION=$(read_env "$INTEGRATION_APP" PAYMENT_PROVIDER_API_VERSION)
SIMULATION_STATUS=$(read_env "$INTEGRATION_APP" CRAVES_CASHFREE_SANDBOX_REFUND_SIMULATION_STATUS)
DELIVERY_COMMAND=$(read_env "$INTEGRATION_APP" CRAVES_DELIVERY_COMMAND_ENABLED)
BORZO_ENABLED=$(read_env "$INTEGRATION_APP" BORZO_API_ENABLED)

CURRENT_STAGE="safe_publisher_only"
if [[ "$ORDER_EVENT_TYPES" == *"REFUND_REQUESTED"* ]]; then
  CURRENT_STAGE="stage1_request_publication"
fi
if [[ "${TIMEOUT_WORKER,,}" == "true" ]]; then
  CURRENT_STAGE="stage2_timeout_worker"
fi
if [[ "${PROVIDER_EXECUTION,,}" == "true" ]]; then
  CURRENT_STAGE="stage3_cashfree_sandbox_execution"
fi
if [[ "${RECONCILIATION,,}" == "true" ]]; then
  CURRENT_STAGE="stage4_reconciliation"
fi
if [[ "${NOTIFICATION_DISPATCHER,,}" == "true" ]]; then
  CURRENT_STAGE="stage5_customer_notifications"
fi

INTEGRATION_DLQ=$(az servicebus topic subscription show \
  --resource-group "$RESOURCE_GROUP" \
  --namespace-name "$SERVICE_BUS_NAMESPACE" \
  --topic-name "$SERVICE_BUS_TOPIC" \
  --name "$INTEGRATION_REFUND_SUBSCRIPTION" \
  --query countDetails.deadLetterMessageCount \
  --output tsv)

ORDER_DLQ=$(az servicebus topic subscription show \
  --resource-group "$RESOURCE_GROUP" \
  --namespace-name "$SERVICE_BUS_NAMESPACE" \
  --topic-name "$SERVICE_BUS_TOPIC" \
  --name "$ORDER_REFUND_STATUS_SUBSCRIPTION" \
  --query countDetails.deadLetterMessageCount \
  --output tsv)

cat <<REPORT
============================================================
CRAVES REFUND ROLLOUT STATUS
Current stage: $CURRENT_STAGE

Order Service
  Event allow-list: ${ORDER_EVENT_TYPES:-<empty>}
  Chef timeout worker: ${TIMEOUT_WORKER:-false}
  Refund-status consumer: ${ORDER_STATUS_CONSUMER:-false}
  Notification dispatcher: ${NOTIFICATION_DISPATCHER:-false}

Integration Service
  Refund-request consumer: ${INTEGRATION_CONSUMER:-false}
  Refund-status publisher: ${STATUS_PUBLISHER:-false}
  Cashfree execution: ${PROVIDER_EXECUTION:-false}
  Reconciliation: ${RECONCILIATION:-false}
  Payment environment: ${PAYMENT_ENVIRONMENT:-<empty>}
  Payment API version: ${PAYMENT_API_VERSION:-<empty>}
  Sandbox simulation: ${SIMULATION_STATUS:-<empty>}
  Delivery command: ${DELIVERY_COMMAND:-false}
  Borzo API: ${BORZO_ENABLED:-false}

Service Bus
  REFUND_REQUESTED DLQ: ${INTEGRATION_DLQ:-0}
  REFUND_STATUS_CHANGED DLQ: ${ORDER_DLQ:-0}
============================================================
REPORT

echo ""
echo "Order domain-event outbox backlog:"
query_app_database "$ORDER_APP" "
  SELECT event_type, status, COUNT(*) AS row_count,
         MIN(created_at) AS oldest_created_at
  FROM order_schema.domain_event_outbox
  WHERE event_type IN ('REFUND_REQUESTED', 'CHEF_ACCEPTED_ORDER')
  GROUP BY event_type, status
  ORDER BY event_type, status;
"

echo ""
echo "Order notification outbox backlog:"
query_app_database "$ORDER_APP" "
  SELECT event_type, status, COUNT(*) AS row_count,
         MIN(created_at) AS oldest_created_at
  FROM order_schema.notification_outbox
  WHERE event_type IN (
    'REFUND_REQUESTED', 'REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED',
    'CHEF_ORDER_AWAITING_ACCEPTANCE', 'CHEF_ACCEPTANCE_REMINDER',
    'CHEF_ACCEPTANCE_URGENT_REMINDER'
  )
  GROUP BY event_type, status
  ORDER BY event_type, status;
"

echo ""
echo "Integration refund workflow backlog:"
query_app_database "$INTEGRATION_APP" "
  SELECT status, COALESCE(provider_status, '<none>') AS provider_status,
         COUNT(*) AS row_count, MIN(created_at) AS oldest_created_at
  FROM payment_schema.refund
  GROUP BY status, provider_status
  ORDER BY status, provider_status;
"

echo ""
echo "Integration refund-status outbox backlog:"
query_app_database "$INTEGRATION_APP" "
  SELECT status, COUNT(*) AS row_count,
         MIN(created_at) AS oldest_created_at,
         MAX(attempt_count) AS max_attempt_count
  FROM payment_schema.refund_status_outbox
  GROUP BY status
  ORDER BY status;
"

UNSAFE=false
if [[ "${PROVIDER_EXECUTION,,}" == "true" && "${PAYMENT_ENVIRONMENT,,}" != "sandbox" ]]; then
  echo "ERROR: Cashfree execution is enabled outside sandbox."
  UNSAFE=true
fi
if [[ -n "$DELIVERY_COMMAND" && "${DELIVERY_COMMAND,,}" != "false" ]]; then
  echo "ERROR: Delivery command execution changed during refund rollout."
  UNSAFE=true
fi
if [[ -n "$BORZO_ENABLED" && "${BORZO_ENABLED,,}" != "false" ]]; then
  echo "ERROR: Borzo execution changed during refund rollout."
  UNSAFE=true
fi
if [[ "${INTEGRATION_DLQ:-0}" != "0" || "${ORDER_DLQ:-0}" != "0" ]]; then
  echo "ERROR: One or more refund Service Bus DLQs are not empty."
  UNSAFE=true
fi

if [[ "$UNSAFE" == "true" ]]; then
  exit 1
fi

echo "SUCCESS: Refund rollout safety report completed without unsafe runtime findings."
