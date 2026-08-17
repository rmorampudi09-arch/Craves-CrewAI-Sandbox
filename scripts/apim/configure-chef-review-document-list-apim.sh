#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
APP="${USER_CHEF_APP:-ca-craves-user-chef-service-prod}"
API_VERSION="${API_VERSION:-2022-08-01}"
CONFIRM_APIM_WRITE="${CONFIRM_APIM_WRITE:-false}"
HEALTH_ATTEMPTS="${CRAVES_APIM_BACKEND_HEALTH_ATTEMPTS:-12}"
HEALTH_SLEEP_SECONDS="${CRAVES_APIM_BACKEND_HEALTH_SLEEP_SECONDS:-10}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/subscription-backoffice/authenticated-policy.xml"
API_PATH="api/v1/backoffice/chef-reviews"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "Authenticated APIM policy template is missing"
[[ "${CONFIRM_APIM_WRITE,,}" == "true" ]] || fail "Set CONFIRM_APIM_WRITE=true for the controlled APIM write"
[[ "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || fail "CRAVES_APIM_BACKEND_HEALTH_ATTEMPTS must be a positive integer"
[[ "$HEALTH_SLEEP_SECONDS" =~ ^[0-9]+$ ]] || fail "CRAVES_APIM_BACKEND_HEALTH_SLEEP_SECONDS must be a non-negative integer"

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
APP_JSON=$(az containerapp show -g "$RG" -n "$APP" -o json --only-show-errors)
FQDN=$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")
LATEST=$(jq -r '.properties.latestRevisionName // ""' <<<"$APP_JSON")
READY=$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$APP_JSON")
RUNNING=$(jq -r '.properties.runningStatus // ""' <<<"$APP_JSON")
[[ -n "$FQDN" && "$LATEST" == "$READY" && "$RUNNING" == "Running" ]] || fail "User-Chef Service is not ready: latest=$LATEST ready=$READY running=$RUNNING"
APP_BASE="https://${FQDN}"

wait_for_backend_health() {
  local ATTEMPT BODY CODE STATUS
  BODY="/tmp/craves-chef-review-apim-health-${BASHPID}.json"
  for ((ATTEMPT=1; ATTEMPT<=HEALTH_ATTEMPTS; ATTEMPT++)); do
    : >"$BODY"
    CODE=$(curl \
      --silent \
      --show-error \
      --connect-timeout 10 \
      --max-time 30 \
      --output "$BODY" \
      --write-out '%{http_code}' \
      "${APP_BASE}/actuator/health" || true)
    STATUS=$(jq -r '.status // empty' "$BODY" 2>/dev/null || true)

    if [[ "$CODE" == "200" && "$STATUS" == "UP" ]]; then
      rm -f "$BODY"
      echo "PASS: User-Chef Service aggregate health is UP attempt=$ATTEMPT/$HEALTH_ATTEMPTS"
      return 0
    fi

    echo "WAIT: User-Chef Service aggregate health attempt=$ATTEMPT/$HEALTH_ATTEMPTS HTTP=${CODE:-curl-error} status=${STATUS:-unavailable}" >&2
    if (( ATTEMPT < HEALTH_ATTEMPTS )); then
      sleep "$HEALTH_SLEEP_SECONDS"
    fi
  done
  rm -f "$BODY"
  return 1
}

wait_for_backend_health || fail "User-Chef Service aggregate health did not become UP; APIM write was not attempted"
BACKEND="${APP_BASE}/api/v1/backoffice/chef-reviews"

mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} == 1 )) || fail "Expected exactly one existing APIM API for ${API_PATH}; refusing to create an overlapping API"
API_ID="${API_IDS[0]}"
SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
[[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing API ${API_ID} unexpectedly requires a subscription key"

MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
OP_ID="list-chef-review-documents"
PARAMS='[{"name":"applicationId","type":"string","required":true}]'
BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
trap 'rm -f "$BODY" "$RENDERED" "$POLICY_BODY"' EXIT

jq -n --argjson params "$PARAMS" '{properties:{displayName:"List Chef review documents",method:"GET",urlTemplate:"/{applicationId}/documents",templateParameters:$params,responses:[{statusCode:200,description:"Document metadata"},{statusCode:401,description:"Authentication required"},{statusCode:403,description:"Access denied"},{statusCode:404,description:"Not found"}]}}' >"$BODY"
az rest --method put --url "${MGMT}/operations/${OP_ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
sed "s|__BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
az rest --method put --url "${MGMT}/operations/${OP_ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
POLICY=$(az rest --method get --url "${MGMT}/operations/${OP_ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
[[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Bearer"* && "$POLICY" == *"no-store"* ]] || fail "Admin Chef document-list policy verification failed"
az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$OP_ID" -o none --only-show-errors

echo "SUCCESS: Admin Chef review document-list operation configured on existing APIM API ${API_ID}."
