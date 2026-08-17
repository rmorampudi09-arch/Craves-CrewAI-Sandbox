#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$ROOT/config/production/backend-production-readiness.json"
OUTPUT="${1:-$ROOT/backend-production-run-plan.md}"
command -v jq >/dev/null || { echo "ERROR: jq is required" >&2; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "ERROR: manifest missing" >&2; exit 1; }

mapfile -t CI_PIPELINES < <(find "$ROOT" -maxdepth 1 -type f -name 'azure-pipelines*-ci.yml' -printf '%f\n' | sort)
mapfile -t STATUS_PIPELINES < <(find "$ROOT" -maxdepth 1 -type f -name 'azure-pipelines*-status.yml' -printf '%f\n' | sort)
mapfile -t ACTIVATION_PIPELINES < <(find "$ROOT" -maxdepth 1 -type f \( -name 'azure-pipelines*-activation.yml' -o -name 'azure-pipelines*-apim.yml' \) -printf '%f\n' | sort)
mapfile -t ROLLBACK_PIPELINES < <(find "$ROOT" -maxdepth 1 -type f -name 'azure-pipelines*-rollback.yml' -printf '%f\n' | sort)

{
  echo '# Craves backend production run plan'
  echo
  echo '> Generated from source only. This file does not run, merge, deploy or activate anything.'
  echo
  echo '## Mandatory sequence'
  echo
  echo '1. Run all static/module CI pipelines listed below.'
  echo '2. Run the read-only release readiness orchestrator.'
  echo '3. Fix every failure; regenerate the immutable source manifest.'
  echo '4. Merge the stacked PRs parent-first. Do not skip feature-only synchronization history when resolving ancestry.'
  echo '5. Deploy seven Spring services with all new feature/provider/worker flags disabled.'
  echo '6. Verify Flyway history, health, revision readiness and rollback image for each service.'
  echo '7. Configure Service Bus, managed identities, APIM and secret references.'
  echo '8. Validate Redis and external provider dependencies without enabling execution.'
  echo '9. Activate downstream consumers before upstream publishers and provider execution.'
  echo '10. Complete security, authorization, load, restore, observability and controlled provider tests.'
  echo
  echo '## Merge groups'
  echo
  jq -r '.mergeGroups[] | "- **\(.name):** PRs \(.pullRequests)"' "$MANIFEST"
  echo
  echo '## CI pipelines discovered in the checkout'
  echo
  for FILE in "${CI_PIPELINES[@]}"; do echo "- \`$FILE\`"; done
  echo
  echo '## Read-only status pipelines'
  echo
  for FILE in "${STATUS_PIPELINES[@]}"; do echo "- \`$FILE\`"; done
  echo
  echo '## Activation or APIM write pipelines — run only after explicit review'
  echo
  for FILE in "${ACTIVATION_PIPELINES[@]}"; do echo "- \`$FILE\`"; done
  echo
  echo '## Rollback pipelines — validate before activation'
  echo
  for FILE in "${ROLLBACK_PIPELINES[@]}"; do echo "- \`$FILE\`"; done
  echo
  echo '## Evidence required by workstream'
  echo
  jq -r '.workstreams[] | "### \(.id). \(.name)\n\n\(.execution)\n\nEvidence:\n" + (.evidence | map("- " + .) | join("\n")) + "\n"' "$MANIFEST"
  echo '## Hard stops'
  echo
  jq -r '.hardStops[] | "- " + .' "$MANIFEST"
} >"$OUTPUT"

echo "Generated: $OUTPUT"
