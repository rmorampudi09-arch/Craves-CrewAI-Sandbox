#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

log() {
  printf '[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*" >&2
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 2
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "Required environment variable is missing: $name"
}

is_true() {
  case "${1,,}" in
    true|1|yes) return 0 ;;
    *) return 1 ;;
  esac
}

cleanup() {
  local exit_code=$?
  if [[ -n "${WORK_DIR:-}" && -d "${WORK_DIR:-}" ]]; then
    rm -rf "$WORK_DIR"
  fi
  exit "$exit_code"
}

trap cleanup EXIT
trap 'fail "Customer web certificate orchestration failed near line $LINENO."' ERR

for env_name in \
  RESOURCE_GROUP \
  KEY_VAULT_NAME \
  KEY_VAULT_CERTIFICATE_NAME \
  CONTAINER_APP_ENVIRONMENT_NAME \
  CONTAINER_APP_NAME \
  APEX_HOSTNAME \
  WWW_HOSTNAME; do
  require_env "$env_name"
done

RENEW_BEFORE_DAYS="${RENEW_BEFORE_DAYS:-30}"
FORCE_ISSUE="${FORCE_ISSUE:-false}"
CONTAINER_APP_CERTIFICATE_API_VERSION="${CONTAINER_APP_CERTIFICATE_API_VERSION:-2024-02-02-preview}"
RENEW_BEFORE_SECONDS=$((RENEW_BEFORE_DAYS * 86400))
WORK_DIR="$(mktemp -d)"
CURRENT_CERT_FILE="$WORK_DIR/current-certificate.pem"
ISSUANCE_LOG="$WORK_DIR/issuance.log"
ARM_BODY_FILE="$WORK_DIR/container-apps-certificate.json"
RENEW_SCRIPT="infra/certificates/web-letsencrypt/renew-web-certificate.sh"

[[ "$RENEW_BEFORE_DAYS" =~ ^[0-9]+$ ]] || fail "RENEW_BEFORE_DAYS must be a non-negative integer."
[[ -f "$RENEW_SCRIPT" ]] || fail "Certificate issuance script was not found: $RENEW_SCRIPT"

VERSIONLESS_SECRET_ID="https://${KEY_VAULT_NAME}.vault.azure.net/secrets/${KEY_VAULT_CERTIFICATE_NAME}"

certificate_version_id() {
  az keyvault certificate show \
    --vault-name "$KEY_VAULT_NAME" \
    --name "$KEY_VAULT_CERTIFICATE_NAME" \
    --query id \
    --output tsv \
    --only-show-errors 2>/dev/null || true
}

custom_domain_binding_required() {
  local custom_domains
  custom_domains="$(az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CONTAINER_APP_NAME" \
    --query 'properties.configuration.ingress.customDomains[].name' \
    --output tsv \
    --only-show-errors 2>/dev/null || true)"

  if grep -Fxq "$APEX_HOSTNAME" <<<"$custom_domains" && \
     grep -Fxq "$WWW_HOSTNAME" <<<"$custom_domains"; then
    printf 'false'
  else
    printf 'true'
  fi
}

sync_container_apps_certificate() {
  local environment_id environment_location certificate_resource_id certificate_uri
  local provisioning_state current_key_vault_url

  environment_id="$(az containerapp env show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
    --query id \
    --output tsv \
    --only-show-errors)"
  environment_location="$(az containerapp env show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
    --query location \
    --output tsv \
    --only-show-errors)"

  [[ -n "$environment_id" ]] || fail "Container Apps environment ID could not be resolved."
  [[ -n "$environment_location" ]] || fail "Container Apps environment location could not be resolved."

  certificate_resource_id="${environment_id}/certificates/${KEY_VAULT_CERTIFICATE_NAME}"
  certificate_uri="${certificate_resource_id}?api-version=${CONTAINER_APP_CERTIFICATE_API_VERSION}"

  cat > "$ARM_BODY_FILE" <<JSON
{
  "location": "$environment_location",
  "properties": {
    "certificateKeyVaultProperties": {
      "identity": "system",
      "keyVaultUrl": "$VERSIONLESS_SECRET_ID"
    },
    "certificateType": "ServerSSLCertificate"
  },
  "tags": {
    "Application": "Craves",
    "Environment": "ProdLow",
    "ManagedBy": "AzureDevOps",
    "Purpose": "CustomerWeb-TLS"
  }
}
JSON

  log "Synchronizing ${KEY_VAULT_CERTIFICATE_NAME} into ${CONTAINER_APP_ENVIRONMENT_NAME} through ARM."
  az rest \
    --method put \
    --uri "$certificate_uri" \
    --headers 'Content-Type=application/json' \
    --body "@$ARM_BODY_FILE" \
    --only-show-errors \
    --output none

  provisioning_state=""
  current_key_vault_url=""

  for attempt in {1..30}; do
    provisioning_state="$(az rest \
      --method get \
      --uri "$certificate_uri" \
      --query properties.provisioningState \
      --output tsv \
      --only-show-errors 2>/dev/null || true)"
    current_key_vault_url="$(az rest \
      --method get \
      --uri "$certificate_uri" \
      --query properties.certificateKeyVaultProperties.keyVaultUrl \
      --output tsv \
      --only-show-errors 2>/dev/null || true)"

    if [[ "$provisioning_state" == "Succeeded" && "$current_key_vault_url" == "$VERSIONLESS_SECRET_ID" ]]; then
      printf '%s' "$certificate_resource_id"
      return 0
    fi

    if [[ "$provisioning_state" == "Failed" || "$provisioning_state" == "Canceled" ]]; then
      fail "Container Apps certificate synchronization ended in state ${provisioning_state}."
    fi

    log "Waiting for certificate synchronization; attempt ${attempt}/30, state=${provisioning_state:-unknown}."
    sleep 10
  done

  fail "Container Apps certificate synchronization did not reach Succeeded with the versionless Key Vault URL."
}

print_existing_certificate_summary() {
  local certificate_id environment_certificate_id certificate_not_after certificate_fingerprint

  if [[ ! -s "$CURRENT_CERT_FILE" ]]; then
    az keyvault certificate download \
      --vault-name "$KEY_VAULT_NAME" \
      --name "$KEY_VAULT_CERTIFICATE_NAME" \
      --file "$CURRENT_CERT_FILE" \
      --encoding PEM \
      --only-show-errors \
      --output none
  fi

  certificate_not_after="$(openssl x509 -in "$CURRENT_CERT_FILE" -noout -enddate | cut -d= -f2-)"
  certificate_fingerprint="$(openssl x509 \
    -in "$CURRENT_CERT_FILE" \
    -noout \
    -sha256 \
    -fingerprint | cut -d= -f2 | tr -d ':')"
  certificate_id="$(certificate_version_id)"
  environment_certificate_id="$(sync_container_apps_certificate)"

  printf 'CERTIFICATE_CHANGED=false\n'
  printf 'CERTIFICATE_NAME=%s\n' "$KEY_VAULT_CERTIFICATE_NAME"
  printf 'CERTIFICATE_ID=%s\n' "$certificate_id"
  printf 'VERSIONLESS_SECRET_ID=%s\n' "$VERSIONLESS_SECRET_ID"
  printf 'CERTIFICATE_NOT_AFTER=%s\n' "$certificate_not_after"
  printf 'CERTIFICATE_SHA256=%s\n' "$certificate_fingerprint"
  printf 'CONTAINER_APP_ENVIRONMENT_CERTIFICATE_ID=%s\n' "$environment_certificate_id"
  printf 'CUSTOM_DOMAIN_BINDING_REQUIRED=%s\n' "$(custom_domain_binding_required)"
}

PREVIOUS_CERTIFICATE_ID="$(certificate_version_id)"

if [[ -n "$PREVIOUS_CERTIFICATE_ID" ]] && ! is_true "$FORCE_ISSUE"; then
  az keyvault certificate download \
    --vault-name "$KEY_VAULT_NAME" \
    --name "$KEY_VAULT_CERTIFICATE_NAME" \
    --file "$CURRENT_CERT_FILE" \
    --encoding PEM \
    --only-show-errors \
    --output none

  CURRENT_SAN="$(openssl x509 -in "$CURRENT_CERT_FILE" -noout -ext subjectAltName)"
  grep -Fq "DNS:${APEX_HOSTNAME}" <<<"$CURRENT_SAN" || \
    fail "Existing certificate is missing DNS:${APEX_HOSTNAME}."
  grep -Fq "DNS:${WWW_HOSTNAME}" <<<"$CURRENT_SAN" || \
    fail "Existing certificate is missing DNS:${WWW_HOSTNAME}."

  if openssl x509 \
      -in "$CURRENT_CERT_FILE" \
      -checkend "$RENEW_BEFORE_SECONDS" \
      -noout >/dev/null 2>&1; then
    log "Existing certificate remains valid beyond the renewal window; no new Let's Encrypt order will be placed."
    print_existing_certificate_summary
    exit 0
  fi
fi

log "Certificate issuance or renewal is required. Ensuring the extended Container Apps CLI commands are available."
az extension add \
  --name containerapp \
  --upgrade \
  --allow-preview true \
  --only-show-errors \
  --output none || log "Container Apps extension installation failed; ARM recovery remains available."

set +e
set +o pipefail
bash "$RENEW_SCRIPT" 2>&1 | tee "$ISSUANCE_LOG"
ISSUANCE_EXIT_CODE=${PIPESTATUS[0]}
set -o pipefail
set -e

CURRENT_CERTIFICATE_ID="$(certificate_version_id)"

if [[ "$ISSUANCE_EXIT_CODE" -ne 0 ]]; then
  if [[ -n "$CURRENT_CERTIFICATE_ID" && "$CURRENT_CERTIFICATE_ID" != "$PREVIOUS_CERTIFICATE_ID" ]]; then
    log "A new Key Vault certificate version was created before the CLI compatibility failure. Completing Container Apps synchronization through ARM."
    ENVIRONMENT_CERTIFICATE_ID="$(sync_container_apps_certificate)"
    printf 'CERTIFICATE_RECOVERED_AFTER_CLI_FAILURE=true\n'
    printf 'CERTIFICATE_CHANGED=true\n'
    printf 'CERTIFICATE_NAME=%s\n' "$KEY_VAULT_CERTIFICATE_NAME"
    printf 'CERTIFICATE_ID=%s\n' "$CURRENT_CERTIFICATE_ID"
    printf 'VERSIONLESS_SECRET_ID=%s\n' "$VERSIONLESS_SECRET_ID"
    printf 'CONTAINER_APP_ENVIRONMENT_CERTIFICATE_ID=%s\n' "$ENVIRONMENT_CERTIFICATE_ID"
    printf 'CUSTOM_DOMAIN_BINDING_REQUIRED=%s\n' "$(custom_domain_binding_required)"
    exit 0
  fi

  printf 'ERROR: Certificate issuance failed before a new Key Vault certificate version was created.\n' >&2
  exit "$ISSUANCE_EXIT_CODE"
fi

ENVIRONMENT_CERTIFICATE_ID="$(sync_container_apps_certificate)"
printf 'CONTAINER_APP_ENVIRONMENT_CERTIFICATE_ID=%s\n' "$ENVIRONMENT_CERTIFICATE_ID"
printf 'CUSTOM_DOMAIN_BINDING_REQUIRED=%s\n' "$(custom_domain_binding_required)"
