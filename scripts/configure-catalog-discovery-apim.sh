#!/usr/bin/env bash
set -euo pipefail

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
CATALOG_APP="${CATALOG_APP:-ca-craves-catalog-service-prodlo}"
API_ID="${API_ID:-craves-catalog-discovery-v1}"
API_PATH="${API_PATH:-api/v1/discovery}"
API_VERSION="2022-08-01"

AZURE_SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
CATALOG_FQDN="$(az containerapp show \
  --name "$CATALOG_APP" \
  --resource-group "$RG" \
  --query 'properties.configuration.ingress.fqdn' \
  -o tsv)"

CATALOG_BASE_URL="https://${CATALOG_FQDN}"
DISCOVERY_SERVICE_URL="${CATALOG_BASE_URL}/api/v1/discovery"
MGMT_BASE="https://management.azure.com/subscriptions/${AZURE_SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}/apis"

if [[ -z "$CATALOG_FQDN" ]]; then
  echo "Catalog Container App FQDN could not be resolved." >&2
  exit 1
fi

echo "Catalog backend: $CATALOG_BASE_URL"
curl -sS --fail --max-time 30 "$CATALOG_BASE_URL/actuator/health" >/dev/null
echo "Catalog health check passed."

if az apim api show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  >/dev/null 2>&1; then
  az apim api update \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --set serviceUrl="$DISCOVERY_SERVICE_URL" path="$API_PATH" subscriptionRequired=false \
    -o none
else
  az apim api create \
    --resource-group "$RG" \
    --service-name "$APIM" \
    --api-id "$API_ID" \
    --display-name "Craves Catalog Discovery API" \
    --path "$API_PATH" \
    --service-url "$DISCOVERY_SERVICE_URL" \
    --protocols https \
    --subscription-required false \
    -o none
fi

put_operation() {
  local operation_id="$1"
  local url_template="$2"
  local display_name="$3"
  local body_file

  body_file="$(mktemp)"
  trap 'rm -f "$body_file"' RETURN

  cat > "$body_file" <<JSON
{
  "properties": {
    "displayName": "$display_name",
    "method": "GET",
    "urlTemplate": "$url_template",
    "templateParameters": [],
    "responses": []
  }
}
JSON

  az rest \
    --method put \
    --url "${MGMT_BASE}/${API_ID}/operations/${operation_id}?api-version=${API_VERSION}" \
    --body @"$body_file" \
    -o none

  rm -f "$body_file"
  trap - RETURN
}

put_operation "discover-nearby-kitchens" "/kitchens" "Discover nearby kitchens"
put_operation "discover-nearby-menu-items" "/menu-items" "Discover nearby menu items"

echo
echo "========== Configured API =========="
az apim api show \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  --query '{name:name,path:path,serviceUrl:serviceUrl,subscriptionRequired:subscriptionRequired}' \
  -o table

echo
echo "========== Configured Operations =========="
az apim api operation list \
  --resource-group "$RG" \
  --service-name "$APIM" \
  --api-id "$API_ID" \
  --query '[].{name:name,method:method,urlTemplate:urlTemplate}' \
  -o table

echo
echo "Catalog discovery APIM routes are configured."
