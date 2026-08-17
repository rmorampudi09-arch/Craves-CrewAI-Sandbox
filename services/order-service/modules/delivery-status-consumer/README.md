# Order Service — Delivery Status Consumer

## Purpose

This module consumes `DELIVERY_STATUS_CHANGED` v1 events from the Craves domain-event topic and creates an Order-owned delivery projection for each chef-specific sub-order.

It does not call a delivery provider, create a booking, calculate pricing, change commission, decide serviceability or replace the commercial order lifecycle.

## Runtime flow

```text
Integration Service delivery outbox
  -> Azure Service Bus topic: craves-domain-events
  -> filtered subscription: order-service-delivery-status-changed
  -> Order Service manual-lock consumer
  -> idempotent delivery_status_inbox
  -> chef-sub-order row lock
  -> stale and terminal protection
  -> customer_order delivery projection
  -> append-only order_delivery_status_history
  -> existing Order notification_outbox
  -> existing Notification Service internal API
```

## Why Notification Service does not consume the v1 event directly

`DELIVERY_STATUS_CHANGED` v1 contains delivery, checkout and chef-sub-order identifiers, but it does not contain customer or chef recipient identity IDs.

Order Service already owns the customer-order relationship. It therefore resolves the customer identity from its own database and writes the existing notification outbox transactionally.

This prevents Notification Service from reading Order tables directly or inventing an undocumented cross-service lookup.

## Files

```text
src/main/java/in/craves/order/config/DeliveryStatusConsumerProperties.java
src/main/java/in/craves/order/delivery/DeliveryStatusModels.java
src/main/java/in/craves/order/delivery/DeliveryStatusEventValidator.java
src/main/java/in/craves/order/delivery/DeliveryStatusTransitionPolicy.java
src/main/java/in/craves/order/delivery/DeliveryStatusCustomerNotificationService.java
src/main/java/in/craves/order/delivery/DeliveryStatusUpdateService.java
src/main/java/in/craves/order/delivery/DeliveryStatusChangedServiceBusProcessor.java
src/main/java/in/craves/order/delivery/DeliveryStatusQueryService.java
src/main/java/in/craves/order/web/DeliveryStatusController.java
src/main/java/in/craves/order/web/DeliveryStatusDtos.java
src/main/resources/db/migration/V9__delivery_status_consumer.sql
```

## Configuration

The consumer is disabled by default:

```text
CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=false
```

When activated, it uses:

```text
CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE
CRAVES_DOMAIN_EVENTS_TOPIC_NAME
CRAVES_DELIVERY_STATUS_SUBSCRIPTION
CRAVES_DELIVERY_STATUS_MAX_CONCURRENT_MESSAGES
CRAVES_DELIVERY_STATUS_PREFETCH_COUNT
CRAVES_DELIVERY_STATUS_MAX_DELIVERY_ATTEMPTS
```

A connection string remains supported for local/emergency compatibility, but Azure runtime should use the Container App managed identity.

## Database objects

Flyway V9 adds:

- delivery projection columns to `order_schema.customer_order`;
- `order_schema.delivery_status_inbox` for idempotency and processing outcomes;
- `order_schema.order_delivery_status_history` for append-only applied history;
- unique and dispatch-supporting indexes;
- canonical normalized-status constraints.

V9 is additive and does not backfill or activate delivery processing.

## Customer API

```http
GET /api/v1/orders/{orderId}/delivery-status
```

The endpoint requires the authenticated customer to own the order.

It exposes only:

- normalized delivery status;
- provider name;
- tracking URL;
- observation timestamp;
- normalized status history.

It does not expose the raw provider callback or provider delivery identifier.

## Idempotency

The event ID is the inbox primary key. Repeated broker deliveries complete successfully without applying the event twice.

The customer notification key is:

```text
delivery-status-{eventId}
```

This prevents duplicate in-app notices.

## Out-of-order protection

An event is not applied when:

- its `observedAt` is older than or equal to the accepted projection timestamp;
- the accepted projection is terminal and the incoming status differs;
- its normalized status and tracking URL do not change the projection.

The corresponding inbox result is `STALE`, `TERMINAL_PROTECTED` or `NO_CHANGE`.

## Order lifecycle boundary

Provider callbacks update dedicated `delivery_*` columns only.

They do not update `customer_order.status`. This is deliberate: commercial order transitions, refund consequences and support actions require approved product rules and must not be inferred from a provider callback.

## Local tests

```bash
cd services/order-service
mvn -B clean verify
```

## CI

Run:

```text
azure-pipelines-delivery-status-downstream-ci.yml
```

The pipeline verifies:

- Order Service Java 21 build/tests;
- Notification Service compatibility build/tests;
- all event JSON schemas;
- fail-closed source defaults;
- runtime-preserving routine deployment controls.

## Service Bus subscription activation safety

`azure-pipelines-order-delivery-status-consumer-enable.yml` owns the one-time Order delivery-status subscription preparation and consumer activation.

For a missing subscription it performs this order:

```text
verify Order + Integration safety state
  -> verify active Order secretRefs are Key Vault-backed
  -> create order-service-delivery-status-changed as Disabled
  -> create delivery-status-changed-only SQL filter
  -> verify exact filter expression
  -> remove $Default rule
  -> verify Azure Service Bus Data Receiver, including inherited RBAC
  -> activate subscription
  -> enable only the four Order delivery-status consumer settings
  -> verify new revision health
  -> verify unrelated env/config/identity/secret metadata are unchanged
```

The filter expression is:

```text
eventType = 'DELIVERY_STATUS_CHANGED' OR event_type = 'DELIVERY_STATUS_CHANGED'
```

The pipeline deliberately does **not** enable:

```text
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED
CRAVES_DELIVERY_COMMAND_ENABLED
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED
BORZO_API_ENABLED
```

It requires those Integration/provider controls to remain false or absent before enabling the downstream Order consumer.

The subscription is created Disabled first. If Order lacks `Azure Service Bus Data Receiver`, the pipeline stops before enabling the consumer and leaves the newly created subscription Disabled. The output prints the managed-identity principal ID and exact subscription scope required for the one-time RBAC assignment.

## Deployment and activation order

1. Merge only after branch CI succeeds.
2. Deploy Order code from merged `main` when required and confirm Flyway V9 is present.
3. Run `azure-pipelines-delivery-status-rollout-status.yml`.
4. If the filtered subscription is missing, run `azure-pipelines-order-delivery-status-consumer-enable.yml`.
5. If the activation pipeline reports missing `Azure Service Bus Data Receiver`, grant that role once to the Order system-assigned managed identity at the printed subscription scope or an approved parent scope, then rerun.
6. Confirm the Order consumer is healthy and the delivery-status subscription DLQ is empty.
7. Validate one synthetic `DELIVERY_STATUS_CHANGED` event, including duplicate/stale/terminal behavior.
8. Run `azure-pipelines-integration-delivery-status-publisher-enable.yml` only after the downstream consumer validation passes.
9. Add/verify the APIM route for the customer delivery-status endpoint.
10. Keep webhook processing, tracking reconciliation and Borzo disabled until their later controlled activation stages.

## Rollback

Run:

```text
azure-pipelines-delivery-status-rollback.yml
```

Rollback disables:

- Order delivery-status consumption;
- Integration delivery-status publication;
- Integration webhook/tracking execution;
- Borzo.

It never deletes durable event, inbox, history, notification or provider-audit data.

The consumer-enable pipeline also has a narrow automatic rollback for its four Order consumer configuration values when the new revision or preservation checks fail. A subscription created by that run is left Disabled rather than deleted so that diagnostic evidence is preserved.

## Manual steps

- Azure DevOps: register the YAML pipelines if they are not already visible as Azure DevOps Pipeline objects.
- Azure RBAC: when reported by the activation pipeline, grant `Azure Service Bus Data Receiver` to the Order Container App system-assigned managed identity at the printed Service Bus subscription scope or an approved parent scope.
- APIM: add/verify the customer delivery-status GET operation only after the Order consumer path is proven.
- Do not paste secret values into chat, pipeline YAML or Azure DevOps plain-text variables.
- No new paid Azure SKU is required; the activation uses one subscription inside the existing Service Bus namespace.
