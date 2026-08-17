#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
SUB_APP="${SUBSCRIPTION_APP:-ca-craves-subscription-service-p}"
INT_APP="${INTEGRATION_APP:-ca-craves-integration-service-pr}"
ORDER_APP="${ORDER_APP:-ca-craves-order-service-prodlow}"
SB_NS="${SERVICE_BUS_NAMESPACE:-sb-craves-prodlow-l3ing6}"
CONFIRM_RBAC_WRITE="${CONFIRM_SERVICE_BUS_RBAC_WRITE:-false}"
VERIFY_ATTEMPTS="${CRAVES_RBAC_VERIFY_ATTEMPTS:-30}"
VERIFY_SLEEP_SECONDS="${CRAVES_RBAC_VERIFY_SLEEP_SECONDS:-10}"

fail() { echo "ERROR: $*" >&2; exit 1; }

for tool in az jq; do
  command -v "$tool" >/dev/null || fail "$tool is required"
done

[[ "${CONFIRM_RBAC_WRITE,,}" == "true" ]] \
  || fail "Set CONFIRM_SERVICE_BUS_RBAC_WRITE=true for this guarded RBAC remediation"
[[ "$VERIFY_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] \
  || fail "CRAVES_RBAC_VERIFY_ATTEMPTS must be a positive integer"
[[ "$VERIFY_SLEEP_SECONDS" =~ ^[0-9]+$ ]] \
  || fail "CRAVES_RBAC_VERIFY_SLEEP_SECONDS must be a non-negative integer"

NS_SCOPE=$(az servicebus namespace show \
  -g "$RG" \
  -n "$SB_NS" \
  --query id \
  -o tsv)
[[ -n "$NS_SCOPE" ]] || fail "Service Bus namespace scope could not be resolved"

echo "Target Service Bus namespace: $SB_NS"
echo "RBAC scope: namespace only (least-privilege rollout scope)"

principal_id() {
  local APP="$1" LABEL="$2" PRINCIPAL
  PRINCIPAL=$(az containerapp show \
    -g "$RG" \
    -n "$APP" \
    --query identity.principalId \
    -o tsv)
  [[ -n "$PRINCIPAL" && "$PRINCIPAL" != "null" ]] \
    || fail "$LABEL does not have a system-assigned managed identity"
  printf '%s' "$PRINCIPAL"
}

has_role() {
  local PRINCIPAL="$1" ROLE="$2" COUNT
  COUNT=$(az role assignment list \
    --assignee-object-id "$PRINCIPAL" \
    --all \
    -o json \
    | jq --arg role "$ROLE" --arg scope "$NS_SCOPE" \
      '[.[] | select(.roleDefinitionName == $role and (.scope == $scope or (.scope | startswith($scope + "/"))))] | length')
  [[ "$COUNT" -gt 0 ]]
}

wait_for_role() {
  local PRINCIPAL="$1" ROLE="$2" LABEL="$3" ATTEMPT
  for ((ATTEMPT=1; ATTEMPT<=VERIFY_ATTEMPTS; ATTEMPT++)); do
    if has_role "$PRINCIPAL" "$ROLE"; then
      echo "PASS: $LABEL has $ROLE"
      return 0
    fi
    echo "WAIT: $LABEL $ROLE propagation attempt=$ATTEMPT/$VERIFY_ATTEMPTS"
    if (( ATTEMPT < VERIFY_ATTEMPTS )); then
      sleep "$VERIFY_SLEEP_SECONDS"
    fi
  done
  fail "$LABEL $ROLE was not visible after RBAC propagation wait"
}

ensure_role() {
  local APP="$1" ROLE="$2" LABEL="$3" PRINCIPAL
  PRINCIPAL=$(principal_id "$APP" "$LABEL")

  if has_role "$PRINCIPAL" "$ROLE"; then
    echo "PASS: $LABEL already has $ROLE; no write required"
    return 0
  fi

  echo "WRITE: assigning $ROLE to $LABEL at Service Bus namespace scope"
  if ! az role assignment create \
    --assignee-object-id "$PRINCIPAL" \
    --assignee-principal-type ServicePrincipal \
    --role "$ROLE" \
    --scope "$NS_SCOPE" \
    --only-show-errors \
    -o none; then
    fail "Unable to assign $ROLE to $LABEL. The Azure DevOps service connection must have permission to create Azure RBAC role assignments at this scope (for example Owner or User Access Administrator)."
  fi

  wait_for_role "$PRINCIPAL" "$ROLE" "$LABEL"
}

# Exact runtime role matrix required by subscription-sandbox-runtime-preflight.sh.
ensure_role "$SUB_APP" "Azure Service Bus Data Sender" "Subscription Service"
ensure_role "$SUB_APP" "Azure Service Bus Data Receiver" "Subscription Service"
ensure_role "$INT_APP" "Azure Service Bus Data Sender" "Integration Service"
ensure_role "$INT_APP" "Azure Service Bus Data Receiver" "Integration Service"
ensure_role "$ORDER_APP" "Azure Service Bus Data Receiver" "Order Service"

echo "============================================================"
echo "SUCCESS: REQUIRED SUBSCRIPTION SANDBOX SERVICE BUS RBAC IS PRESENT"
echo "Only missing Service Bus data-role assignments were created."
echo "No Container App settings, secrets, APIM policies, database state, or runtime feature flags were changed."
echo "============================================================"
