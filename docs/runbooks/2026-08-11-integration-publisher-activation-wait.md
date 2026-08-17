# Integration delivery-status publisher activation wait hardening

Date: 2026-08-11
Environment: Craves prod-low

## Incident evidence

The guarded Integration `DELIVERY_STATUS_CHANGED` publisher activation created revision `ca-craves-integration-service-pr--0000062` at `2026-08-10T17:43:50Z`. The activation helper observed that revision as latest with `CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=true`, but it had not become the latest Ready revision within the helper's previous ten-minute observation window, so the helper rolled the publisher flag back.

Revision-specific application logs later showed Tomcat listening on port 8080 and `IntegrationServiceApplication` completing Spring Boot startup at approximately `2026-08-10T17:56:55Z`, about thirteen minutes after revision creation. Managed identity successfully acquired a token for Azure Service Bus and an existing Service Bus receiver link opened successfully.

A later read-only revision query showed revision `0000062` as:

- active: false
- healthState: Healthy
- provisioningState: Provisioned
- replicas: 0
- runningState: Stopped
- trafficWeight: 0

The evidence therefore supports a slow Azure Container Apps provisioning/readiness transition rather than a deterministic Spring startup failure.

## Change

`integration-delivery-status-publisher-enable-v3.sh` replaces the fixed 60 x 10 second wait used by the v2 helper with a guarded default observation window of 150 x 10 seconds (25 minutes).

The v3 helper:

- preserves all v2 preflight safety checks;
- captures the first newly-created activation revision and refuses to accept an unrelated later revision;
- logs health, provisioning state, revision running state, replica count, app running state, and publisher flag on every poll;
- treats `Unhealthy` as recoverable while the observation window remains open;
- fails early if the revision enters terminal `provisioningState=Failed` or `runningState=Failed`;
- requires `latestRevisionName == latestReadyRevisionName`, `healthState=Healthy`, app `runningStatus=Running`, and publisher flag `true` before success;
- preserves the existing rollback behavior on timeout or terminal failure;
- preserves unrelated environment, configuration, template, managed identity, and Key Vault secret metadata;
- leaves delivery command, reconciliation, webhook processing, tracking reconciliation, and Borzo disabled.

## Runtime configuration

The default wait can be overridden only through process environment variables if a future controlled pipeline requires it:

- `CRAVES_PUBLISHER_ACTIVATION_MAX_ATTEMPTS` (default `150`)
- `CRAVES_PUBLISHER_ACTIVATION_POLL_SECONDS` (default `10`)

The Azure DevOps pipeline does not currently expose these as user parameters; the controlled default remains 25 minutes.

## Deployment/run sequence

1. Merge the source change after repository CI passes.
2. Do not redeploy Integration Service for this helper-only change.
3. Run `azure-pipelines-integration-delivery-status-publisher-enable.yml` from `main`.
4. Set only `confirmPublisherActivation=true`.
5. Observe the newly-created revision until it becomes Ready/Healthy or enters a terminal failure state.
6. On PASS, immediately run the existing read-only delivery-status rollout-status verification before any synthetic end-to-end publisher test.

## Explicitly unchanged

- no credential rotation
- no secret value changes
- no Azure resource creation
- no Service Bus topology changes
- no RBAC changes
- no Borzo/provider activation
- no delivery command activation
- no webhook/tracking/reconciliation activation
- no pricing, commission, serviceability, tax, payout, or compliance business logic changes
