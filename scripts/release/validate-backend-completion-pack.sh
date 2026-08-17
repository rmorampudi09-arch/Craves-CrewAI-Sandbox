#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PACK="$ROOT/config/production/backend-completion-pack.json"
INVENTORY="$ROOT/config/production/azure-resource-inventory.json"
PIPELINE="$ROOT/azure-pipelines-backend-completion.yml"
DEPLOY_SCRIPT="$ROOT/scripts/release/deploy-backend-release.sh"
SINGLE_SERVICE_DEPLOY_SCRIPT="$ROOT/scripts/release/deploy-single-service-preserve-runtime.sh"
SMOKE_SCRIPT="$ROOT/scripts/release/smoke-containerapp-health.sh"
CATALOG_SECURITY="$ROOT/services/catalog-service/src/main/java/in/craves/catalog/security/SecurityConfig.java"

SERVICE_PIPELINES=(
  "$ROOT/azure-pipelines-auth-service.yml"
  "$ROOT/azure-pipelines-user-chef-service.yml"
  "$ROOT/azure-pipelines-catalog-service.yml"
  "$ROOT/azure-pipelines-order-service.yml"
  "$ROOT/azure-pipelines-subscription-service.yml"
  "$ROOT/azure-pipelines-integration-service.yml"
  "$ROOT/azure-pipelines-notification-service.yml"
)

RUNTIME_PRESERVING_PIPELINES=(
  "$ROOT/azure-pipelines-auth-service.yml"
  "$ROOT/azure-pipelines-user-chef-service.yml"
  "$ROOT/azure-pipelines-order-service.yml"
  "$ROOT/azure-pipelines-subscription-service.yml"
  "$ROOT/azure-pipelines-integration-service.yml"
  "$ROOT/azure-pipelines-notification-service.yml"
)

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v jq >/dev/null || fail 'jq is required'
command -v python3 >/dev/null || fail 'python3 is required'
[[ -s "$PACK" ]] || fail "missing backend completion pack: $PACK"
[[ -s "$INVENTORY" ]] || fail "missing Azure resource inventory: $INVENTORY"
[[ -s "$PIPELINE" ]] || fail "missing backend completion pipeline: $PIPELINE"
[[ -s "$DEPLOY_SCRIPT" ]] || fail "missing backend deployment script: $DEPLOY_SCRIPT"
[[ -s "$SINGLE_SERVICE_DEPLOY_SCRIPT" ]] || fail "missing runtime-preserving single-service deploy script: $SINGLE_SERVICE_DEPLOY_SCRIPT"
[[ -s "$SMOKE_SCRIPT" ]] || fail "missing Container App health smoke script: $SMOKE_SCRIPT"
[[ -s "$CATALOG_SECURITY" ]] || fail "missing Catalog security configuration: $CATALOG_SECURITY"

for service_pipeline in "${SERVICE_PIPELINES[@]}"; do
  [[ -s "$service_pipeline" ]] || fail "missing service pipeline: $service_pipeline"
done

jq -e '
  .schemaVersion == 1
  and .sourceVerificationOnlyByDefault == true
  and .preserveRuntimeEnvironmentOnDeployment == true
  and .externalProviderActivationDeferred == true
  and (.services | length == 7)
  and ([.services[].key] | length == (unique | length))
  and ([.services[].path] | length == (unique | length))
  and ([.services[].imageRepository] | length == (unique | length))
  and ([.services[].containerApp] | length == (unique | length))
  and ([.services[].deployOrder] == ([.services[].deployOrder] | sort))
' "$PACK" >/dev/null || fail 'backend completion pack structure is invalid'

[[ "$(jq -r '.azure.resourceGroup' "$PACK")" == "$(jq -r '.resourceGroup' "$INVENTORY")" ]] \
  || fail 'resource group differs from the canonical Azure inventory'
[[ "$(jq -r '.azure.containerRegistry' "$PACK")" == "$(jq -r '.containerRegistry' "$INVENTORY")" ]] \
  || fail 'container registry differs from the canonical Azure inventory'

PACK_APPS=$(jq -S '.services | map({key, value: .containerApp}) | from_entries' "$PACK")
INVENTORY_APPS=$(jq -S '.containerApps' "$INVENTORY")
[[ "$PACK_APPS" == "$INVENTORY_APPS" ]] \
  || fail 'Container App names differ from the canonical Azure inventory'

while IFS= read -r service; do
  path=$(jq -r '.path' <<<"$service")
  service_root="$ROOT/$path"
  [[ -s "$service_root/pom.xml" ]] || fail "missing Maven descriptor: $path/pom.xml"
  [[ -s "$service_root/Dockerfile" ]] || fail "missing Dockerfile: $path/Dockerfile"
  grep -F '<java.version>21</java.version>' "$service_root/pom.xml" >/dev/null \
    || fail "$path does not target Java 21"
  grep -Eq '^FROM maven:3\.9\.[0-9]+-eclipse-temurin-21 AS ' "$service_root/Dockerfile" \
    || fail "$path Dockerfile does not build its own JAR with Maven and Java 21"
  grep -Eq '^USER[[:space:]]+10001:10001$' "$service_root/Dockerfile" \
    || fail "$path Dockerfile does not use the required non-root runtime identity"

  while IFS= read -r flag; do
    [[ -z "$flag" ]] && continue
    grep -R -F --include='*.yml' --include='*.yaml' --include='*.java' \
      "${flag}:false" "$service_root/src/main" >/dev/null \
      || fail "$path does not explicitly default $flag to false"
  done < <(jq -r '.defaultFalseFlags[]' <<<"$service")
done < <(jq -c '.services[]' "$PACK")

python3 - "$PIPELINE" "${SERVICE_PIPELINES[@]}" <<'PY'
import sys
from pathlib import Path
try:
    import yaml
except ImportError as exc:
    raise SystemExit('ERROR: PyYAML is required') from exc

for raw_path in sys.argv[1:]:
    path = Path(raw_path)
    try:
        yaml.safe_load(path.read_text(encoding='utf-8'))
    except Exception as exc:
        raise SystemExit(f'ERROR: invalid Azure Pipeline YAML in {path.name}: {exc}') from exc
PY

bash -n "$DEPLOY_SCRIPT" "$SINGLE_SERVICE_DEPLOY_SCRIPT" "$SMOKE_SCRIPT"

grep -F '"/actuator/health/**"' "$CATALOG_SECURITY" >/dev/null \
  || fail 'Catalog security must permit the readiness and liveness health subtree'
grep -F '/actuator/health/readiness' "$SMOKE_SCRIPT" >/dev/null \
  || fail 'Container App smoke must test the readiness health endpoint first'
grep -F 'SMOKE_ATTEMPTS' "$SMOKE_SCRIPT" >/dev/null \
  || fail 'Container App smoke must retry transient ingress propagation'

grep -F 'verify_active_secret_refs_are_key_vault_backed' "$SINGLE_SERVICE_DEPLOY_SCRIPT" >/dev/null \
  || fail 'single-service deployment must verify active Key Vault secret bindings'
grep -F 'secret_metadata_hash' "$SINGLE_SERVICE_DEPLOY_SCRIPT" >/dev/null \
  || fail 'single-service deployment must preserve Container App secret metadata'
grep -F 'runtime_template_hash' "$SINGLE_SERVICE_DEPLOY_SCRIPT" >/dev/null \
  || fail 'single-service deployment must preserve runtime template state'
grep -F 'configuration_hash' "$SINGLE_SERVICE_DEPLOY_SCRIPT" >/dev/null \
  || fail 'single-service deployment must preserve Container App configuration'
grep -F 'identity_hash' "$SINGLE_SERVICE_DEPLOY_SCRIPT" >/dev/null \
  || fail 'single-service deployment must preserve managed identity state'

for service_pipeline in "${RUNTIME_PRESERVING_PIPELINES[@]}"; do
  grep -F 'scripts/release/deploy-single-service-preserve-runtime.sh' "$service_pipeline" >/dev/null \
    || fail "$(basename "$service_pipeline") must use the shared runtime-preserving deployment helper"
done

if grep -En 'apps/customer-web|apps/mobile|managed-redis|customer-web' "$PACK" "$PIPELINE" "$DEPLOY_SCRIPT" "$SINGLE_SERVICE_DEPLOY_SCRIPT" "$SMOKE_SCRIPT"; then
  fail 'backend completion files must not include web, mobile, or Managed Redis work'
fi

if grep -En -- '--set-env-vars|--replace-env-vars|secret set|--secrets' "$DEPLOY_SCRIPT" "$SINGLE_SERVICE_DEPLOY_SCRIPT"; then
  fail 'backend deployment helpers must preserve the live environment and secret bindings'
fi

if grep -En -- '--set-env-vars|--replace-env-vars|containerapp secret set|containerapp ingress update|--min-replicas|--max-replicas' "${SERVICE_PIPELINES[@]}"; then
  fail 'service-specific deployment pipelines must not reconstruct runtime env, rewrite secrets, change ingress, or change scaling'
fi

if grep -En ':latest([[:space:]]|$)' "$PIPELINE" "$DEPLOY_SCRIPT" "$SINGLE_SERVICE_DEPLOY_SCRIPT" "${SERVICE_PIPELINES[@]}"; then
  fail 'mutable latest image tags are forbidden'
fi

echo 'SUCCESS: backend completion pack and service deployment preservation contracts passed.'
