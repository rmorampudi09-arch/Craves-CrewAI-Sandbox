# Craves Delivery Status Downstream Consumers — Engineering Handover

**Document date:** 2026-07-28  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Branch:** `feature/delivery-status-downstream-consumers`  
**Base commit:** `1e963328e401a2d7931b93ebdbbc11879159ed73`  
**Primary service changed:** Order Service  
**Supporting service verified:** Notification Service  
**Status:** Code pushed to feature branch; branch CI, PR review, merge and Azure rollout pending  
**Confidentiality:** Craves internal engineering and operations

---

## 1. Executive summary

This release completes the safe downstream application path for `DELIVERY_STATUS_CHANGED` v1 events produced by Integration Service.

The release allows Order Service to consume provider-neutral delivery changes, persist a customer-order delivery projection, retain an append-only history and prepare customer in-app notifications through the existing notification outbox.

## 2. Scope completed

The release contains:

- a managed-identity Azure Service Bus consumer;
- strict v1 event validation;
- durable inbox idempotency;
- row locking;
- stale-event protection;
- terminal-state protection;
- separate delivery projection columns;
- append-only delivery history;
- customer notification outbox records;
- a customer delivery-status API;
- Flyway V9;
- unit and migration tests;
- fail-closed deployment safeguards;
- controlled activation, publisher, status and rollback pipelines.

## 3. Scope deliberately excluded

This release does not:

- create a delivery;
- call Borzo;
- enable provider tracking reads;
- register a public callback;
- modify APIM;
- calculate delivery fees;
- decide serviceability radius;
- calculate commission;
- determine refund consequences;
- update FSSAI or other compliance logic;
- activate external email, push or SMS providers.

## 4. Approved architectural basis

The implementation follows the approved event dependency:

```text
Integration Service
  -> DELIVERY_STATUS_CHANGED
  -> Order Service
  -> customer delivery projection and notification outbox
```

The event is delivered at least once, so the consumer is idempotent.

## 5. Existing upstream prerequisite

Integration Service PR #24 is merged and deployed.

The upstream release provides:

- `DELIVERY_STATUS_CHANGED` v1 schema;
- transactional status outbox;
- independently controlled status publisher;
- webhook and tracking reconciliation workers;
- Flyway V102;
- provider-neutral canonical status mapping.

## 6. Current Integration baseline

At the start of this branch:

```text
image: integration-service:140
running: Running
delivery command: false
create reconciliation: false
webhook processing: false
tracking reconciliation: false
status publisher: false
Borzo API: false
```

## 7. Event contract consumed

Envelope fields:

```text
eventId
eventType
eventVersion
occurredAt
correlationId
causationId (nullable)
source
subject
data
```

## 8. Event business data consumed

```text
deliveryJobId
orderId
chefSubOrderId
providerId
providerDeliveryId
status
trackingUrl
observedAt
```

## 9. Identifier interpretation

For the current Order architecture:

- `orderId` is the checkout identifier and correlation identifier;
- `chefSubOrderId` is `order_schema.customer_order.id`;
- `deliveryJobId` is Integration Service's provider-neutral delivery job identifier.

## 10. Event subject rule

The consumer requires:

```text
subject = delivery-job/{deliveryJobId}
```

A mismatch is non-retryable and is dead-lettered.

## 11. Event source rule

Only this source is accepted:

```text
integration-service
```

## 12. Event type and version rule

```text
eventType = DELIVERY_STATUS_CHANGED
eventVersion = 1.0
```

## 13. Canonical statuses

The accepted normalized statuses are:

```text
PENDING
SEARCHING
COURIER_ASSIGNED
COURIER_TO_PICKUP
AT_PICKUP
PICKED_UP
IN_TRANSIT
AT_DROPOFF
DELIVERED
CANCELLED
DELAYED
RETURNING
RETURNED
FAILED
```

## 14. Tracking URL validation

A non-empty tracking URL must be an absolute HTTP or HTTPS URI.

Javascript, file and other schemes are rejected.

## 15. Consumer configuration class

Path:

```text
services/order-service/src/main/java/in/craves/order/config/
  DeliveryStatusConsumerProperties.java
```

## 16. Consumer runtime defaults

```text
CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=false
subscription=order-service-delivery-status-changed
max concurrent messages=2
prefetch=4
max delivery attempts=5
lock auto-renewal=5 minutes
```

## 17. Service Bus processor

Path:

```text
services/order-service/src/main/java/in/craves/order/delivery/
  DeliveryStatusChangedServiceBusProcessor.java
```

## 18. Service Bus receive behavior

The processor uses:

- `PEEK_LOCK`;
- manual completion;
- bounded concurrency;
- bounded prefetch;
- explicit abandon for retry;
- explicit dead-letter reasons.

## 19. Dead-letter reasons

```text
INVALID_DELIVERY_STATUS
DELIVERY_STATUS_NOT_READY
DELIVERY_STATUS_PROCESSING_FAILED
```

## 20. Managed identity behavior

Azure runtime should use:

```text
DefaultAzureCredential
CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE
```

A connection string is supported only for compatibility and local/emergency operation.

## 21. Durable inbox

Flyway V9 creates:

```text
order_schema.delivery_status_inbox
```

The event ID is the primary key.

## 22. Inbox processing outcomes

```text
RECEIVED
PROCESSED
STALE
TERMINAL_PROTECTED
NO_CHANGE
```

## 23. Duplicate event behavior

A repeated event ID does not insert a second inbox row.

The broker message is completed without changing the order projection, history or notification outbox again.

## 24. Transaction boundary

The following actions occur in one PostgreSQL transaction:

```text
insert inbox
lock chef sub-order
validate identifiers and eligibility
decide apply/ignore
update delivery projection
append delivery history
write notification outbox
mark inbox processed
```

## 25. Row locking

The consumer locks the target row using:

```sql
SELECT ...
FROM order_schema.customer_order
WHERE id = ?
FOR UPDATE
```

This prevents two delivery events from racing on one chef-specific order.

## 26. Order identity validation

The consumer verifies:

```text
customer_order.id = chefSubOrderId
customer_order.checkout_id = orderId
```

## 27. Chef acceptance prerequisite

`accepted_at` must exist.

If the event arrives before acceptance metadata is available, the consumer retries rather than applying an incomplete business relationship.

## 28. Refund and cancellation boundary

Delivery projection changes are rejected when the order is already in a commercial state incompatible with a new delivery update:

```text
CHEF_REJECTED
CANCELLED
REFUND_PENDING
REFUNDED
REFUND_FAILED
```

## 29. Delivery identity immutability

After the first applied event, the following identifiers cannot change:

```text
deliveryJobId
providerId
providerDeliveryId
```

A change is treated as non-retryable because it could attach one provider transaction to another order.

## 30. Stale-event rule

An incoming event is stale when:

```text
incoming.observedAt <= current.delivery_status_observed_at
```

The projection is not regressed.

## 31. Terminal-state rule

These projection statuses are terminal:

```text
DELIVERED
CANCELLED
RETURNED
FAILED
```

A later different status cannot replace them.

## 32. No-change rule

A newer event is marked `NO_CHANGE` when normalized status and tracking URL are unchanged.

This avoids noisy history and duplicate customer notices.

## 33. Separate delivery projection decision

Provider callbacks update dedicated `delivery_*` columns.

They do not update `customer_order.status`.

## 34. Why commercial order status is not changed

A provider status does not by itself define:

- payment settlement;
- refund eligibility;
- customer compensation;
- chef liability;
- support escalation;
- final commercial completion.

Those are product decisions and remain outside this module.

## 35. Projection columns

V9 adds:

```text
delivery_job_id
delivery_provider_id
delivery_provider_delivery_id
delivery_status
delivery_tracking_url
delivery_status_observed_at
delivery_status_event_id
```

## 36. Projection uniqueness

Partial unique indexes protect:

```text
delivery_job_id
delivery_status_event_id
```

when they are non-null.

## 37. Applied history

V9 creates:

```text
order_schema.order_delivery_status_history
```

It contains only applied delivery changes.

## 38. Raw payload location

The raw event JSON is retained in the delivery inbox.

It is not returned by the customer API and is not copied into customer notifications.

## 39. Customer notification architecture

The current path is:

```text
Order delivery consumer
  -> order_schema.notification_outbox
  -> existing Order notification dispatcher
  -> Notification Service internal API
  -> notification_request
  -> in_app_notification
```

## 40. Why there is no direct Notification Service topic consumer

`DELIVERY_STATUS_CHANGED` v1 does not include customer or chef recipient identity IDs.

Notification Service must not read Order Service tables directly.

Order Service already owns the customer relationship, so it resolves the recipient from its own locked row.

## 41. Future event v2 option

A future reviewed contract could add recipient data such as:

```text
customerIdentityId
chefIdentityId
recipient roles
```

Only then should a direct Notification Service subscription be considered.

## 42. Notification idempotency

Notification event key:

```text
delivery-status-{eventId}
```

The existing unique outbox key prevents duplicate customer notices.

## 43. Notification statuses

Customer notices are created for meaningful milestones:

```text
COURIER_ASSIGNED
AT_PICKUP
PICKED_UP
IN_TRANSIT
AT_DROPOFF
DELIVERED
DELAYED
CANCELLED
FAILED
RETURNING
RETURNED
```

## 44. Internal statuses without notification

No customer notice is created for:

```text
PENDING
SEARCHING
COURIER_TO_PICKUP
```

These can change frequently or do not yet provide useful customer action.

## 45. Notification privacy

The notification payload excludes:

- raw callback payload;
- phone numbers;
- delivery addresses;
- provider authentication data;
- provider delivery identifier.

## 46. Customer query endpoint

```http
GET /api/v1/orders/{orderId}/delivery-status
```

## 47. Query authorization

The endpoint requires:

- an authenticated Craves principal;
- the `CUSTOMER` role;
- ownership of the requested order.

## 48. Query response

The response contains:

```text
orderId
deliveryJobId
providerId
status
trackingUrl
observedAt
normalized history
```

## 49. Query exclusions

The response does not expose:

- provider delivery ID;
- raw provider status;
- raw callback JSON;
- internal inbox results;
- delivery audit errors;
- secrets.

## 50. Migration

Path:

```text
services/order-service/src/main/resources/db/migration/
  V9__delivery_status_consumer.sql
```

## 51. Migration safety

V9 is additive.

It does not:

- backfill historical delivery status;
- enable the consumer;
- enable the publisher;
- delete an order;
- rewrite commercial order status.

## 52. Application configuration update

Path:

```text
services/order-service/src/main/resources/application.yml
```

The new consumer is explicitly disabled by default.

## 53. Application bootstrap update

Path:

```text
services/order-service/src/main/java/in/craves/order/
  OrderServiceApplication.java
```

`DeliveryStatusConsumerProperties` is registered with existing configuration properties.

## 54. Normal deployment hardening

Path:

```text
azure-pipelines-order-service.yml
```

Every normal Order deployment explicitly writes:

```text
CRAVES_DELIVERY_STATUS_CONSUMER_ENABLED=false
```

## 55. CI pipeline

```text
azure-pipelines-delivery-status-downstream-ci.yml
```

It runs:

- Order Service Java 21 `clean verify`;
- Notification Service Java 21 `clean verify`;
- JSON schema parsing;
- fail-closed configuration checks.

## 56. Order consumer activation pipeline

```text
azure-pipelines-order-delivery-status-consumer-enable.yml
```

It:

- uses the existing Service Bus namespace;
- creates the filtered subscription if missing;
- removes the default catch-all rule;
- creates the approved SQL filter;
- verifies managed-identity Receiver access;
- enables only the Order delivery consumer.

## 57. Subscription filter

Approved SQL filter accepts the event property set by Integration Service:

```text
eventType = 'DELIVERY_STATUS_CHANGED'
OR event_type = 'DELIVERY_STATUS_CHANGED'
```

## 58. Integration publisher activation pipeline

```text
azure-pipelines-integration-delivery-status-publisher-enable.yml
```

It refuses activation unless:

- the Order consumer is enabled;
- the subscription exists;
- the filter is correct;
- the Order delivery DLQ is empty;
- Integration has Service Bus Sender access;
- delivery command, reconciliation, webhook, tracking and Borzo remain disabled.

## 59. Rollout status pipeline

```text
azure-pipelines-delivery-status-rollout-status.yml
```

It is read-only and reports:

- Order revision and consumer flag;
- notification dispatcher flag;
- Integration delivery flags;
- Borzo flag;
- subscription active/DLQ counts;
- filter rules.

## 60. Rollback pipeline

```text
azure-pipelines-delivery-status-rollback.yml
```

It returns the event flow to fail-closed state without deleting durable records.

## 61. Test classes

```text
DeliveryStatusConsumerPropertiesTest
DeliveryStatusEventValidatorTest
DeliveryStatusTransitionPolicyTest
DeliveryStatusCustomerNotificationServiceTest
DeliveryStatusMigrationTest
```

## 62. Test coverage intent

Tests cover:

- fail-closed defaults;
- exact contract acceptance;
- wrong subject rejection;
- unsupported status rejection;
- unsafe tracking URL rejection;
- first/newer application;
- stale protection;
- terminal protection;
- no-change detection;
- notification idempotency key;
- notification privacy;
- migration safeguards.

## 63. Branch state before CI

```text
branch: feature/delivery-status-downstream-consumers
base: main
base SHA: 1e963328e401a2d7931b93ebdbbc11879159ed73
```

## 64. Required CI result

```text
Compile and test Order Service delivery consumer — Succeeded
Verify Notification Service compatibility — Succeeded
Validate all domain-event schema JSON files — Succeeded
Verify fail-closed delivery controls — Succeeded
```

## 65. Required PR review

Review must confirm:

- no provider create call exists;
- no Order commercial status mutation exists;
- stale and terminal protection are correct;
- inbox and projection writes are transactional;
- recipient identity is resolved only from Order-owned data;
- raw callback payload is not exposed publicly;
- normal deployment returns the consumer to false.

## 66. Merge rule

Merge only the exact branch head tested by CI.

Any commit after the successful run requires a new CI run.

## 67. Deployment pipeline order

After merge:

```text
1. azure-pipelines-order-service.yml
2. azure-pipelines-delivery-status-rollout-status.yml
3. azure-pipelines-order-delivery-status-consumer-enable.yml
4. azure-pipelines-delivery-status-rollout-status.yml
5. controlled synthetic Order consumer validation
6. azure-pipelines-integration-delivery-status-publisher-enable.yml
7. controlled end-to-end synthetic publication
8. APIM route addition
```

## 68. Initial deployment acceptance

Required after normal Order deployment:

```text
new Order image deployed
latest revision ready
running status Running
Flyway V9 successful
delivery status consumer false
```

## 69. Consumer activation acceptance

Required:

```text
subscription active
approved SQL filter only
Order managed identity has Receiver
Order consumer true
Order revision ready
Order DLQ zero
```

## 70. Publisher activation acceptance

Required:

```text
Order consumer true
Order DLQ zero
Integration managed identity has Sender
status publisher true
delivery command false
create reconciliation false
webhook false
tracking false
Borzo false
```

## 71. Synthetic validation plan

Create one synthetic, nonfinancial chef-specific order fixture in Order database and one synthetic `DELIVERY_STATUS_CHANGED` event.

Use exact UUIDs and clean only those rows after validation.

## 72. Synthetic validation assertions

For one first valid event:

```text
one inbox row PROCESSED
one customer_order projection update
one delivery history row
zero duplicate history
one notification outbox row when milestone is meaningful
```

## 73. Duplicate validation assertions

For the same event ID delivered again:

```text
one inbox row total
one history row total
one notification outbox row total
no projection regression
```

## 74. Stale validation assertions

For an older observation timestamp:

```text
inbox result STALE
projection unchanged
no applied history row
no notification outbox row
```

## 75. Terminal validation assertions

For `DELIVERED` followed by a newer different status:

```text
inbox result TERMINAL_PROTECTED
projection remains DELIVERED
no extra applied history
no extra notification
```

## 76. API validation

After APIM mapping, call:

```http
GET /api/v1/orders/{syntheticOrderId}/delivery-status
Authorization: Bearer <Firebase-backed Craves access token>
```

Verify only the owning customer receives the projection.

## 77. APIM work deferred until deployment

The APIM operation should be added only after the Order revision containing the endpoint is deployed.

Proposed public operation:

```text
GET /api/v1/orders/{orderId}/delivery-status
backend: Order Service
JWT policy: same as existing customer Order operations
```

## 78. APIM policy requirements

- require the existing authenticated customer JWT;
- preserve correlation ID;
- do not cache personalized responses;
- do not log authorization headers;
- do not expose the internal Container App hostname;
- apply the existing Order API rate-limit policy.

## 79. Notification dispatcher dependency

Customer notification rows remain durable in `order_schema.notification_outbox` while:

```text
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=false
```

The dispatcher should be enabled only after the internal Notification URL, shared key reference and expected backlog count are verified.

## 80. Notification Service dependency

Notification Service must remain healthy and its internal endpoint must accept idempotent `requestKey` values.

No Notification Service schema change is required by this release.

## 81. Manual Azure RBAC step

The activation pipeline may stop with exit code 2 when the Order managed identity lacks:

```text
Azure Service Bus Data Receiver
```

Grant the role at the exact subscription or namespace scope printed by the pipeline, then rerun.

## 82. Azure resource impact

The activation pipeline may create one topic subscription and one SQL rule inside the existing Service Bus namespace.

It does not create a new namespace, tier, Container App, database or APIM instance.

## 83. Billing warning

The subscription uses an already provisioned Service Bus namespace. It does not introduce a separate Azure SKU, but message operations continue to count against the existing namespace usage.

## 84. Secret handling

No secret is stored in this branch.

Do not paste:

- Service Bus connection strings;
- internal notification keys;
- Firebase credentials;
- provider tokens;
- database passwords.

## 85. Logging boundary

Logs include event IDs, message IDs and normalized result names.

Logs must not include:

- raw callback payload;
- delivery address;
- phone number;
- authorization token;
- provider token.

## 86. Monitoring queries

Inbox outcomes:

```sql
SELECT processing_status, COUNT(*)
FROM order_schema.delivery_status_inbox
GROUP BY processing_status
ORDER BY processing_status;
```

## 87. Monitoring current projection

```sql
SELECT delivery_status, COUNT(*)
FROM order_schema.customer_order
WHERE delivery_status IS NOT NULL
GROUP BY delivery_status
ORDER BY delivery_status;
```

## 88. Monitoring notification backlog

```sql
SELECT status, COUNT(*)
FROM order_schema.notification_outbox
WHERE event_key LIKE 'delivery-status-%'
GROUP BY status
ORDER BY status;
```

## 89. Monitoring ignored events

```sql
SELECT processing_status, COUNT(*)
FROM order_schema.delivery_status_inbox
WHERE processing_status IN ('STALE', 'TERMINAL_PROTECTED', 'NO_CHANGE')
GROUP BY processing_status;
```

## 90. Monitoring Service Bus DLQ

Use the rollout status pipeline or Azure CLI to watch:

```text
order-service-delivery-status-changed
countDetails.deadLetterMessageCount
```

## 91. Operational alert recommendations

Create alerts later for:

- delivery-status DLQ greater than zero;
- oldest active delivery-status message above the agreed threshold;
- Order consumer revision unhealthy;
- notification delivery-status backlog above the agreed threshold;
- repeated invalid-contract dead letters.

Threshold values are operational decisions and are not hard-coded here.

## 92. Failure containment

A consumer failure does not call a provider and does not cancel a delivery.

The event remains in Service Bus for retry or moves to DLQ according to bounded delivery attempts.

## 93. Database rollback policy

Do not delete V9 or manually edit Flyway history.

V9 is additive and safe while the consumer is disabled.

Any future removal must be a reviewed forward migration.

## 94. Service rollback policy

Disable the consumer and publisher first.

Retain:

- inbox evidence;
- applied history;
- notification outbox;
- provider audit;
- Service Bus DLQ.

## 95. Provider safety state

This branch never authorizes:

```text
BORZO_API_ENABLED=true
CRAVES_DELIVERY_COMMAND_ENABLED=true
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=true
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=true
```

## 96. Product decisions still pending

The following remain outside engineering inference:

- delivery cancellation consequences;
- automatic refund after delivery failure;
- compensation rules;
- commission responsibility;
- delivery radius;
- customer support escalation timings;
- chef penalties;
- notification wording approval for production localization.

## 97. UI work still pending

Web and React Native clients must later consume the APIM delivery-status endpoint and render:

- current status;
- tracking link;
- timeline;
- safe delay/failure support guidance.

## 98. Direct provider work still pending

After downstream validation:

- register public callback URL;
- validate signature through APIM/backend route;
- perform controlled provider sandbox tracking;
- only later consider provider create activation.

## 99. Immediate next action

Run branch CI against:

```text
feature/delivery-status-downstream-consumers
azure-pipelines-delivery-status-downstream-ci.yml
```

## 100. Final handover state

Code and rollout controls are present on the feature branch.

No production activation has occurred.

The correct next gate is CI, followed by exact-head review and merge—not APIM or provider activation.
