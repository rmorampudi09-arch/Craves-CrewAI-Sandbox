# Craves Order Service - Chef Acceptance Transactional Outbox Handover

Date: 16 July 2026  
Branch: `feature/order-chef-accepted-outbox`  
Service: Order Service  
Event: `CHEF_ACCEPTED_ORDER` version `1.0`  
Classification: Internal Confidential

## 1. Purpose

This module closes the reliability gap between a chef accepting a paid order and the Integration Service receiving the delivery-scheduling event.

Without a transactional outbox, the application could update the order to `CHEF_ACCEPTED` and then fail before publishing to Azure Service Bus. The customer would see an accepted order, but delivery scheduling would never begin. Publishing first and updating the database second creates the opposite risk: delivery could begin for an order that never committed.

The implemented design writes the order update and the event record in the same PostgreSQL transaction. A separate worker publishes the durable event later.

## 2. Authoritative architecture rules applied

The implementation follows the approved HLD baseline:

- Order Service owns chef acceptance and order state.
- `CHEF_ACCEPTED_ORDER` is published by Order Service.
- Integration and Notification Services are consumers.
- Critical database changes plus events use a transactional outbox.
- Service Bus processing is at least once and consumers must be idempotent.
- Delivery must not be created at payment success.
- Each chef-specific order produces one delivery job after chef acceptance.
- Azure managed identity is preferred over static Service Bus credentials.

## 3. Product decisions confirmed in this module

### 3.1 Preparation time

A chef must submit a positive whole number of minutes.

```text
prepTimeMinutes > 0
```

No product-level maximum preparation time is imposed.

### 3.2 Allowed state transition

Only this transition is valid:

```text
CHEF_ACCEPTANCE_PENDING -> CHEF_ACCEPTED
```

Acceptance is rejected from `PAYMENT_PENDING`, `PAID`, `CHEF_REJECTED`, `CANCELLED`, refund states, and completed states.

### 3.3 Repeated acceptance

```text
Same order + same prepTimeMinutes
    -> return existing accepted order
    -> no timestamp change
    -> no second outbox event

Same order + different prepTimeMinutes
    -> HTTP 409
    -> ORDER_ALREADY_ACCEPTED
```

### 3.4 Acceptance timeout

The agreed 30-minute timeout is not implemented in this branch. It remains the next separate module because it also requires reminder scheduling, automatic rejection, refund events, and Cashfree integration.

## 4. Files added

```text
contracts/events/chef-accepted-order-v1.schema.json
azure-pipelines-order-domain-events-enable.yml
scripts/configure-order-domain-event-publisher.sh
services/order-service/CHEF_ACCEPTED_OUTBOX.md
services/order-service/src/main/java/in/craves/order/config/DomainEventOutboxProperties.java
services/order-service/src/main/java/in/craves/order/config/ServiceBusDomainEventProperties.java
services/order-service/src/main/java/in/craves/order/event/DomainEventEnvelope.java
services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventData.java
services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventSource.java
services/order-service/src/main/java/in/craves/order/event/ChefAcceptedOrderEventFactory.java
services/order-service/src/main/java/in/craves/order/event/SerializedDomainEvent.java
services/order-service/src/main/java/in/craves/order/outbox/DomainEventTransport.java
services/order-service/src/main/java/in/craves/order/outbox/OrderDomainOutboxRecord.java
services/order-service/src/main/java/in/craves/order/outbox/OrderDomainOutboxRepository.java
services/order-service/src/main/java/in/craves/order/outbox/ServiceBusDomainEventTransport.java
services/order-service/src/main/java/in/craves/order/outbox/OrderDomainOutboxPublisherWorker.java
services/order-service/src/main/java/in/craves/order/service/ChefAcceptancePolicy.java
services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceService.java
services/order-service/src/main/resources/db/migration/V5__chef_acceptance_domain_outbox.sql
services/order-service/src/test/java/in/craves/order/event/ChefAcceptedOrderEventFactoryTest.java
services/order-service/src/test/java/in/craves/order/service/ChefAcceptancePolicyTest.java
docs/handover/2026-07-16-order-chef-accepted-domain-outbox.md
```

## 5. Files modified

```text
services/order-service/pom.xml
services/order-service/src/main/java/in/craves/order/OrderServiceApplication.java
services/order-service/src/main/java/in/craves/order/web/ChefOrderController.java
services/order-service/src/main/resources/application.yml
```

The existing main Order Service pipeline remains the build/deployment pipeline. A second controlled pipeline enables Service Bus publication only after Azure prerequisites are verified.

## 6. API behavior

Endpoint:

```http
POST /api/v1/chef/orders/{orderId}/accept
Authorization: Bearer <Craves token with CHEF role>
X-Correlation-ID: <optional UUID>
Idempotency-Key: <optional stable request key>
Content-Type: application/json
```

Request:

```json
{
  "prepTimeMinutes": 35,
  "note": "Order confirmed"
}
```

The controller passes correlation and idempotency metadata to `ChefAcceptanceService`.

## 7. Transaction flow

```text
ChefAcceptanceService.accept
    |
    +-- Verify CHEF role and kitchen ownership through existing Order logic
    |
    +-- SELECT order row FOR UPDATE
    |
    +-- ChefAcceptancePolicy decides:
    |      ACCEPT
    |      IDEMPOTENT_SUCCESS
    |      or structured conflict
    |
    +-- UPDATE customer_order
    |      status = CHEF_ACCEPTED
    |      prep_time_minutes = request value
    |      accepted_at = now()
    |      ready_at = now() + prep minutes
    |
    +-- INSERT order_status_history
    |
    +-- Build versioned CHEF_ACCEPTED_ORDER envelope
    |
    +-- INSERT domain_event_outbox
    |
    +-- COMMIT
```

Any failure before commit rolls back the order state, history row, and outbox row together.

## 8. Database migration V5

Migration:

```text
V5__chef_acceptance_domain_outbox.sql
```

### 8.1 customer_order changes

```text
accepted_at TIMESTAMPTZ
```

Constraints:

- preparation time is null or positive;
- a newly written `CHEF_ACCEPTED` row must have preparation time, accepted time, and ready time;
- `ready_at` must be later than `accepted_at`.

Constraints are added as `NOT VALID` so historical rows are not assigned invented values, while new and updated rows are protected.

### 8.2 domain_event_outbox

Important columns:

```text
id
unique event_key
aggregate_type
aggregate_id
event_type
event_version
occurred_at
correlation_id
causation_id
source
subject
payload_json
status
attempts
next_attempt_at
lock_token
locked_at
broker_message_id
published_at
last_error
created_at
updated_at
```

Allowed statuses:

```text
PENDING
PROCESSING
FAILED
PUBLISHED
DEAD
```

Unique key:

```text
CHEF_ACCEPTED_ORDER:<chef-specific-order-id>
```

This prevents a duplicate business event even if the API request is retried.

## 9. Event contract

Schema:

```text
contracts/events/chef-accepted-order-v1.schema.json
```

Envelope:

```text
eventId
eventType
eventVersion
occurredAt
correlationId
causationId
source
subject
data
```

Data contract intentionally matches the existing Integration Service model:

```text
orderId
chefSubOrderId
readyAt
distanceKm
area
deliveryRequest
```

Mapping:

```text
orderId = checkout_id
chefSubOrderId = customer_order.id
readyAt = persisted ready_at
distanceKm = null; Integration derives it from immutable coordinates
area = immutable pickup area snapshot
```

The delivery request contains:

```text
matter
totalWeightGrams
thermoboxRequired
pickup stop
dropoff stop
```

## 10. PII and security

The event contains delivery-required phone numbers, addresses, and coordinates. This is intentional because the existing Integration Service delivery contract consumes immutable pickup and drop-off details.

Controls:

- The event is never returned to customer or chef clients.
- The event remains in Order PostgreSQL and the protected Service Bus topic.
- The publisher logs only event ID, type, and attempt count.
- `payload_json` must not appear in application logs or pipeline output.
- Azure Service Bus access uses managed identity.
- No Service Bus connection string is required or accepted by this implementation.
- Integration consumers must mask PII in errors and telemetry.

## 11. Publisher worker

The scheduled worker is disabled by default.

When enabled, it:

1. claims a batch using `FOR UPDATE SKIP LOCKED`;
2. supports multiple Order Service replicas;
3. gives each claim a lock token;
4. reclaims stale `PROCESSING` rows after the configured lease;
5. sends to `craves-domain-events`;
6. uses outbox UUID as Service Bus `messageId`;
7. marks the row `PUBLISHED` only after send succeeds;
8. applies bounded exponential retry after failure;
9. marks exhausted records `DEAD` for operations review.

If a process crashes after Service Bus accepts a message but before PostgreSQL is marked published, the same event may be sent again. This is expected under at-least-once delivery. Consumers must deduplicate by `eventId` or the chef sub-order ID.

## 12. Runtime configuration

```text
CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED=false
CRAVES_DOMAIN_EVENT_OUTBOX_FIXED_DELAY_MS=5000
CRAVES_DOMAIN_EVENT_OUTBOX_BATCH_SIZE=20
CRAVES_DOMAIN_EVENT_OUTBOX_MAX_ATTEMPTS=10
CRAVES_DOMAIN_EVENT_OUTBOX_RETRY_BASE_DELAY_SECONDS=5
CRAVES_DOMAIN_EVENT_OUTBOX_STALE_LOCK_SECONDS=300

CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED=false
CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=
CRAVES_DOMAIN_EVENTS_TOPIC_NAME=craves-domain-events
```

The two enable flags are intentionally false until managed identity and topic access are confirmed.

Chef acceptance still creates `PENDING` outbox rows while publication is disabled. No event is lost; publication waits until enablement.

## 13. Build and deployment sequence

### Step 1 - merge branch

Merge through a reviewed pull request.

### Step 2 - deploy Order Service

Run:

```text
azure-pipelines-order-service.yml
```

Use the existing default parameters.

Expected result:

- Maven compile and tests pass;
- image is built and pushed;
- Flyway V5 applies during startup;
- new Container App revision becomes healthy;
- domain publisher remains disabled.

### Step 3 - verify Azure prerequisites

Do not enable publication until all of these are true:

- Existing Service Bus namespace is identified.
- Existing `craves-domain-events` topic exists.
- Order Container App has system-assigned or approved user-assigned managed identity.
- Order managed identity has `Azure Service Bus Data Sender` at topic or namespace scope.
- Integration subscription/consumer design is confirmed before live event creation.

### Step 4 - enable publisher

Use the controlled pipeline:

```text
azure-pipelines-order-domain-events-enable.yml
```

Required parameter:

```text
serviceBusFullyQualifiedNamespace = <namespace>.servicebus.windows.net
```

Alternative Cloud Shell validation script:

```bash
SERVICE_BUS_NAMESPACE="<namespace>.servicebus.windows.net" \
  bash scripts/configure-order-domain-event-publisher.sh
```

Neither path creates Azure resources or role assignments.

## 14. Manual steps required

### Azure Portal / Azure CLI

1. Confirm the Service Bus namespace and topic already exist.
2. Confirm Order Container App managed identity.
3. Assign `Azure Service Bus Data Sender` to the Order managed identity if missing.
4. Wait for Azure RBAC propagation.
5. Run the controlled enablement pipeline.

Creating a new Service Bus namespace or changing its SKU is billing-sensitive and is not included in this branch. Stop and obtain explicit approval if the expected namespace/topic does not exist.

### Azure DevOps

- Add the new YAML pipeline `azure-pipelines-order-domain-events-enable.yml`.
- Reuse the existing `AZURE_SERVICE_CONNECTION`.
- Do not add a Service Bus connection string secret.

### Secrets and credentials

No new secret key is required.

Managed identity replaces static Service Bus credentials.

## 15. Test plan

### 15.1 Automated tests

```bash
cd services/order-service
mvn -B clean test
```

Tests cover:

- valid transition from `CHEF_ACCEPTANCE_PENDING`;
- idempotent repeat with same preparation time;
- conflict on different preparation time;
- rejection of `PAYMENT_PENDING` and `PAID` acceptance;
- rejection of non-positive preparation time;
- event envelope and Integration-compatible payload;
- stable causation ID from an idempotency key.

### 15.2 Database verification

After one controlled chef acceptance:

```sql
SELECT
    id,
    status,
    prep_time_minutes,
    accepted_at,
    ready_at
FROM order_schema.customer_order
WHERE id = '<chef-sub-order-id>';
```

Expected:

```text
status = CHEF_ACCEPTED
prep_time_minutes > 0
accepted_at is populated
ready_at > accepted_at
```

Outbox verification without displaying PII:

```sql
SELECT
    id,
    event_key,
    event_type,
    event_version,
    status,
    attempts,
    occurred_at,
    published_at,
    last_error
FROM order_schema.domain_event_outbox
WHERE aggregate_id = '<chef-sub-order-id>';
```

Do not select or paste `payload_json` during routine verification.

### 15.3 Idempotency verification

1. Accept an eligible order with 35 minutes.
2. Repeat with 35 minutes.
3. Verify HTTP success and one outbox row.
4. Repeat with 45 minutes.
5. Verify HTTP 409 and `ORDER_ALREADY_ACCEPTED`.

### 15.4 Publication verification

After publisher enablement:

- outbox row moves `PENDING -> PROCESSING -> PUBLISHED`;
- `broker_message_id` equals event ID;
- Service Bus topic metrics show one successful incoming message;
- no PII appears in Order logs;
- Integration consumer deduplicates retries.

## 16. Operational queries

Pending or failed rows:

```sql
SELECT
    status,
    count(*) AS event_count,
    min(created_at) AS oldest_created_at
FROM order_schema.domain_event_outbox
WHERE status <> 'PUBLISHED'
GROUP BY status
ORDER BY status;
```

Dead records:

```sql
SELECT
    id,
    aggregate_id,
    event_type,
    attempts,
    last_error,
    updated_at
FROM order_schema.domain_event_outbox
WHERE status = 'DEAD'
ORDER BY updated_at DESC;
```

Do not automatically reset or replay a dead event without checking whether Integration already processed the event ID.

## 17. Known risks

### 17.1 Existing Order ownership lookup

Chef ownership currently depends on a synchronous Catalog lookup. Chef acceptance therefore fails safely if ownership cannot be confirmed. A future Order-owned chef-assignment snapshot can remove that runtime dependency.

### 17.2 Existing payment transition gap

The current tested checkout creates `PAYMENT_PENDING`. This module correctly refuses chef acceptance until the verified payment flow moves the order to `CHEF_ACCEPTANCE_PENDING`.

Do not manually change production order status merely to test chef acceptance. Complete the controlled Cashfree/payment transition path first or use a dedicated non-production fixture.

### 17.3 Consumer readiness

Publishing should remain disabled until the Integration Service domain-event subscription is ready. Durable outbox rows can safely accumulate temporarily, but operations must monitor their age.

### 17.4 PII in internal event

The event carries the minimum data required by the current provider-neutral delivery request. Service Bus authorization, retention, diagnostic settings, and consumer logging must be reviewed before production traffic.

## 18. Deferred next module

The next module is:

```text
30-minute acceptance timeout
    -> reminders
    -> automatic CHEF_REJECTED with reason CHEF_ACCEPTANCE_TIMEOUT
    -> REFUND_REQUESTED transactional outbox
    -> Integration Service Cashfree refund command
    -> REFUND_PENDING / REFUNDED lifecycle
```

Explicit chef rejection should use the same refund outcome with reason `CHEF_DECLINED`.

Automatic reassignment is not part of V1 because changing chef, menu, price, and preparation time requires customer consent and additional product rules.

## 19. Pending validation remembered from the prior module

After the Order pipeline synchronization, rerun checkout with an empty cart.

Expected:

```text
HTTP 400
Cart is empty
```

This validates that the pipeline-deployed User-Chef internal shared secret remains correct. This check was explicitly deferred by the product owner and must remain on the pending validation list.
