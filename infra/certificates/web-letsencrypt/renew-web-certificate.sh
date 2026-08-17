#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

log() {
  printf '[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command is missing: $1"
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
  unset AZUREDNS_BEARERTOKEN PFX_PASSWORD || true
  exit "$exit_code"
}

trap cleanup EXIT
trap 'fail "Customer web certificate automation failed near line $LINENO."' ERR

for command_name in az dig git openssl; do
  require_command "$command_name"
done

for env_name in \
  SUBSCRIPTION_ID \
  RESOURCE_GROUP \
  APEX_DNS_ZONE_NAME \
  WWW_DNS_ZONE_NAME \
  PARENT_DNS_SERVER \
  KEY_VAULT_NAME \
  KEY_VAULT_CERTIFICATE_NAME \
  CONTAINER_APP_ENVIRONMENT_NAME \
  CONTAINER_APP_NAME \
  APEX_HOSTNAME \
  WWW_HOSTNAME \
  CONTAINER_APP_DEFAULT_HOSTNAME \
  LETSENCRYPT_CONTACT_EMAIL; do
  require_env "$env_name"
done

ACME_SH_VERSION="${ACME_SH_VERSION:-3.1.4}"
RENEW_BEFORE_DAYS="${RENEW_BEFORE_DAYS:-30}"
DNS_PROPAGATION_SECONDS="${DNS_PROPAGATION_SECONDS:-90}"
FORCE_ISSUE="${FORCE_ISSUE:-false}"

[[ "$RENEW_BEFORE_DAYS" =~ ^[0-9]+$ ]] || fail "RENEW_BEFORE_DAYS must be a non-negative integer."
[[ "$DNS_PROPAGATION_SECONDS" =~ ^[0-9]+$ ]] || fail "DNS_PROPAGATION_SECONDS must be a non-negative integer."
[[ "$APEX_HOSTNAME" == *.* ]] || fail "APEX_HOSTNAME must be a fully qualified domain name."
[[ "$WWW_HOSTNAME" == "www.${APEX_HOSTNAME}" ]] || fail "WWW_HOSTNAME must equal www.${APEX_HOSTNAME}."
[[ "$APEX_DNS_ZONE_NAME" == "_acme-challenge.${APEX_HOSTNAME}" ]] || \
  fail "APEX_DNS_ZONE_NAME must equal _acme-challenge.${APEX_HOSTNAME}."
[[ "$WWW_DNS_ZONE_NAME" == "_acme-challenge.${WWW_HOSTNAME}" ]] || \
  fail "WWW_DNS_ZONE_NAME must equal _acme-challenge.${WWW_HOSTNAME}."
[[ "$LETSENCRYPT_CONTACT_EMAIL" == *@* ]] || fail "LETSENCRYPT_CONTACT_EMAIL is invalid."

RENEW_BEFORE_SECONDS=$((RENEW_BEFORE_DAYS * 86400))
WORK_DIR="$(mktemp -d)"
CURRENT_CERT_FILE="$WORK_DIR/current-certificate.pem"
ACME_SOURCE_DIR="$WORK_DIR/acme-source"
ACME_HOME="$WORK_DIR/acme-home"
ACME_CERT_HOME="$WORK_DIR/acme-certs"
OUTPUT_DIR="$WORK_DIR/output"
PFX_FILE="$WORK_DIR/${KEY_VAULT_CERTIFICATE_NAME}.pfx"

mkdir -p "$ACME_HOME" "$ACME_CERT_HOME" "$OUTPUT_DIR"

log "Selecting Azure subscription."
az account set --subscription "$SUBSCRIPTION_ID"

ACTIVE_SUBSCRIPTION_ID="$(az account show --query id --output tsv --only-show-errors)"
[[ "$ACTIVE_SUBSCRIPTION_ID" == "$SUBSCRIPTION_ID" ]] || \
  fail "The authenticated Azure context is using the wrong subscription."

TENANT_ID="$(az account show --query tenantId --output tsv --only-show-errors)"
[[ -n "$TENANT_ID" ]] || fail "Azure tenant ID could not be resolved."

log "Validating Azure DNS zones, authoritative delegations, Key Vault and Container Apps resources."
for dns_zone in "$APEX_DNS_ZONE_NAME" "$WWW_DNS_ZONE_NAME"; do
  mapfile -t expected_name_servers < <(
    az network dns zone show \
      --resource-group "$RESOURCE_GROUP" \
      --name "$dns_zone" \
      --query 'nameServers[]' \
      --output tsv \
      --only-show-errors | sed 's/\.$//' | sort -u
  )

  [[ "${#expected_name_servers[@]}" -eq 4 ]] || \
    fail "Expected four Azure DNS nameservers on zone ${dns_zone}."

  mapfile -t delegated_name_servers < <(
    dig @"$PARENT_DNS_SERVER" \
      "$dns_zone" \
      NS \
      +norecurse \
      +noall \
      +authority 2>/dev/null | \
    awk '$4 == "NS" {print $5}' | \
    sed 's/\.$//' | \
    sort -u
  )

  [[ "${#delegated_name_servers[@]}" -eq 4 ]] || \
    fail "Expected four delegated nameservers for ${dns_zone} from ${PARENT_DNS_SERVER}."

  [[ "$(printf '%s\n' "${delegated_name_servers[@]}")" == \
     "$(printf '%s\n' "${expected_name_servers[@]}")" ]] || \
    fail "Parent DNS delegation for ${dns_zone} does not match its Azure DNS zone."

  for name_server in "${delegated_name_servers[@]}"; do
    case "$name_server" in
      *.azure-dns.com|*.azure-dns.net|*.azure-dns.org|*.azure-dns.info) ;;
      *) fail "Unexpected delegated nameserver for ${dns_zone}: ${name_server}" ;;
    esac
  done
done

az keyvault show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$KEY_VAULT_NAME" \
  --only-show-errors \
  --output none

ENVIRONMENT_PRINCIPAL_ID="$(az containerapp env show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
  --query identity.principalId \
  --output tsv \
  --only-show-errors)"

[[ -n "$ENVIRONMENT_PRINCIPAL_ID" && "$ENVIRONMENT_PRINCIPAL_ID" != "null" ]] || \
  fail "Container Apps environment system-assigned identity is not enabled."

ACTUAL_APP_FQDN="$(az containerapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CONTAINER_APP_NAME" \
  --query properties.configuration.ingress.fqdn \
  --output tsv \
  --only-show-errors)"

[[ "$ACTUAL_APP_FQDN" == "$CONTAINER_APP_DEFAULT_HOSTNAME" ]] || \
  fail "Container App default hostname does not match ${CONTAINER_APP_DEFAULT_HOSTNAME}."

APP_RUNNING_STATUS="$(az containerapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CONTAINER_APP_NAME" \
  --query properties.runningStatus \
  --output tsv \
  --only-show-errors)"

[[ "$APP_RUNNING_STATUS" == "Running" ]] || \
  fail "Container App is not running. Current status: ${APP_RUNNING_STATUS}."

CERTIFICATE_EXISTS=false
if az keyvault certificate show \
    --vault-name "$KEY_VAULT_NAME" \
    --name "$KEY_VAULT_CERTIFICATE_NAME" \
    --only-show-errors \
    --output none 2>/dev/null; then
  CERTIFICATE_EXISTS=true

  az keyvault certificate download \
    --vault-name "$KEY_VAULT_NAME" \
    --name "$KEY_VAULT_CERTIFICATE_NAME" \
    --file "$CURRENT_CERT_FILE" \
    --encoding PEM \
    --only-show-errors \
    --output none

  CURRENT_SAN="$(openssl x509 -in "$CURRENT_CERT_FILE" -noout -ext subjectAltName)"
  grep -Fq "DNS:${APEX_HOSTNAME}" <<<"$CURRENT_SAN" || \
    fail "Existing Key Vault certificate is missing DNS:${APEX_HOSTNAME}."
  grep -Fq "DNS:${WWW_HOSTNAME}" <<<"$CURRENT_SAN" || \
    fail "Existing Key Vault certificate is missing DNS:${WWW_HOSTNAME}."

  CURRENT_NOT_AFTER="$(openssl x509 -in "$CURRENT_CERT_FILE" -noout -enddate | cut -d= -f2-)"
  log "Current Key Vault certificate expires: ${CURRENT_NOT_AFTER}"

  if ! is_true "$FORCE_ISSUE" && openssl x509 \
      -in "$CURRENT_CERT_FILE" \
      -checkend "$RENEW_BEFORE_SECONDS" \
      -noout >/dev/null 2>&1; then
    log "Certificate remains valid for more than ${RENEW_BEFORE_DAYS} days. No issuance is required."
    printf 'CERTIFICATE_CHANGED=false\n'
    printf 'CERTIFICATE_NAME=%s\n' "$KEY_VAULT_CERTIFICATE_NAME"
    printf 'CERTIFICATE_NOT_AFTER=%s\n' "$CURRENT_NOT_AFTER"
    printf 'CONTAINER_APP_ENVIRONMENT=%s\n' "$CONTAINER_APP_ENVIRONMENT_NAME"
    exit 0
  fi
fi

if is_true "$FORCE_ISSUE"; then
  log "Forced issuance was requested."
elif [[ "$CERTIFICATE_EXISTS" == true ]]; then
  log "Certificate is within the ${RENEW_BEFORE_DAYS}-day renewal window."
else
  log "No existing Key Vault certificate was found. Performing initial issuance."
fi

log "Downloading pinned acme.sh release ${ACME_SH_VERSION}."
git clone \
  --quiet \
  --depth 1 \
  --branch "$ACME_SH_VERSION" \
  https://github.com/acmesh-official/acme.sh.git \
  "$ACME_SOURCE_DIR"

ACME_SH="$ACME_SOURCE_DIR/acme.sh"
[[ -x "$ACME_SH" ]] || fail "Pinned acme.sh executable was not found."

ACME_REPORTED_VERSION="$($ACME_SH --version 2>&1 | tail -n 1 | awk '{print $NF}')"
ACME_REPORTED_VERSION="${ACME_REPORTED_VERSION#v}"
[[ "$ACME_REPORTED_VERSION" == "$ACME_SH_VERSION" ]] || \
  fail "Downloaded acme.sh version ${ACME_REPORTED_VERSION} does not match ${ACME_SH_VERSION}."

log "Obtaining an Azure Resource Manager bearer token for DNS-01 validation."
export AZUREDNS_SUBSCRIPTIONID="$SUBSCRIPTION_ID"
export AZUREDNS_TENANTID="$TENANT_ID"
export AZUREDNS_MANAGEDIDENTITY="false"
AZUREDNS_BEARERTOKEN="$(az account get-access-token \
  --resource https://management.azure.com/ \
  --query accessToken \
  --output tsv \
  --only-show-errors)"
export AZUREDNS_BEARERTOKEN

[[ -n "$AZUREDNS_BEARERTOKEN" ]] || fail "Azure DNS bearer token could not be obtained."

log "Registering the ephemeral ACME account."
"$ACME_SH" \
  --home "$ACME_HOME" \
  --config-home "$ACME_HOME" \
  --cert-home "$ACME_CERT_HOME" \
  --server letsencrypt \
  --register-account \
  --accountemail "$LETSENCRYPT_CONTACT_EMAIL"

log "Requesting a Let's Encrypt SAN certificate through Azure DNS DNS-01 validation."
"$ACME_SH" \
  --home "$ACME_HOME" \
  --config-home "$ACME_HOME" \
  --cert-home "$ACME_CERT_HOME" \
  --server letsencrypt \
  --issue \
  --dns dns_azure \
  --domain "$APEX_HOSTNAME" \
  --domain "$WWW_HOSTNAME" \
  --keylength 2048 \
  --dnssleep "$DNS_PROPAGATION_SECONDS"

log "Copying certificate material into the protected temporary directory."
"$ACME_SH" \
  --home "$ACME_HOME" \
  --config-home "$ACME_HOME" \
  --cert-home "$ACME_CERT_HOME" \
  --install-cert \
  --domain "$APEX_HOSTNAME" \
  --key-file "$OUTPUT_DIR/private-key.pem" \
  --cert-file "$OUTPUT_DIR/certificate.pem" \
  --ca-file "$OUTPUT_DIR/issuer-chain.pem" \
  --fullchain-file "$OUTPUT_DIR/full-chain.pem"

for certificate_file in \
  "$OUTPUT_DIR/private-key.pem" \
  "$OUTPUT_DIR/certificate.pem" \
  "$OUTPUT_DIR/issuer-chain.pem" \
  "$OUTPUT_DIR/full-chain.pem"; do
  [[ -s "$certificate_file" ]] || fail "Expected certificate file was not created: $certificate_file"
done

openssl rsa \
  -in "$OUTPUT_DIR/private-key.pem" \
  -check \
  -noout >/dev/null 2>&1

openssl verify \
  -CAfile "$OUTPUT_DIR/issuer-chain.pem" \
  "$OUTPUT_DIR/certificate.pem" >/dev/null

CERTIFICATE_SAN="$(openssl x509 \
  -in "$OUTPUT_DIR/certificate.pem" \
  -noout \
  -ext subjectAltName)"

grep -Fq "DNS:${APEX_HOSTNAME}" <<<"$CERTIFICATE_SAN" || \
  fail "Issued certificate does not contain DNS:${APEX_HOSTNAME}."
grep -Fq "DNS:${WWW_HOSTNAME}" <<<"$CERTIFICATE_SAN" || \
  fail "Issued certificate does not contain DNS:${WWW_HOSTNAME}."

ISSUED_NOT_AFTER="$(openssl x509 -in "$OUTPUT_DIR/certificate.pem" -noout -enddate | cut -d= -f2-)"
ISSUED_FINGERPRINT="$(openssl x509 \
  -in "$OUTPUT_DIR/certificate.pem" \
  -noout \
  -sha256 \
  -fingerprint | cut -d= -f2 | tr -d ':')"

log "Building an encrypted PKCS#12 package for Key Vault import."
PFX_PASSWORD="$(openssl rand -base64 48 | tr -d '\n')"
export PFX_PASSWORD

openssl pkcs12 \
  -export \
  -out "$PFX_FILE" \
  -inkey "$OUTPUT_DIR/private-key.pem" \
  -in "$OUTPUT_DIR/certificate.pem" \
  -certfile "$OUTPUT_DIR/issuer-chain.pem" \
  -name "$APEX_HOSTNAME" \
  -passout env:PFX_PASSWORD

[[ -s "$PFX_FILE" ]] || fail "PKCS#12 package was not created."

log "Importing the certificate as a new version in Azure Key Vault."
az keyvault certificate import \
  --vault-name "$KEY_VAULT_NAME" \
  --name "$KEY_VAULT_CERTIFICATE_NAME" \
  --file "$PFX_FILE" \
  --password "$PFX_PASSWORD" \
  --tags \
    Domains="${APEX_HOSTNAME},${WWW_HOSTNAME}" \
    Issuer=LetsEncrypt \
    ManagedBy=AzureDevOps \
    Purpose=CustomerWeb-TLS \
  --only-show-errors \
  --output none

unset PFX_PASSWORD
rm -f "$PFX_FILE"

IMPORTED_CERT_FILE="$WORK_DIR/imported-certificate.pem"
az keyvault certificate download \
  --vault-name "$KEY_VAULT_NAME" \
  --name "$KEY_VAULT_CERTIFICATE_NAME" \
  --file "$IMPORTED_CERT_FILE" \
  --encoding PEM \
  --only-show-errors \
  --output none

IMPORTED_FINGERPRINT="$(openssl x509 \
  -in "$IMPORTED_CERT_FILE" \
  -noout \
  -sha256 \
  -fingerprint | cut -d= -f2 | tr -d ':')"

[[ "$IMPORTED_FINGERPRINT" == "$ISSUED_FINGERPRINT" ]] || \
  fail "Key Vault certificate fingerprint does not match the issued certificate."

CERTIFICATE_ID="$(az keyvault certificate show \
  --vault-name "$KEY_VAULT_NAME" \
  --name "$KEY_VAULT_CERTIFICATE_NAME" \
  --query id \
  --output tsv \
  --only-show-errors)"

VERSIONLESS_SECRET_ID="https://${KEY_VAULT_NAME}.vault.azure.net/secrets/${KEY_VAULT_CERTIFICATE_NAME}"

log "Importing the versionless Key Vault certificate into the Container Apps environment."
az containerapp env certificate upload \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
  --certificate-name "$KEY_VAULT_CERTIFICATE_NAME" \
  --akv-url "$VERSIONLESS_SECRET_ID" \
  --identity system \
  --only-show-errors \
  --output none

ENV_CERTIFICATE_ID="$(az containerapp env certificate list \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
  --query "[?name=='${KEY_VAULT_CERTIFICATE_NAME}'].id | [0]" \
  --output tsv \
  --only-show-errors)"

[[ -n "$ENV_CERTIFICATE_ID" && "$ENV_CERTIFICATE_ID" != "null" ]] || \
  fail "The imported certificate was not found in the Container Apps environment."

log "Customer web certificate issuance and imports completed successfully."
printf 'CERTIFICATE_CHANGED=true\n'
printf 'CERTIFICATE_NAME=%s\n' "$KEY_VAULT_CERTIFICATE_NAME"
printf 'CERTIFICATE_ID=%s\n' "$CERTIFICATE_ID"
printf 'VERSIONLESS_SECRET_ID=%s\n' "$VERSIONLESS_SECRET_ID"
printf 'CERTIFICATE_NOT_AFTER=%s\n' "$ISSUED_NOT_AFTER"
printf 'CERTIFICATE_SHA256=%s\n' "$ISSUED_FINGERPRINT"
printf 'CONTAINER_APP_ENVIRONMENT_CERTIFICATE_ID=%s\n' "$ENV_CERTIFICATE_ID"
printf 'CUSTOM_DOMAIN_BINDING_REQUIRED=true\n'
