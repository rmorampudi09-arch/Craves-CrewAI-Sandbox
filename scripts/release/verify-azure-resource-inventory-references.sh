#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INVENTORY="$ROOT/config/production/azure-resource-inventory.json"
fail() { echo "ERROR: $*" >&2; exit 1; }
for tool in jq grep find; do command -v "$tool" >/dev/null || fail "$tool is required"; done
[[ -f "$INVENTORY" ]] || fail "Azure resource inventory is missing"
jq -e '.schemaVersion == 1 and (.containerApps | length) == 7' "$INVENTORY" >/dev/null

check_pipeline_default() {
  local key="$1" pipeline="$2" expected
  expected=$(jq -r --arg key "$key" '.containerApps[$key]' "$INVENTORY")
  [[ -n "$expected" && "$expected" != "null" ]] || fail "Inventory is missing containerApps.$key"
  [[ -f "$ROOT/$pipeline" ]] || fail "Deployment pipeline is missing: $pipeline"
  grep -Fq -- "$expected" "$ROOT/$pipeline" \
    || fail "$pipeline does not reference canonical Container App $expected"
  echo "INVENTORY_OK $key=$expected pipeline=$pipeline"
}

check_pipeline_default auth azure-pipelines-auth-service.yml
check_pipeline_default userChef azure-pipelines-user-chef-service.yml
check_pipeline_default catalog azure-pipelines-catalog-service.yml
check_pipeline_default order azure-pipelines-order-service.yml
check_pipeline_default subscription azure-pipelines-subscription-service.yml
check_pipeline_default integration azure-pipelines-integration-service.yml
check_pipeline_default notification azure-pipelines-notification-service.yml

mapfile -t RUNTIME_FILES < <(
  {
    find "$ROOT" -maxdepth 1 -type f -name 'azure-pipelines*.yml' -print
    find "$ROOT/scripts" "$ROOT/infra" -type f \( -name '*.sh' -o -name '*.yml' -o -name '*.yaml' -o -name '*.xml' \) -print
  } | sort -u
)
(( ${#RUNTIME_FILES[@]} > 0 )) || fail "No runtime pipeline/script files were found"

while IFS= read -r stale; do
  [[ -n "$stale" ]] || continue
  matches=$(grep -Fl -- "$stale" "${RUNTIME_FILES[@]}" || true)
  if [[ -n "$matches" ]]; then
    echo "ERROR: Stale Container App name '$stale' remains in runtime files:" >&2
    echo "$matches" >&2
    exit 1
  fi
done < <(jq -r '.forbiddenStaleContainerAppNames[]' "$INVENTORY")

# Cross-module controls that must target canonical shortened names.
notification=$(jq -r '.containerApps.notification' "$INVENTORY")
user_chef=$(jq -r '.containerApps.userChef' "$INVENTORY")
catalog=$(jq -r '.containerApps.catalog' "$INVENTORY")
subscription=$(jq -r '.containerApps.subscription' "$INVENTORY")
for file in \
  azure-pipelines-notification-production-activation.yml \
  azure-pipelines-notification-production-rollback.yml \
  azure-pipelines-backend-notification-recovery-activation.yml \
  azure-pipelines-backend-notification-recovery-rollback.yml \
  scripts/apim/configure-admin-notification-recovery-apim.sh; do
  grep -Fq -- "$notification" "$ROOT/$file" || fail "$file does not target canonical notification app $notification"
done
for file in azure-pipelines-backend-redis-security-activation.yml azure-pipelines-backend-redis-security-rollback.yml; do
  grep -Fq -- "$user_chef" "$ROOT/$file" || fail "$file does not target canonical User-Chef app $user_chef"
  grep -Fq -- "$catalog" "$ROOT/$file" || fail "$file does not target canonical Catalog app $catalog"
  grep -Fq -- "$subscription" "$ROOT/$file" || fail "$file does not target canonical Subscription app $subscription"
  grep -Fq -- "$notification" "$ROOT/$file" || fail "$file does not target canonical Notification app $notification"
done

echo "SUCCESS: Azure runtime pipeline and script references match the canonical resource inventory."
