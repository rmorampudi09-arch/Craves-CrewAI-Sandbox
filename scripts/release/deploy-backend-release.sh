#!/usr/bin/env bash
set -euo pipefail
set +x

RESOURCE_GROUP=${1:?resource group required}
PACK_FILE=${2:?backend completion pack required}
IMAGE_MANIFEST=${3:?image manifest required}
OUTPUT_DIR=${4:?evidence output directory required}
EXPECTED_SOURCE_SHA=${5:?expected source SHA required}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

CONFIRMATION=${CONFIRM_DEPLOYMENT:-}
BACKUP_CONFIRMATION=${DATABASE_BACKUP_CONFIRMATION:-}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[[ "$CONFIRMATION" == 'DEPLOY_SEVEN_SERVICES' ]] \
  || fail 'CONFIRM_DEPLOYMENT must be DEPLOY_SEVEN_SERVICES.'
[[ "$BACKUP_CONFIRMATION" == 'DATABASE_BACKUP_VERIFIED' ]] \
  || fail 'DATABASE_BACKUP_CONFIRMATION must be DATABASE_BACKUP_VERIFIED.'
command -v az >/dev/null || fail 'Azure CLI is required.'
command -v jq >/dev/null || fail 'jq is required.'
command -v sha256sum >/dev/null || fail 'sha256sum is required.'
[[ -s "$PACK_FILE" ]] || fail "Backend completion pack is missing: $PACK_FILE"
[[ -s "$IMAGE_MANIFEST" ]] || fail "Image manifest is missing: $IMAGE_MANIFEST"

EXPECTED_RG=$(jq -r '.azure.resourceGroup' "$PACK_FILE")
ACR_NAME=$(jq -r '.azure.containerRegistry' "$PACK_FILE")
ACR_LOGIN=$(jq -r '.azure.containerRegistryLoginServer' "$PACK_FILE")
[[ "$RESOURCE_GROUP" == "$EXPECTED_RG" ]] \
  || fail "Resource group must match the backend completion pack: $EXPECTED_RG"

jq -e \
  --arg registry "$ACR_NAME" \
  --arg sourceSha "$EXPECTED_SOURCE_SHA" '
    .schemaVersion == 1
    and .registry == $registry
    and .sourceSha == $sourceSha
    and (.images | length == 7)
    and ([.images[].serviceKey] | length == (unique | length))
    and ([.images[].repository] | length == (unique | length))
    and ([.images[].digest] | all(test("^sha256:[0-9a-f]{64}$")))
  ' "$IMAGE_MANIFEST" >/dev/null || fail 'Image manifest validation failed.'

RELEASE_MODE=$(jq -r '.releaseMode // "DEPLOY_BACKEND"' "$IMAGE_MANIFEST")
EXPECTED_DEPLOY_COUNT=7
case "$RELEASE_MODE" in
  DEPLOY_BACKEND)
    ;;
  REPAIR_CATALOG_HEALTH_AND_DEPLOY)
    EXPECTED_DEPLOY_COUNT=1
    ;;
  *)
    fail "Unsupported deployment release mode: $RELEASE_MODE"
    ;;
esac

mkdir -p "$OUTPUT_DIR"
EVENTS="$OUTPUT_DIR/deployment-events.jsonl"
ROLLBACK_MAP="$OUTPUT_DIR/rollback-map.jsonl"
: >"$EVENTS"
: >"$ROLLBACK_MAP"

declare -a UPDATED_KEYS=()
declare -A APP_BY_KEY=()
declare -A PREVIOUS_IMAGE_BY_KEY=()
declare -A PREVIOUS_REVISION_BY_KEY=()
declare -A PREVIOUS_ENV_HASH_BY_KEY=()

record_event() {
  local service_key=$1
  local app_name=$2
  local phase=$3
  local status=$4
  local image=$5
  local revision=$6
  jq -cn \
    --arg serviceKey "$service_key" \
    --arg containerApp "$app_name" \
    --arg phase "$phase" \
    --arg status "$status" \
    --arg image "$image" \
    --arg revision "$revision" \
    --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{serviceKey:$serviceKey,containerApp:$containerApp,phase:$phase,status:$status,image:$image,revision:$revision,timestamp:$timestamp}' \
    >>"$EVENTS"
}

environment_hash() {
  local app_name=$1
  local revision_name=$2

  az containerapp revision show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$app_name" \
    --revision "$revision_name" \
    --query 'properties.template.containers[0].env' \
    --output json \
    --only-show-errors \
    | jq -S '
        (. // [])
        | map({
            name: .name,
            value: (.value // null),
            secretRef: (.secretRef // null)
          })
        | sort_by(.name)
      ' \
    | sha256sum \
    | cut -d' ' -f1
}

wait_ready() {
  local app_name=$1
  local expected_image=$2
  local attempts=${3:-60}
  local sleep_seconds=${4:-10}
  local attempt current_image latest_revision ready_revision running_status health_state

  for ((attempt=1; attempt<=attempts; attempt++)); do
    current_image=$(az containerapp show -g "$RESOURCE_GROUP" -n "$app_name" \
      --query 'properties.template.containers[0].image' -o tsv --only-show-errors 2>/dev/null || true)
    latest_revision=$(az containerapp show -g "$RESOURCE_GROUP" -n "$app_name" \
      --query 'properties.latestRevisionName' -o tsv --only-show-errors 2>/dev/null || true)
    ready_revision=$(az containerapp show -g "$RESOURCE_GROUP" -n "$app_name" \
      --query 'properties.latestReadyRevisionName' -o tsv --only-show-errors 2>/dev/null || true)
    running_status=$(az containerapp show -g "$RESOURCE_GROUP" -n "$app_name" \
      --query 'properties.runningStatus' -o tsv --only-show-errors 2>/dev/null || true)
    health_state=''
    if [[ -n "$latest_revision" ]]; then
      health_state=$(az containerapp revision show -g "$RESOURCE_GROUP" -n "$app_name" --revision "$latest_revision" \
        --query properties.healthState -o tsv --only-show-errors 2>/dev/null || true)
    fi

    if [[ "$current_image" == "$expected_image" \
      && -n "$latest_revision" \
      && "$latest_revision" == "$ready_revision" \
      && "$running_status" == 'Running' \
      && "$health_state" == 'Healthy' ]]; then
      printf '%s\n' "$latest_revision"
      return 0
    fi

    if [[ "$running_status" == 'Failed' || "$health_state" == 'Unhealthy' ]]; then
      az containerapp logs show -g "$RESOURCE_GROUP" -n "$app_name" --revision "$latest_revision" \
        --type console --tail 200 --format text --only-show-errors || true
      return 1
    fi

    echo "Waiting for $app_name ($attempt/$attempts): running=$running_status health=$health_state latest=$latest_revision ready=$ready_revision" >&2
    sleep "$sleep_seconds"
  done
  return 1
}

rollback_updated_services() {
  local reason=$1
  local index key app previous_image rollback_revision
  echo "Rolling back ${#UPDATED_KEYS[@]} updated service(s) in reverse order: $reason" >&2

  for ((index=${#UPDATED_KEYS[@]}-1; index>=0; index--)); do
    key=${UPDATED_KEYS[$index]}
    app=${APP_BY_KEY[$key]}
    previous_image=${PREVIOUS_IMAGE_BY_KEY[$key]}
    [[ -n "$previous_image" ]] || continue

    if az containerapp update -g "$RESOURCE_GROUP" -n "$app" \
      --image "$previous_image" --no-wait --only-show-errors >/dev/null; then
      if rollback_revision=$(wait_ready "$app" "$previous_image" 60 10); then
        if [[ "$(environment_hash "$app" "$rollback_revision")" == "${PREVIOUS_ENV_HASH_BY_KEY[$key]}" ]]; then
          record_event "$key" "$app" 'rollback' 'ready' "$previous_image" "$rollback_revision"
        else
          record_event "$key" "$app" 'rollback' 'environment-mismatch' "$previous_image" "$rollback_revision"
        fi
      else
        record_event "$key" "$app" 'rollback' 'readiness-failed' "$previous_image" ''
      fi
    else
      record_event "$key" "$app" 'rollback' 'update-failed' "$previous_image" ''
    fi
  done
}

abort_release() {
  local message=$1
  rollback_updated_services "$message"
  fail "$message"
}

# Complete every read-only check before the first Container App mutation.
while IFS= read -r service; do
  key=$(jq -r '.key' <<<"$service")
  app=$(jq -r '.containerApp' <<<"$service")
  repository=$(jq -r '.imageRepository' <<<"$service")
  digest=$(jq -r --arg key "$key" '.images[] | select(.serviceKey == $key) | .digest' "$IMAGE_MANIFEST")
  manifest_repository=$(jq -r --arg key "$key" '.images[] | select(.serviceKey == $key) | .repository' "$IMAGE_MANIFEST")

  [[ "$manifest_repository" == "$repository" ]] \
    || fail "Image repository mismatch for $key."
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || fail "Image digest is missing or malformed for $key."
  az containerapp show -g "$RESOURCE_GROUP" -n "$app" --only-show-errors >/dev/null \
    || fail "Container App not found: $app"
  az acr repository show --name "$ACR_NAME" --image "$repository@$digest" --only-show-errors >/dev/null \
    || fail "Image digest is not present in ACR: $repository@$digest"
done < <(jq -c '.services[]' "$PACK_FILE")

while IFS= read -r service; do
  key=$(jq -r '.key' <<<"$service")
  app=$(jq -r '.containerApp' <<<"$service")
  repository=$(jq -r '.imageRepository' <<<"$service")

  if [[ "$RELEASE_MODE" == 'REPAIR_CATALOG_HEALTH_AND_DEPLOY' && "$key" != 'catalog' ]]; then
    echo "Skipping unchanged service in Catalog repair mode: $key"
    continue
  fi

  digest=$(jq -r --arg key "$key" '.images[] | select(.serviceKey == $key) | .digest' "$IMAGE_MANIFEST")
  target_image="$ACR_LOGIN/$repository@$digest"

  previous_image=$(az containerapp show -g "$RESOURCE_GROUP" -n "$app" \
    --query 'properties.template.containers[0].image' -o tsv --only-show-errors)
  previous_revision=$(az containerapp show -g "$RESOURCE_GROUP" -n "$app" \
    --query 'properties.latestReadyRevisionName' -o tsv --only-show-errors)
  previous_env_hash=$(environment_hash "$app" "$previous_revision")

  APP_BY_KEY[$key]=$app
  PREVIOUS_IMAGE_BY_KEY[$key]=$previous_image
  PREVIOUS_REVISION_BY_KEY[$key]=$previous_revision
  PREVIOUS_ENV_HASH_BY_KEY[$key]=$previous_env_hash
  UPDATED_KEYS+=("$key")

  jq -cn \
    --arg serviceKey "$key" \
    --arg containerApp "$app" \
    --arg previousImage "$previous_image" \
    --arg previousReadyRevision "$previous_revision" \
    --arg environmentHash "$previous_env_hash" \
    '{serviceKey:$serviceKey,containerApp:$containerApp,previousImage:$previousImage,previousReadyRevision:$previousReadyRevision,environmentHash:$environmentHash}' \
    >>"$ROLLBACK_MAP"
  record_event "$key" "$app" 'before' 'ready' "$previous_image" "$previous_revision"

  echo "========== DEPLOY $key -> $app =========="
  az containerapp update -g "$RESOURCE_GROUP" -n "$app" \
    --image "$target_image" --no-wait --only-show-errors >/dev/null \
    || abort_release "Container App update failed for $app."

  new_revision=$(wait_ready "$app" "$target_image" 60 10) \
    || abort_release "New revision did not become ready for $app."
  record_event "$key" "$app" 'readiness' 'ready' "$target_image" "$new_revision"

  [[ "$(environment_hash "$app" "$new_revision")" == "$previous_env_hash" ]] \
    || abort_release "Runtime environment changed unexpectedly for $app."
  record_event "$key" "$app" 'environment' 'preserved' "$target_image" "$new_revision"

  bash "$ROOT/scripts/release/smoke-containerapp-health.sh" "$RESOURCE_GROUP" "$app" \
    || abort_release "Health smoke failed for $app."
  record_event "$key" "$app" 'health' 'passed' "$target_image" "$new_revision"
done < <(jq -c '.services[]' "$PACK_FILE")

jq -s '.' "$EVENTS" >"$OUTPUT_DIR/deployment-events.json"
jq -s '.' "$ROLLBACK_MAP" >"$OUTPUT_DIR/rollback-map.json"

jq -n \
  --arg resourceGroup "$RESOURCE_GROUP" \
  --arg sourceSha "$EXPECTED_SOURCE_SHA" \
  --arg releaseMode "$RELEASE_MODE" \
  --argjson expectedDeployCount "$EXPECTED_DEPLOY_COUNT" \
  --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --argjson deploymentEvents "$(cat "$OUTPUT_DIR/deployment-events.json")" \
  --argjson rollbackMap "$(cat "$OUTPUT_DIR/rollback-map.json")" \
  '{schemaVersion:1,resourceGroup:$resourceGroup,sourceSha:$sourceSha,releaseMode:$releaseMode,expectedDeployCount:$expectedDeployCount,generatedAt:$generatedAt,runtimeEnvironmentPreserved:true,externalProvidersActivated:false,secretsReadOrChanged:false,deploymentEvents:$deploymentEvents,rollbackMap:$rollbackMap}' \
  >"$OUTPUT_DIR/backend-deployment-manifest.json"

jq -e \
  --argjson expectedDeployCount "$EXPECTED_DEPLOY_COUNT" '
  .runtimeEnvironmentPreserved == true
  and .externalProvidersActivated == false
  and .secretsReadOrChanged == false
  and .expectedDeployCount == $expectedDeployCount
  and (.rollbackMap | length == $expectedDeployCount)
  and ([.deploymentEvents[] | select(.phase == "health" and .status == "passed")] | length == $expectedDeployCount)
' "$OUTPUT_DIR/backend-deployment-manifest.json" >/dev/null \
  || fail 'Final backend deployment evidence validation failed.'

echo 'SUCCESS: seven backend services deployed by digest; runtime configuration was preserved.'
