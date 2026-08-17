# Integration unhealthy revision diagnostics

Date: 2026-08-10
Scope: Craves prod-low Integration Service
Target failure: `ca-craves-integration-service-pr--0000062`

## Why this exists

The guarded `DELIVERY_STATUS_CHANGED` publisher activation created Integration revision `0000062` with `CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=true`, while the previously healthy revision `0000061` remained the latest Ready revision. The new revision remained `Unhealthy` until the activation pipeline timed out and rolled the publisher flag back.

The normal Integration image deployment immediately before this activation had already succeeded, so the next investigation must be based on runtime revision evidence rather than another speculative source change or a longer timeout.

## Pipeline

`azure-pipelines-integration-revision-diagnostics.yml`

Default target revision:

`ca-craves-integration-service-pr--0000062`

## What it reads

- current Integration Container App latest/ready/running state
- safe delivery feature flags only
- recent revision health/provisioning state
- failed revision image/resources/probes/scale and safe delivery feature flags
- failed revision replica/container state
- Container Apps system logs
- failed revision application console logs when an addressable replica remains

## Safety properties

The pipeline is read-only. It does not:

- call `az containerapp update`
- change revision traffic
- restart/deactivate revisions
- change environment variables
- read Key Vault secret values
- create/delete/modify Container App secrets
- create or modify RBAC
- change Service Bus resources
- enable Borzo, delivery commands, reconciliation, webhook processing, tracking, or provider execution
- rotate any credential

A defensive redaction pass masks common password/secret/token/authorization patterns if application logs unexpectedly contain them.

## Required variable

Use the established Azure DevOps variable:

`AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection`

Do not place any credential value into the pipeline parameters.

## Interpretation

The most useful evidence is normally in sections 4-6:

- replica `restartCount` or `runningStateDetails`
- Container Apps system events for startup/probe failures
- Spring Boot console exception/stack trace from the failed revision

Do not rerun publisher activation until this evidence is reviewed and the root cause is identified.

## Current rollout safety state

The activation pipeline invoked its rollback path after revision `0000062` failed to become healthy. The next diagnostic step must verify current runtime state rather than assuming rollback completion from the pipeline message alone.
