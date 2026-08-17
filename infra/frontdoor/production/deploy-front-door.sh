#!/usr/bin/env bash
set -euo pipefail
set +x

ACTION="${ACTION:-discover}"
RG="${RESOURCE_GROUP:-rg-craves-prodlow-centralindia}"
WEB_APP="${WEB_CONTAINER_APP:-ca-craves-web-prodlow}"
PROFILE="${FRONT_DOOR_PROFILE:-afd-craves-prodlow}"
KV="${KEY_VAULT_NAME:-kvcravesprodlowl3ing6}"
CERT="${CERTIFICATE_SECRET_NAME:-craves-web-tls}"
WAF_MODE="${WAF_MODE:-Detection}"
CONFIRM_PREMIUM="${CONFIRM_PREMIUM:-DO_NOT_PROVISION_PREMIUM}"
CONFIRM_DOMAINS="${CONFIRM_DOMAINS:-DO_NOT_PREPARE_DOMAINS}"
CONFIRM_ASSOCIATE="${CONFIRM_ASSOCIATE:-DO_NOT_ASSOCIATE_DOMAINS}"
LAW_NAME="${LOG_ANALYTICS_WORKSPACE_NAME:-}"
OUT="${ARTIFACT_DIR:-./craves-frontdoor-status}"
mkdir -p "$OUT"

fail(){ echo "ERROR: $*" >&2; exit 1; }
info(){ echo "INFO: $*"; }
case "$ACTION" in discover|deploy|validate|prepareDomains|associateDomains) ;; *) fail "Unsupported action $ACTION";; esac
case "$WAF_MODE" in Detection|Prevention) ;; *) fail "WAF_MODE must be Detection or Prevention";; esac
for c in az jq curl; do command -v "$c" >/dev/null || fail "$c is required"; done

az account show >/dev/null
az extension add -n cdn --upgrade --yes --only-show-errors >/dev/null
az extension add -n front-door --upgrade --yes --only-show-errors >/dev/null || true

SUB="$(az account show --query id -o tsv)"
SUB_NAME="$(az account show --query name -o tsv)"
SUFFIX="$(tr -d '-' <<<"$SUB" | cut -c1-8 | tr '[:upper:]' '[:lower:]')"
ENDPOINT="craves-prodlow-$SUFFIX"
ORIGIN_GROUP="craves-web-origin-group"
ORIGIN="craves-web-origin"
APP_ROUTE="craves-web-route"
STATIC_ROUTE="craves-static-route"
RULESET="cravessecurityheaders"
RULE_NAME="securityheaders"
STATIC_RULESET="cravesstaticidentity"
STATIC_RULE_NAME="forceidentityencoding"
WAF="craveswaf$SUFFIX"
SECURITY_POLICY="craves-web-security"
AFD_SECRET="craves-web-tls"
APEX_RES="craves-in"
WWW_RES="www-craves-in"

WEB_JSON="$(az containerapp show -g "$RG" -n "$WEB_APP" -o json)"
ORIGIN_FQDN="$(jq -r '.properties.configuration.ingress.fqdn // empty' <<<"$WEB_JSON")"
EXTERNAL="$(jq -r '.properties.configuration.ingress.external // false' <<<"$WEB_JSON")"
RUNNING="$(jq -r '.properties.runningStatus // "Unknown"' <<<"$WEB_JSON")"
[[ -n "$ORIGIN_FQDN" ]] || fail "$WEB_APP has no ingress FQDN"
[[ "$EXTERNAL" == true ]] || fail "$WEB_APP external ingress is not enabled"

profile_exists(){ az afd profile show -g "$RG" --profile-name "$PROFILE" >/dev/null 2>&1; }
endpoint_host(){ az afd endpoint show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --query hostName -o tsv; }

ensure_profile(){
  if ! profile_exists; then
    [[ "$CONFIRM_PREMIUM" == PROVISION_PREMIUM_AFD ]] || fail "Premium Front Door is billable. Use confirmPremium=PROVISION_PREMIUM_AFD."
    az afd profile create -g "$RG" --profile-name "$PROFILE" --sku Premium_AzureFrontDoor --only-show-errors >/dev/null
  fi
  local sku id uri
  sku="$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query sku.name -o tsv)"
  if [[ "$sku" == Standard_AzureFrontDoor ]]; then
    [[ "$CONFIRM_PREMIUM" == PROVISION_PREMIUM_AFD ]] || fail "Standard to Premium is billable and one-way. Use confirmPremium=PROVISION_PREMIUM_AFD."
    id="$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query id -o tsv)"
    uri="https://management.azure.com${id}/upgrade?api-version=2025-04-15"
    az rest --method post --uri "$uri" --body '{}' --only-show-errors >/dev/null
    for _ in $(seq 1 80); do
      sku="$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query sku.name -o tsv 2>/dev/null || true)"
      [[ "$sku" == Premium_AzureFrontDoor ]] && break
      sleep 15
    done
  fi
  [[ "$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query sku.name -o tsv)" == Premium_AzureFrontDoor ]] || fail "Premium upgrade did not complete"
}

ensure_headers(){
  az afd rule-set show -g "$RG" --profile-name "$PROFILE" --rule-set-name "$RULESET" >/dev/null 2>&1 || az afd rule-set create -g "$RG" --profile-name "$PROFILE" --rule-set-name "$RULESET" --only-show-errors >/dev/null

  # Azure CLI 2.88.0 moved Front Door rule actions to a new structured
  # argument parser. Complex header values containing spaces, commas, and
  # semicolons are not handled reliably by that shorthand parser. Use the
  # documented 2025-04-15 ARM rule PUT so the payload is valid JSON and the
  # operation remains idempotent for both create and update.
  local rule_uri rule_body
  rule_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/ruleSets/${RULESET}/rules/${RULE_NAME}?api-version=2025-04-15"
  rule_body="$(jq -nc '{
    properties: {
      order: 1,
      conditions: [],
      actions: [
        {
          name: "ModifyResponseHeader",
          parameters: {
            headerAction: "Overwrite",
            headerName: "Strict-Transport-Security",
            typeName: "DeliveryRuleHeaderActionParameters",
            value: "max-age=31536000; includeSubDomains"
          }
        },
        {
          name: "ModifyResponseHeader",
          parameters: {
            headerAction: "Overwrite",
            headerName: "X-Content-Type-Options",
            typeName: "DeliveryRuleHeaderActionParameters",
            value: "nosniff"
          }
        },
        {
          name: "ModifyResponseHeader",
          parameters: {
            headerAction: "Overwrite",
            headerName: "Referrer-Policy",
            typeName: "DeliveryRuleHeaderActionParameters",
            value: "strict-origin-when-cross-origin"
          }
        },
        {
          name: "ModifyResponseHeader",
          parameters: {
            headerAction: "Overwrite",
            headerName: "X-Frame-Options",
            typeName: "DeliveryRuleHeaderActionParameters",
            value: "SAMEORIGIN"
          }
        },
        {
          name: "ModifyResponseHeader",
          parameters: {
            headerAction: "Overwrite",
            headerName: "Permissions-Policy",
            typeName: "DeliveryRuleHeaderActionParameters",
            value: "camera=(), microphone=(), geolocation=(self)"
          }
        }
      ],
      matchProcessingBehavior: "Continue"
    }
  }')"
  az rest \
    --method put \
    --uri "$rule_uri" \
    --headers Content-Type=application/json \
    --body "$rule_body" \
    --only-show-errors >/dev/null
}

ensure_static_identity_encoding(){
  # The customer-web origin stalls when a browser advertises gzip for immutable
  # Next.js assets. Normalize only the static-route origin request to identity;
  # Front Door still caches the response globally and dynamic traffic is
  # untouched.
  az afd rule-set show -g "$RG" --profile-name "$PROFILE" --rule-set-name "$STATIC_RULESET" >/dev/null 2>&1 || az afd rule-set create -g "$RG" --profile-name "$PROFILE" --rule-set-name "$STATIC_RULESET" --only-show-errors >/dev/null

  local rule_uri rule_body old_rule_uri
  rule_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/ruleSets/${STATIC_RULESET}/rules/${STATIC_RULE_NAME}?api-version=2025-04-15"
  rule_body="$(jq -nc '{
    properties: {
      order: 1,
      conditions: [],
      actions: [
        {
          name: "ModifyRequestHeader",
          parameters: {
            headerAction: "Overwrite",
            headerName: "Accept-Encoding",
            typeName: "DeliveryRuleHeaderActionParameters",
            value: "identity"
          }
        }
      ],
      matchProcessingBehavior: "Continue"
    }
  }')"
  az rest --method put --uri "$rule_uri" --headers Content-Type=application/json --body "$rule_body" --only-show-errors >/dev/null

  old_rule_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/ruleSets/${RULESET}/rules/gzipfallback?api-version=2025-04-15"
  if az rest --method get --uri "$old_rule_uri" --only-show-errors >/dev/null 2>&1; then
    az rest --method delete --uri "$old_rule_uri" --only-show-errors >/dev/null
  fi
}

purge_static_assets(){
  local purge_uri purge_body
  purge_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/afdEndpoints/${ENDPOINT}/purge?api-version=2025-04-15"
  purge_body="$(jq -nc '{contentPaths:["/_next/static/*"]}')"
  az rest --method post --uri "$purge_uri" --headers Content-Type=application/json --body "$purge_body" --only-show-errors >/dev/null
}

ensure_edge(){
  az afd endpoint show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" >/dev/null 2>&1 || az afd endpoint create -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --enabled-state Enabled --only-show-errors >/dev/null
  az afd origin-group show -g "$RG" --profile-name "$PROFILE" --origin-group-name "$ORIGIN_GROUP" >/dev/null 2>&1 || az afd origin-group create -g "$RG" --profile-name "$PROFILE" --origin-group-name "$ORIGIN_GROUP" --probe-request-type HEAD --probe-protocol Https --probe-path / --probe-interval-in-seconds 120 --sample-size 4 --successful-samples-required 3 --additional-latency-in-milliseconds 50 --only-show-errors >/dev/null
  if az afd origin show -g "$RG" --profile-name "$PROFILE" --origin-group-name "$ORIGIN_GROUP" --origin-name "$ORIGIN" >/dev/null 2>&1; then
    az afd origin update -g "$RG" --profile-name "$PROFILE" --origin-group-name "$ORIGIN_GROUP" --origin-name "$ORIGIN" --host-name "$ORIGIN_FQDN" --origin-host-header "$ORIGIN_FQDN" --priority 1 --weight 1000 --enabled-state Enabled --http-port 80 --https-port 443 --enforce-certificate-name-check true --only-show-errors >/dev/null
  else
    az afd origin create -g "$RG" --profile-name "$PROFILE" --origin-group-name "$ORIGIN_GROUP" --origin-name "$ORIGIN" --host-name "$ORIGIN_FQDN" --origin-host-header "$ORIGIN_FQDN" --priority 1 --weight 1000 --enabled-state Enabled --http-port 80 --https-port 443 --enforce-certificate-name-check true --only-show-errors >/dev/null
  fi
  ensure_headers
  ensure_static_identity_encoding
  local ogid rsid static_rsid rs static_rs cache
  ogid="$(az afd origin-group show -g "$RG" --profile-name "$PROFILE" --origin-group-name "$ORIGIN_GROUP" --query id -o tsv)"
  rsid="$(az afd rule-set show -g "$RG" --profile-name "$PROFILE" --rule-set-name "$RULESET" --query id -o tsv)"
  static_rsid="$(az afd rule-set show -g "$RG" --profile-name "$PROFILE" --rule-set-name "$STATIC_RULESET" --query id -o tsv)"
  rs="[{id:${rsid}}]"
  static_rs="[{id:${rsid}},{id:${static_rsid}}]"
  if az afd route show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$APP_ROUTE" >/dev/null 2>&1; then
    az afd route update -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$APP_ROUTE" --origin-group "$ogid" --patterns-to-match '/*' --supported-protocols Http Https --forwarding-protocol HttpsOnly --https-redirect Enabled --link-to-default-domain Enabled --formatted-rule-sets "$rs" --enabled-state Enabled --only-show-errors >/dev/null
  else
    az afd route create -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$APP_ROUTE" --origin-group "$ogid" --patterns-to-match '/*' --supported-protocols Http Https --forwarding-protocol HttpsOnly --https-redirect Enabled --link-to-default-domain Enabled --formatted-rule-sets "$rs" --enabled-state Enabled --only-show-errors >/dev/null
  fi
  # Cache immutable Next.js build assets, but do not ask Front Door to
  # generate compressed representations. The origin already provides safe
  # HTTP compression, and disabling edge compression prevents cold clients
  # from stalling on JavaScript/CSS bundles.
  cache='{compression-settings:{content-types-to-compress:[text/css,text/javascript,application/javascript,application/json,image/svg+xml,font/woff2,font/woff],is-compression-enabled:false},query-string-caching-behavior:IgnoreQueryString}'
  if az afd route show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$STATIC_ROUTE" >/dev/null 2>&1; then
    az afd route update -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$STATIC_ROUTE" --origin-group "$ogid" --patterns-to-match '/_next/static/*' --supported-protocols Http Https --forwarding-protocol HttpsOnly --https-redirect Enabled --link-to-default-domain Enabled --formatted-rule-sets "$static_rs" --cache-configuration "$cache" --enabled-state Enabled --only-show-errors >/dev/null
  else
    az afd route create -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$STATIC_ROUTE" --origin-group "$ogid" --patterns-to-match '/_next/static/*' --supported-protocols Http Https --forwarding-protocol HttpsOnly --https-redirect Enabled --link-to-default-domain Enabled --formatted-rule-sets "$static_rs" --cache-configuration "$cache" --enabled-state Enabled --only-show-errors >/dev/null
  fi
}

ensure_waf(){
  az network front-door waf-policy show -g "$RG" -n "$WAF" >/dev/null 2>&1 || az network front-door waf-policy create -g "$RG" -n "$WAF" --sku Premium_AzureFrontDoor --disabled false --mode "$WAF_MODE" --only-show-errors >/dev/null
  az network front-door waf-policy update -g "$RG" -n "$WAF" --disabled false --mode "$WAF_MODE" --only-show-errors >/dev/null
  local raw sets
  raw="$(az network front-door waf-policy managed-rules list -g "$RG" --policy-name "$WAF" -o json 2>/dev/null || printf '[]')"
  sets="$(jq -c 'if type=="array" then . elif (.managedRuleSets?|type)=="array" then .managedRuleSets else [] end' <<<"$raw")"
  if ! jq -e '.[]? | select((.ruleSetType//.type)=="Microsoft_DefaultRuleSet" and (.ruleSetVersion//.version)=="2.2")' <<<"$sets" >/dev/null; then
    jq -e '.[]? | select((.ruleSetType//.type)=="Microsoft_DefaultRuleSet")' <<<"$sets" >/dev/null && fail "Existing WAF uses another Default Rule Set; review before changing versions"
    az network front-door waf-policy managed-rules add -g "$RG" --policy-name "$WAF" --type Microsoft_DefaultRuleSet --version 2.2 --action Block --only-show-errors >/dev/null
  fi
  if ! jq -e '.[]? | select((.ruleSetType//.type)=="Microsoft_BotManagerRuleSet")' <<<"$sets" >/dev/null; then
    az network front-door waf-policy managed-rules add -g "$RG" --policy-name "$WAF" --type Microsoft_BotManagerRuleSet --version 1.1 --only-show-errors >/dev/null
  fi
  local ep wafid domains security_policy_uri security_policy_body
  ep="$(az afd endpoint show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --query id -o tsv)"
  wafid="$(az network front-door waf-policy show -g "$RG" -n "$WAF" --query id -o tsv)"
  # Re-running the deploy action after custom-domain cutover must not remove
  # those domains from the WAF security policy. Include every successfully
  # provisioned custom domain together with the default endpoint.
  domains="$(
    az afd custom-domain list -g "$RG" --profile-name "$PROFILE" -o json |
      jq -c --arg endpoint "$ep" '
        [{id: $endpoint}] +
        [
          .[] |
          select((.provisioningState // .properties.provisioningState // "") == "Succeeded") |
          {id: .id}
        ] |
        unique_by(.id)
      '
  )"

  # Azure CLI 2.88.0 removed the previous --domains and --waf-policy
  # parameters from the security-policy create/update commands. The documented
  # 2025-04-15 PUT is idempotent and works for both initial creation and
  # subsequent association updates.
  security_policy_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/securityPolicies/${SECURITY_POLICY}?api-version=2025-04-15"
  security_policy_body="$(jq -nc --arg waf "$wafid" --argjson domains "$domains" '{
    properties: {
      parameters: {
        type: "WebApplicationFirewall",
        wafPolicy: {id: $waf},
        associations: [
          {
            domains: $domains,
            patternsToMatch: ["/*"]
          }
        ]
      }
    }
  }')"
  az rest \
    --method put \
    --uri "$security_policy_uri" \
    --headers Content-Type=application/json \
    --body "$security_policy_body" \
    --only-show-errors >/dev/null
}

ensure_diagnostics(){
  local wid count pid
  if [[ -n "$LAW_NAME" ]]; then
    wid="$(az monitor log-analytics workspace show -g "$RG" -n "$LAW_NAME" --query id -o tsv)"
  else
    count="$(az monitor log-analytics workspace list -g "$RG" --query 'length(@)' -o tsv 2>/dev/null || echo 0)"
    [[ "$count" == 1 ]] || { echo "WARNING: expected one Log Analytics workspace in $RG; found $count. Diagnostics not configured."; return; }
    wid="$(az monitor log-analytics workspace list -g "$RG" --query '[0].id' -o tsv)"
  fi
  pid="$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query id -o tsv)"
  az monitor diagnostic-settings create --name craves-frontdoor-diagnostics --resource "$pid" --workspace "$wid" --logs '[{"categoryGroup":"allLogs","enabled":true}]' --metrics '[{"category":"AllMetrics","enabled":true}]' --only-show-errors >/dev/null
}

prepare_domains(){
  [[ "$CONFIRM_DOMAINS" == PREPARE_CRAVES_DOMAINS ]] || fail "Use confirmDomains=PREPARE_CRAVES_DOMAINS"
  az afd profile identity assign -g "$RG" --profile-name "$PROFILE" --system-assigned --only-show-errors >/dev/null
  local principal kvid source access secret_uri secret_body secret_state
  principal="$(az afd profile identity show -g "$RG" --profile-name "$PROFILE" --query principalId -o tsv)"
  kvid="$(az keyvault show -g "$RG" -n "$KV" --query id -o tsv)"
  source="$kvid/secrets/$CERT"
  access="$(az role assignment list --assignee-object-id "$principal" --scope "$kvid" --query "[?roleDefinitionName=='Key Vault Secrets User'] | length(@)" -o tsv 2>/dev/null || echo 0)"
  [[ "$access" != 0 ]] || fail "Assign Key Vault Secrets User on $KV to Front Door identity object ID $principal, then rerun"
  # Azure CLI 2.88.0 replaced the prior --secret-source and
  # --use-latest-version flags with a fragile structured --parameters input.
  # Use the documented 2025-04-15 ARM Secret PUT so the Key Vault reference
  # stays versionless and Front Door automatically follows the latest version.
  secret_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/secrets/${AFD_SECRET}?api-version=2025-04-15"
  secret_body="$(jq -nc --arg source "$source" '{
    properties: {
      parameters: {
        type: "CustomerCertificate",
        secretSource: {id: $source},
        useLatestVersion: true
      }
    }
  }')"
  az rest \
    --method put \
    --uri "$secret_uri" \
    --headers Content-Type=application/json \
    --body "$secret_body" \
    --only-show-errors >/dev/null

  # az rest returns after an accepted asynchronous PUT. Wait for the secret
  # before creating domains that reference it.
  secret_state=''
  for _ in $(seq 1 80); do
    secret_state="$(az afd secret show -g "$RG" --profile-name "$PROFILE" --secret-name "$AFD_SECRET" --query provisioningState -o tsv 2>/dev/null || true)"
    [[ "$secret_state" == Succeeded ]] && break
    [[ "$secret_state" == Failed ]] && fail "Front Door secret provisioning failed"
    sleep 15
  done
  [[ "$secret_state" == Succeeded ]] || fail "Front Door secret provisioning did not complete"
  # Submit both independent domain writes without serially occupying the
  # pipeline agent for each Azure long-running operation, then bound the
  # combined wait and surface a precise failure state.
  local apex_state www_state
  if ! az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$APEX_RES" >/dev/null 2>&1; then
    az afd custom-domain create -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$APEX_RES" --host-name craves.in --minimum-tls-version TLS12 --certificate-type CustomerCertificate --secret "$AFD_SECRET" --no-wait --only-show-errors >/dev/null
  fi
  if ! az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$WWW_RES" >/dev/null 2>&1; then
    az afd custom-domain create -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$WWW_RES" --host-name www.craves.in --minimum-tls-version TLS12 --certificate-type CustomerCertificate --secret "$AFD_SECRET" --no-wait --only-show-errors >/dev/null
  fi

  apex_state=''
  www_state=''
  for _ in $(seq 1 120); do
    apex_state="$(az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$APEX_RES" --query provisioningState -o tsv 2>/dev/null || true)"
    www_state="$(az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$WWW_RES" --query provisioningState -o tsv 2>/dev/null || true)"
    [[ "$apex_state" == Failed ]] && fail "Apex Front Door custom-domain provisioning failed"
    [[ "$www_state" == Failed ]] && fail "WWW Front Door custom-domain provisioning failed"
    [[ "$apex_state" == Succeeded && "$www_state" == Succeeded ]] && break
    sleep 15
  done
  [[ "$apex_state" == Succeeded && "$www_state" == Succeeded ]] || fail "Front Door custom-domain provisioning did not complete; apex=$apex_state www=$www_state"
}

write_dns(){
  local at wt host
  at="$(az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$APEX_RES" --query validationProperties.validationToken -o tsv 2>/dev/null || true)"
  wt="$(az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$WWW_RES" --query validationProperties.validationToken -o tsv 2>/dev/null || true)"
  host="$(endpoint_host 2>/dev/null || true)"
  cat >"$OUT/craves-frontdoor-dns-required.md" <<DNS
# Craves Front Door DNS handoff

Do not change current traffic records until validation and WAF testing finish.

- TXT `_dnsauth` = `$at`
- TXT `_dnsauth.www` = `$wt`
- Final `www` CNAME target = `$host`
- Apex `craves.in`: do not create an A record from a resolved Front Door IP. Use Azure DNS alias or a DNS provider with apex CNAME flattening.
DNS
}

associate_domains(){
  [[ "$CONFIRM_ASSOCIATE" == ASSOCIATE_CRAVES_DOMAINS ]] || fail "Use confirmAssociate=ASSOCIATE_CRAVES_DOMAINS"
  local aj wj as ws aid wid cd ep wafid security_policy_uri security_policy_body
  aj="$(az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$APEX_RES" -o json)"
  wj="$(az afd custom-domain show -g "$RG" --profile-name "$PROFILE" --custom-domain-name "$WWW_RES" -o json)"
  as="$(jq -r '.domainValidationState // .properties.domainValidationState // empty' <<<"$aj")"; ws="$(jq -r '.domainValidationState // .properties.domainValidationState // empty' <<<"$wj")"
  [[ "$as" == Approved && "$ws" == Approved ]] || fail "Domains must both be Approved; apex=$as www=$ws"
  aid="$(jq -r .id <<<"$aj")"; wid="$(jq -r .id <<<"$wj")"; cd="[{id:${aid}},{id:${wid}}]"
  az afd route update -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$APP_ROUTE" --formatted-custom-domains "$cd" --link-to-default-domain Enabled --only-show-errors >/dev/null
  az afd route update -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$STATIC_ROUTE" --formatted-custom-domains "$cd" --link-to-default-domain Enabled --only-show-errors >/dev/null
  ep="$(az afd endpoint show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --query id -o tsv)"
  wafid="$(az network front-door waf-policy show -g "$RG" -n "$WAF" --query id -o tsv)"
  security_policy_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/securityPolicies/${SECURITY_POLICY}?api-version=2025-04-15"
  security_policy_body="$(jq -nc \
    --arg waf "$wafid" \
    --arg endpoint "$ep" \
    --arg apex "$aid" \
    --arg www "$wid" \
    '{
      properties: {
        parameters: {
          type: "WebApplicationFirewall",
          wafPolicy: {id: $waf},
          associations: [
            {
              domains: [{id: $endpoint}, {id: $apex}, {id: $www}],
              patternsToMatch: ["/*"]
            }
          ]
        }
      }
    }')"
  az rest \
    --method put \
    --uri "$security_policy_uri" \
    --headers Content-Type=application/json \
    --body "$security_policy_body" \
    --only-show-errors >/dev/null
}

validate_edge(){
  profile_exists || fail "Front Door not deployed"
  [[ "$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query sku.name -o tsv)" == Premium_AzureFrontDoor ]] || fail "Front Door is not Premium"
  local app_cache static_cache
  app_cache="$(az afd route show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$APP_ROUTE" --query cacheConfiguration -o json)"
  static_cache="$(az afd route show -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" --route-name "$STATIC_ROUTE" --query cacheConfiguration -o json)"
  # Azure CLI 2.88.0 emits an empty string (instead of JSON null) when the
  # route has no cache configuration. Normalize it before semantic validation.
  [[ -n "$app_cache" ]] || app_cache='null'
  info "App route cache configuration: $app_cache"
  info "Static route cache configuration: $static_cache"

  # Azure CLI 2.88.0 can serialize a disabled cache configuration as either
  # null or an object whose effective settings are empty/disabled. Validate
  # the effective behavior so a dynamic route is never accepted as cached.
  jq -e '
    . == null or
    (
      (type == "object") and
      ((.queryStringCachingBehavior // null) == null) and
      ((.compressionSettings.isCompressionEnabled // false) == false)
    )
  ' <<<"$app_cache" >/dev/null || fail "App route must not be cached: $app_cache"

  jq -e '
    (type == "object") and
    (.queryStringCachingBehavior == "IgnoreQueryString") and
    (.compressionSettings.isCompressionEnabled == false)
  ' <<<"$static_cache" >/dev/null || fail "Static route caching must remain enabled with Front Door compression disabled: $static_cache"

  local static_rule_uri
  static_rule_uri="https://management.azure.com/subscriptions/${SUB}/resourceGroups/${RG}/providers/Microsoft.Cdn/profiles/${PROFILE}/ruleSets/${STATIC_RULESET}/rules/${STATIC_RULE_NAME}?api-version=2025-04-15"
  az rest --method get --uri "$static_rule_uri" --only-show-errors |
    jq -e '.properties.actions[] | select(.name == "ModifyRequestHeader" and .parameters.headerName == "Accept-Encoding" and .parameters.headerAction == "Overwrite" and .parameters.value == "identity")' >/dev/null ||
    fail "Static route must normalize Accept-Encoding to identity"

  local host code headers
  host="$(endpoint_host)"; headers="$OUT/frontdoor-home-headers.txt"; code=''
  for _ in $(seq 1 80); do code="$(curl -sS -D "$headers" -o /dev/null -w '%{http_code}' --max-time 30 "https://$host/" || true)"; [[ "$code" =~ ^(200|301|302|307|308)$ ]] && break; sleep 15; done
  [[ "$code" =~ ^(200|301|302|307|308)$ ]] || fail "Endpoint HTTP validation failed: $code"
  grep -qi '^strict-transport-security:' "$headers" || fail "HSTS header missing"
  grep -qi '^x-content-type-options:[[:space:]]*nosniff' "$headers" || fail "nosniff header missing"
  info "Edge validation passed: https://$host/ HTTP=$code"
}

write_status(){
  local exists=false sku='' state='NotDeployed' host='' routes='[]' domains='[]' wafmode='NotDeployed'
  if profile_exists; then
    exists=true; sku="$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query sku.name -o tsv)"; state="$(az afd profile show -g "$RG" --profile-name "$PROFILE" --query provisioningState -o tsv)"; host="$(endpoint_host 2>/dev/null || true)"
    routes="$(az afd route list -g "$RG" --profile-name "$PROFILE" --endpoint-name "$ENDPOINT" -o json 2>/dev/null || echo '[]')"; domains="$(az afd custom-domain list -g "$RG" --profile-name "$PROFILE" -o json 2>/dev/null || echo '[]')"
  fi
  az network front-door waf-policy show -g "$RG" -n "$WAF" >/dev/null 2>&1 && wafmode="$(az network front-door waf-policy show -g "$RG" -n "$WAF" --query policySettings.mode -o tsv)"
  jq -n --arg action "$ACTION" --arg sub "$SUB_NAME" --arg rg "$RG" --arg origin "$ORIGIN_FQDN" --arg running "$RUNNING" --argjson exists "$exists" --arg profile "$PROFILE" --arg sku "$sku" --arg state "$state" --arg host "$host" --arg waf "$WAF" --arg wafmode "$wafmode" --argjson routes "$routes" --argjson domains "$domains" '{schemaVersion:2,action:$action,subscription:$sub,resourceGroup:$rg,origin:{fqdn:$origin,runningStatus:$running},frontDoor:{exists:$exists,profile:$profile,sku:$sku,state:$state,endpointHost:$host,routes:$routes,customDomains:$domains},waf:{name:$waf,mode:$wafmode}}' >"$OUT/craves-frontdoor-status.json"
  jq . "$OUT/craves-frontdoor-status.json"
}

case "$ACTION" in
  deploy) ensure_profile; ensure_edge; ensure_waf; ensure_diagnostics; validate_edge; purge_static_assets ;;
  validate) validate_edge ;;
  prepareDomains) profile_exists || fail "Deploy first"; prepare_domains; write_dns ;;
  associateDomains) profile_exists || fail "Deploy first"; associate_domains; write_dns; validate_edge ;;
  discover) ;;
esac
write_status
