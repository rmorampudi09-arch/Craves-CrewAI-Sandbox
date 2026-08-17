# 2026-08-12 — Craves Azure Front Door / CDN launch-readiness handover

## Goal

Continue the backend/infrastructure journey after the subscription module and make Azure Front Door/CDN production-ready without changing the already-working Spring Boot/APIM/customer-web behavior or moving DNS prematurely.

## Existing state reviewed

The repository already contained two equivalent Front Door bootstrap pipelines:

- `/azure-pipelines-cdn-front-door.yml`
- `/azure-pipelines-front-door-cdn.yml`

The earlier version created a Standard Front Door profile, a Container Apps origin, an application route and a cached `/_next/static/*` route. It did not provide Premium managed WAF rules, bot protection, production custom domains, Key Vault edge TLS integration, security headers, diagnostics or a staged DNS handoff.

The active customer web origin remains:

```text
ca-craves-web-prodlow
```

in:

```text
rg-craves-prodlow-centralindia
```

The existing Craves certificate automation already maintains a SAN certificate for `craves.in` and `www.craves.in` under the versionless Key Vault secret `craves-web-tls` in `kvcravesprodlowl3ing6`.

## Delivered files

### `/infra/frontdoor/production/deploy-front-door.sh`

Canonical idempotent Azure CLI implementation with these actions:

- `discover`
- `deploy`
- `validate`
- `prepareDomains`
- `associateDomains`

Controls include explicit Premium billing confirmation, no automatic DNS change, HTTPS-only origin forwarding, origin certificate-name checks, no application-response caching, immutable static caching, security headers, WAF DRS 2.2, Bot Manager 1.1, Detection/Prevention mode, Log Analytics diagnostics, Key Vault TLS reference and validation artifacts.

### `/azure-pipelines-cdn-front-door.yml`

Canonical manual Azure DevOps entry point using the established `Craves-Dev-Service-Connection`.

### `/azure-pipelines-front-door-cdn.yml`

Backward-compatible entry point kept aligned so an existing Azure DevOps pipeline using the older YAML path does not break.

### `/infra/frontdoor/production/README.md`

Operational instructions, manual intervention, DNS constraints, WAF rollout, certificate flow, origin-protection risk and rollback.

## Deliberate non-changes

This module does not change Spring Boot services, APIM routes, databases, Redis, Firebase, Cashfree, delivery integrations, customer/chef UI or the current production DNS records.

## Safe execution order

1. Run `discover` first.
2. Run `deploy` with `confirmPremium=PROVISION_PREMIUM_AFD` and `wafMode=Detection`.
3. Review the `craves-frontdoor-status` pipeline artifact and WAF logs.
4. Run `prepareDomains` with `confirmDomains=PREPARE_CRAVES_DOMAINS`.
5. If requested, assign `Key Vault Secrets User` on `kvcravesprodlowl3ing6` to the Front Door managed identity and rerun.
6. Add the `_dnsauth` TXT records from the artifact.
7. Run `discover` until both domains report `Approved`.
8. Run `associateDomains` with `confirmAssociate=ASSOCIATE_CRAVES_DOMAINS`.
9. Re-run `deploy` with `wafMode=Prevention` and perform full customer/chef functional smoke tests through Front Door.
10. Only after all gates pass, perform the manual DNS cutover.

## Apex DNS decision

Azure Front Door has no stable public IP. `www.craves.in` can CNAME to the Front Door endpoint, but the apex `craves.in` needs alias/CNAME-flattening support. The parent Craves zone currently remains at GoDaddy while ACME child zones are delegated to Azure DNS.

For a production-grade apex, the preferred design is to migrate the parent zone to Azure DNS and use an Azure DNS alias to Front Door, but only after every M365, ACS, SPF/DMARC, DKIM, ACME delegation and application record has been copied and verified. This is intentionally not automated by the Front Door pipeline.

## Origin protection launch gate

The current generated Container Apps hostname is public. That means traffic can potentially bypass Front Door/WAF by hitting the origin directly. Microsoft now supports Azure Front Door Premium Private Link to Azure Container Apps, but it requires a compatible workload-profiles Container Apps environment and adds Private Link charges.

Therefore the first `discover`/Azure inspection must confirm the current Container Apps environment topology before origin lock-down is implemented. Do not rebuild or convert the environment blindly because that could impact the working customer web deployment.

## Billing warning

Azure Front Door Premium is a paid resource. If the existing profile is Standard, the upgrade is one-way and cannot be downgraded in place. The pipeline requires the explicit `PROVISION_PREMIUM_AFD` confirmation token.

## Rollback

Before DNS cutover, no customer traffic has moved, so Front Door can be corrected without production impact. After DNS cutover, rollback is DNS-first: restore the exact previous apex and `www` values, verify the direct Container App remains healthy, and leave Front Door in place for investigation.
