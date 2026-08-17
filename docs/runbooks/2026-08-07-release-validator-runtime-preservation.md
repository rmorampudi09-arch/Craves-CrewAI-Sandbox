# Release Validator Runtime-Preservation Alignment

Date: 2026-08-07

## Purpose

The service-specific backend deployment pipelines were changed to preserve the live Container App runtime and Key Vault references instead of reconstructing environment variables and local secret objects during every code deployment.

Several older release validators still expected the previous deployment model. They therefore needed to be aligned with the new contract before the production-readiness or delivery-status CI pipelines could be used again.

## Updated files

```text
scripts/release/verify-seven-service-deployment-contracts.sh
scripts/release/verify-deployment-environment-bindings.sh
azure-pipelines-delivery-status-downstream-ci.yml
```

## New validation rule

Routine service deployment pipelines must:

- build and test with Java 21;
- build and push an immutable image;
- deploy the image without rewriting environment variables;
- preserve Key Vault-backed secret references;
- preserve ingress, scaling, managed identity and feature/provider flags;
- use the shared runtime-preserving deployment helper where applicable;
- never require application credential values as pipeline variables.

The Catalog pipeline remains a separately hardened image-only flow and is validated for its own environment/secret metadata preservation safeguards.

## Forbidden routine-deployment mutations

The release validators now reject these patterns in service-specific deployment pipelines:

```text
--set-env-vars
--replace-env-vars
az containerapp secret set
az containerapp ingress update
--min-replicas
--max-replicas
```

Explicit activation/rollback pipelines may still change their narrowly scoped activation flags because that is their purpose. They are not routine code-deployment pipelines.

## Delivery-status CI correction

`azure-pipelines-delivery-status-downstream-ci.yml` previously proved fail-closed behavior by grepping hard-coded `...=false` assignments from the normal Order and Integration deployment pipelines.

It now verifies the actual source defaults instead:

```text
CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=false
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
BORZO_API_ENABLED=false
```

It also verifies that the routine Order and Integration pipelines use runtime-preserving deployment and do not reconstruct environment variables or local secrets.

## Manual steps

No Azure mutation is required for this validator correction.

No new resource, secret, credential or billing-sensitive Azure component is created.

After merge, use the existing production-readiness and delivery-status CI pipelines from `main`.
