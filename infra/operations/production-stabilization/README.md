# Craves Production Stabilization Verification

This module verifies the production-low customer website after the `craves.in` and `www.craves.in` migration to Azure Container Apps.

It is intentionally read-only. It does not change DNS, certificates, custom-domain bindings, Container App revisions, APIM configuration, or application settings. It also does not provision Azure Monitor, Application Insights, Log Analytics, action groups, or any other billable Azure resource.

## Scope

The checks cover:

- Azure subscription safety check.
- Customer Container App running state.
- Latest revision equals latest ready revision.
- Container Apps environment static IP remains `4.187.245.188`.
- `craves.in` and `www.craves.in` remain `SniEnabled`.
- Both customer domains remain bound to `craves-web-tls`.
- Container Apps certificate provisioning is `Succeeded` and valid.
- Key Vault certificate SANs include `craves.in` and `www.craves.in`.
- Certificate has more than 30 days remaining.
- GoDaddy authoritative DNS and Google/Cloudflare recursive DNS all match the cutover state.
- `asuid` ownership TXT records remain correct.
- Both ACME validation child zones retain four delegated nameservers.
- Both public customer URLs return HTTP 200 with valid TLS from `4.187.245.188`.
- `api.craves.in` CNAME and APIM TLS remain unchanged.
- The legacy Azure Static Web App remains reachable as a temporary rollback target.

## Files

```text
azure-pipelines-production-stabilization.yml
infra/operations/production-stabilization/
├── README.md
└── check-production.sh
```

## Azure DevOps setup

Create an Azure DevOps YAML pipeline from:

```text
/azure-pipelines-production-stabilization.yml
```

Add this pipeline variable:

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

This is not a secret.

The YAML runs every six hours from `main` and can also be queued manually. The schedule consumes Azure DevOps hosted-agent minutes but does not provision an Azure resource.

## Expected success

A healthy run ends with:

```text
FINAL RESULT: PASS
FAILURES: 0
PRODUCTION_STABILIZATION_STATUS=PASS
LEGACY_ROLLBACK_TARGET_AVAILABLE=true
```

## Failure handling

Do not immediately roll back on a single failed assertion. Use the failed section to classify the issue first:

- Runtime/revision failure: investigate the Container App revision before changing DNS.
- DNS mismatch: compare GoDaddy authoritative DNS with public resolvers. A recursive-only mismatch can be cache propagation.
- TLS or certificate failure: keep custom-domain bindings intact while checking `craves-web-tls` and the renewal pipeline.
- APIM failure: do not change customer website DNS; API routing is a separate component.
- Legacy rollback target failure: this only reduces rollback options; it does not imply the current Container App is unhealthy.

## 48-hour stabilization gate

Keep the legacy Static Web App available until all of the following are true:

1. Customer and chef smoke tests remain successful.
2. At least eight consecutive six-hour stabilization runs pass, covering approximately 48 hours.
3. No unresolved HTTP/TLS/DNS/custom-domain regression is observed.
4. The certificate automation pipeline is healthy.

After the gate passes, the legacy Static Web App can be reviewed for retirement as a separate change. Do not delete it as part of this verification module.

## Cost note

This module creates no paid Azure resources. The scheduled Azure DevOps pipeline consumes build-agent minutes according to the project's existing Azure DevOps entitlement. If continuous Azure Monitor alerts are later required, treat that as a separate billing-sensitive infrastructure change and review cost before provisioning.
