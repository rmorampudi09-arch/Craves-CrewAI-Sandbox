# Chef Acceptance Timeout Module

This module extends Craves Order Service with a customer-protective response window for paid chef-specific orders.

## Behavior

```text
Verified payment
    -> CHEF_ACCEPTANCE_PENDING
    -> 30-minute response window
```

```text
T+0   New order notification
T+10  Reminder
T+20  Urgent reminder
T+30  Timeout and refund request
```

An explicit chef rejection before expiry uses `CHEF_DECLINED`. A response after expiry is treated as `CHEF_ACCEPTANCE_TIMEOUT`.

Both rejection outcomes store the full chef-specific `grand_total` as the requested refund amount and create one `REFUND_REQUESTED` domain event in the same PostgreSQL transaction.

## Safety defaults

```text
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

These defaults mean:

- automatic reminders and timeout processing are not active yet;
- explicit rejection can store a durable refund request;
- `REFUND_REQUESTED` remains held in the domain outbox;
- existing `CHEF_ACCEPTED_ORDER` publication continues;
- no Cashfree refund API is called.

The worker and refund-event publication will be enabled only after Integration Service has the compatible Service Bus consumer and Cashfree sandbox refund workflow.

## Files

```text
../../src/main/resources/db/migration/V6__chef_acceptance_timeout_refund_trigger.sql
../../src/main/java/in/craves/order/config/ChefAcceptanceWindowProperties.java
../../src/main/java/in/craves/order/service/ChefAcceptanceWindowWorker.java
../../src/main/java/in/craves/order/service/ChefAcceptanceWorkRepository.java
../../src/main/java/in/craves/order/service/ChefAcceptanceResolutionService.java
../../src/main/java/in/craves/order/service/ChefAcceptanceInitialNotificationService.java
../../src/main/java/in/craves/order/event/RefundRequestedEventFactory.java
../../../../contracts/events/refund-requested-v1.schema.json
```

## Local setup

No new secret is required. Use the normal Order Service database, JWT, Catalog and Notification settings documented in `services/order-service/README.md`.

Keep these settings for safe local validation:

```text
CRAVES_CHEF_ACCEPTANCE_TIMEOUT_MINUTES=30
CRAVES_CHEF_ACCEPTANCE_FIRST_REMINDER_MINUTES=10
CRAVES_CHEF_ACCEPTANCE_SECOND_REMINDER_MINUTES=20
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

Run tests:

```bash
cd services/order-service
mvn -B clean verify
```

Validate event schemas from the repository root:

```bash
for schema in contracts/events/*.schema.json; do
  python3 -m json.tool "$schema" >/dev/null
done
```

## Deployment

Use the existing pipeline:

```text
azure-pipelines-order-service.yml
```

After deployment, confirm:

```text
Flyway V6 applied
latest revision = latest ready revision
running status = Running
worker absent or false
event allow-list absent or CHEF_ACCEPTED_ORDER
```

## Manual steps required

- Run `azure-pipelines-order-service-ci.yml` from the feature branch.
- Merge only after all tests and event-schema validation pass.
- Deploy Order Service from `main`.
- Do not enable the worker yet.
- Do not add `REFUND_REQUESTED` to the event allow-list yet.
- No Azure resource, paid SKU, Cashfree credential or new secret is required.

## Next module

Integration Service must consume `REFUND_REQUESTED`, persist an idempotent refund request, call Cashfree only in sandbox-controlled mode, reconcile provider status and publish `REFUND_STATUS_CHANGED`.
