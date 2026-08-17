# Integration delivery-status publisher startup isolation

Date: 2026-08-10

## Context

The Order `DELIVERY_STATUS_CHANGED` consumer was enabled and the isolated synthetic consumer validation passed. The next controlled step was to enable only the Integration Service delivery-status outbox publisher while keeping delivery command execution, reconciliation, webhook processing, tracking reconciliation and Borzo disabled.

The first guarded publisher activation created Integration revision `ca-craves-integration-service-pr--0000059` with `CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=true`, but that revision never became Ready/Healthy within the 10-minute activation window. The activation pipeline rolled the publisher flag back to its exact previous state.

Observed progression:

- existing revision `0000058`: Ready, Healthy, Running, publisher false
- new revision `0000059`: latest, publisher true, previous revision remained Ready, health reported `None`
- guarded pipeline timed out and executed rollback

No provider execution, credential rotation, secret mutation or real-order delivery action was performed.

## Source-level coupling found

`DeliveryServiceBusConfiguration` was enabled when either the delivery-command feature or the delivery-status publisher feature was enabled, but inside that configuration it always created both:

- `deliveryCommandSender` targeting the delivery command queue
- `deliveryDomainEventSender` targeting the domain-events topic

`DeliveryServiceBusPublisher` also required both sender beans in its constructor.

Therefore publisher-only activation was not isolated at the Spring bean level: turning on the status publisher also instantiated the delivery-command queue sender even though `CRAVES_DELIVERY_COMMAND_ENABLED=false`.

## Fix

### `services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryServiceBusConfiguration.java`

Each sender now has its own feature condition:

- `deliveryCommandSender` exists only when `craves.delivery-command.enabled=true`
- `deliveryDomainEventSender` exists only when `craves.delivery-command.status-publisher-enabled=true`

The shared client factory remains available whenever either capability is enabled.

### `services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryServiceBusPublisher.java`

The publisher now obtains the two qualified senders independently through `ObjectProvider`.

- delivery scheduling/cancellation requires only the command sender
- domain-event publication requires only the domain-event sender
- invoking a capability whose sender is disabled fails explicitly with `IllegalStateException`

This keeps the existing class/API shape for `DeliveryCommandScheduler` and `DeliveryOutboxPublisher` while removing the cross-feature startup dependency.

### Regression tests

`services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryServiceBusPublisherIsolationTest.java` verifies:

1. publisher-only mode can publish with no command sender;
2. command-only mode can operate with no domain-event sender;
3. the two sender bean methods are conditioned on their own feature flags.

## Runtime safety preserved

This source change does not enable anything by itself. The runtime rollout remains:

1. merge only after backend source-integrity and Maven verification pass;
2. deploy Integration Service through the normal runtime-preserving Integration deployment pipeline while publisher remains false;
3. verify the new Integration image is healthy with all runtime flags unchanged;
4. rerun `azure-pipelines-integration-delivery-status-publisher-enable.yml` with explicit activation confirmation;
5. require Order consumer enabled, Service Bus subscription/filter healthy and DLQ empty;
6. enable only `CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=true`;
7. keep delivery command, reconciliation, webhook processing, tracking reconciliation and Borzo disabled.

## Explicitly unchanged

- no Cashfree changes
- no Borzo/provider activation
- no delivery command activation
- no webhook/tracking activation
- no credential rotation
- no Key Vault secret value changes
- no pricing, commission, serviceability or compliance logic changes
- no real customer order used for synthetic validation
