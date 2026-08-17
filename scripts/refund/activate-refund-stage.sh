#!/usr/bin/env bash
set -euo pipefail
set +x

required_vars=(
  TARGET_STAGE
  RESOURCE_GROUP
  ORDER_APP
  INTEGRATION_APP
  NOTIFICATION_APP
  SERVICE_BUS_NAMESPACE
  SERVICE_BUS_TOPIC
  INTEGRATION_REFUND_SUBSCRIPTION
  INTEGRATION_REFUND_RULE
)

for name in "${required_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: Required variable $name is missing."
    exit 1
  fi
done

CONFIRM_FINANCIAL_SANDBOX_EXECUTION="${CONFIRM_FINANCIAL_SANDBOX_EXECUTION:-false}"
EXPECTED_REFUND_REQUEST_OUTBOX_COUNT="${EXPECTED_REFUND_REQUEST_OUTBOX_COUNT:-0}"
EXPECTED_EXECUTABLE_REFUND_COUNT="${EXPECTED_EXECUTABLE_REFUND_COUNT:-0}"
EXPECTED_NOTIFICATION_BACKLOG_COUNT="${EXPECTED_NOTIFICATION_BACKLOG_COUNT:-0}"
CASHFREE_API_VERSION="${CASHFREE_API_VERSION:-2025-01-01}"
CASHFREE_SANDBOX_SIMULATION_STATUS="${CASHFREE_SANDBOX_SIMULATION_STATUS:-PENDING}"

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

require_true() {
  local label="$1"
  local value="$2"
  if [[ "${value,,}" != "true" ]]; then
    echo "ERROR: $label must be true. Current value: ${value:-<empty>}"
    exit 1
  fi
}

require_false_or_empty() {
  local label="$1"
  local value="$2"
  if [[ -n "$value" && "${value,,}" != "false" ]]; then
    echo "ERROR: $label must be false or absent. Current value: $value"
    exit 1
  fi
}

normalized_event_types() {
  local value="$1"
  printf '%s' "$value" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | sed '/^$/d' \
    | sort -u \
    | paste -sd, -
}

require_event_types() {
  local expected="$1"
  local actual
  actual=$(normalized_event_types "$(read_env "$ORDER_APP" CRAVES_DOMAIN_EVENT_ENABLED_TYPES)")
  if [[ "$actual" != "$expected" ]]; then
    echo "ERROR: Order domain-event allow-list is not at the required stage."
    echo "Expected: $expected"
    echo "Actual:   ${actual:-<empty>}"
    exit 1
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

  if [[ -z "$jdbc_url" || -z "$username" || -z "$password" ]]; then
    echo "ERROR: Database settings could not be resolved for $app." >&2
    return 1
  fi

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
    --command="$sql"
}

require_count() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "ERROR: $label does not match the explicitly approved count."
    echo "Expected: $expected"
    echo "Actual:   $actual"
    exit 1
  fi
}

wait_healthy_revision() {
  local app="$1"
  local previous_revision="$2"

  for attempt in $(seq 1 60); do
    local latest ready running health
    latest=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$app" --query properties.latestRevisionName --output tsv)
    ready=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$app" --query properties.latestReadyRevisionName --output tsv)
    running=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$app" --query properties.runningStatus --output tsv)
    health=$(az containerapp revision show --resource-group "$RESOURCE_GROUP" --name "$app" --revision "$latest" --query properties.healthState --output tsv 2>/dev/null || true)

    echo "$app attempt $attempt/60: latest=$latest ready=$ready health=$health running=$running"

    if [[ "$latest" != "$previous_revision" \
          && "$latest" == "$ready" \
          && "$health" == "Healthy" \
          && "$running" == "Running" ]]; then
      return 0
    fi

    if [[ "$health" == "Unhealthy" || "$running" == "Failed" ]]; then
      az containerapp logs show \
        --resource-group "$RESOURCE_GROUP" \
        --name "$app" \
        --revision "$latest" \
        --type console \
        --tail 100 \
        --format text || true
      return 1
    fi

    sleep 10
  done

  echo "ERROR: $app did not become healthy after the stage change."
  return 1
}

verify_common_entities() {
  az containerapp show --resource-group "$RESOURCE_GROUP" --name "$ORDER_APP" --output none
  az containerapp show --resource-group "$RESOURCE_GROUP" --name "$INTEGRATION_APP" --output none
  az containerapp show --resource-group "$RESOURCE_GROUP" --name "$NOTIFICATION_APP" --output none
  az servicebus topic show \
    --resource-group "$RESOURCE_GROUP" \
    --namespace-name "$SERVICE_BUS_NAMESPACE" \
    --name "$SERVICE_BUS_TOPIC" \
    --output none

  require_true "Order refund-status consumer" "$(read_env "$ORDER_APP" CRAVES_REFUND_STATUS_CONSUMER_ENABLED)"
  require_true "Order domain-event outbox" "$(read_env "$ORDER_APP" CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED)"
  require_true "Order Service Bus publisher" "$(read_env "$ORDER_APP" CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED)"
  require_true "Integration refund consumer" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_CONSUMER_ENABLED)"
  require_true "Integration refund-status publisher" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_STATUS_PUBLISHER_ENABLED)"
  require_false_or_empty "Delivery command execution" "$(read_env "$INTEGRATION_APP" CRAVES_DELIVERY_COMMAND_ENABLED)"
  require_false_or_empty "Borzo execution" "$(read_env "$INTEGRATION_APP" BORZO_API_ENABLED)"
}

verify_refund_requested_subscription() {
  az servicebus topic subscription show \
    --resource-group "$RESOURCE_GROUP" \
    --namespace-name "$SERVICE_BUS_NAMESPACE" \
    --topic-name "$SERVICE_BUS_TOPIC" \
    --name "$INTEGRATION_REFUND_SUBSCRIPTION" \
    --output none

  local filter_type expression dlq_count
  filter_type=$(az servicebus topic subscription rule show \
    --resource-group "$RESOURCE_GROUP" \
    --namespace-name "$SERVICE_BUS_NAMESPACE" \
    --topic-name "$SERVICE_BUS_TOPIC" \
    --subscription-name "$INTEGRATION_REFUND_SUBSCRIPTION" \
    --name "$INTEGRATION_REFUND_RULE" \
    --query filterType \
    --output tsv)
  expression=$(az servicebus topic subscription rule show \
    --resource-group "$RESOURCE_GROUP" \
    --namespace-name "$SERVICE_BUS_NAMESPACE" \
    --topic-name "$SERVICE_BUS_TOPIC" \
    --subscription-name "$INTEGRATION_REFUND_SUBSCRIPTION" \
    --name "$INTEGRATION_REFUND_RULE" \
    --query sqlFilter.sqlExpression \
    --output tsv)

  if [[ "$filter_type" != "SqlFilter" \
        || "$expression" != *"eventType"* \
        || "$expression" != *"REFUND_REQUESTED"* ]]; then
    echo "ERROR: Integration refund-request subscription does not have the approved filter."
    echo "Filter type: $filter_type"
    echo "Expression:  $expression"
    exit 1
  fi

  dlq_count=$(az servicebus topic subscription show \
    --resource-group "$RESOURCE_GROUP" \
    --namespace-name "$SERVICE_BUS_NAMESPACE" \
    --topic-name "$SERVICE_BUS_TOPIC" \
    --name "$INTEGRATION_REFUND_SUBSCRIPTION" \
    --query countDetails.deadLetterMessageCount \
    --output tsv)

  if [[ "${dlq_count:-0}" != "0" ]]; then
    echo "ERROR: Integration refund-request DLQ is not empty. Count: $dlq_count"
    exit 1
  fi
}

stage1_request_publication() {
  verify_common_entities
  verify_refund_requested_subscription
  require_event_types "CHEF_ACCEPTED_ORDER"
  require_false_or_empty "Chef timeout worker" "$(read_env "$ORDER_APP" CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED)"
  require_false_or_empty "Notification dispatcher" "$(read_env "$ORDER_APP" CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)"
  require_false_or_empty "Cashfree execution" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED)"
  require_false_or_empty "Cashfree reconciliation" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_RECONCILIATION_ENABLED)"

  local backlog previous
  backlog=$(query_app_database "$ORDER_APP" "
    SELECT COUNT(*)
    FROM order_schema.domain_event_outbox
    WHERE event_type = 'REFUND_REQUESTED'
      AND status IN ('PENDING', 'PROCESSING', 'FAILED', 'DEAD');
  ")
  require_count "REFUND_REQUESTED Order outbox backlog" "$backlog" "$EXPECTED_REFUND_REQUEST_OUTBOX_COUNT"

  previous=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$ORDER_APP" --query properties.latestRevisionName --output tsv)
  az containerapp update \
    --resource-group "$RESOURCE_GROUP" \
    --name "$ORDER_APP" \
    --set-env-vars \
      CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER,REFUND_REQUESTED \
      CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=false \
    --output none
  wait_healthy_revision "$ORDER_APP" "$previous"

  echo "SUCCESS: Stage 1 enabled REFUND_REQUESTED publication only."
}

stage2_timeout_worker() {
  verify_common_entities
  verify_refund_requested_subscription
  require_event_types "CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
  require_false_or_empty "Chef timeout worker" "$(read_env "$ORDER_APP" CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED)"
  require_false_or_empty "Notification dispatcher" "$(read_env "$ORDER_APP" CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)"
  require_false_or_empty "Cashfree execution" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED)"
  require_false_or_empty "Cashfree reconciliation" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_RECONCILIATION_ENABLED)"

  local previous
  previous=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$ORDER_APP" --query properties.latestRevisionName --output tsv)
  az containerapp update \
    --resource-group "$RESOURCE_GROUP" \
    --name "$ORDER_APP" \
    --set-env-vars \
      CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER,REFUND_REQUESTED \
      CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=true \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=false \
    --output none
  wait_healthy_revision "$ORDER_APP" "$previous"

  echo "SUCCESS: Stage 2 enabled the chef timeout worker. Cashfree remains disabled."
}

stage3_cashfree_sandbox_execution() {
  verify_common_entities
  verify_refund_requested_subscription
  require_event_types "CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
  require_true "Chef timeout worker" "$(read_env "$ORDER_APP" CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED)"
  require_false_or_empty "Notification dispatcher" "$(read_env "$ORDER_APP" CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)"
  require_false_or_empty "Cashfree execution" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED)"
  require_false_or_empty "Cashfree reconciliation" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_RECONCILIATION_ENABLED)"

  if [[ "${CONFIRM_FINANCIAL_SANDBOX_EXECUTION,,}" != "true" ]]; then
    echo "ERROR: Stage 3 requires confirmFinancialSandboxExecution=true."
    exit 1
  fi
  if [[ -z "${CASHFREE_SANDBOX_CLIENT_ID:-}" || -z "${CASHFREE_SANDBOX_CLIENT_SECRET:-}" ]]; then
    echo "ERROR: Cashfree sandbox Azure DevOps secret variables are missing."
    echo "Required names: CASHFREE_SANDBOX_CLIENT_ID and CASHFREE_SANDBOX_CLIENT_SECRET"
    exit 1
  fi
  if [[ "$CASHFREE_SANDBOX_SIMULATION_STATUS" != "PENDING" \
        && "$CASHFREE_SANDBOX_SIMULATION_STATUS" != "SUCCESS" \
        && "$CASHFREE_SANDBOX_SIMULATION_STATUS" != "FAILED" ]]; then
    echo "ERROR: Unsupported sandbox simulation status: $CASHFREE_SANDBOX_SIMULATION_STATUS"
    exit 1
  fi

  local executable previous
  executable=$(query_app_database "$INTEGRATION_APP" "
    SELECT COUNT(*)
    FROM payment_schema.refund
    WHERE status IN ('REQUESTED', 'RETRY')
      AND checkout_id IS NOT NULL
      AND chef_sub_order_id IS NOT NULL
      AND customer_identity_id IS NOT NULL
      AND request_event_id IS NOT NULL
      AND idempotency_key IS NOT NULL
      AND cashfree_order_id IS NOT NULL
      AND cf_refund_id IS NULL;
  ")
  require_count "Cashfree-executable refund backlog" "$executable" "$EXPECTED_EXECUTABLE_REFUND_COUNT"

  az containerapp secret set \
    --resource-group "$RESOURCE_GROUP" \
    --name "$INTEGRATION_APP" \
    --secrets \
      "cashfree-sandbox-client-id=$CASHFREE_SANDBOX_CLIENT_ID" \
      "cashfree-sandbox-client-secret=$CASHFREE_SANDBOX_CLIENT_SECRET" \
    --output none

  previous=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$INTEGRATION_APP" --query properties.latestRevisionName --output tsv)
  az containerapp update \
    --resource-group "$RESOURCE_GROUP" \
    --name "$INTEGRATION_APP" \
    --set-env-vars \
      PAYMENT_PROVIDER_ENVIRONMENT=sandbox \
      PAYMENT_PROVIDER_API_VERSION="$CASHFREE_API_VERSION" \
      PAYMENT_PROVIDER_CLIENT_ID=secretref:cashfree-sandbox-client-id \
      PAYMENT_PROVIDER_CLIENT_KEY=secretref:cashfree-sandbox-client-secret \
      CRAVES_CASHFREE_SANDBOX_REFUND_SIMULATION_STATUS="$CASHFREE_SANDBOX_SIMULATION_STATUS" \
      CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=true \
      CRAVES_REFUND_RECONCILIATION_ENABLED=false \
      CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true \
    --output none
  wait_healthy_revision "$INTEGRATION_APP" "$previous"

  unset CASHFREE_SANDBOX_CLIENT_ID CASHFREE_SANDBOX_CLIENT_SECRET
  echo "SUCCESS: Stage 3 enabled Cashfree sandbox refund execution. Reconciliation remains disabled."
}

stage4_reconciliation() {
  verify_common_entities
  require_event_types "CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
  require_true "Chef timeout worker" "$(read_env "$ORDER_APP" CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED)"
  require_true "Cashfree sandbox execution" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED)"
  require_false_or_empty "Cashfree reconciliation" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_RECONCILIATION_ENABLED)"
  require_false_or_empty "Notification dispatcher" "$(read_env "$ORDER_APP" CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)"

  local environment
  environment=$(read_env "$INTEGRATION_APP" PAYMENT_PROVIDER_ENVIRONMENT)
  if [[ "${environment,,}" != "sandbox" ]]; then
    echo "ERROR: Reconciliation can be enabled only while PAYMENT_PROVIDER_ENVIRONMENT=sandbox."
    exit 1
  fi
  if [[ -z "$(env_secret_ref "$INTEGRATION_APP" PAYMENT_PROVIDER_CLIENT_ID)" \
        || -z "$(env_secret_ref "$INTEGRATION_APP" PAYMENT_PROVIDER_CLIENT_KEY)" ]]; then
    echo "ERROR: Cashfree credential environment variables are not secret references."
    exit 1
  fi

  local previous
  previous=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$INTEGRATION_APP" --query properties.latestRevisionName --output tsv)
  az containerapp update \
    --resource-group "$RESOURCE_GROUP" \
    --name "$INTEGRATION_APP" \
    --set-env-vars \
      PAYMENT_PROVIDER_ENVIRONMENT=sandbox \
      CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=true \
      CRAVES_REFUND_RECONCILIATION_ENABLED=true \
      CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true \
    --output none
  wait_healthy_revision "$INTEGRATION_APP" "$previous"

  echo "SUCCESS: Stage 4 enabled Cashfree sandbox reconciliation."
}

stage5_customer_notifications() {
  verify_common_entities
  require_event_types "CHEF_ACCEPTED_ORDER,REFUND_REQUESTED"
  require_true "Chef timeout worker" "$(read_env "$ORDER_APP" CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED)"
  require_true "Cashfree sandbox execution" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED)"
  require_true "Cashfree reconciliation" "$(read_env "$INTEGRATION_APP" CRAVES_REFUND_RECONCILIATION_ENABLED)"
  require_false_or_empty "Notification dispatcher" "$(read_env "$ORDER_APP" CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED)"

  local backlog order_key notification_key fqdn previous
  backlog=$(query_app_database "$ORDER_APP" "
    SELECT COUNT(*)
    FROM order_schema.notification_outbox
    WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');
  ")
  require_count "Order notification outbox backlog" "$backlog" "$EXPECTED_NOTIFICATION_BACKLOG_COUNT"

  order_key=$(resolve_env "$ORDER_APP" CRAVES_NOTIFICATION_INTERNAL_KEY)
  notification_key=$(resolve_env "$NOTIFICATION_APP" CRAVES_INTERNAL_SERVICE_KEY)
  if [[ -z "$order_key" || -z "$notification_key" ]]; then
    echo "ERROR: Notification internal service keys are not configured on both services."
    exit 1
  fi
  if [[ "$order_key" != "$notification_key" ]]; then
    echo "ERROR: Order and Notification internal service keys do not match."
    exit 1
  fi

  fqdn=$(az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$NOTIFICATION_APP" \
    --query properties.configuration.ingress.fqdn \
    --output tsv)
  if [[ -z "$fqdn" ]]; then
    echo "ERROR: Notification Container App ingress FQDN is unavailable."
    exit 1
  fi

  previous=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$ORDER_APP" --query properties.latestRevisionName --output tsv)
  az containerapp update \
    --resource-group "$RESOURCE_GROUP" \
    --name "$ORDER_APP" \
    --set-env-vars \
      CRAVES_NOTIFICATION_INTERNAL_BASE_URL="https://$fqdn" \
      CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=true \
    --output none
  wait_healthy_revision "$ORDER_APP" "$previous"

  echo "SUCCESS: Stage 5 enabled customer and chef notification outbox dispatch."
}

case "$TARGET_STAGE" in
  stage1_request_publication)
    stage1_request_publication
    ;;
  stage2_timeout_worker)
    stage2_timeout_worker
    ;;
  stage3_cashfree_sandbox_execution)
    stage3_cashfree_sandbox_execution
    ;;
  stage4_reconciliation)
    stage4_reconciliation
    ;;
  stage5_customer_notifications)
    stage5_customer_notifications
    ;;
  *)
    echo "ERROR: Unsupported TARGET_STAGE: $TARGET_STAGE"
    exit 1
    ;;
esac

echo "============================================================"
echo "Refund activation stage completed: $TARGET_STAGE"
echo "No production Cashfree environment was enabled."
echo "Delivery commands and Borzo remain disabled."
echo "============================================================"
