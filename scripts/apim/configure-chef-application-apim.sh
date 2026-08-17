#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
APP="${USER_CHEF_APP:-ca-craves-user-chef-service-prod}"
API_PATH="${API_PATH:-api/v1/chef/application}"
API_ID_DEFAULT="craves-chef-application-v1"
API_VERSION="${API_VERSION:-2022-08-01}"
HEALTH_ATTEMPTS="${CRAVES_APIM_BACKEND_HEALTH_ATTEMPTS:-12}"
HEALTH_SLEEP_SECONDS="${CRAVES_APIM_BACKEND_HEALTH_SLEEP_SECONDS:-10}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY_TEMPLATE="$ROOT/infra/apim/chef-application/chef-application-policy.xml"

fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in az jq curl sed; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$POLICY_TEMPLATE" ]] || fail "Policy template is missing"
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
  BODY="/tmp/craves-chef-application-apim-health-${BASHPID}.json"
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

mapfile -t API_IDS < <(az apim api list -g "$RG" --service-name "$APIM" --query "[?path=='${API_PATH}'].name" -o tsv)
(( ${#API_IDS[@]} <= 1 )) || fail "Multiple APIM APIs own $API_PATH"
if (( ${#API_IDS[@]} == 0 )); then
  API_ID="$API_ID_DEFAULT"
  az apim api create -g "$RG" --service-name "$APIM" --api-id "$API_ID" --display-name "Craves Chef Application API" --path "$API_PATH" --protocols https --subscription-required false -o none
else
  API_ID="${API_IDS[0]}"
  SUB_REQUIRED=$(az apim api show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --query subscriptionRequired -o tsv)
  [[ "${SUB_REQUIRED,,}" == "false" ]] || fail "Existing Chef Application API requires a subscription key; this script will not relax it"
fi

MGMT="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis/${API_ID}"
for SCOPE_URL in \
  "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/policies/policy?api-version=${API_VERSION}" \
  "${MGMT}/policies/policy?api-version=${API_VERSION}"; do
  POLICY=$(az rest --method get --url "$SCOPE_URL" --query properties.value -o tsv 2>/dev/null || true)
  [[ "$POLICY" != *'set-backend-service backend-id='* ]] || fail "Inherited backend-id policy cannot be safely overridden"
done

BACKEND="${APP_BASE}/api/v1/chef/application"
put_operation() {
  local ID="$1" METHOD="$2" TEMPLATE="$3" DISPLAY="$4"
  local BODY RENDERED POLICY_BODY
  BODY=$(mktemp); RENDERED=$(mktemp); POLICY_BODY=$(mktemp)
  cat >"$BODY" <<JSON
{"properties":{"displayName":"$DISPLAY","method":"$METHOD","urlTemplate":"$TEMPLATE","templateParameters":[],"responses":[{"statusCode":200,"description":"Chef application response"},{"statusCode":400,"description":"Invalid request"},{"statusCode":401,"description":"Authentication required"}]}}
JSON
  az rest --method put --url "${MGMT}/operations/${ID}?api-version=${API_VERSION}" --body @"$BODY" -o none
  sed "s|__CHEF_APPLICATION_BACKEND_URL__|${BACKEND}|g" "$POLICY_TEMPLATE" >"$RENDERED"
  jq -Rs '{properties:{format:"rawxml",value:.}}' "$RENDERED" >"$POLICY_BODY"
  az rest --method put --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --body @"$POLICY_BODY" -o none
  rm -f "$BODY" "$RENDERED" "$POLICY_BODY"
}

put_operation "get-chef-application" "GET" "/" "Get chef application"
put_operation "submit-chef-application" "POST" "/" "Submit chef application"
put_operation "upload-chef-proof-file" "POST" "/proof-files" "Upload chef proof file"

for ID in get-chef-application submit-chef-application upload-chef-proof-file; do
  az apim api operation show -g "$RG" --service-name "$APIM" --api-id "$API_ID" --operation-id "$ID" -o none
  POLICY=$(az rest --method get --url "${MGMT}/operations/${ID}/policies/policy?api-version=${API_VERSION}" --query properties.value -o tsv)
  [[ "$POLICY" == *"$BACKEND"* && "$POLICY" == *"Authorization"* && "$POLICY" == *"no-store"* ]] || fail "Operation $ID policy verification failed"
done

echo "SUCCESS: Chef application APIM operations configured on API $API_ID."
