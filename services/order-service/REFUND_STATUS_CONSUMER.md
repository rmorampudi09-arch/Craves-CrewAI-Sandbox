# Order Service REFUND_STATUS_CHANGED Consumer

This module consumes `REFUND_STATUS_CHANGED` v1 events from Integration Service and updates the affected chef-specific order.

## Safety defaults

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=false
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

A normal Order Service deployment applies Flyway V7 and deploys the code, but it does not consume refund status events.

## Input contract

```text
Event type: REFUND_STATUS_CHANGED
Version: 1.0
Source: integration-service
Topic: craves-domain-events
Subscription: order-service-refund-status-changed
Filter: eventType = 'REFUND_STATUS_CHANGED'
```

Normalized statuses:

```text
PENDING or ONHOLD    -> REFUND_PENDING
SUCCESS              -> REFUNDED
FAILED or CANCELLED  -> REFUND_FAILED
```

## Transactional processing

For each event, Order Service:

1. Validates the complete event envelope and provider-status mapping.
2. Inserts the event ID into `order_schema.refund_status_inbox`.
3. Locks the affected `customer_order` row.
4. Verifies checkout, customer, currency, reason and amount against the original refund request.
5. Rejects stale or out-of-order events.
6. Updates the order status and provider references.
7. Writes `order_status_history` when the normalized status changes.
8. Marks the inbox row `PROCESSED` in the same PostgreSQL transaction.

Duplicate Service Bus delivery with the same event ID is completed without applying the order update again.

## Flyway V7

```text
src/main/resources/db/migration/V7__refund_status_consumer.sql
```

Adds refund provider metadata to `order_schema.customer_order` and creates:

```text
order_schema.refund_status_inbox
```

## Runtime enablement

After CI, merge and normal Order deployment, register and run:

```text
/azure-pipelines-order-refund-status-consumer-enable.yml
```

The Order Container App managed identity requires `Azure Service Bus Data Receiver` at the refund-status subscription or namespace scope.

## Deferred work

- Customer notification templates for pending, completed and failed refunds.
- Integration `REFUND_STATUS_CHANGED` publisher enablement.
- Cashfree sandbox refund execution and reconciliation.
- Order `REFUND_REQUESTED` allow-list update.
- Chef acceptance timeout worker enablement.

Do not enable those items until this consumer has passed invalid-event and valid-event persistence tests.
