#!/usr/bin/env bash
set -euo pipefail
set +x

ACTION="${ACTION:-discover}"
RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
PROFILE="${FRONT_DOOR_PROFILE:-afd-craves-prodlow}"
CONFIRM_PREMIUM="${CONFIRM_PREMIUM:-DO_NOT_PROVISION_PREMIUM}"
LAW_NAME="${LOG_ANALYTICS_WORKSPACE_NAME:-AUTO_DETECT}"

fail(){ echo "ERROR: $*" >&2; exit 1; }
info(){ echo "INFO: $*"; }

az account show >/dev/null

# Azure DevOps can require a non-empty runtime string. AUTO_DETECT means the
# main deployment script should use its existing single-workspace auto-detect logic.
if [[ "$LAW_NAME" == "AUTO_DETECT" ]]; then
  export LOG_ANALYTICS_WORKSPACE_NAME=""
fi

# Azure Front Door Standard -> Premium uses a dedicated REST upgrade operation.
# The REST contract requires wafMappingList. For a profile with no existing
# security policies/WAF mappings, the correct payload is an empty list.
# Perform this before the main deployment script so that the original script
# sees Premium and skips its legacy upgrade branch.
if [[ "$ACTION" == "deploy" ]]; then
  SUB="$(az account show --query id -o tsv)"
  PROFILE_ID="/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}"

  if az resource show --ids "$PROFILE_ID" --api-version 2025-04-15 >/dev/null 2>&1; then
    SKU="$(az resource show --ids "$PROFILE_ID" --api-version 2025-04-15 --query sku.name -o tsv)"
    STATE="$(az resource show --ids "$PROFILE_ID" --api-version 2025-04-15 --query properties.provisioningState -o tsv)"

    if [[ "$SKU" == "Standard_AzureFrontDoor" ]]; then
      [[ "$CONFIRM_PREMIUM" == "PROVISION_PREMIUM_AFD" ]] || fail "Standard to Premium is billable and one-way. Use confirmPremium=PROVISION_PREMIUM_AFD."
      [[ "$STATE" == "Succeeded" ]] || fail "Front Door profile is not ready for upgrade. provisioningState=$STATE"

      SECURITY_POLICIES_JSON="$(az rest --method get --uri "https://management.azure.com${PROFILE_ID}/securityPolicies?api-version=2025-04-15" -o json)"
      SECURITY_POLICY_COUNT="$(jq -r '.value | length' <<<"$SECURITY_POLICIES_JSON")"
      [[ "$SECURITY_POLICY_COUNT" == "0" ]] || fail "Existing Front Door security policies were found. WAF mapping must be reviewed before Standard-to-Premium upgrade."

      info "Upgrading Azure Front Door profile $PROFILE from Standard to Premium using no-WAF mapping payload."
      az rest \
        --method post \
        --uri "https://management.azure.com${PROFILE_ID}/upgrade?api-version=2025-04-15" \
        --headers Content-Type=application/json \
        --body '{"wafMappingList":[]}' \
        --only-show-errors >/dev/null

      for _ in $(seq 1 80); do
        SKU="$(az resource show --ids "$PROFILE_ID" --api-version 2025-04-15 --query sku.name -o tsv 2>/dev/null || true)"
        STATE="$(az resource show --ids "$PROFILE_ID" --api-version 2025-04-15 --query properties.provisioningState -o tsv 2>/dev/null || true)"
        if [[ "$SKU" == "Premium_AzureFrontDoor" && "$STATE" == "Succeeded" ]]; then
          info "Azure Front Door Premium upgrade completed."
          break
        fi
        sleep 15
      done

      [[ "$SKU" == "Premium_AzureFrontDoor" && "$STATE" == "Succeeded" ]] || fail "Premium upgrade did not complete. sku=$SKU provisioningState=$STATE"
    fi
  fi
fi

# The production script now uses Azure-valid resource names and the documented
# Front Door REST rule payload directly.
bash "infra/frontdoor/production/deploy-front-door.sh"
