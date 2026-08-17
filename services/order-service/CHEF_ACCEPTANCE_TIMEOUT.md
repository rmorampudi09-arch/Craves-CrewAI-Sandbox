# Chef Acceptance Timeout and Refund Trigger

This Order Service module protects customers when a paid chef-specific order is not confirmed by its kitchen.

## Locked V1 behavior

```text
Verified payment
    -> CHEF_ACCEPTANCE_PENDING
    -> 30-minute chef response window
```

Notification schedule:

```text
T+0   New paid order notification to the chef
T+10  Reminder
T+20  Urgent reminder
T+30  Automatic timeout
```

Valid outcomes:

```text
Chef accepts before expiry
    -> CHEF_ACCEPTED
    -> CHEF_ACCEPTED_ORDER event

Chef explicitly rejects
    -> CHEF_REJECTED
    -> reason CHEF_DECLINED
    -> REFUND_REQUESTED stored transactionally

Chef does not respond within 30 minutes
    -> CHEF_REJECTED
    -> reason CHEF_ACCEPTANCE_TIMEOUT
    -> REFUND_REQUESTED stored transactionally
```

The refund amount is the immutable `customer_order.grand_total` for that chef-specific order. One rejected kitchen order does not cancel accepted orders from other kitchens in the same checkout.

## Safety state in this module

The timeout worker is disabled by default:

```text
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
```

The Order Service domain publisher continues to publish only:

```text
CHEF_ACCEPTED_ORDER
```

Default:

```text
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

`REFUND_REQUESTED` rows are durable in `order_schema.domain_event_outbox`, but remain unclaimed while that event type is not enabled. This prevents a refund event from reaching Service Bus before Integration Service has a compatible refund consumer.

Do not enable `REFUND_REQUESTED` publication or the timeout worker until the Integration Service refund consumer, Cashfree sandbox adapter, subscription filter, and reconciliation path are deployed and validated.

## Database migration

```text
src/main/resources/db/migration/V6__chef_acceptance_timeout_refund_trigger.sql
```

Adds:

```text
chef_acceptance_requested_at
chef_acceptance_expires_at
chef_acceptance_initial_recorded_at
chef_acceptance_reminder_10_recorded_at
chef_acceptance_reminder_20_recorded_at
chef_rejection_code
refund_requested_at
refund_requested_amount
```

Existing `CHEF_ACCEPTANCE_PENDING` rows receive a fresh 30-minute window at migration time. Historical timestamps are not invented for completed orders.

## Payment callback integration

`PaymentCallbackService.markCheckoutPaid(...)` now opens the acceptance window using PostgreSQL time:

```text
chef_acceptance_requested_at = now()
chef_acceptance_expires_at = now() + 30 minutes
```

Repeated payment callbacks do not restart an already-open window because only pre-acceptance payment states are transitioned.

## Chef APIs

Accept:

```http
POST /api/v1/chef/orders/{orderId}/accept
Idempotency-Key: <optional stable client request key>
X-Correlation-ID: <optional UUID>
Content-Type: application/json
```

```json
{
  "prepTimeMinutes": 35,
  "note": "Order confirmed"
}
```

Acceptance after the deadline returns:

```json
{
  "error": "CHEF_ACCEPTANCE_EXPIRED",
  "message": "The 30-minute chef acceptance window has expired."
}
```

Reject:

```http
POST /api/v1/chef/orders/{orderId}/reject
Idempotency-Key: <optional stable client request key>
X-Correlation-ID: <optional UUID>
Content-Type: application/json
```

```json
{
  "reason": "Unable to prepare this order"
}
```

Only `CHEF_ACCEPTANCE_PENDING` can be rejected. A repeated explicit rejection is idempotent.

## REFUND_REQUESTED contract

Schema:

```text
contracts/events/refund-requested-v1.schema.json
```

Payload fields:

```text
checkoutId
chefSubOrderId
customerIdentityId
refundAmount
currency
reason
requestedAt
```

Allowed reasons:

```text
CHEF_DECLINED
CHEF_ACCEPTANCE_TIMEOUT
```

The event does not contain Cashfree credentials or a fabricated provider payment ID. Integration Service resolves the payment record by `checkoutId` in `payment_schema.payment_order`.

## Multi-replica behavior

Candidate scans are intentionally non-locking and bounded. Every actual reminder, rejection, timeout, notification record, and refund event is protected by a PostgreSQL row lock and rechecked inside a transaction.

Stable uniqueness keys prevent duplicate notifications and domain events:

```text
chef-new-order-{orderId}
chef-acceptance-reminder-10-{orderId}
chef-acceptance-reminder-20-{orderId}
refund-requested-order-{orderId}
REFUND_REQUESTED:{orderId}
```

## Environment variables

```text
CRAVES_CHEF_ACCEPTANCE_TIMEOUT_MINUTES=30
CRAVES_CHEF_ACCEPTANCE_FIRST_REMINDER_MINUTES=10
CRAVES_CHEF_ACCEPTANCE_SECOND_REMINDER_MINUTES=20
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_CHEF_ACCEPTANCE_WORKER_FIXED_DELAY_MS=30000
CRAVES_CHEF_ACCEPTANCE_WORKER_BATCH_SIZE=20
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

Preparation time remains separate from the acceptance timeout. The chef-provided preparation time must be positive but has no product-level maximum in this module.

## Local validation

```bash
cd services/order-service
mvn -B clean verify
```

Validate all event schema files:

```bash
for schema in ../../contracts/events/*.schema.json; do
  python3 -m json.tool "$schema" >/dev/null
done
```

## Deployment

Deploy with:

```text
azure-pipelines-order-service.yml
```

After deployment verify:

```text
latest revision = latest ready revision
running status = Running
Flyway V6 succeeded
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED is absent or false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES is absent or CHEF_ACCEPTED_ORDER
```

## Manual steps required

- Run the Order Service build-only pipeline from the feature branch before merging.
- Deploy Order Service from `main` after merge.
- Confirm Flyway V6 and the healthy revision.
- Do not enable the timeout worker yet.
- Do not enable `REFUND_REQUESTED` publication yet.
- Do not create or paste Cashfree keys for this module.
- No new Azure resource or paid SKU is required.

## Deferred next module

Integration Service must add:

```text
REFUND_REQUESTED Service Bus subscription and filter
idempotent refund-request persistence
Cashfree sandbox refund adapter
refund reconciliation worker
REFUND_STATUS_CHANGED publisher
Order Service refund status consumer
```

Only after that module passes sandbox and DLQ tests should the Order timeout worker and `REFUND_REQUESTED` publication be enabled together.
