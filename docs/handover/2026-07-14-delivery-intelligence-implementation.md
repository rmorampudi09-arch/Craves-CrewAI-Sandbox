# Delivery Intelligence Implementation Handover — 14 July 2026

## Input reviewed

- `delivery_intelligence (1).zip`
- `CRV-INT-DELIVERY-001 Delivery Provider Integration Blueprint`
- Approved Craves HLD and the 8 July continuation baseline

## Architecture decision

The Python package is treated as an algorithm prototype/reference. The production implementation is a Java 21/Spring Boot port inside `services/integration-service`.

Reasons:

1. Craves backend stack is locked to Java 21/Spring Boot.
2. The prototype stores data in SQLite despite deployment comments referring to PostgreSQL.
3. Its model store is local filesystem, not Azure Blob Storage.
4. Its rider directory is simulated and does not call real provider APIs.
5. A separate always-on Python Container App plus daily Container Apps Job would add paid resources and deployment complexity.
6. In-memory bandit state would diverge across replicas and disappear on restart.

## Implemented in Git

- Provider registry and provider capability model.
- Canonical `DeliveryProviderAdapter` interface.
- Idempotent assignment by chef-specific sub-order.
- Explainable candidate score audit.
- Seven-day live metrics plus daily faded historical state.
- Persisted Thompson-sampling state.
- Proximity/ETA and provider-quality blended ranking.
- Greedy and stochastic selection modes.
- Delivery outcome scoring and durable duplicate protection.
- Daily archival scheduler protected by PostgreSQL advisory transaction lock.
- Delivery command/job/event/webhook inbox/outbox database foundations.
- Internal service-key authorization on new endpoints.
- Unit tests for core scoring, predictor, and beta sampling.

## Deliberately not faked

- No simulated riders in deployed code.
- No guessed Shadowfax, Porter, Borzo, or Shiprocket endpoints.
- No vendor token in source.
- No trained ML model from synthetic data.
- No new Azure Service Bus or Container App resources.

## Pending provider implementation

For each provider, obtain the authoritative sandbox specification and credentials, then implement one adapter under the canonical interface. The first adapter can be Borzo after its complete current create/cancel/tracking/webhook contract is obtained.

## Pending event wiring

The database foundations exist, but production delayed dispatch still needs the existing or approved Azure Service Bus topology:

- `CHEF_ACCEPTED_ORDER` event from Order Service.
- Scheduled `delivery-command` message close to `ready_at`.
- Integration worker fan-out quote and ranked provider fallback.
- `DELIVERY_STATUS_CHANGED` event to Order and Notification services.

Do not provision a new Service Bus namespace or paid resource until the owner confirms the existing resource status and cost.

## Manual deployment steps

1. Run the existing Integration Service Azure DevOps pipeline.
2. Confirm Maven build and tests pass.
3. Confirm Flyway applies `V2__delivery_intelligence_foundation.sql` to `craves_integration_db`.
4. Confirm the Integration Container App health endpoint remains healthy.
5. Confirm `CRAVES_INTERNAL_SERVICE_KEY` exists as a secret-backed runtime value; do not print it.
6. Keep vendor providers inactive until their authoritative sandbox adapters are implemented and verified.

## Verification completed before push

- The supplied Python package compiled and its local cascade simulation executed.
- The Java 21 production port compiled with local framework stubs.
- A deterministic algorithm smoke test passed for outcome scoring and improving-versus-declining partner prediction.
- Maven was not available in the implementation sandbox, so the Azure DevOps pipeline remains the authoritative full Spring Boot build and test gate.
