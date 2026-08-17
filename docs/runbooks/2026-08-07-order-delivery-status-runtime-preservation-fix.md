# Order Delivery Status Activation — Runtime Preservation False Positive

Date: 2026-08-07

## Observed failure

The one-time Order `DELIVERY_STATUS_CHANGED` activation reached runtime-preservation validation and failed with:

```text
ERROR: Unrelated Order runtime configuration changed during activation.
```

The activation pipeline rolled the four delivery-status consumer controls back and restored the Service Bus subscription's previous status.

## Root cause

The activation pipeline hashed the entire `properties.configuration` object before and after creating a new Container Apps revision.

Azure Container Apps can update `properties.configuration.ingress.traffic` as revision bookkeeping when a new revision is created. This is not an application runtime drift and is already normalized out by the repository's proven `scripts/release/deploy-single-service-preserve-runtime.sh` helper.

## Correct validation contract

For configuration preservation, hash:

```jq
.properties.configuration
| if .ingress then .ingress |= del(.traffic) else . end
```

Continue to verify independently:

- unrelated environment variables;
- managed identity;
- Key Vault secret metadata;
- template non-environment runtime settings;
- Integration delivery/Borzo safety flags;
- liveness/readiness.

Only Azure-managed ingress traffic routing metadata associated with revision creation is excluded.

## Safety

This correction does not enable Borzo, call a provider, change credentials, modify Key Vault values, or relax checks on ingress configuration other than revision traffic bookkeeping.
