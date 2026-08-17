# Craves Technical Handover — Order Refund Status Consumer

**Date:** 16 July 2026  
**Service:** Order Service  
**Branch:** `feature/order-refund-status-consumer`  
**Status:** Build validation pending

## 1. Purpose

This module closes the Order Service side of the refund-status loop. Integration Service will eventually publish `REFUND_STATUS_CHANGED` after Cashfree create-refund or reconciliation results. Order Service consumes that event and updates only the affected chef-specific order.

## 2. Architecture alignment

The implementation follows the approved event-driven rules already used by Craves:

- Azure Service Bus delivery is at least once.
- Consumers must be idempotent.
- Event bodies use the standard event envelope.
- Managed identity is preferred for Azure authentication.
- One multi-chef checkout may contain independent chef-specific orders.
- Only the rejected or expired chef-specific order changes refund state.

## 3. Files added

```text
azure-pipelines-order-refund-status-consumer-enable.yml
services/order-service/REFUND_STATUS_CONSUMER.md
services/order-service/src/main/java/in/craves/order/config/RefundStatusConsumerProperties.java
services/order-service/src/main/java/in/craves/order/refund/RefundStatusModels.java
services/order-service/src/main/java/in/craves/order/refund/RefundStatusEventValidator.java
services/order-service/src/main/java/in/craves/order/refund/RefundStatusTransitionPolicy.java
services/order-service/src/main/java/in/craves/order/refund/RefundStatusUpdateService.java
services/order-service/src/main/java/in/craves/order/refund/RefundStatusChangedServiceBusProcessor.java
services/order-service/src/main/resources/db/migration/V7__refund_status_consumer.sql
services/order-service/src/test/java/in/craves/order/config/RefundStatusConsumerPropertiesTest.java
services/order-service/src/test/java/in/craves/order/refund/RefundStatusEventValidatorTest.java
services/order-service/src/test/java/in/craves/order/refund/RefundStatusTransitionPolicyTest.java
```

Modified:

```text
services/order-service/src/main/java/in/craves/order/OrderServiceApplication.java
services/order-service/src/main/java/in/craves/order/web/ApiDtos.java
services/order-service/src/main/resources/application.yml
```

## 4. Runtime configuration

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=false
CRAVES_REFUND_STATUS_SUBSCRIPTION=order-service-refund-status-changed
CRAVES_REFUND_STATUS_MAX_CONCURRENT_MESSAGES=2
CRAVES_REFUND_STATUS_PREFETCH_COUNT=4
CRAVES_REFUND_STATUS_MAX_DELIVERY_ATTEMPTS=5
```

The existing namespace and topic variables are reused:

```text
CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE
CRAVES_DOMAIN_EVENTS_TOPIC_NAME
```

## 5. Database changes

Flyway V7 adds the following provider-derived fields to `order_schema.customer_order`:

```text
refund_id
refund_reference
refund_provider_status
cf_refund_id
refund_status_event_id
refund_status_updated_at
refund_completed_at
refund_failed_at
```

It also creates:

```text
order_schema.refund_status_inbox
```

The inbox event ID is the primary key. This makes repeated Service Bus delivery idempotent.

## 6. Validation rules

The consumer requires:

```text
eventType = REFUND_STATUS_CHANGED
eventVersion = 1.0
source = integration-service
subject = chefSubOrderId
correlationId = checkoutId
currency = INR
reason = CHEF_DECLINED or CHEF_ACCEPTANCE_TIMEOUT
refundAmount > 0
```

Provider mapping is enforced:

```text
REFUND_PENDING -> PENDING or ONHOLD
REFUNDED       -> SUCCESS
REFUND_FAILED  -> FAILED or CANCELLED
```

The locked order must match:

```text
checkoutId
customerIdentityId
currency
chef rejection reason
original refund-request amount
existing refund ID/reference, when already stored
```

## 7. Ordering and terminal safety

Events with an `updatedAt` timestamp that is not newer than the stored refund timestamp are marked `STALE` and completed without updating the order.

A `REFUNDED` order is never downgraded to pending or failed by a later out-of-order event.

A newer success event may recover an earlier failed state if operations or provider reconciliation later confirms the refund.

## 8. Service Bus behavior

```text
Namespace: sb-craves-prodlow-l3ing6
Topic: craves-domain-events
Subscription: order-service-refund-status-changed
Rule: refund-status-changed-only
SQL filter: eventType = 'REFUND_STATUS_CHANGED'
```

The processor uses:

```text
PEEK_LOCK
manual completion
bounded concurrent calls
bounded prefetch
five application delivery attempts
explicit dead-letter reasons
```

Invalid or financially inconsistent events are dead-lettered. Temporarily missing order/refund-request state is retried before dead-lettering.

## 9. Status changes

Order Service updates `customer_order.status` to one of:

```text
REFUND_PENDING
REFUNDED
REFUND_FAILED
```

`REFUND_FAILED` was added to the API enum so customer and chef order reads do not fail when that state is stored.

A status-history row is written only when the normalized status changes.

## 10. Safety state after normal deployment

Normal deployment must leave:

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=false
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

Therefore:

- Integration status events are not consumed yet.
- Order timeout automation remains paused.
- `REFUND_REQUESTED` remains excluded from Order publication.
- Cashfree execution remains disabled in Integration Service.

## 11. Validation sequence

1. Run the existing Order CI pipeline from `feature/order-refund-status-consumer`.
2. Merge only after `mvn -B clean verify` succeeds.
3. Run the normal Order deployment from `main`.
4. Confirm Flyway V7 and a healthy revision.
5. Register and run `azure-pipelines-order-refund-status-consumer-enable.yml`.
6. If the pipeline reports a missing Receiver role, grant it once using an authorized Azure account and rerun.
7. Send an invalid `REFUND_STATUS_CHANGED` event and confirm dead-lettering.
8. Send a controlled valid event tied to a synthetic rejected order and confirm status persistence plus cleanup.
9. Only after those tests, enable the Integration refund-status publisher.

## 12. Manual steps required

- Register the new enablement YAML once in Azure DevOps.
- Reuse `AZURE_SERVICE_CONNECTION = Craves-Dev-Service-Connection`.
- The Order managed identity may require a one-time `Azure Service Bus Data Receiver` role assignment.
- Do not paste Cashfree credentials into chat, Git, pipeline logs or test events.
- Do not enable Cashfree, reconciliation, Order refund publication or the timeout worker during this module.

## 13. Pending work

```text
Customer refund status notification templates
Integration REFUND_STATUS_CHANGED publisher enablement
Cashfree sandbox refund execution
Cashfree refund reconciliation
Order REFUND_REQUESTED allow-list update
Chef acceptance timeout worker enablement
End-to-end paid-order timeout and refund test
Refund DLQ alerting and replay runbook
```
