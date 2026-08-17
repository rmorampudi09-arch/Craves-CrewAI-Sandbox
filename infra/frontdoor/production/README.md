# Craves Azure Front Door / CDN production module

This module turns the existing Craves customer-web Container App into a production edge origin behind Azure Front Door Premium. It is intentionally traffic-safe: provisioning Front Door does **not** change `craves.in` or `www.craves.in` DNS.

## Existing Craves resources used

- Resource group: `rg-craves-prodlow-centralindia`
- Customer web origin: `ca-craves-web-prodlow`
- Front Door profile: `afd-craves-prodlow`
- Key Vault: `kvcravesprodlowl3ing6`
- Existing rotating SAN certificate secret: `craves-web-tls`
- Azure DevOps service connection: `Craves-Dev-Service-Connection`

Spring Boot services, APIM, PostgreSQL/PostGIS, Redis, Firebase, Cashfree, delivery integrations and the customer/chef UI are not changed by this module.

## Production architecture

```text
Customer
   |
   | HTTPS
   v
Azure Front Door Premium
   |- HTTPS redirect
   |- WAF managed rules
   |    |- Microsoft Default Rule Set 2.2
   |    `- Bot Manager 1.1
   |- security response headers
   |- CDN cache only for /_next/static/*
   `- diagnostic logs -> existing Log Analytics workspace
   |
   | HTTPS only + origin certificate-name validation
   v
ca-craves-web-prodlow
```

## Why Premium

Craves is being prepared for a full-scale launch. Premium is used because Microsoft-managed WAF rules and bot protection are Premium capabilities. Premium is billable. A Standard profile can be upgraded without downtime, but the upgrade cannot be downgraded in place. The pipeline therefore refuses a create/upgrade unless `confirmPremium=PROVISION_PREMIUM_AFD` is supplied.

## Caching safety

Only immutable Next.js assets under `/_next/static/*` are cached. The catch-all `/*` application route has no Front Door cache configuration. This prevents personalized HTML, login/session responses, checkout responses and BFF/API responses from being cached accidentally.

## WAF rollout

Start with `Detection`. This logs rule matches without blocking customers. Review WAF logs and functional smoke tests, then switch to `Prevention` before public DNS cutover. Do not create broad exclusions; any exclusion must be based on an observed false positive and scoped to the exact rule/request field.

## Certificate model

The module uses the existing customer-managed SAN certificate `craves-web-tls` from Key Vault. Front Door gets a system-assigned managed identity and references the versionless Key Vault secret, allowing the existing Craves Let's Encrypt rotation flow to continue supplying newer versions.

The Front Door identity needs this one-time role on `kvcravesprodlowl3ing6`:

```text
Key Vault Secrets User
```

`prepareDomains` stops safely and prints the managed-identity object ID if the role is missing. Do not paste credentials, bearer tokens, private keys or PFX passwords into chat or pipeline variables.

## Pipeline actions

### discover

Read-only. Produces the current origin/Front Door/WAF/domain status artifact. No Azure resource changes.

### deploy

Creates or completes:

- Front Door Premium profile
- Front Door endpoint
- HTTPS Container Apps origin
- app route with no cache
- `/_next/static/*` CDN route with compression/cache
- security-header ruleset
- WAF policy
- DRS 2.2
- Bot Manager 1.1
- WAF security policy
- diagnostic setting to an existing Log Analytics workspace
- runtime edge validation

Required for a billable Premium create/upgrade:

```text
confirmPremium = PROVISION_PREMIUM_AFD
```

Recommended first run:

```text
wafMode = Detection
```

If exactly one Log Analytics workspace exists in the resource group it is auto-detected. If there is more than one, supply `logAnalyticsWorkspaceName`. This module deliberately does not create another paid workspace automatically.

### validate

Checks the Premium tier, no-cache app route, cached static route, endpoint HTTP response, HSTS and `X-Content-Type-Options: nosniff`.

### prepareDomains

Creates Front Door domain resources for `craves.in` and `www.craves.in` and emits the current `_dnsauth` TXT tokens.

Required:

```text
confirmDomains = PREPARE_CRAVES_DOMAINS
```

This does not move production traffic.

### associateDomains

Run only after both Front Door custom domains report `Approved`. It associates both domains with both routes and extends WAF protection to them.

Required:

```text
confirmAssociate = ASSOCIATE_CRAVES_DOMAINS
```

This still does not edit GoDaddy DNS.

## DNS constraint for `craves.in`

Azure Front Door has no stable public IP. Never resolve the `*.azurefd.net` hostname and create an apex A record from that IP.

`www.craves.in` can use a normal CNAME to the generated Front Door hostname.

The apex `craves.in` requires alias/CNAME-flattening support. Azure recommends Azure DNS for apex domains. Craves currently keeps the parent zone at GoDaddy and has already delegated ACME child zones to Azure DNS. Therefore a production apex cutover must either:

1. migrate the parent `craves.in` zone to Azure DNS after recreating and verifying **every** existing record, then use an Azure DNS alias to Front Door; or
2. use a DNS provider that supports apex CNAME flattening.

Do not change nameservers until M365, ACS email, SPF/DMARC, DKIM, all ACME NS delegations and every existing application record are inventoried and recreated.

## Security headers

- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `X-Frame-Options: SAMEORIGIN`
- `Permissions-Policy: camera=(), microphone=(), geolocation=(self)`

A strict CSP is intentionally not invented at the edge because Firebase, Cashfree, analytics and image-source domains must come from the final application configuration. An incorrect CSP could break sign-in or checkout.

## Manual intervention required

- **Billing-sensitive:** explicitly approve Premium provisioning/upgrade in the pipeline.
- Select the intended existing Log Analytics workspace if auto-detection finds multiple.
- Assign `Key Vault Secrets User` to the Front Door managed identity if requested by `prepareDomains`.
- Add the emitted `_dnsauth` TXT records.
- Do not move `@` or `www` until domain validation, WAF review and application smoke tests are complete.
- Before cutover, switch WAF to `Prevention` and validate customer/chef authentication, discovery, cart, checkout, orders and notifications through Front Door.
- Perform final DNS cutover manually.

## Origin-protection risk

Until the Container Apps origin is converted to a private Front Door origin or otherwise locked to Front Door, its generated public hostname remains a possible WAF bypass path. Azure Front Door Premium supports Private Link to Azure Container Apps, but Microsoft requires a compatible workload-profiles Container Apps environment and Private Link adds cost. Treat origin lock-down as a launch gate after the first `discover` run confirms the current Container Apps environment topology; do not blindly rebuild the environment.

## Rollback

Before DNS cutover, no customer traffic has moved. After cutover, restore the exact previous apex and `www` DNS values while leaving Front Door resources available for investigation. Do not delete Front Door custom domains while DNS still points at them.

## Local validation

```bash
bash -n infra/frontdoor/production/deploy-front-door.sh
```
