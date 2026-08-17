#!/usr/bin/env bash
set -euo pipefail

: "${CRAVES_CONFIRM_BILLABLE_AZURE_MAPS:=false}"
: "${CRAVES_EXPECTED_SUBSCRIPTION_ID:=4f897b61-9b52-44b4-8cf1-bdac281cc1aa}"
: "${CRAVES_RESOURCE_GROUP:=rg-craves-prodlow-centralindia}"
: "${CRAVES_CUSTOMER_WEB_APP:=ca-craves-web-prodlow}"
: "${CRAVES_USER_CHEF_APP:=ca-craves-user-chef-service-prod}"
: "${CRAVES_AZURE_MAPS_ACCOUNT:=maps-craves-prodlow-l3ing6}"
: "${CRAVES_AZURE_MAPS_LOCATION:=global}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[[ "${CRAVES_CONFIRM_BILLABLE_AZURE_MAPS,,}" == "true" ]] || fail \
  "CRAVES_CONFIRM_BILLABLE_AZURE_MAPS=true is required because Azure Maps is a billable metered resource."

for command in az jq; do
  command -v "$command" >/dev/null 2>&1 || fail "$command is required"
done

az account set --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID"
ACTIVE_SUBSCRIPTION_ID="$(az account show --query id -o tsv --only-show-errors)"
[[ "$ACTIVE_SUBSCRIPTION_ID" == "$CRAVES_EXPECTED_SUBSCRIPTION_ID" ]] || fail \
  "Azure CLI is using subscription $ACTIVE_SUBSCRIPTION_ID instead of $CRAVES_EXPECTED_SUBSCRIPTION_ID"

RG_LOCATION="$(az group show \
  --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
  --name "$CRAVES_RESOURCE_GROUP" \
  --query location \
  -o tsv \
  --only-show-errors)" || fail "Resource group $CRAVES_RESOURCE_GROUP was not found"
[[ -n "$RG_LOCATION" ]] || fail "Resource group location could not be resolved"

MAPS_LOCATION="$CRAVES_AZURE_MAPS_LOCATION"
[[ -n "$MAPS_LOCATION" ]] || fail "Azure Maps account location could not be resolved"

ensure_system_identity() {
  local app_name="$1"
  local principal_id
  principal_id="$(az containerapp show \
    --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
    --resource-group "$CRAVES_RESOURCE_GROUP" \
    --name "$app_name" \
    --query identity.principalId \
    -o tsv \
    --only-show-errors)"
  if [[ -z "$principal_id" ]]; then
    echo "$app_name has no system-assigned managed identity. Enabling it now." >&2
    az containerapp identity assign \
      --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
      --resource-group "$CRAVES_RESOURCE_GROUP" \
      --name "$app_name" \
      --system-assigned \
      --only-show-errors >/dev/null
    principal_id="$(az containerapp show \
      --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
      --resource-group "$CRAVES_RESOURCE_GROUP" \
      --name "$app_name" \
      --query identity.principalId \
      -o tsv \
      --only-show-errors)"
  fi
  [[ -n "$principal_id" ]] || fail "$app_name managed identity principal ID could not be resolved"
  printf '%s\n' "$principal_id"
}

WEB_PRINCIPAL_ID="$(ensure_system_identity "$CRAVES_CUSTOMER_WEB_APP")"
USER_CHEF_PRINCIPAL_ID="$(ensure_system_identity "$CRAVES_USER_CHEF_APP")"

if az maps account show \
  --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
  --resource-group "$CRAVES_RESOURCE_GROUP" \
  --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
  --only-show-errors >/dev/null 2>&1; then
  echo "Azure Maps account already exists: $CRAVES_AZURE_MAPS_ACCOUNT"
  MAPS_KIND="$(az maps account show \
    --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
    --resource-group "$CRAVES_RESOURCE_GROUP" \
    --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
    --query kind -o tsv --only-show-errors)"
  MAPS_SKU="$(az maps account show \
    --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
    --resource-group "$CRAVES_RESOURCE_GROUP" \
    --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
    --query sku.name -o tsv --only-show-errors)"
  [[ "$MAPS_KIND" == "Gen2" ]] || fail "Existing Azure Maps account is $MAPS_KIND; expected Gen2"
  [[ "$MAPS_SKU" == "G2" ]] || fail "Existing Azure Maps account SKU is $MAPS_SKU; expected G2"
  az maps account update \
    --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
    --resource-group "$CRAVES_RESOURCE_GROUP" \
    --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
    --sku G2 \
    --kind Gen2 \
    --disable-local-auth true \
    --only-show-errors >/dev/null
else
  echo "Creating BILLABLE Azure Maps Gen2/G2 account: $CRAVES_AZURE_MAPS_ACCOUNT in $MAPS_LOCATION"
  az maps account create \
    --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
    --resource-group "$CRAVES_RESOURCE_GROUP" \
    --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
    --location "$MAPS_LOCATION" \
    --sku G2 \
    --kind Gen2 \
    --disable-local-auth true \
    --accept-tos \
    --tags application=craves environment=prodlow capability=customer-location \
    --only-show-errors >/dev/null
fi

MAPS_ID="$(az maps account show \
  --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
  --resource-group "$CRAVES_RESOURCE_GROUP" \
  --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
  --query id -o tsv --only-show-errors)"
MAPS_CLIENT_ID="$(az maps account show \
  --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
  --resource-group "$CRAVES_RESOURCE_GROUP" \
  --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
  --query properties.uniqueId -o tsv --only-show-errors)"
[[ -n "$MAPS_ID" && -n "$MAPS_CLIENT_ID" ]] || fail "Azure Maps account ID/unique client ID could not be resolved"

grant_maps_reader() {
  local principal_id="$1"
  local app_name="$2"
  local role_count
  role_count="$(az role assignment list \
    --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
    --assignee-object-id "$principal_id" \
    --scope "$MAPS_ID" \
    --query "[?roleDefinitionName=='Azure Maps Data Reader'] | length(@)" \
    -o tsv \
    --only-show-errors)"
  if [[ "$role_count" == "0" ]]; then
    echo "Granting $app_name managed identity Azure Maps Data Reader at the Maps account scope."
    az role assignment create \
      --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
      --assignee-object-id "$principal_id" \
      --assignee-principal-type ServicePrincipal \
      --role "Azure Maps Data Reader" \
      --scope "$MAPS_ID" \
      --only-show-errors >/dev/null
  fi

  for attempt in $(seq 1 12); do
    role_count="$(az role assignment list \
      --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
      --assignee-object-id "$principal_id" \
      --scope "$MAPS_ID" \
      --query "[?roleDefinitionName=='Azure Maps Data Reader'] | length(@)" \
      -o tsv \
      --only-show-errors || true)"
    [[ "$role_count" != "0" && -n "$role_count" ]] && return
    [[ "$attempt" -lt 12 ]] && sleep 10
  done
  fail "Azure Maps Data Reader role assignment for $app_name did not become visible"
}

grant_maps_reader "$WEB_PRINCIPAL_ID" "$CRAVES_CUSTOMER_WEB_APP"
grant_maps_reader "$USER_CHEF_PRINCIPAL_ID" "$CRAVES_USER_CHEF_APP"

bind_maps_config() {
  local app_name="$1"
  local app_json latest_revision latest_ready configured_client_id configured_endpoint
  echo "Binding non-secret Azure Maps configuration to $app_name."
  az containerapp update \
    --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
    --resource-group "$CRAVES_RESOURCE_GROUP" \
    --name "$app_name" \
    --set-env-vars \
      "AZURE_MAPS_CLIENT_ID=$MAPS_CLIENT_ID" \
      "AZURE_MAPS_ENDPOINT=https://atlas.microsoft.com" \
    --only-show-errors >/dev/null

  for attempt in $(seq 1 40); do
    app_json="$(az containerapp show \
      --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
      --resource-group "$CRAVES_RESOURCE_GROUP" \
      --name "$app_name" \
      -o json --only-show-errors)"
    latest_revision="$(jq -r '.properties.latestRevisionName // ""' <<<"$app_json")"
    latest_ready="$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$app_json")"
    if [[ -n "$latest_revision" && "$latest_ready" == "$latest_revision" ]]; then
      break
    fi
    [[ "$attempt" -lt 40 ]] && sleep 10
  done
  [[ -n "${latest_revision:-}" && "$latest_ready" == "$latest_revision" ]] || fail \
    "$app_name did not report the Azure Maps configuration revision Ready"

  configured_client_id="$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "AZURE_MAPS_CLIENT_ID") | .value][0] // ""' <<<"$app_json")"
  configured_endpoint="$(jq -r '[.properties.template.containers[0].env[]? | select(.name == "AZURE_MAPS_ENDPOINT") | .value][0] // ""' <<<"$app_json")"
  [[ "$configured_client_id" == "$MAPS_CLIENT_ID" ]] || fail "$app_name AZURE_MAPS_CLIENT_ID was not bound correctly"
  [[ "$configured_endpoint" == "https://atlas.microsoft.com" ]] || fail "$app_name AZURE_MAPS_ENDPOINT was not bound correctly"
}

bind_maps_config "$CRAVES_CUSTOMER_WEB_APP"
bind_maps_config "$CRAVES_USER_CHEF_APP"

LOCAL_AUTH_DISABLED="$(az maps account show \
  --subscription "$CRAVES_EXPECTED_SUBSCRIPTION_ID" \
  --resource-group "$CRAVES_RESOURCE_GROUP" \
  --account-name "$CRAVES_AZURE_MAPS_ACCOUNT" \
  --query properties.disableLocalAuth -o tsv --only-show-errors)"
[[ "${LOCAL_AUTH_DISABLED,,}" == "true" ]] || fail "Azure Maps local/shared-key authentication is not disabled"

cat <<EOF
Azure Maps location foundation is configured.
Maps account: $CRAVES_AZURE_MAPS_ACCOUNT
Maps region: $MAPS_LOCATION
Maps kind/SKU: Gen2/G2
Shared-key auth: disabled
Customer web managed identity: $WEB_PRINCIPAL_ID
User-Chef managed identity: $USER_CHEF_PRINCIPAL_ID
Role: Azure Maps Data Reader
Browser/mobile-exposed map credential: none
EOF
