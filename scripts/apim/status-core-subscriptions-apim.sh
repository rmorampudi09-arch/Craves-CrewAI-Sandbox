#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
SUB_APP="${SUB_APP:-ca-craves-subscription-service-p}"
WEB_APP="${WEB_APP:-ca-craves-web-prodlow}"
APIM_HOST="${APIM_HOST:-api.craves.in}"
API_ID="craves-subscriptions-v1"
API_VERSION="2022-08-01"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl; do command -v "$tool" >/dev/null || fail "$tool is required"; done

SUBSCRIPTION_ID="$(az account show --query id -o tsv --only-show-errors)"
[[ -n "$SUBSCRIPTION_ID" ]] || fail "Azure subscription ID could not be resolved"

SUB_APP_JSON="$(az containerapp show \
  --resource-group "$RG" \
  --name "$SUB_APP" \
  -o json \
  --only-show-errors)"
SUB_FQDN="$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$SUB_APP_JSON")"
SUB_EXTERNAL="$(jq -r '.properties.configuration.ingress.external // false' <<<"$SUB_APP_JSON")"
SUB_RUNNING="$(jq -r '.properties.runningStatus // ""' <<<"$SUB_APP_JSON")"
SUB_READY="$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$SUB_APP_JSON")"
[[ -n "$SUB_FQDN" ]] || fail "Subscription Service FQDN could not be resolved"
echo "Subscription Service: fqdn=$SUB_FQDN external=$SUB_EXTERNAL running=$SUB_RUNNING readyRevision=$SUB_READY"

WEB_APP_JSON="$(az containerapp show \
  --resource-group "$RG" \
  --name "$WEB_APP" \
  -o json \
  --only-show-errors)"
WEB_API_BASE="$(jq -r '
  [(.properties.template.containers[0].env // [])[] | select(.name == "CRAVES_API_BASE_URL") | .value]
  | last // ""
' <<<"$WEB_APP_JSON")"
WEB_API_SECRET_REF="$(jq -r '
  [(.properties.template.containers[0].env // [])[] | select(.name == "CRAVES_API_BASE_URL") | .secretRef]
  | last // ""
' <<<"$WEB_APP_JSON")"
if [[ -n "$WEB_API_BASE" ]]; then
  echo "Customer Web CRAVES_API_BASE_URL=$WEB_API_BASE"
elif [[ -n "$WEB_API_SECRET_REF" ]]; then
  echo "Customer Web CRAVES_API_BASE_URL is supplied by secretRef=$WEB_API_SECRET_REF"
else
  fail "Customer Web is missing CRAVES_API_BASE_URL"
fi

APIM_JSON="$(az apim show \
  --resource-group "$RG" \
  --name "$APIM" \
  -o json \
  --only-show-errors)"
APIM_GATEWAY_URL="$(jq -r '.gatewayUrl // .properties.gatewayUrl // ""' <<<"$APIM_JSON")"
APIM_PUBLIC_NETWORK="$(jq -r '.publicNetworkAccess // .properties.publicNetworkAccess // ""' <<<"$APIM_JSON")"
APIM_VNET_TYPE="$(jq -r '.virtualNetworkType // .properties.virtualNetworkType // ""' <<<"$APIM_JSON")"
APIM_STATE="$(jq -r '.provisioningState // .properties.provisioningState // ""' <<<"$APIM_JSON")"
[[ -n "$APIM_GATEWAY_URL" ]] || fail "APIM gateway URL could not be resolved from Azure"
ACTUAL_GATEWAY_HOST="${APIM_GATEWAY_URL#https://}"
ACTUAL_GATEWAY_HOST="${ACTUAL_GATEWAY_HOST%%/*}"
echo "APIM runtime: gatewayUrl=$APIM_GATEWAY_URL provisioningState=$APIM_STATE publicNetworkAccess=${APIM_PUBLIC_NETWORK:-default} virtualNetworkType=${APIM_VNET_TYPE:-None}"

if [[ "$APIM_HOST" != "$ACTUAL_GATEWAY_HOST" ]]; then
  echo "WARNING: Pipeline APIM_HOST=$APIM_HOST differs from Azure gateway host=$ACTUAL_GATEWAY_HOST" >&2
fi

if [[ -n "$WEB_API_BASE" ]]; then
  EXPECTED_WEB_API_BASE="https://${ACTUAL_GATEWAY_HOST}/api/v1"
  if [[ "${WEB_API_BASE%/}" != "$EXPECTED_WEB_API_BASE" ]]; then
    echo "WARNING: Customer Web API base differs from current APIM gateway base: web=${WEB_API_BASE%/} expected=$EXPECTED_WEB_API_BASE" >&2
  else
    echo "PASS: Customer Web points to the current APIM gateway"
  fi
fi

EXPECTED_PATH="api/v1/subscriptions"
EXPECTED_SERVICE_URL="https://${SUB_FQDN}/api/v1/subscriptions"
MGMT_BASE="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis"

API_JSON="$(az rest \
  --method get \
  --url "${MGMT_BASE}/${API_ID}?api-version=${API_VERSION}" \
  -o json)"

ACTUAL_PATH="$(jq -r '.properties.path // ""' <<<"$API_JSON")"
ACTUAL_SERVICE_URL="$(jq -r '.properties.serviceUrl // ""' <<<"$API_JSON")"
SUB_REQUIRED="$(jq -r '.properties.subscriptionRequired // false' <<<"$API_JSON")"

[[ "$ACTUAL_PATH" == "$EXPECTED_PATH" ]] || fail "APIM path mismatch: expected=$EXPECTED_PATH actual=$ACTUAL_PATH"
[[ "$ACTUAL_SERVICE_URL" == "$EXPECTED_SERVICE_URL" ]] || fail "APIM backend mismatch: expected=$EXPECTED_SERVICE_URL actual=$ACTUAL_SERVICE_URL"
[[ "$SUB_REQUIRED" == "false" ]] || fail "APIM subscriptionRequired must be false"
echo "PASS: core Subscription API points to the current Subscription Service"

OPS_JSON="$(az rest \
  --method get \
  --url "${MGMT_BASE}/${API_ID}/operations?api-version=${API_VERSION}" \
  -o json)"

require_operation() {
  local OP_ID="$1" METHOD="$2" TEMPLATE="$3"
  local COUNT
  COUNT="$(jq --arg id "$OP_ID" --arg method "$METHOD" --arg template "$TEMPLATE" \
    '[.value[] | select(.name == $id and .properties.method == $method and .properties.urlTemplate == $template)] | length' \
    <<<"$OPS_JSON")"
  [[ "$COUNT" == "1" ]] || fail "Missing or incorrect APIM operation: $OP_ID $METHOD $TEMPLATE"
  echo "PASS: $OP_ID $METHOD $TEMPLATE"
}

require_operation "list-plans" "GET" "/plans"
require_operation "get-plan" "GET" "/plans/{planId}"
require_operation "create-subscription" "POST" "/"
require_operation "list-my-subscriptions" "GET" "/"
require_operation "get-subscription" "GET" "/{subscriptionId}"
require_operation "pause-subscription" "PATCH" "/{subscriptionId}/pause"
require_operation "cancel-subscription" "PATCH" "/{subscriptionId}/cancel"

probe_http() {
  local URL="$1" LABEL="$2" BODY="$3"
  local ERR RC CODE
  ERR="$(mktemp)"
  : >"$BODY"
  set +e
  CODE="$(curl \
    --silent \
    --show-error \
    --connect-timeout 10 \
    --max-time 30 \
    --output "$BODY" \
    --write-out '%{http_code}' \
    "$URL" 2>"$ERR")"
  RC=$?
  set -e
  if [[ "$RC" -ne 0 ]]; then
    echo "TRANSPORT: $LABEL curl_exit=$RC http=${CODE:-000}" >&2
    sed -n '1,8p' "$ERR" >&2 || true
  else
    echo "HTTP: $LABEL -> $CODE"
  fi
  rm -f "$ERR"
  PROBE_RC="$RC"
  PROBE_CODE="${CODE:-000}"
}

TMP_FILES=()
cleanup() {
  if (( ${#TMP_FILES[@]} > 0 )); then
    rm -f "${TMP_FILES[@]}"
  fi
}
trap cleanup EXIT

BACKEND_PLANS_BODY="$(mktemp)"
TMP_FILES+=("$BACKEND_PLANS_BODY")
BACKEND_PLANS_CODE="SKIPPED"
BACKEND_PLANS_RC="SKIPPED"
if [[ "$SUB_EXTERNAL" == "true" ]]; then
  probe_http "https://${SUB_FQDN}/api/v1/subscriptions/plans" "Subscription Service public plans direct" "$BACKEND_PLANS_BODY"
  BACKEND_PLANS_CODE="$PROBE_CODE"
  BACKEND_PLANS_RC="$PROBE_RC"
  if [[ "$BACKEND_PLANS_RC" == "0" && "$BACKEND_PLANS_CODE" == "200" ]] && jq -e 'type == "array"' "$BACKEND_PLANS_BODY" >/dev/null 2>&1; then
    echo "PASS: Subscription Service direct public plans -> HTTP 200 JSON array"
  else
    echo "WARNING: Direct Subscription Service plans probe did not prove a healthy public backend (curl_exit=$BACKEND_PLANS_RC HTTP=$BACKEND_PLANS_CODE)." >&2
  fi
else
  echo "INFO: Subscription Service ingress is internal; skipping public-agent direct backend probe."
fi

PLANS_BODY="$(mktemp)"
TMP_FILES+=("$PLANS_BODY")
probe_http "${APIM_GATEWAY_URL%/}/api/v1/subscriptions/plans" "APIM public Subscription plans" "$PLANS_BODY"
PLANS_RC="$PROBE_RC"
PLANS_CODE="$PROBE_CODE"

if [[ "$PLANS_RC" != "0" ]]; then
  echo "------------------------------------------------------------" >&2
  echo "APIM GATEWAY TRANSPORT DIAGNOSIS" >&2
  echo "Azure management plane is reachable and the Subscription API definition is present." >&2
  echo "APIM gateway did not return any HTTP response to the agent." >&2
  echo "APIM state: provisioningState=$APIM_STATE publicNetworkAccess=${APIM_PUBLIC_NETWORK:-default} virtualNetworkType=${APIM_VNET_TYPE:-None}" >&2
  if [[ "$BACKEND_PLANS_RC" == "0" && "$BACKEND_PLANS_CODE" == "200" ]]; then
    echo "Direct Subscription Service returned HTTP 200, isolating the failure to APIM gateway reachability/networking rather than the Subscription Service route." >&2
  elif [[ "$BACKEND_PLANS_RC" != "SKIPPED" ]]; then
    echo "Direct Subscription Service also could not be proven healthy from this public agent; inspect both APIM and Container App network exposure." >&2
  fi
  fail "APIM gateway transport failed (curl_exit=$PLANS_RC HTTP=$PLANS_CODE). Do not run the APIM route-write repair for a transport failure."
fi

if [[ "$PLANS_CODE" != "200" ]]; then
  echo "Public plans response:" >&2
  head -c 1000 "$PLANS_BODY" >&2 || true
  echo >&2
  fail "Public Subscription plans route returned HTTP=$PLANS_CODE instead of 200"
fi
jq -e 'type == "array"' "$PLANS_BODY" >/dev/null \
  || fail "Public Subscription plans route did not return a JSON array"
echo "PASS: APIM public subscription plans route -> HTTP 200 JSON array"

PROTECTED_BODY="$(mktemp)"
TMP_FILES+=("$PROTECTED_BODY")
probe_http "${APIM_GATEWAY_URL%/}/api/v1/subscriptions" "APIM protected subscriptions root" "$PROTECTED_BODY"
PROTECTED_RC="$PROBE_RC"
PROTECTED_CODE="$PROBE_CODE"
[[ "$PROTECTED_RC" == "0" ]] || fail "Protected subscriptions root had an APIM transport failure (curl_exit=$PROTECTED_RC HTTP=$PROTECTED_CODE)"
case "$PROTECTED_CODE" in
  401|403) echo "PASS: protected subscriptions root is routed and rejects an unauthenticated request with HTTP $PROTECTED_CODE" ;;
  *) fail "Protected subscriptions root returned HTTP=$PROTECTED_CODE; expected 401 or 403. A 404/5xx indicates a gateway/backend routing problem." ;;
esac

echo "============================================================"
echo "SUCCESS: CORE SUBSCRIPTIONS APIM STATUS PASSED"
echo "No Azure resource was changed by this status check."
echo "============================================================"
