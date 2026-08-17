# Craves Order Chef-Acceptance Timeout and Refund Trigger Handover

Date: 2026-07-16  
Branch: `feature/order-chef-timeout-refund-trigger`  
Service: Order Service  
Database: `craves_business_db`, schema `order_schema`  
Status: implementation complete, build validation pending

## 1. Purpose

This change adds the Order Service side of the customer-protection flow used when a paid chef-specific order is not accepted by its kitchen.

The approved V1 rule is:

```text
Verified payment
    -> CHEF_ACCEPTANCE_PENDING
    -> chef has 30 minutes to decide
```

Possible outcomes:

```text
Accept before expiry
    -> CHEF_ACCEPTED
    -> existing CHEF_ACCEPTED_ORDER event

Explicit reject
    -> CHEF_REJECTED
    -> CHEF_DECLINED
    -> durable REFUND_REQUESTED record

No response for 30 minutes
    -> CHEF_REJECTED
    -> CHEF_ACCEPTANCE_TIMEOUT
    -> durable REFUND_REQUESTED record
```

The customer refund request is scoped to the rejected chef-specific order. Other accepted kitchen orders in the same checkout continue normally.

## 2. Business decisions applied

The user approved a customer-protective marketplace behavior based on common food-delivery marketplace expectations.

Locked decisions:

```text
Chef response timeout: 30 minutes
Initial chef notification: immediately after payment
First reminder: 10 minutes
Second reminder: 20 minutes
Timeout: 30 minutes
Explicit rejection refund: full chef-sub-order grand_total
Timeout refund: full chef-sub-order grand_total
Automatic reassignment: not implemented in V1
Refund deductions: none introduced
Preparation-time maximum: none introduced
```

No pricing, commission, GST, delivery-radius, FSSAI, cancellation fee, or legal policy was added.

## 3. Architecture alignment

The module follows the existing Craves architecture:

```text
Order Service owns order state
PostgreSQL owns durable transaction state
Critical domain events use transactional outbox
Azure Service Bus remains the domain event transport
Integration Service owns Cashfree interaction
Notification Service owns user-facing notification delivery
```

Order Service does not call Cashfree directly.

## 4. Safety boundary

This branch deliberately separates durable refund intent from live refund execution.

Defaults after deployment:

```text
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

Consequences:

```text
Flyway V6 can deploy safely.
Payment callbacks can create 30-minute windows.
Explicit rejection can create a durable REFUND_REQUESTED outbox row.
REFUND_REQUESTED is not claimed by the Service Bus publisher yet.
Automatic timeout processing does not run yet.
CHEF_ACCEPTED_ORDER publication continues normally.
```

The refund event remains stored with `PENDING` status until a later Integration Service module enables that event type.

This prevents the existing delivery consumer from receiving an unsupported refund message and prevents customers from being promised an automatically executed Cashfree refund before that consumer exists.

## 5. Files added

```text
contracts/events/refund-requested-v1.schema.json
services/order-service/CHEF_ACCEPTANCE_TIMEOUT.md
services/order-service/src/main/java/in/craves/order/config/ChefAcceptanceWindowProperties.java
services/order-service/src/main/java/in/craves/order/event/RefundRequestedEventData.java
services/order-service/src/main/java/in/craves/order/event/RefundRequestedEventFactory.java
services/order-service/src/main/java/in/craves/order/event/RefundRequestedEventSource.java
services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceInitialNotificationService.java
services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceResolutionService.java
services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceWindowWorker.java
services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceWorkRepository.java
services/order-service/src/main/resources/db/migration/V6__chef_acceptance_timeout_refund_trigger.sql
services/order-service/src/test/java/in/craves/order/config/ChefAcceptanceWindowPropertiesTest.java
services/order-service/src/test/java/in/craves/order/config/DomainEventOutboxPropertiesTest.java
services/order-service/src/test/java/in/craves/order/event/RefundRequestedEventFactoryTest.java
```

## 6. Files modified

```text
azure-pipelines-order-service-ci.yml
services/order-service/src/main/java/in/craves/order/OrderServiceApplication.java
services/order-service/src/main/java/in/craves/order/config/DomainEventOutboxProperties.java
services/order-service/src/main/java/in/craves/order/outbox/OrderDomainOutboxPublisherWorker.java
services/order-service/src/main/java/in/craves/order/outbox/OrderDomainOutboxRepository.java
services/order-service/src/main/java/in/craves/order/service/ChefAcceptanceService.java
services/order-service/src/main/java/in/craves/order/service/NotificationOutboxRepository.java
services/order-service/src/main/java/in/craves/order/service/PaymentCallbackService.java
services/order-service/src/main/java/in/craves/order/web/ChefOrderController.java
services/order-service/src/main/resources/application.yml
```

## 7. Payment-to-acceptance transition

`PaymentCallbackService.markCheckoutPaid(...)` remains the only current code path that moves paid orders into chef acceptance.

Before this change:

```text
status = CHEF_ACCEPTANCE_PENDING
```

After this change:

```text
status = CHEF_ACCEPTANCE_PENDING
chef_acceptance_requested_at = database now
chef_acceptance_expires_at = database now + 30 minutes
initial/reminder markers = null
rejection/refund markers = null
```

PostgreSQL time is used to avoid differences between Container App replica clocks.

Repeated payment callbacks do not reset orders that are already in `CHEF_ACCEPTANCE_PENDING` or later states.

## 8. Acceptance after expiry

Chef acceptance now locks the order row and reads:

```text
status
prep_time_minutes
chef_acceptance_expires_at
database now
```

An acceptance request is rejected when the deadline has passed:

```text
HTTP 409
CHEF_ACCEPTANCE_EXPIRED
```

The existing idempotent behavior is preserved:

```text
Already accepted + same prep time
    -> existing accepted order

Already accepted + different prep time
    -> ORDER_ALREADY_ACCEPTED
```

## 9. Explicit rejection

Endpoint:

```http
POST /api/v1/chef/orders/{orderId}/reject
```

Optional headers:

```text
X-Correlation-ID
Idempotency-Key
```

The service:

```text
validates CHEF role
validates kitchen ownership
locks the order row
requires CHEF_ACCEPTANCE_PENDING
changes status to CHEF_REJECTED
sets chef_rejection_code = CHEF_DECLINED
copies grand_total to refund_requested_amount
sets refund_requested_at using database time
writes order_status_history
writes REFUND_REQUESTED domain outbox event
writes customer notification outbox record
commits all database changes together
```

Repeated explicit rejection is idempotent when the existing rejection code is `CHEF_DECLINED`.

## 10. Automatic timeout

The scheduled worker is present but disabled by default.

When enabled, every cycle performs bounded scans:

```text
expired orders
initial notifications
10-minute reminders
20-minute reminders
```

Actual actions lock and recheck each row inside a transaction. Multiple Order Service replicas may scan the same candidate, but only one can record the state transition or reminder marker.

Timeout transaction:

```text
CHEF_ACCEPTANCE_PENDING
    -> CHEF_REJECTED
chef_rejection_code = CHEF_ACCEPTANCE_TIMEOUT
refund_requested_amount = grand_total
refund_requested_at = database now
order status history inserted
REFUND_REQUESTED inserted
customer notification inserted
```

## 11. Notification records

Stable notification keys:

```text
chef-new-order-{orderId}
chef-acceptance-reminder-10-{orderId}
chef-acceptance-reminder-20-{orderId}
refund-requested-order-{orderId}
```

Notification audiences:

```text
Initial, 10-minute, and 20-minute notifications -> CHEF
Refund requested notification -> CUSTOMER
```

The existing notification outbox dispatcher remains responsible for sending records to Notification Service.

## 12. REFUND_REQUESTED event

Event envelope:

```json
{
  "eventId": "uuid",
  "eventType": "REFUND_REQUESTED",
  "eventVersion": "1.0",
  "occurredAt": "2026-07-16T12:30:00Z",
  "correlationId": "uuid",
  "causationId": "uuid",
  "source": "order-service",
  "subject": "chef-sub-order-uuid",
  "data": {
    "checkoutId": "uuid",
    "chefSubOrderId": "uuid",
    "customerIdentityId": "uuid",
    "refundAmount": 220.00,
    "currency": "INR",
    "reason": "CHEF_ACCEPTANCE_TIMEOUT",
    "requestedAt": "2026-07-16T12:30:00Z"
  }
}
```

The event intentionally does not contain a guessed Cashfree order ID. Integration Service owns `payment_schema.payment_order` and resolves the payment using `checkoutId`.

## 13. Domain-event publication allow-list

The existing domain outbox publisher previously claimed every pending event.

It now accepts an allow-list:

```text
CRAVES_DOMAIN_EVENT_ENABLED_TYPES
```

Default:

```text
CHEF_ACCEPTED_ORDER
```

The repository adds `event_type IN (...)` to the `FOR UPDATE SKIP LOCKED` claim query.

This allows the Order Service to store future event contracts transactionally before their consumers are enabled, without sending unsupported messages.

Later Integration enablement value:

```text
CHEF_ACCEPTED_ORDER,REFUND_REQUESTED
```

Do not apply that value in this module.

## 14. Flyway V6

Migration:

```text
V6__chef_acceptance_timeout_refund_trigger.sql
```

Columns:

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

Constraints:

```text
CHEF_ACCEPTANCE_PENDING requires a valid requested/expires window
rejection code is limited to approved V1 values
refund request requires CHEF_REJECTED, a reason, and positive amount
```

Indexes:

```text
acceptance expiry scan
initial notification scan
10-minute reminder scan
20-minute reminder scan
```

Existing pending rows receive a new 30-minute window from migration execution. This avoids immediately timing out an old test order based on an invented historical deadline.

## 15. Configuration

```text
CRAVES_CHEF_ACCEPTANCE_TIMEOUT_MINUTES=30
CRAVES_CHEF_ACCEPTANCE_FIRST_REMINDER_MINUTES=10
CRAVES_CHEF_ACCEPTANCE_SECOND_REMINDER_MINUTES=20
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_CHEF_ACCEPTANCE_WORKER_FIXED_DELAY_MS=30000
CRAVES_CHEF_ACCEPTANCE_WORKER_BATCH_SIZE=20
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

No secrets are introduced.

## 16. Build validation

Run the existing build-only pipeline from the feature branch:

```text
azure-pipelines-order-service-ci.yml
branch: feature/order-chef-timeout-refund-trigger
```

It performs:

```text
Java 21 setup
mvn -B clean verify
JSON syntax validation for every contracts/events/*.schema.json file
```

Expected result:

```text
BUILD SUCCESS
all tests passed
all event schema JSON files valid
```

## 17. Deployment procedure

After CI succeeds and the PR is merged:

```text
Pipeline: azure-pipelines-order-service.yml
Branch: main
Parameters: existing defaults
```

Expected deployment:

```text
Maven build and tests
new image pushed to existing ACR
Flyway V6 applied during startup
new Order Container App revision becomes healthy
```

No new Azure resource or paid SKU is created.

## 18. Post-deployment verification

Verify the revision:

```bash
RG="rg-craves-prodlow-centralindia"
APP="ca-craves-order-service-prodlow"

az containerapp show \
  --resource-group "$RG" \
  --name "$APP" \
  --query "{latestRevision:properties.latestRevisionName,latestReadyRevision:properties.latestReadyRevisionName,runningStatus:properties.runningStatus}" \
  -o table
```

Verify safety settings:

```bash
az containerapp show \
  --resource-group "$RG" \
  --name "$APP" \
  --query "properties.template.containers[0].env[?name=='CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED' || name=='CRAVES_DOMAIN_EVENT_ENABLED_TYPES'].{Name:name,Value:value}" \
  -o table
```

Safe result:

```text
worker absent or false
event types absent or CHEF_ACCEPTED_ORDER
```

## 19. Manual steps required

### Azure DevOps

- Run build-only CI from the feature branch.
- Merge only after CI succeeds.
- Run the existing Order Service deployment pipeline from `main`.

### Azure Portal / Cloud Shell

- Confirm Flyway V6 through startup logs or schema history.
- Confirm the newest Order revision is healthy.
- Do not enable the timeout worker.
- Do not add `REFUND_REQUESTED` to enabled event types.

### Secrets

No new secret is required.

### Billing

No new Azure resource and no new paid SKU is required.

## 20. Local testing limitations

A genuine full test requires:

```text
verified Cashfree payment callback
real CHEF identity token
order owned by that chef
Notification Service runtime settings
Integration Service refund consumer
```

This module should therefore be validated first through unit tests, Flyway startup, and database/outbox inspection with the worker disabled.

Do not bypass payment by directly changing production order rows.

## 21. Pending integration module

The next module must implement in Integration Service:

```text
new Service Bus subscription for REFUND_REQUESTED
SQL filter on application property eventType
managed-identity receiver validation
idempotent payment lookup by checkoutId
refund request table workflow
Cashfree sandbox refund API adapter
provider execution disabled by default
retry and reconciliation
REFUND_STATUS_CHANGED event
Order Service consumer for refund state
DLQ tests
```

After that module succeeds:

```text
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER,REFUND_REQUESTED
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=true
```

Those two settings must be enabled together in a controlled pipeline.

## 22. Known risks and mitigations

### Risk: consumer not ready

Mitigation: refund events are excluded from the publisher allow-list.

### Risk: multiple Container App replicas process the same timeout

Mitigation: row lock, state recheck, unique event key, and unique notification keys.

### Risk: payment callback is repeated

Mitigation: only pre-acceptance payment states open the window.

### Risk: chef accepts at or after deadline

Mitigation: acceptance reads the database deadline while holding the order row lock.

### Risk: one chef fails in a multi-chef checkout

Mitigation: refund amount comes only from that chef-specific order `grand_total`.

### Risk: customer expects immediate bank credit

Mitigation: customer wording says the refund was requested. Final completion notification is deferred until Cashfree status is confirmed.

## 23. Final achieved state

After this branch is merged and deployed safely:

```text
Payment starts a durable 30-minute chef window.
Chef acceptance cannot occur after expiry.
Explicit rejection creates a durable refund request.
Timeout/reminder worker code is deployed but disabled.
REFUND_REQUESTED contract is versioned and tested.
Refund events remain held until Integration Service is ready.
CHEF_ACCEPTED_ORDER publication remains operational.
```
