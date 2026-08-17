# Craves Multi-Provider Sandbox Baseline — 16 August 2026

## Purpose

Prepare the Craves delivery platform for multiple provider sandboxes without allowing unimplemented provider integrations to receive customer delivery traffic.

## Source-of-truth provider plan

The delivery blueprint defines the core provider sequence as:

1. Shadowfax — primary target once vendor sandbox credentials/API contract are issued.
2. Borzo — implemented and currently proven in full sandbox.
3. Porter — tertiary/capacity provider after Enterprise API onboarding.
4. Shiprocket Quick — supplementary provider, held until API/attribution requirements are confirmed.

Delhivery was added later as an additional guarded provider option.

## Current executable provider state

### Borzo

Status: FULL_SANDBOX / executable.

Repository proof includes provider adapter, Spring runtime configuration, signed webhook handling, delivery-command integration, tracking/reconciliation and safety tests.

### Shadowfax

Status: SIMPLE_SANDBOX only.

Pipeline:

```text
azure-pipelines-shadowfax-environment.yml
```

Safe parameters:

```text
targetEnvironment=SANDBOX
enableProvider=false
confirmProductionActivation=false
```

Full sandbox is intentionally blocked until a real Java adapter, Spring runtime config, vendor-approved HTTPS endpoint and secret-backed credential exist.

### Porter

Status: SIMPLE_SANDBOX only.

Pipeline:

```text
azure-pipelines-porter-environment.yml
```

Safe parameters:

```text
targetEnvironment=SANDBOX
enableProvider=false
confirmProductionActivation=false
```

Full sandbox is intentionally blocked until a real Java adapter, Spring runtime config, vendor-issued API endpoint and secret-backed credential exist.

### Shiprocket Quick

Status: SIMPLE_SANDBOX only.

Pipeline:

```text
azure-pipelines-shiprocket-production-activation.yml
```

Safe parameters:

```text
targetEnvironment=SANDBOX
enableProvider=false
confirmProductionActivation=false
```

The pipeline now validates that an actual Java adapter directory exists before allowing `enableProvider=true`; checking only environment-variable names is not sufficient.

### Delhivery

Status: SIMPLE_SANDBOX / guarded optional provider.

Pipeline:

```text
azure-pipelines-delhivery-environment.yml
```

Safe parameters remain:

```text
targetEnvironment=SANDBOX
enableProvider=false
confirmProductionSwitch=false
```

## Expected Azure runtime matrix after safe pipeline runs

```text
BORZO_API_ENVIRONMENT=SANDBOX
BORZO_API_ENABLED=true

SHADOWFAX_API_ENVIRONMENT=SANDBOX
SHADOWFAX_API_ENABLED=false

PORTER_API_ENVIRONMENT=SANDBOX
PORTER_API_ENABLED=false

SHIPROCKET_API_ENVIRONMENT=SANDBOX
SHIPROCKET_API_ENABLED=false

DELHIVERY_API_ENVIRONMENT=SANDBOX
DELHIVERY_API_ENABLED=false

CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
CRAVES_DELIVERY_COMMAND_ENABLED=true
```

The last two flags remain true because Borzo full sandbox is already the executable delivery path.

## Important interpretation

`SIMPLE_SANDBOX` means the provider is explicitly sandbox-scoped and fail-closed. It does not mean Craves sends test API calls to that provider.

Do not create fake quotes, fake agents or fake provider responses merely to make the intelligent router show multiple candidates.

Until at least one additional real adapter is implemented and enabled in FULL_SANDBOX, the intelligent assignment candidate pool will continue to contain Borzo as the only executable provider.

## Files changed

```text
azure-pipelines-shadowfax-environment.yml
azure-pipelines-porter-environment.yml
azure-pipelines-shiprocket-production-activation.yml
azure-pipelines-delivery-provider-production-ci.yml
services/integration-service/modules/delivery-provider-production/README.md
docs/handover/2026-08-16-multi-provider-sandbox-baseline.md
```

## Azure DevOps portal work required

Create Azure DevOps pipeline definitions for the two newly added YAML files if they do not yet appear in the pipeline list:

```text
azure-pipelines-shadowfax-environment.yml
azure-pipelines-porter-environment.yml
```

Then run the safety CI and the three core-provider simple-sandbox switches in this order:

```text
1. azure-pipelines-delivery-provider-production-ci.yml
2. azure-pipelines-shadowfax-environment.yml
3. azure-pipelines-porter-environment.yml
4. azure-pipelines-shiprocket-production-activation.yml
```

Do not rerun Borzo activation solely for this baseline; Borzo is already in proven full sandbox. Delhivery may remain in its existing SANDBOX/disabled state.

## Parameters for portal runs

Shadowfax:

```text
Branch: main
targetEnvironment: SANDBOX
enableProvider: false
confirmProductionActivation: false
providerBaseUrl: leave placeholder unchanged
authSecretName: leave default
```

Porter:

```text
Branch: main
targetEnvironment: SANDBOX
enableProvider: false
confirmProductionActivation: false
providerBaseUrl: leave placeholder unchanged
authSecretName: leave default
```

Shiprocket Quick:

```text
Branch: main
targetEnvironment: SANDBOX
enableProvider: false
confirmProductionActivation: false
providerBaseUrl: leave placeholder unchanged
authSecretName: leave default
```

Because `enableProvider=false`, these simple-sandbox runs do not require provider credentials.

## Vendor onboarding required for FULL_SANDBOX

### Shadowfax

Obtain the authoritative sandbox API contract and credential from Shadowfax, including serviceability/quote, create, cancellation, tracking, webhook authentication/signature and canonical status information.

### Porter

Complete Enterprise API onboarding and obtain the vendor-issued hyperlocal API contract, credential, sandbox/test endpoint, webhook contract and two-wheeler delivery capabilities.

### Shiprocket Quick

Obtain explicit Quick API access and a vendor-approved test/sandbox procedure. Do not assume a production API host is a safe sandbox merely because credentials are test credentials.

## Secret handling

When credentials are issued, store them in Azure Key Vault / Container App secret storage and pass only secret references. Never place secret values in Git, pipeline parameters, documentation or chat.

## Tomorrow's order test expectation

New customer orders should continue to be quoted and booked only through Borzo until another provider reaches FULL_SANDBOX.

The provider-outage resilience fix deployed on 16 August 2026 should also be verified with new orders: transient provider infrastructure unavailability must move delivery commands into `WAITING_FOR_PROVIDER` with scheduled retry rather than rapidly exhausting the delivery attempt budget.

## Pending

- Shadowfax Spring adapter and webhook controller.
- Porter Spring adapter and webhook controller.
- Shiprocket Quick Spring adapter and webhook controller.
- Provider-specific DB activation synchronization when each full-sandbox adapter is proven.
- APIM webhook routes only after matching Spring endpoints and signature verification exist.
- Multi-provider sandbox intelligence E2E with at least two real executable provider candidates.
