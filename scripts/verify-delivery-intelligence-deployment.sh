#!/usr/bin/env bash
set -euo pipefail

RG="${RESOURCE_GROUP_NAME:-rg-craves-prodlow-centralindia}"
APP="${INTEGRATION_CONTAINER_APP_NAME:-ca-craves-integration-service-pr}"
EXPECTED_IMAGE_PART="craves/integration-service"

printf '\n============================================================\n'
printf 'VERIFY CRAVES DELIVERY INTELLIGENCE DEPLOYMENT\n'
printf '============================================================\n\n'

az containerapp show \
  --name "$APP" \
  --resource-group "$RG" \
  --query "{name:name,runningStatus:properties.runningStatus,latestRevision:properties.latestRevisionName,image:properties.template.containers[0].image,targetPort:properties.configuration.ingress.targetPort,fqdn:properties.configuration.ingress.fqdn}" \
  -o table

IMAGE=$(az containerapp show --name "$APP" --resource-group "$RG" \
  --query "properties.template.containers[0].image" -o tsv)
FQDN=$(az containerapp show --name "$APP" --resource-group "$RG" \
  --query "properties.configuration.ingress.fqdn" -o tsv)

if [[ "$IMAGE" != *"$EXPECTED_IMAGE_PART"* ]]; then
  echo "FAILED: Unexpected Integration Service image: $IMAGE"
  exit 1
fi

echo "OK: Integration Service image is active: $IMAGE"

printf '\n==================== INTERNAL KEY CONFIG ====================\n'
KEY_CONFIG=$(az containerapp show --name "$APP" --resource-group "$RG" \
  --query "properties.template.containers[0].env[?name=='CRAVES_INTERNAL_SERVICE_KEY'].{name:name,secretRef:secretRef}" -o json)
if [[ "$KEY_CONFIG" == "[]" ]]; then
  echo "FAILED: CRAVES_INTERNAL_SERVICE_KEY env name is missing."
  echo "Add it as a secret-backed Container App value; do not print the secret."
  exit 1
fi
echo "$KEY_CONFIG"
echo "OK: Internal service key env name exists. Secret value was not printed."

printf '\n==================== HEALTH CHECK ====================\n'
HEALTH_URL="https://$FQDN/actuator/health"
if ! curl -k -fsS --max-time 30 "$HEALTH_URL"; then
  echo
  echo "FAILED: Health endpoint did not respond. Recent logs follow:"
  az containerapp logs show --name "$APP" --resource-group "$RG" --tail 200 || true
  exit 1
fi
echo
echo "OK: Integration Service is healthy."

printf '\n==================== MIGRATION / STARTUP LOG HINTS ====================\n'
az containerapp logs show --name "$APP" --resource-group "$RG" --tail 300 2>/dev/null \
  | grep -iE "flyway|delivery_schema|delivery intelligence|migration" || true

printf '\n============================================================\n'
printf 'SUCCESS: Deployment-level verification completed.\n'
printf 'The Azure DevOps Maven stage remains the authoritative test result.\n'
printf 'No secret values were printed.\n'
printf '============================================================\n'
