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
trap 'fail "Certificate automation failed near line $LINENO."' ERR

for command_name in az dig git openssl; do
  require_command "$command_name"
done

for env_name in \
  SUBSCRIPTION_ID \
  RESOURCE_GROUP \
  DNS_ZONE_NAME \
  KEY_VAULT_NAME \
  KEY_VAULT_CERTIFICATE_NAME \
  APIM_NAME \
  APIM_HOSTNAME \
  APIM_DEFAULT_HOSTNAME \
  LETSENCRYPT_CONTACT_EMAIL; do
  require_env "$env_name"
done

ACME_SH_VERSION="${ACME_SH_VERSION:-3.1.4}"
RENEW_BEFORE_DAYS="${RENEW_BEFORE_DAYS:-30}"
DNS_PROPAGATION_SECONDS="${DNS_PROPAGATION_SECONDS:-60}"
FORCE_ISSUE="${FORCE_ISSUE:-false}"

[[ "$RENEW_BEFORE_DAYS" =~ ^[0-9]+$ ]] || fail "RENEW_BEFORE_DAYS must be a non-negative integer."
[[ "$DNS_PROPAGATION_SECONDS" =~ ^[0-9]+$ ]] || fail "DNS_PROPAGATION_SECONDS must be a non-negative integer."
[[ "$APIM_HOSTNAME" == *.* ]] || fail "APIM_HOSTNAME must be a fully qualified domain name."
[[ "$DNS_ZONE_NAME" == "_acme-challenge.${APIM_HOSTNAME}" ]] || \
  fail "DNS_ZONE_NAME must equal _acme-challenge.${APIM_HOSTNAME}."
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

log "Validating Azure resources and DNS delegation."
az network dns zone show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$DNS_ZONE_NAME" \
  --only-show-errors \
  --output none

az keyvault show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$KEY_VAULT_NAME" \
  --only-show-errors \
  --output none

APIM_GATEWAY_URL="$(az apim show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APIM_NAME" \
  --query gatewayUrl \
  --output tsv \
  --only-show-errors)"

[[ "$APIM_GATEWAY_URL" == "https://${APIM_DEFAULT_HOSTNAME}" ]] || \
  fail "APIM default gateway does not match ${APIM_DEFAULT_HOSTNAME}."

PUBLIC_CNAME="$(dig +short CNAME "$APIM_HOSTNAME" | sed 's/\.$//' | tail -n 1)"
[[ "$PUBLIC_CNAME" == "$APIM_DEFAULT_HOSTNAME" ]] || \
  fail "${APIM_HOSTNAME} CNAME does not resolve to ${APIM_DEFAULT_HOSTNAME}."

mapfile -t DELEGATED_NAME_SERVERS < <(dig +short NS "$DNS_ZONE_NAME" | sed 's/\.$//' | sort -u)
[[ "${#DELEGATED_NAME_SERVERS[@]}" -eq 4 ]] || \
  fail "Expected four delegated Azure DNS nameservers for ${DNS_ZONE_NAME}."

for name_server in "${DELEGATED_NAME_SERVERS[@]}"; do
  [[ "$name_server" == *"azure-dns."* || "$name_server" == *"azure-dns.com" || "$name_server" == *"azure-dns.net" || "$name_server" == *"azure-dns.org" || "$name_server" == *"azure-dns.info" ]] || \
    fail "Unexpected delegated nameserver: ${name_server}"
done

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

log "Obtaining an Azure Resource Manager bearer token for the DNS challenge."
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

log "Requesting the Let's Encrypt certificate through Azure DNS DNS-01 validation."
"$ACME_SH" \
  --home "$ACME_HOME" \
  --config-home "$ACME_HOME" \
  --cert-home "$ACME_CERT_HOME" \
  --server letsencrypt \
  --issue \
  --dns dns_azure \
  --domain "$APIM_HOSTNAME" \
  --keylength 2048 \
  --dnssleep "$DNS_PROPAGATION_SECONDS"

log "Copying certificate material into the protected temporary directory."
"$ACME_SH" \
  --home "$ACME_HOME" \
  --config-home "$ACME_HOME" \
  --cert-home "$ACME_CERT_HOME" \
  --install-cert \
  --domain "$APIM_HOSTNAME" \
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

grep -Fq "DNS:${APIM_HOSTNAME}" <<<"$CERTIFICATE_SAN" || \
  fail "Issued certificate does not contain DNS:${APIM_HOSTNAME}."

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
  -name "$APIM_HOSTNAME" \
  -passout env:PFX_PASSWORD

[[ -s "$PFX_FILE" ]] || fail "PKCS#12 package was not created."

log "Importing the certificate as a new version in Azure Key Vault."
az keyvault certificate import \
  --vault-name "$KEY_VAULT_NAME" \
  --name "$KEY_VAULT_CERTIFICATE_NAME" \
  --file "$PFX_FILE" \
  --password "$PFX_PASSWORD" \
  --tags \
    Domain="$APIM_HOSTNAME" \
    Issuer=LetsEncrypt \
    ManagedBy=AzureDevOps \
    Purpose=APIM-TLS \
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

log "Certificate issuance and Key Vault import completed successfully."
printf 'CERTIFICATE_CHANGED=true\n'
printf 'CERTIFICATE_NAME=%s\n' "$KEY_VAULT_CERTIFICATE_NAME"
printf 'CERTIFICATE_ID=%s\n' "$CERTIFICATE_ID"
printf 'VERSIONLESS_SECRET_ID=%s\n' "$VERSIONLESS_SECRET_ID"
printf 'CERTIFICATE_NOT_AFTER=%s\n' "$ISSUED_NOT_AFTER"
printf 'CERTIFICATE_SHA256=%s\n' "$ISSUED_FINGERPRINT"
printf 'APIM_BINDING_REQUIRED=%s\n' "$([[ "$CERTIFICATE_EXISTS" == true ]] && echo false || echo true)"
