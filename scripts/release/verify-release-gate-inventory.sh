#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

required=(
  scripts/release/validate-stacked-pr-chain.sh
  scripts/release/validate-pipeline-yaml.py
  scripts/release/scan-secret-material.sh
  scripts/release/validate-java21-maven.sh
  scripts/release/validate-node-lockfiles.sh
  scripts/release/validate-dockerfiles.sh
  scripts/release/validate-flyway-migrations.py
  scripts/release/validate-apim-assets.sh
  scripts/release/verify-containerapps-readonly.sh
  scripts/release/verify-failclosed-controls.sh
  scripts/release/smoke-containerapp-health.sh
  scripts/release/validate-rollback-readiness.sh
  scripts/release/verify-observability-baseline.sh
  scripts/release/generate-release-manifest.py
  azure-pipelines-release-pr-stack-validator.yml
  azure-pipelines-release-pipeline-yaml-validator.yml
  azure-pipelines-release-secret-material-gate.yml
  azure-pipelines-release-java21-maven-gate.yml
  azure-pipelines-release-node-lockfile-gate.yml
  azure-pipelines-release-docker-hardening-gate.yml
  azure-pipelines-release-flyway-order-gate.yml
  azure-pipelines-release-apim-policy-gate.yml
  azure-pipelines-release-containerapp-preflight-gate.yml
  azure-pipelines-release-failclosed-controls-gate.yml
  azure-pipelines-release-service-health-smoke-gate.yml
  azure-pipelines-release-rollback-readiness-gate.yml
  azure-pipelines-release-observability-baseline-gate.yml
  azure-pipelines-release-manifest-generator.yml
)

failures=0
for path in "${required[@]}"; do
  if [[ ! -s "$path" ]]; then
    echo "ERROR: required release gate asset is missing or empty: $path" >&2
    failures=$((failures+1))
  fi
done

if grep -REn '(az[[:space:]]+containerapp[[:space:]]+update|az[[:space:]]+apim[[:space:]].*(create|update|delete)|docker[[:space:]]+push)' \
  scripts/release azure-pipelines-release-*.yml | grep -v 'validate-rollback-readiness.sh'; then
  echo 'ERROR: a read-only release gate appears to contain a mutating deployment command.' >&2
  failures=$((failures+1))
fi

(( failures == 0 )) || { echo "FAILED: $failures release-gate inventory issue(s)." >&2; exit 1; }
echo "SUCCESS: ${#required[@]} release gate assets are present and the gate layer remains non-deploying."
