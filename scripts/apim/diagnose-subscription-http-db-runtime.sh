#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
SUB_APP="${SUB_APP:-ca-craves-subscription-service-p}"
DATABASE_NAME="${DATABASE_NAME:-craves_business_db}"

for tool in az jq curl; do
  command -v "$tool" >/dev/null || {
    echo "RUNTIME_DIAGNOSTIC_ERROR: $tool is required" >&2
    exit 0
  }
done

APP_JSON="$(az containerapp show --resource-group "$RG" --name "$SUB_APP" -o json --only-show-errors 2>/dev/null || true)"
if [[ -z "$APP_JSON" ]]; then
  echo "RUNTIME_DIAGNOSTIC_ERROR: Subscription Container App could not be read" >&2
  exit 0
fi

FQDN="$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$APP_JSON")"
REVISION="$(jq -r '.properties.latestReadyRevisionName // .properties.latestRevisionName // ""' <<<"$APP_JSON")"
ENVIRONMENT_ID="$(jq -r '.properties.environmentId // .properties.managedEnvironmentId // ""' <<<"$APP_JSON")"
ENVIRONMENT_NAME="${ENVIRONMENT_ID##*/}"
IMAGE="$(jq -r '.properties.template.containers[0].image // ""' <<<"$APP_JSON")"
MIN_REPLICAS="$(jq -r '.properties.template.scale.minReplicas // 0' <<<"$APP_JSON")"
MAX_REPLICAS="$(jq -r '.properties.template.scale.maxReplicas // 10' <<<"$APP_JSON")"

printf '%s\n' '============================================================'
printf '%s\n' 'READ-ONLY SUBSCRIPTION HTTP + DATABASE RUNTIME DIAGNOSTICS'
echo "Container App: $SUB_APP"
echo "Revision: ${REVISION:-unknown}"
echo "Image: ${IMAGE:-unknown}"
echo "FQDN: ${FQDN:-unknown}"
echo "Environment: ${ENVIRONMENT_NAME:-unknown}"
echo "Scale: minReplicas=$MIN_REPLICAS maxReplicas=$MAX_REPLICAS"

echo 'Runtime environment variable sources (values are NOT printed):'
jq -r '
  (.properties.template.containers[0].env // [])[]
  | "  " + .name + " source=" + (if (.secretRef // "") != "" then "secretRef:" + .secretRef else "plain-value" end)
' <<<"$APP_JSON" | sort

probe() {
  local URL="$1" LABEL="$2" MAX_TIME="$3"
  local BODY ERR META RC
  BODY="$(mktemp)"
  ERR="$(mktemp)"
  set +e
  META="$(curl --http1.1 \
    --silent \
    --show-error \
    --connect-timeout 8 \
    --max-time "$MAX_TIME" \
    --output "$BODY" \
    --write-out 'http=%{http_code} dns=%{time_namelookup} connect=%{time_connect} tls=%{time_appconnect} first_byte=%{time_starttransfer} total=%{time_total}' \
    "$URL" 2>"$ERR")"
  RC=$?
  set -e

  echo "HTTP_PROBE $LABEL curl_exit=$RC $META"
  if [[ -s "$ERR" ]]; then
    sed -n '1,6p' "$ERR" | sed 's/^/  curl: /'
  fi
  if [[ -s "$BODY" ]]; then
    echo '  response-body-preview:'
    head -c 1200 "$BODY" | tr '\n' ' '
    echo
  fi
  rm -f "$BODY" "$ERR"
}

if [[ -n "$FQDN" ]]; then
  BASE="https://${FQDN}"
  probe "$BASE/actuator/health/liveness" 'DIRECT_LIVENESS' 15
  probe "$BASE/actuator/health/readiness" 'DIRECT_READINESS' 15
  probe "$BASE/actuator/health" 'DIRECT_FULL_HEALTH' 40
  probe "$BASE/api/v1/subscriptions/plans" 'DIRECT_PUBLIC_PLANS' 40
  probe "$BASE/api/v1/subscriptions/plans" 'DIRECT_PUBLIC_PLANS_WARM' 40
fi

APIM_JSON="$(az apim show --resource-group "$RG" --name "$APIM" -o json --only-show-errors 2>/dev/null || true)"
APIM_GATEWAY_URL=""
if [[ -n "$APIM_JSON" ]] && jq -e . >/dev/null 2>&1 <<<"$APIM_JSON"; then
  APIM_GATEWAY_URL="$(jq -r '.gatewayUrl // .properties.gatewayUrl // ""' <<<"$APIM_JSON")"
fi
if [[ -n "$APIM_GATEWAY_URL" ]]; then
  probe "${APIM_GATEWAY_URL%/}/api/v1/subscriptions/plans" 'APIM_PUBLIC_PLANS' 45
fi

echo '------------------------------------------------------------'
echo 'PostgreSQL topology:'
mapfile -t PG_SERVERS < <(az postgres flexible-server list \
  --resource-group "$RG" \
  --query '[].name' \
  -o tsv \
  --only-show-errors 2>/dev/null || true)

MATCHED=0
for SERVER in "${PG_SERVERS[@]}"; do
  [[ -n "$SERVER" ]] || continue
  if ! az postgres flexible-server db show \
      --resource-group "$RG" \
      --server-name "$SERVER" \
      --name "$DATABASE_NAME" \
      --output none \
      --only-show-errors 2>/dev/null; then
    continue
  fi

  MATCHED=$((MATCHED + 1))
  PG_JSON="$(az postgres flexible-server show --resource-group "$RG" --name "$SERVER" -o json --only-show-errors)"
  PG_STATE="$(jq -r '.state // .properties.state // ""' <<<"$PG_JSON")"
  PG_FQDN="$(jq -r '.fullyQualifiedDomainName // .properties.fullyQualifiedDomainName // ""' <<<"$PG_JSON")"
  PG_PUBLIC="$(jq -r '.network.publicNetworkAccess // .properties.network.publicNetworkAccess // ""' <<<"$PG_JSON")"
  PG_SUBNET="$(jq -r '.network.delegatedSubnetResourceId // .properties.network.delegatedSubnetResourceId // ""' <<<"$PG_JSON")"
  PG_PRIVATE_DNS="$(jq -r '.network.privateDnsZoneArmResourceId // .properties.network.privateDnsZoneArmResourceId // ""' <<<"$PG_JSON")"
  echo "PostgreSQL: server=$SERVER database=$DATABASE_NAME state=${PG_STATE:-unknown} fqdn=${PG_FQDN:-unknown} publicNetworkAccess=${PG_PUBLIC:-unknown}"
  echo "PostgreSQL: delegatedSubnet=${PG_SUBNET:-none} privateDnsZone=${PG_PRIVATE_DNS:-none}"

  FIREWALL="$(az postgres flexible-server firewall-rule list \
    --resource-group "$RG" \
    --server-name "$SERVER" \
    -o json \
    --only-show-errors 2>/dev/null || echo '[]')"
  echo "PostgreSQL firewall rules: $(jq 'length' <<<"$FIREWALL")"
  jq -r '.[] | "  rule=" + (.name // "") + " start=" + (.startIpAddress // "") + " end=" + (.endIpAddress // "")' <<<"$FIREWALL"
done

if [[ "$MATCHED" -eq 0 ]]; then
  echo "DATABASE_TOPOLOGY_WARNING: No PostgreSQL Flexible Server in $RG was found hosting $DATABASE_NAME"
elif [[ "$MATCHED" -gt 1 ]]; then
  echo "DATABASE_TOPOLOGY_WARNING: More than one PostgreSQL Flexible Server hosts $DATABASE_NAME"
fi

if [[ -n "$ENVIRONMENT_NAME" ]]; then
  ENV_JSON="$(az containerapp env show --resource-group "$RG" --name "$ENVIRONMENT_NAME" -o json --only-show-errors 2>/dev/null || true)"
  if [[ -n "$ENV_JSON" ]]; then
    ENV_SUBNET="$(jq -r '.properties.vnetConfiguration.infrastructureSubnetId // .properties.vnetConfiguration.infrastructureSubnetResourceId // ""' <<<"$ENV_JSON")"
    ENV_INTERNAL="$(jq -r '.properties.vnetConfiguration.internal // false' <<<"$ENV_JSON")"
    ENV_STATIC_IP="$(jq -r '.properties.staticIp // ""' <<<"$ENV_JSON")"
    echo "Container Apps environment networking: internal=$ENV_INTERNAL infrastructureSubnet=${ENV_SUBNET:-none} staticIp=${ENV_STATIC_IP:-none}"
  fi
fi

echo '------------------------------------------------------------'
echo 'Current revision metadata:'
if [[ -n "$REVISION" ]]; then
  az containerapp revision show \
    --resource-group "$RG" \
    --name "$SUB_APP" \
    --revision "$REVISION" \
    --query '{name:name,active:properties.active,trafficWeight:properties.trafficWeight,provisioningState:properties.provisioningState,runningState:properties.runningState,healthState:properties.healthState,replicas:properties.replicas}' \
    -o jsonc \
    --only-show-errors 2>/dev/null || true
fi

echo 'Recent Subscription Service console evidence (filtered; secret values are not queried):'
LOG_ARGS=(
  --resource-group "$RG"
  --name "$SUB_APP"
  --type console
  --tail 220
  --format text
  --only-show-errors
)
if [[ -n "$REVISION" ]]; then
  LOG_ARGS+=(--revision "$REVISION")
fi
LOGS="$(az containerapp logs show "${LOG_ARGS[@]}" 2>/dev/null || true)"
if [[ -n "$LOGS" ]]; then
  printf '%s\n' "$LOGS" \
    | grep -Ei 'Hikari|PostgreSQL|PSQL|JDBC|Flyway|connection|timeout|SQLException|DataSource|ERROR|WARN|Exception' \
    | tail -n 120 \
    || true
else
  echo '  console logs unavailable from this agent'
fi

printf '%s\n' '============================================================'
printf '%s\n' 'END READ-ONLY SUBSCRIPTION HTTP + DATABASE DIAGNOSTICS'
printf '%s\n' '============================================================'
exit 0
