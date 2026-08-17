# Borzo deployment startup failure: duplicate Spring bean name

Date: 2026-07-14
Service: `services/integration-service`
Environment: Azure Container Apps prod-low
Failed revision: `ca-craves-integration-service-pr--0000006`
Serving revision during incident: `ca-craves-integration-service-pr--0000005`

## Symptom

The Azure DevOps pipeline compiled and tested successfully, but the new Container App revision never became ready. Startup stopped before Flyway migration and before the embedded web server became available.

## Root cause

Two different Spring components used the same default bean name `deliveryProviderAdapterRegistry`:

- `in.craves.integration.delivery.DeliveryProviderAdapterRegistry` — existing Delivery Intelligence registry.
- `in.craves.integration.delivery.provider.DeliveryProviderAdapterRegistry` — newly added Borzo foundation registry.

Spring derives the default component bean name from the class name, not the package. Because both classes had the same simple class name, component scanning raised `ConflictingBeanDefinitionException`.

## Fix

The unused duplicate registry under `delivery/provider` was removed. The existing Delivery Intelligence registry remains unchanged.

A new regression test scans all Spring components under `in.craves.integration`, generates their effective annotation-based bean names, and fails when two components resolve to the same name:

`services/integration-service/src/test/java/in/craves/integration/config/SpringComponentBeanNameUniquenessTest.java`

## Safety and data impact

- No database migration was applied by the failed revision because startup stopped during configuration parsing.
- The previous healthy revision continued serving traffic.
- No Borzo API token or callback token was configured.
- Borzo remains disabled and inactive.
- No live delivery was created.

## Verification required

1. Run the Integration Service Azure DevOps pipeline from `main`.
2. Confirm the Maven test stage passes, including `SpringComponentBeanNameUniquenessTest`.
3. Confirm the newest Container App revision reports `Healthy`.
4. Confirm `latestRevisionName` equals `latestReadyRevisionName`.
5. Confirm Flyway validates/applies V3 and the application logs `Started IntegrationServiceApplication`.
6. Confirm `/actuator/health` returns `UP`.

## Pending after recovery

The Borzo adapter remains sandbox-only and disabled. Callback URL configuration, sandbox secret injection, signed callback testing, and provider activation are separate controlled steps.
