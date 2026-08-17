# Craves Technical Handover — Integration Refund Workflow

**Date:** 16 July 2026  
**Service:** Integration Service  
**Branch:** `feature/integration-refund-workflow`  
**Status:** Build validation pending  

## 1. Purpose

This module implements the Integration Service side of the customer-protective refund flow created when a paid chef-specific order is rejected or expires during the 30-minute chef acceptance window.

It does not enable automatic refunds by deployment alone. All consumer, provider, reconciliation and status-publication switches default to `false`.

## 2. Business behavior

```text
CHEF_DECLINED or CHEF_ACCEPTANCE_TIMEOUT
    -> Order Service writes REFUND_REQUESTED transactionally
    -> Integration Service validates the payment and refund amount
    -> one refund intent is persisted per chef-specific order
    -> Cashfree STANDARD refund is created idempotently
    -> pending refunds are reconciled
    -> REFUND_STATUS_CHANGED is stored transactionally
```

The requested refund amount is the immutable `grand_total` of only the rejected chef-specific order. Other accepted kitchen orders in the checkout continue independently.

## 3. Files added

```text
azure-pipelines-integration-refund-consumer-enable.yml
azure-pipelines-integration-service-ci.yml
contracts/events/refund-status-changed-v1.schema.json
services/integration-service/REFUND_WORKFLOW.md
services/integration-service/src/main/java/in/craves/integration/refund/CashfreeRefundClient.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundEventValidator.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundExecutionWorker.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundModels.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundRepository.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundRequestService.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundRequestedServiceBusProcessor.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundStatusEventFactory.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundStatusOutboxPublisher.java
services/integration-service/src/main/java/in/craves/integration/refund/RefundWorkflowProperties.java
services/integration-service/src/main/resources/db/migration/V100__refund_workflow_foundation.sql
services/integration-service/src/test/java/in/craves/integration/refund/RefundEventValidatorTest.java
services/integration-service/src/test/java/in/craves/integration/refund/RefundRequestServiceTest.java
services/integration-service/src/test/java/in/craves/integration/refund/RefundStatusEventFactoryTest.java
```

Modified:

```text
services/integration-service/src/main/resources/application.yml
```

## 4. Runtime switches

```text
CRAVES_REFUND_CONSUMER_ENABLED=false
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=false
```

Additional settings:

```text
SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE
SERVICE_BUS_TOPIC_NAME
SERVICE_BUS_REFUND_REQUESTED_SUBSCRIPTION
CRAVES_REFUND_MAX_CONCURRENT_MESSAGES
CRAVES_REFUND_PREFETCH_COUNT
CRAVES_REFUND_MAX_DELIVERY_ATTEMPTS
CRAVES_REFUND_WORKER_BATCH_SIZE
CRAVES_REFUND_WORKER_FIXED_DELAY_MS
CRAVES_REFUND_MAX_PROVIDER_ATTEMPTS
CRAVES_REFUND_RETRY_BASE_DELAY_SECONDS
CRAVES_REFUND_STALE_LOCK_SECONDS
CRAVES_REFUND_STATUS_OUTBOX_BATCH_SIZE
CRAVES_REFUND_STATUS_OUTBOX_FIXED_DELAY_MS
```

## 5. Database changes

Flyway V100 extends the existing `payment_schema.refund` table with checkout, chef sub-order, customer, provider, event, lock, retry and reconciliation metadata.

It adds:

```text
payment_schema.refund_request_inbox
payment_schema.refund_status_outbox
```

Important uniqueness controls:

```text
chef_sub_order_id
request_event_id
idempotency_key
refund_ref
refund status event_key
```

The payment row is locked before calculating cumulative refunds. This serializes simultaneous refund requests for different chef orders from the same checkout and prevents their combined amount from exceeding the captured amount.

## 6. Service Bus behavior

Consumer entity:

```text
Namespace: sb-craves-prodlow-l3ing6
Topic: craves-domain-events
Subscription: integration-service-refund-requested
Rule: refund-requested-only
SQL filter: eventType = 'REFUND_REQUESTED'
```

The processor still validates the complete envelope after the broker filter. Invalid and financially impossible messages are dead-lettered; transient payment-record availability or infrastructure failures are retried up to the configured delivery limit.

Payload bodies are never logged.

## 7. Cashfree behavior

Create:

```http
POST /pg/orders/{order_id}/refunds
```

Fetch/reconcile:

```http
GET /pg/orders/{order_id}/refunds/{refund_id}
```

Headers include:

```text
x-client-id
x-client-secret
x-api-version
x-idempotency-key
```

Create body:

```json
{
  "refund_amount": 220.00,
  "refund_id": "CRV<compact-chef-sub-order-uuid>",
  "refund_note": "Craves refund: CHEF_ACCEPTANCE_TIMEOUT",
  "refund_speed": "STANDARD"
}
```

No credential is added to source code, documentation or event payloads.

## 8. Multi-replica safety

Refund and status-outbox workers use:

```text
FOR UPDATE SKIP LOCKED
bounded batch size
lock token
locked_at timestamp
stale-lock recovery
attempt counter
bounded exponential backoff
terminal DEAD_LETTER state
```

Cashfree retries reuse the same merchant refund ID and UUID idempotency key.

## 9. Events

Input:

```text
REFUND_REQUESTED v1
```

Output:

```text
REFUND_STATUS_CHANGED v1
```

Normalized output statuses:

```text
REFUND_PENDING
REFUNDED
REFUND_FAILED
```

The output event publisher stays disabled until Order Service has a matching consumer.

## 10. Validation sequence

### Build-only

```text
Pipeline YAML: azure-pipelines-integration-service-ci.yml
Branch: feature/integration-refund-workflow
```

Expected:

```text
mvn -B clean verify
all event schema JSON files valid
```

### Deploy code only

After merge:

```text
Pipeline: azure-pipelines-integration-service.yml
Branch: main
```

Expected:

```text
new Integration revision healthy
Flyway V100 applied
all CRAVES_REFUND_* switches absent or false
```

### Prepare consumer only

```text
Pipeline: azure-pipelines-integration-refund-consumer-enable.yml
Branch: main
```

Expected:

```text
filtered subscription exists
Integration managed identity has Data Receiver
CRAVES_REFUND_CONSUMER_ENABLED=true
provider execution=false
reconciliation=false
status publisher=false
```

## 11. Manual steps required

- Run build-only CI before merge.
- Run the normal Integration deployment after merge.
- Verify Flyway V100 and the latest healthy revision.
- Run the controlled consumer-enablement pipeline.
- Never paste Cashfree credentials into chat.
- Confirm the existing Integration Container App has sandbox Cashfree credentials through its secret configuration before any provider enablement.
- Keep production payment environment disabled.
- Keep the Order chef timeout worker disabled until a controlled end-to-end refund test passes.

## 12. Pending work

```text
Order Service REFUND_STATUS_CHANGED consumer
customer refund status notifications
controlled Cashfree sandbox execution enablement pipeline
controlled Order REFUND_REQUESTED publisher allow-list update
controlled chef timeout worker enablement
end-to-end paid-order rejection test
DLQ inspection and replay procedure
refund DEAD_LETTER alerting
operations dashboard and reconciliation report
```

No new paid Azure SKU is introduced by this module. The consumer setup adds one subscription entity to the existing Service Bus topic.
