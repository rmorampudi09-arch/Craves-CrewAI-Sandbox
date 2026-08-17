# Subscription occurrence to Order fulfillment

## Purpose

This module closes the paid recurring-meal fulfillment path without inventing subscription economics.

```text
paid billing cycle
→ occurrence READY_FOR_ORDER
→ configurable dispatch lead
→ Subscription transactional request outbox
→ Service Bus SUBSCRIPTION_ORDER_REQUESTED
→ Order durable inbox
→ Catalog/address ownership validation
→ idempotent subscription order
→ durable internal callback outbox
→ occurrence ORDER_CREATED
```

## Subscription-side controls

```text
CRAVES_SUBSCRIPTION_ORDER_REQUEST_WORKER_ENABLED=false
CRAVES_SUBSCRIPTION_ORDER_PUBLISHER_ENABLED=false
CRAVES_SUBSCRIPTION_ORDER_LEAD_HOURS=-1
```

The request worker refuses to start until an approved lead from 0 to 168 hours is configured. Source code does not choose that operational value.

## Order-side controls

```text
CRAVES_SUBSCRIPTION_ORDER_CONSUMER_ENABLED=false
CRAVES_SUBSCRIPTION_ORDER_CALLBACK_WORKER_ENABLED=false
```

The Order consumer validates every menu item against active Catalog data, verifies that all items belong to the plan chef's active kitchen, validates the customer-owned active delivery address, and stores immutable pickup/drop-off/package snapshots.

## Financial boundary

Subscription order customer charge fields are stored as zero because the customer has already paid the subscription invoice. The order is explicitly marked:

```text
order_source = SUBSCRIPTION
financial_allocation_status = PENDING_POLICY
```

This is not a chef payout amount. Commission, tax allocation, chef earnings, refund allocation and settlement rules belong to the separate financial-ledger module.

## Idempotency and recovery

- One occurrence can create only one Order.
- Request events are idempotent by event and occurrence.
- Subscription request publication uses a transactional outbox.
- Order creation writes a durable callback outbox in the same transaction.
- Callback delivery retries with stale-lock recovery and local dead-letter state.
- The Subscription callback is idempotent and rejects conflicting Order IDs.

## Database changes

Subscription Service Flyway V7 adds occurrence dispatch state and request outbox.

Order Service Flyway V11 adds subscription order source fields, the request inbox and callback outbox. `checkout_id` becomes nullable only for explicitly constrained subscription orders.

## Local validation

```bash
cd services/subscription-service && mvn -B -ntp verify
cd services/order-service && mvn -B -ntp verify
```

## Later activation order

1. Apply V7 and V11 with every new flag false.
2. Configure the filtered Service Bus subscription.
3. Deploy Order consumer and callback worker disabled.
4. Validate a synthetic request, duplicate, invalid chef/kitchen, and invalid address.
5. Enable Order consumer.
6. Enable Order callback worker and verify occurrence reaches `ORDER_CREATED`.
7. Set the approved dispatch lead.
8. Enable Subscription request publisher.
9. Enable Subscription request worker last.

No pipeline, migration, message, order or Azure change was executed while this code was created.
