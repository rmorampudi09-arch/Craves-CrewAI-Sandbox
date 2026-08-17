#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$ROOT/config/production/backend-production-readiness.json"
fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in jq grep find; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$MANIFEST" ]] || fail "Production readiness manifest is missing"
jq -e '.schemaVersion == 1 and (.workstreams | length) == 8 and .runtimeChangesAllowedByThisPack == false' "$MANIFEST" >/dev/null

while IFS= read -r SERVICE; do
  [[ -d "$ROOT/$SERVICE" ]] || fail "Service directory is missing: $SERVICE"
  [[ -f "$ROOT/$SERVICE/pom.xml" ]] || fail "Maven pom is missing: $SERVICE"
  [[ -f "$ROOT/$SERVICE/Dockerfile" ]] || fail "Dockerfile is missing: $SERVICE"
done < <(jq -r '.services[]' "$MANIFEST")

mapfile -t APPLICATION_FILES < <(find "$ROOT/services" -path '*/src/main/resources/application.yml' -type f -print)
(( ${#APPLICATION_FILES[@]} > 0 )) || fail "No service application.yml files were found"
mapfile -t PIPELINE_FILES < <(find "$ROOT" -maxdepth 1 -type f -name 'azure-pipelines*.yml' -print)
(( ${#PIPELINE_FILES[@]} > 0 )) || fail "No Azure pipeline definitions were found"
while IFS= read -r FLAG; do
  grep -Rqs -- "$FLAG" "$ROOT/services" "${PIPELINE_FILES[@]}" \
    || fail "Required safety flag is not represented in service source or pipeline controls: $FLAG"
  if grep -En "\$\{${FLAG}:(true|TRUE)\}" "${APPLICATION_FILES[@]}"; then
    fail "Safety flag defaults true in application.yml: $FLAG"
  fi
done < <(jq -r '.requiredDisabledByDefaultFlags[]' "$MANIFEST")

REQUIRED_FILES=(
  azure-pipelines-release-readiness-orchestrator.yml
  azure-pipelines-backend-production-readiness-final.yml
  azure-pipelines-auth-service.yml
  azure-pipelines-user-chef-service.yml
  azure-pipelines-catalog-service.yml
  azure-pipelines-order-service.yml
  azure-pipelines-subscription-service.yml
  azure-pipelines-integration-service.yml
  azure-pipelines-notification-service.yml
  azure-pipelines-admin-operational-investigations-ci.yml
  azure-pipelines-admin-operational-investigations-apim-ci.yml
  azure-pipelines-admin-account-intervention-web-ci.yml
  azure-pipelines-admin-account-intervention-apim-ci.yml
  azure-pipelines-admin-notification-recovery-web-ci.yml
  azure-pipelines-admin-notification-recovery-apim-ci.yml
  config/production/azure-resource-inventory.json
  config/production/container-app-secret-names.json
  scripts/release/verify-azure-resource-inventory-references.sh
  scripts/release/verify-container-app-secret-names.sh
  scripts/release/verify-seven-service-deployment-contracts.sh
  scripts/release/verify-deployment-environment-bindings.sh
  services/auth-service/src/main/resources/db/migration/V5__serialize_account_provider_sync.sql
  services/auth-service/src/test/java/in/craves/auth/admin/AdminAccountInterventionRepositoryTest.java
  services/notification-service/src/test/java/in/craves/notification/delivery/NotificationDeliveryRepositoryTest.java
  services/integration-service/src/main/java/in/craves/integration/config/PaymentApiProperties.java
  services/integration-service/src/test/java/in/craves/integration/config/PaymentApiPropertiesTest.java
  docs/handover/2026-07-31-backend-launch-critical-completion.md
  docs/handover/2026-07-31-admin-account-intervention-web.md
  docs/handover/2026-07-31-admin-notification-recovery-web.md
)
for FILE in "${REQUIRED_FILES[@]}"; do
  [[ -f "$ROOT/$FILE" ]] || fail "Required release artifact is missing: $FILE"
done

while IFS= read -r PIPELINE; do
  grep -Eq '^trigger:[[:space:]]*none$' "$PIPELINE" || fail "Manual pipeline lacks trigger:none: ${PIPELINE#$ROOT/}"
  grep -Eq '^pr:[[:space:]]*none$' "$PIPELINE" || fail "Manual pipeline lacks pr:none: ${PIPELINE#$ROOT/}"
done < <(find "$ROOT" -maxdepth 1 -type f \( -name 'azure-pipelines-*activation*.yml' -o -name 'azure-pipelines-*rollback*.yml' -o -name 'azure-pipelines-admin-*-apim.yml' \) -print)

if grep -REn '(password|secret|accesskey|private[-_ ]?key)[[:space:]]*[:=][[:space:]]*[A-Za-z0-9+/=]{16,}' \
  "$ROOT/config/production" "$ROOT/docs/runbooks" 2>/dev/null; then
  fail "A possible committed secret was detected in production control artifacts"
fi

jq -r '.workstreams[] | "READY-PACK \(.id): \(.name)"' "$MANIFEST"
echo "SUCCESS: Backend production readiness source pack is internally complete. No runtime action was performed."
