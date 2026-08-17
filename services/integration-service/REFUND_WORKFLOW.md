# Integration Service Refund Workflow

This module consumes `REFUND_REQUESTED` domain events and prepares safe, idempotent Cashfree refunds for chef-specific orders.

## Safety defaults

Every runtime switch is disabled by default:

```text
CRAVES_REFUND_CONSUMER_ENABLED=false
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=false
```

Deploying this code and Flyway V100 does not call Cashfree and does not consume Service Bus messages.

## Flow

```text
Order Service transactional outbox
    -> REFUND_REQUESTED
    -> Service Bus filtered subscription
    -> Integration refund inbox
    -> payment and cumulative-amount validation
    -> payment_schema.refund REQUESTED
    -> Cashfree create refund with stable refund_id and x-idempotency-key
    -> reconciliation using Get Refund
    -> REFUND_STATUS_CHANGED transactional outbox
```

## Idempotency

One chef-specific order creates at most one refund row:

```text
unique chef_sub_order_id
unique request_event_id
unique deterministic idempotency_key
unique Cashfree-compatible refund_ref
```

`refund_ref` is `CRV` plus the compact chef sub-order UUID, which is 35 alphanumeric characters. The Cashfree idempotency header is a deterministic UUID derived from the chef sub-order UUID.

## Financial checks

Before accepting a refund request, Integration Service locks the checkout payment row and verifies:

```text
payment status = PAID
payment currency = refund currency
refund amount > 0
refund amount <= captured payment amount
sum(all reserved refunds) + requested refund <= captured payment amount
```

A multi-kitchen checkout may therefore refund only the rejected kitchen order without cancelling accepted kitchen orders.

## Cashfree mapping

```text
Cashfree SUCCESS   -> REFUNDED
Cashfree PENDING   -> REFUND_PENDING
Cashfree ONHOLD    -> REFUND_PENDING
Cashfree FAILED    -> REFUND_FAILED
Cashfree CANCELLED -> REFUND_FAILED
```

The adapter uses:

```http
POST /pg/orders/{order_id}/refunds
GET  /pg/orders/{order_id}/refunds/{refund_id}
```

Create requests use `refund_speed=STANDARD`, stable `refund_id`, and `x-idempotency-key`.

## Database migration

```text
src/main/resources/db/migration/V100__refund_workflow_foundation.sql
```

It extends `payment_schema.refund` and adds:

```text
payment_schema.refund_request_inbox
payment_schema.refund_status_outbox
```

The claim workers use PostgreSQL `FOR UPDATE SKIP LOCKED`, lock tokens, stale-lock recovery, bounded batches and retry limits for safe execution across multiple Container App replicas.

## Service Bus subscription

The consumer uses:

```text
Topic: craves-domain-events
Subscription: integration-service-refund-requested
SQL filter: eventType = 'REFUND_REQUESTED'
```

The Integration Container App managed identity requires `Azure Service Bus Data Receiver` at the subscription scope.

## Local validation

```bash
cd services/integration-service
mvn -B clean verify
```

Validate contracts:

```bash
for schema in ../../contracts/events/*.schema.json; do
  python3 -m json.tool "$schema" >/dev/null
done
```

## Deployment order

1. Run `azure-pipelines-integration-service-ci.yml` from the feature branch.
2. Merge the PR after CI succeeds.
3. Run `azure-pipelines-integration-service.yml` from `main`.
4. Confirm Flyway V100 and a healthy Integration revision.
5. Run `azure-pipelines-integration-refund-consumer-enable.yml` from `main`.
6. Confirm the consumer is enabled while provider execution, reconciliation and status publication remain disabled.
7. Validate one controlled `REFUND_REQUESTED` event persists exactly one `REQUESTED` refund row.
8. Build the Order Service `REFUND_STATUS_CHANGED` consumer before enabling status publication.
9. Enable Cashfree sandbox execution only after credential, amount, retry and DLQ checks pass.
10. Enable the Order timeout worker only after the complete refund path is proven end to end.

## Manual steps required

- Do not paste Cashfree credentials into chat, source control or pipeline logs.
- Keep credentials in the existing Azure Container App secret or Key Vault-backed configuration.
- Verify the Integration managed identity receiver role after creating the subscription.
- Do not enable production Cashfree while validating this module.
- No new Azure namespace, Container App or paid SKU is created. The enablement pipeline adds one subscription entity to the existing Service Bus topic.
