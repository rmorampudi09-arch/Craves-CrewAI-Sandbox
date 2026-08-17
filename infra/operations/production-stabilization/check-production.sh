#!/usr/bin/env bash
set -Eeuo pipefail
set +x

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

for command_name in az curl dig openssl awk sed sort grep; do
  require_command "$command_name"
done

for env_name in \
  SUBSCRIPTION_ID \
  RESOURCE_GROUP \
  CONTAINER_APP_ENVIRONMENT_NAME \
  CUSTOMER_WEB_APP_NAME \
  CUSTOMER_WEB_APP_FQDN \
  CUSTOMER_WEB_STATIC_IP \
  CUSTOMER_WEB_CERTIFICATE_NAME \
  KEY_VAULT_NAME \
  CUSTOMER_APEX_HOSTNAME \
  CUSTOMER_WWW_HOSTNAME \
  CUSTOMER_DOMAIN_VERIFICATION_ID \
  APIM_HOSTNAME \
  ROLLBACK_WWW_HOSTNAME; do
  require_env "$env_name"
done

EXPECTED_APEX_ACME_ZONE="_acme-challenge.${CUSTOMER_APEX_HOSTNAME}"
EXPECTED_WWW_ACME_ZONE="_acme-challenge.${CUSTOMER_WWW_HOSTNAME}"
EXPECTED_CERTIFICATE_ID_SUFFIX="/managedEnvironments/${CONTAINER_APP_ENVIRONMENT_NAME}/certificates/${CUSTOMER_WEB_CERTIFICATE_NAME}"
CERT_MIN_REMAINING_DAYS="${CERT_MIN_REMAINING_DAYS:-30}"

if ! [[ "$CERT_MIN_REMAINING_DAYS" =~ ^[0-9]+$ ]]; then
  fail "CERT_MIN_REMAINING_DAYS must be a non-negative integer."
fi

CERT_MIN_REMAINING_SECONDS=$((CERT_MIN_REMAINING_DAYS * 86400))
RESULT="PASS"
FAILURES=0

record_failure() {
  RESULT="FAIL"
  FAILURES=$((FAILURES + 1))
  printf 'FAILED: %s\n' "$*" >&2
}

record_pass() {
  printf 'PASSED: %s\n' "$*"
}

log "Selecting Azure subscription."
az account set --subscription "$SUBSCRIPTION_ID"

ACTIVE_SUBSCRIPTION_ID="$(
  az account show \
    --query id \
    --output tsv \
    --only-show-errors
)"

if [[ "$ACTIVE_SUBSCRIPTION_ID" != "$SUBSCRIPTION_ID" ]]; then
  fail "Authenticated Azure context is using the wrong subscription."
fi

echo "============================================================"
echo "1. CUSTOMER CONTAINER APP RUNTIME"
echo "============================================================"

APP_STATE="$(
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CUSTOMER_WEB_APP_NAME" \
    --query '{running:properties.runningStatus,latest:properties.latestRevisionName,ready:properties.latestReadyRevisionName,fqdn:properties.configuration.ingress.fqdn}' \
    --output json \
    --only-show-errors
)"

echo "$APP_STATE"

RUNNING_STATUS="$(
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CUSTOMER_WEB_APP_NAME" \
    --query properties.runningStatus \
    --output tsv \
    --only-show-errors
)"

LATEST_REVISION="$(
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CUSTOMER_WEB_APP_NAME" \
    --query properties.latestRevisionName \
    --output tsv \
    --only-show-errors
)"

READY_REVISION="$(
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CUSTOMER_WEB_APP_NAME" \
    --query properties.latestReadyRevisionName \
    --output tsv \
    --only-show-errors
)"

APP_FQDN="$(
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CUSTOMER_WEB_APP_NAME" \
    --query properties.configuration.ingress.fqdn \
    --output tsv \
    --only-show-errors
)"

ENVIRONMENT_IP="$(
  az containerapp env show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
    --query properties.staticIp \
    --output tsv \
    --only-show-errors
)"

VERIFY_ID="$(
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CUSTOMER_WEB_APP_NAME" \
    --query properties.customDomainVerificationId \
    --output tsv \
    --only-show-errors
)"

if [[ "$RUNNING_STATUS" == "Running" ]]; then
  record_pass "Container App running status is Running."
else
  record_failure "Container App running status is ${RUNNING_STATUS:-empty}."
fi

if [[ -n "$LATEST_REVISION" && "$LATEST_REVISION" == "$READY_REVISION" ]]; then
  record_pass "Latest revision equals latest ready revision: $LATEST_REVISION"
else
  record_failure "Latest revision ($LATEST_REVISION) does not equal latest ready revision ($READY_REVISION)."
fi

if [[ "$APP_FQDN" == "$CUSTOMER_WEB_APP_FQDN" ]]; then
  record_pass "Container App FQDN matches expected production hostname."
else
  record_failure "Container App FQDN changed to $APP_FQDN."
fi

if [[ "$ENVIRONMENT_IP" == "$CUSTOMER_WEB_STATIC_IP" ]]; then
  record_pass "Container Apps environment IP is $ENVIRONMENT_IP."
else
  record_failure "Environment IP changed to $ENVIRONMENT_IP."
fi

if [[ "$VERIFY_ID" == "$CUSTOMER_DOMAIN_VERIFICATION_ID" ]]; then
  record_pass "Custom-domain verification ID matches expected value."
else
  record_failure "Custom-domain verification ID changed."
fi

echo ""
echo "============================================================"
echo "2. CUSTOM DOMAINS AND CERTIFICATE BINDING"
echo "============================================================"

CUSTOM_DOMAINS="$(
  az containerapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CUSTOMER_WEB_APP_NAME" \
    --query 'properties.configuration.ingress.customDomains[].{name:name,bindingType:bindingType,certificateId:certificateId}' \
    --output json \
    --only-show-errors
)"

echo "$CUSTOM_DOMAINS"

for HOSTNAME in "$CUSTOMER_APEX_HOSTNAME" "$CUSTOMER_WWW_HOSTNAME"; do
  BINDING_TYPE="$(
    az containerapp show \
      --resource-group "$RESOURCE_GROUP" \
      --name "$CUSTOMER_WEB_APP_NAME" \
      --query "properties.configuration.ingress.customDomains[?name=='${HOSTNAME}'].bindingType | [0]" \
      --output tsv \
      --only-show-errors
  )"

  CERTIFICATE_ID="$(
    az containerapp show \
      --resource-group "$RESOURCE_GROUP" \
      --name "$CUSTOMER_WEB_APP_NAME" \
      --query "properties.configuration.ingress.customDomains[?name=='${HOSTNAME}'].certificateId | [0]" \
      --output tsv \
      --only-show-errors
  )"

  if [[ "$BINDING_TYPE" == "SniEnabled" ]]; then
    record_pass "$HOSTNAME is SNI enabled."
  else
    record_failure "$HOSTNAME binding type is ${BINDING_TYPE:-empty}."
  fi

  if [[ "$CERTIFICATE_ID" == *"$EXPECTED_CERTIFICATE_ID_SUFFIX" ]]; then
    record_pass "$HOSTNAME uses $CUSTOMER_WEB_CERTIFICATE_NAME."
  else
    record_failure "$HOSTNAME is bound to unexpected certificate ID: ${CERTIFICATE_ID:-empty}."
  fi
done

CERT_STATE="$(
  az containerapp env certificate list \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
    --query "[?name=='${CUSTOMER_WEB_CERTIFICATE_NAME}'].properties.provisioningState | [0]" \
    --output tsv \
    --only-show-errors
)"

CERT_VALID="$(
  az containerapp env certificate list \
    --resource-group "$RESOURCE_GROUP" \
    --name "$CONTAINER_APP_ENVIRONMENT_NAME" \
    --query "[?name=='${CUSTOMER_WEB_CERTIFICATE_NAME}'].properties.valid | [0]" \
    --output tsv \
    --only-show-errors
)"

if [[ "$CERT_STATE" == "Succeeded" ]]; then
  record_pass "Container Apps certificate provisioning state is Succeeded."
else
  record_failure "Certificate provisioning state is ${CERT_STATE:-empty}."
fi

if [[ "${CERT_VALID,,}" == "true" ]]; then
  record_pass "Container Apps certificate is valid."
else
  record_failure "Container Apps certificate valid flag is ${CERT_VALID:-empty}."
fi

TEMP_CERT_DIR="$(mktemp -d)"
TEMP_CERT="$TEMP_CERT_DIR/certificate.pem"
trap 'rm -rf "$TEMP_CERT_DIR"' EXIT

az keyvault certificate download \
  --vault-name "$KEY_VAULT_NAME" \
  --name "$CUSTOMER_WEB_CERTIFICATE_NAME" \
  --file "$TEMP_CERT" \
  --encoding PEM \
  --only-show-errors \
  --output none

CERT_SAN="$(openssl x509 -in "$TEMP_CERT" -noout -ext subjectAltName)"
CERT_NOT_AFTER="$(openssl x509 -in "$TEMP_CERT" -noout -enddate | cut -d= -f2-)"

echo "Certificate expiry: $CERT_NOT_AFTER"
echo "$CERT_SAN"

if grep -Fq "DNS:${CUSTOMER_APEX_HOSTNAME}" <<<"$CERT_SAN"; then
  record_pass "Certificate contains ${CUSTOMER_APEX_HOSTNAME}."
else
  record_failure "Certificate SAN is missing ${CUSTOMER_APEX_HOSTNAME}."
fi

if grep -Fq "DNS:${CUSTOMER_WWW_HOSTNAME}" <<<"$CERT_SAN"; then
  record_pass "Certificate contains ${CUSTOMER_WWW_HOSTNAME}."
else
  record_failure "Certificate SAN is missing ${CUSTOMER_WWW_HOSTNAME}."
fi

if openssl x509 \
  -in "$TEMP_CERT" \
  -checkend "$CERT_MIN_REMAINING_SECONDS" \
  -noout >/dev/null 2>&1; then
  record_pass "Certificate has more than ${CERT_MIN_REMAINING_DAYS} days remaining."
else
  record_failure "Certificate has ${CERT_MIN_REMAINING_DAYS} days or less remaining."
fi

echo ""
echo "============================================================"
echo "3. AUTHORITATIVE AND PUBLIC DNS"
echo "============================================================"

AUTH_APEX="$(dig @ns51.domaincontrol.com "$CUSTOMER_APEX_HOSTNAME" A +short | sort -u)"
AUTH_WWW="$(dig @ns51.domaincontrol.com "$CUSTOMER_WWW_HOSTNAME" CNAME +short | sed 's/\.$//' | sort -u)"
GOOGLE_APEX="$(dig @8.8.8.8 "$CUSTOMER_APEX_HOSTNAME" A +short | sort -u)"
GOOGLE_WWW="$(dig @8.8.8.8 "$CUSTOMER_WWW_HOSTNAME" CNAME +short | sed 's/\.$//' | sort -u)"
CLOUDFLARE_APEX="$(dig @1.1.1.1 "$CUSTOMER_APEX_HOSTNAME" A +short | sort -u)"
CLOUDFLARE_WWW="$(dig @1.1.1.1 "$CUSTOMER_WWW_HOSTNAME" CNAME +short | sed 's/\.$//' | sort -u)"

printf 'Authoritative apex: %s\n' "$AUTH_APEX"
printf 'Authoritative www:  %s\n' "$AUTH_WWW"
printf 'Google apex:        %s\n' "$GOOGLE_APEX"
printf 'Google www:         %s\n' "$GOOGLE_WWW"
printf 'Cloudflare apex:    %s\n' "$CLOUDFLARE_APEX"
printf 'Cloudflare www:     %s\n' "$CLOUDFLARE_WWW"

for VALUE in "$AUTH_APEX" "$GOOGLE_APEX" "$CLOUDFLARE_APEX"; do
  if [[ "$VALUE" != "$CUSTOMER_WEB_STATIC_IP" ]]; then
    record_failure "Apex DNS mismatch detected: ${VALUE:-empty}."
  fi
done

for VALUE in "$AUTH_WWW" "$GOOGLE_WWW" "$CLOUDFLARE_WWW"; do
  if [[ "$VALUE" != "$CUSTOMER_WEB_APP_FQDN" ]]; then
    record_failure "WWW CNAME mismatch detected: ${VALUE:-empty}."
  fi
done

if [[ "$AUTH_APEX" == "$CUSTOMER_WEB_STATIC_IP" ]]; then
  record_pass "Apex authoritative DNS points to Container Apps."
fi

if [[ "$AUTH_WWW" == "$CUSTOMER_WEB_APP_FQDN" ]]; then
  record_pass "WWW authoritative DNS points directly to Container App FQDN."
fi

ASUID_APEX="$(dig @8.8.8.8 "asuid.${CUSTOMER_APEX_HOSTNAME}" TXT +short | tr -d '"')"
ASUID_WWW="$(dig @8.8.8.8 "asuid.${CUSTOMER_WWW_HOSTNAME}" TXT +short | tr -d '"')"

if [[ "$ASUID_APEX" == "$CUSTOMER_DOMAIN_VERIFICATION_ID" ]]; then
  record_pass "asuid apex verification record is correct."
else
  record_failure "asuid apex verification record mismatch."
fi

if [[ "$ASUID_WWW" == "$CUSTOMER_DOMAIN_VERIFICATION_ID" ]]; then
  record_pass "asuid www verification record is correct."
else
  record_failure "asuid www verification record mismatch."
fi

for ZONE in "$EXPECTED_APEX_ACME_ZONE" "$EXPECTED_WWW_ACME_ZONE"; do
  NS_COUNT="$(dig "$ZONE" NS +short | sed '/^$/d' | wc -l | tr -d ' ')"

  if [[ "$NS_COUNT" -eq 4 ]]; then
    record_pass "$ZONE has four delegated nameservers."
  else
    record_failure "$ZONE has $NS_COUNT delegated nameservers instead of four."
  fi
done

echo ""
echo "============================================================"
echo "4. LIVE CUSTOMER HTTPS"
echo "============================================================"

for HOSTNAME in "$CUSTOMER_APEX_HOSTNAME" "$CUSTOMER_WWW_HOSTNAME"; do
  CURL_OUTPUT="$(
    curl -sS \
      -L \
      --max-redirs 5 \
      --connect-timeout 15 \
      -o /dev/null \
      -w '%{http_code}|%{ssl_verify_result}|%{remote_ip}|%{url_effective}' \
      "https://${HOSTNAME}/" || true
  )"

  IFS='|' read -r HTTP_STATUS TLS_VERIFY REMOTE_IP FINAL_URL <<<"$CURL_OUTPUT"

  printf '%s -> HTTP %s, TLS %s, remote %s, final %s\n' \
    "$HOSTNAME" \
    "${HTTP_STATUS:-empty}" \
    "${TLS_VERIFY:-empty}" \
    "${REMOTE_IP:-empty}" \
    "${FINAL_URL:-empty}"

  if [[ "$HTTP_STATUS" == "200" ]]; then
    record_pass "$HOSTNAME returns HTTP 200."
  else
    record_failure "$HOSTNAME returned HTTP ${HTTP_STATUS:-empty}."
  fi

  if [[ "$TLS_VERIFY" == "0" ]]; then
    record_pass "$HOSTNAME TLS verification is successful."
  else
    record_failure "$HOSTNAME TLS verification result is ${TLS_VERIFY:-empty}."
  fi

  if [[ "$REMOTE_IP" == "$CUSTOMER_WEB_STATIC_IP" ]]; then
    record_pass "$HOSTNAME is served from expected Container Apps IP."
  else
    record_failure "$HOSTNAME remote IP is ${REMOTE_IP:-empty}."
  fi
done

echo ""
echo "============================================================"
echo "5. APIM TLS AND DNS REGRESSION CHECK"
echo "============================================================"

API_CNAME="$(dig @8.8.8.8 "$APIM_HOSTNAME" CNAME +short | sed 's/\.$//')"
API_CURL="$(
  curl -sS \
    --connect-timeout 15 \
    -o /dev/null \
    -w '%{http_code}|%{ssl_verify_result}|%{remote_ip}' \
    "https://${APIM_HOSTNAME}/" || true
)"

IFS='|' read -r API_HTTP API_TLS API_REMOTE <<<"$API_CURL"

printf 'APIM CNAME: %s\n' "$API_CNAME"
printf 'APIM root: HTTP %s, TLS %s, remote %s\n' \
  "${API_HTTP:-empty}" \
  "${API_TLS:-empty}" \
  "${API_REMOTE:-empty}"

if [[ "$API_CNAME" == "api.craves.in" ]]; then
  record_pass "api.craves.in CNAME is unchanged."
else
  record_failure "api.craves.in CNAME changed to ${API_CNAME:-empty}."
fi

if [[ "$API_TLS" == "0" ]]; then
  record_pass "APIM TLS verification succeeds."
else
  record_failure "APIM TLS verification result is ${API_TLS:-empty}."
fi

if [[ "$API_HTTP" == "404" || "$API_HTTP" == "200" ]]; then
  record_pass "APIM root status is acceptable ($API_HTTP)."
else
  record_failure "Unexpected APIM root HTTP status ${API_HTTP:-empty}."
fi

echo ""
echo "============================================================"
echo "6. ROLLBACK TARGET AVAILABILITY"
echo "============================================================"

ROLLBACK_CURL="$(
  curl -sS \
    --connect-timeout 15 \
    -o /dev/null \
    -w '%{http_code}|%{ssl_verify_result}' \
    "https://${ROLLBACK_WWW_HOSTNAME}/" || true
)"

IFS='|' read -r ROLLBACK_HTTP ROLLBACK_TLS <<<"$ROLLBACK_CURL"

printf '%s -> HTTP %s, TLS %s\n' \
  "$ROLLBACK_WWW_HOSTNAME" \
  "${ROLLBACK_HTTP:-empty}" \
  "${ROLLBACK_TLS:-empty}"

if [[ "$ROLLBACK_TLS" == "0" && "$ROLLBACK_HTTP" =~ ^[234] ]]; then
  record_pass "Legacy Static Web App rollback target is still reachable."
else
  record_failure "Legacy Static Web App rollback target is not healthy enough for rollback."
fi

echo ""
echo "============================================================"
echo "FINAL RESULT: $RESULT"
echo "FAILURES: $FAILURES"
echo "============================================================"

if [[ "$RESULT" != "PASS" ]]; then
  exit 2
fi

printf 'PRODUCTION_STABILIZATION_STATUS=PASS\n'
printf 'CUSTOMER_WEB_LATEST_READY_REVISION=%s\n' "$READY_REVISION"
printf 'CUSTOMER_WEB_CERTIFICATE=%s\n' "$CUSTOMER_WEB_CERTIFICATE_NAME"
printf 'CUSTOMER_WEB_CERTIFICATE_NOT_AFTER=%s\n' "$CERT_NOT_AFTER"
printf 'LEGACY_ROLLBACK_TARGET_AVAILABLE=true\n'