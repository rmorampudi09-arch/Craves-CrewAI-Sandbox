# CRAVES Technical Handover — Refund End-to-End Build, Combined Deployment and Staged Activation

**Document date:** 17 July 2026  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Feature branch:** `feature/refund-end-to-end-rollout`  
**Architecture baseline:** `CRV-ARCH-HLD-001 v1.0`  
**Services:** Order Service, Integration Service, Notification Service  
**Classification:** Internal Confidential  
**Status:** Implementation complete on feature branch; CI validation pending

---

## 1. Executive summary

This handover consolidates the remaining customer-protective refund workflow into one engineering package while preserving controlled runtime activation.

The code, tests, CI, combined deployment, status reporting, staged activation and rollback controls are built together. The deployment pipeline intentionally leaves all newly dangerous runtime switches disabled. Each operational capability is then enabled one stage at a time after its preceding stage has been validated.

The complete intended path is:

```text
Payment verified
        ↓
Chef-specific order waits for chef acceptance
        ↓
Chef declines or 30-minute window expires
        ↓
Order becomes CHEF_REJECTED
        ↓
REFUND_REQUESTED transactional outbox event
        ↓
Azure Service Bus
        ↓
Integration refund-request consumer
        ↓
Idempotent refund intent
        ↓
Cashfree sandbox refund execution
        ↓
Cashfree reconciliation
        ↓
REFUND_STATUS_CHANGED transactional outbox event
        ↓
Azure Service Bus
        ↓
Order refund-status consumer
        ↓
REFUND_PENDING / REFUNDED / REFUND_FAILED
        ↓
Customer in-app notification outbox
        ↓
Notification Service
```

No production Cashfree execution is introduced or enabled by this module.

---

## 2. Why the work is packaged together but activated separately

The three services must agree on event contracts, state transitions, database metadata, provider identifiers, retry behavior and customer messages. Building them together reduces repetitive branches, pull requests, CI runs and deployments.

Runtime activation remains staged because each switch changes a different risk boundary:

1. Publishing `REFUND_REQUESTED` introduces new cross-service messages.
2. Enabling the chef timeout worker begins generating automatic refund requests.
3. Enabling Cashfree sandbox execution begins external financial API calls.
4. Enabling reconciliation begins repeated provider status polling.
5. Enabling notification dispatch exposes the resulting status to customers and chefs.

A problem in one stage can therefore be isolated and rolled back without disabling the already proven lower stages.

---

## 3. Approved product rules preserved

The implementation preserves the previously approved Craves rules:

- The acceptance timer begins only after payment is verified.
- The chef receives a 30-minute response window.
- Initial, 10-minute and 20-minute notification records are supported.
- Explicit chef rejection uses `CHEF_DECLINED`.
- No response by the deadline uses `CHEF_ACCEPTANCE_TIMEOUT`.
- The failed chef-specific order moves to `CHEF_REJECTED`.
- Refund amount is the immutable chef-specific `customer_order.grand_total`.
- A multi-chef checkout refunds only the failed chef-specific order.
- Successful chef-specific orders continue normally.
- V1 performs no automatic reassignment to another chef.
- Repeated events and provider retries must not create duplicate refunds.
- Customer refund state is represented as `REFUND_PENDING`, `REFUNDED` or `REFUND_FAILED`.

No new pricing, commission, delivery fee, refund deduction, GST, FSSAI or delivery-radius rule is introduced.

---

## 4. Architecture principles applied

### 4.1 Transactional outbox

Order Service stores `REFUND_REQUESTED` in `order_schema.domain_event_outbox` in the same PostgreSQL transaction as the rejection and refund-request metadata.

Integration Service stores `REFUND_STATUS_CHANGED` in `payment_schema.refund_status_outbox` in the same transaction as the provider result.

Customer notification records are stored in `order_schema.notification_outbox` in the same transaction as the Order refund-status transition.

### 4.2 At-least-once delivery

Azure Service Bus may deliver the same event more than once. Order and Integration consumers therefore use inbox/idempotency records and stable event identifiers.

### 4.3 Managed identity

Order and Integration Container Apps use managed identity for Service Bus. The staged activation pipeline verifies required Sender and Receiver role assignments but does not create roles.

### 4.4 Fail-closed runtime controls

Every activation stage verifies the current lower-stage state before changing one or more flags. Unexpected values, non-empty DLQs, unapproved database backlogs, missing credentials or unhealthy services stop the pipeline.

### 4.5 Durable rollback

Rollback changes runtime switches only. It never deletes refund intents, provider metadata, inbox rows, outbox rows or customer notification records.

---

## 5. Existing components reused

The combined module deliberately reuses the already implemented and validated components.

### Order Service

```text
ChefAcceptanceWindowWorker
ChefAcceptanceResolutionService
RefundRequestedEventFactory
OrderDomainOutboxRepository
OrderDomainOutboxPublisherWorker
RefundStatusChangedServiceBusProcessor
RefundStatusEventValidator
RefundStatusTransitionPolicy
RefundStatusUpdateService
NotificationOutboxRepository
NotificationOutboxDispatcher
```

### Integration Service

```text
RefundRequestedServiceBusProcessor
RefundEventValidator
RefundRequestService
RefundRepository
RefundExecutionWorker
CashfreeRefundClient
RefundStatusEventFactory
RefundStatusOutboxPublisher
```

### Notification Service

The existing internal notification command endpoint, idempotent request handling and in-app notification persistence are reused through the existing Order notification outbox dispatcher.

---

## 6. New customer refund-status notification component

### File

```text
services/order-service/src/main/java/in/craves/order/refund/RefundStatusCustomerNotificationService.java
```

### Behavior

When an incoming `REFUND_STATUS_CHANGED` event produces a real Order status change, Order Service creates one customer in-app notification outbox record.

Mapping:

```text
REFUND_PENDING
  event type: REFUND_PENDING
  template: REFUND_PENDING_IN_APP
  title: Refund processing

REFUNDED
  event type: REFUNDED
  template: REFUND_COMPLETED_IN_APP
  title: Refund completed

REFUND_FAILED
  event type: REFUND_FAILED
  template: REFUND_FAILED_IN_APP
  title: Refund update required
```

The notification event key is:

```text
refund-status-{REFUND_STATUS_CHANGED eventId}
```

This makes duplicate Service Bus delivery idempotent at the notification-outbox layer.

### Payload

The payload includes:

```text
eventId
orderId
checkoutId
refundId
refundReference
refundAmount
currency
status
providerStatus
updatedAt
cfRefundId, when available
```

No Cashfree credential or secret is included.

---

## 7. Transactional integration with Order refund-state updates

### Modified file

```text
services/order-service/src/main/java/in/craves/order/refund/RefundStatusUpdateService.java
```

The notification record is created only after:

1. The event envelope passes validation.
2. The inbox event ID is inserted successfully.
3. The target chef-specific order is locked.
4. Checkout ID, customer ID, currency, reason and refund amount match.
5. The transition policy confirms that the event is current.
6. The Order row is updated successfully.
7. A real normalized status change occurred.

The following operations occur in the same transaction:

```text
Update customer_order refund state
Insert order_status_history row
Insert customer notification_outbox row
Mark refund_status_inbox PROCESSED
```

A transaction rollback prevents partial customer-visible state.

---

## 8. Cashfree sandbox hardening

### Modified files

```text
services/integration-service/src/main/java/in/craves/integration/config/PaymentProviderProperties.java
services/integration-service/src/main/java/in/craves/integration/refund/CashfreeRefundClient.java
```

### Sandbox environment guard

The staged pipeline always sets:

```text
PAYMENT_PROVIDER_ENVIRONMENT=sandbox
```

No stage contains a command that sets the environment to production.

### Sandbox simulation control

Optional setting:

```text
CRAVES_CASHFREE_SANDBOX_REFUND_SIMULATION_STATUS
```

Allowed values:

```text
PENDING
SUCCESS
FAILED
```

When the environment is sandbox and this setting is populated, the value is used as the refund note for controlled provider simulation. In production, the ordinary Craves reason note is used instead.

### Response handling

The adapter accepts:

- a normal Cashfree refund response object; or
- the first object in a returned array.

A missing `refund_status` remains a retryable provider error rather than being guessed.

### Credentials

Credentials are required and are sent only through headers. The code never logs them.

---

## 9. Automated tests added

### Order test

```text
services/order-service/src/test/java/in/craves/order/refund/RefundStatusCustomerNotificationServiceTest.java
```

Covers:

- stable notification event key;
- completed-refund template selection;
- customer identity and target Order;
- amount, currency, provider status and Cashfree refund ID payload;
- rejection of unsupported normalized statuses.

### Integration test

```text
services/integration-service/src/test/java/in/craves/integration/config/PaymentProviderPropertiesTest.java
```

Covers:

- sandbox endpoint selection;
- production endpoint selection;
- simulation-status property preservation;
- explicit environment distinction.

Existing Order and Integration refund tests continue to run in the combined CI pipeline.

---

## 10. Combined CI pipeline

### File

```text
azure-pipelines-refund-end-to-end-ci.yml
```

### Execution

The pipeline runs Java 21 and executes:

```text
services/order-service:        mvn -B clean verify
services/integration-service:  mvn -B clean verify
services/notification-service: mvn -B clean verify
```

It also:

- validates every `contracts/events/*.schema.json` file;
- runs `bash -n` against every `scripts/refund/*.sh` file;
- confirms dangerous switches remain disabled by default;
- confirms the activation script contains sandbox activation;
- fails if the activation script contains a production Cashfree activation command.

This is the required feature-branch build gate before merge.

---

## 11. Combined deployment pipeline

### File

```text
azure-pipelines-refund-combined-deploy.yml
```

### Services deployed

```text
Order Service
Integration Service
Notification Service
```

### Deployment sequence

1. Build and test all services.
2. Validate event contracts.
3. Verify the current Azure runtime is still in the approved safe state.
4. Build and push three immutable images to ACR.
5. Deploy Notification Service.
6. Deploy Integration Service.
7. Deploy Order Service.
8. Wait for a new healthy and ready revision for each service.

### Runtime state after deployment

Order:

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=true
CRAVES_DOMAIN_EVENT_OUTBOX_ENABLED=true
CRAVES_DOMAIN_EVENT_SERVICE_BUS_ENABLED=true
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=false
```

Integration:

```text
CRAVES_REFUND_CONSUMER_ENABLED=true
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_COMMAND_ENABLED=false
BORZO_API_ENABLED=false
```

The deployment therefore does not publish refund requests, expire chef orders, call Cashfree, reconcile refunds or deliver customer notifications.

---

## 12. Dependency and RBAC preflight

### File

```text
scripts/refund/verify-refund-rollout-dependencies.sh
```

Every staged activation run first verifies:

- Order Container App latest revision is healthy and ready;
- Integration Container App latest revision is healthy and ready;
- Notification Container App latest revision is healthy and ready;
- Order system-assigned identity exists;
- Order identity has `Azure Service Bus Data Sender` at namespace or topic scope;
- Integration system-assigned identity exists;
- Integration identity has `Azure Service Bus Data Receiver`;
- Integration identity has `Azure Service Bus Data Sender` at namespace or topic scope.

The script reports principal IDs and required scopes only when a role is missing. It does not grant roles or create resources.

---

## 13. One staged activation pipeline

### File

```text
azure-pipelines-refund-stage-activation.yml
```

The same pipeline is run multiple times with a different `targetStage`.

Supported values:

```text
stage1_request_publication
stage2_timeout_worker
stage3_cashfree_sandbox_execution
stage4_reconciliation
stage5_customer_notifications
```

Each stage requires the exact previous-stage flags. Skipping stages causes a pipeline failure.

---

## 14. Stage 1 — `REFUND_REQUESTED` publication

### Changes

```text
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=
  CHEF_ACCEPTED_ORDER,REFUND_REQUESTED
```

### Remains disabled

```text
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=false
```

### Gates

- Integration refund subscription exists.
- Rule type is SQL filter.
- Rule expression contains `eventType` and `REFUND_REQUESTED`.
- Integration refund-request DLQ is empty.
- Current Order allow-list contains only `CHEF_ACCEPTED_ORDER`.
- Current Order refund-request outbox backlog equals the explicitly supplied parameter `expectedRefundRequestOutboxCount`.

A default count of zero prevents unreviewed historical events from being released.

---

## 15. Stage 2 — Chef timeout worker

### Changes

```text
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=true
```

### Preconditions

- Stage 1 allow-list is already active.
- Cashfree execution is still disabled.
- Reconciliation is still disabled.
- Notification dispatch is still disabled.

### Result

The worker may now:

- record initial chef notification rows;
- record 10-minute reminder rows;
- record 20-minute urgent reminder rows;
- expire eligible chef-specific orders after the approved deadline;
- set `CHEF_REJECTED` with timeout reason;
- record refund-request metadata;
- create `REFUND_REQUESTED` outbox events.

Integration can persist refund intents, but it cannot call Cashfree yet.

---

## 16. Stage 3 — Cashfree sandbox execution

### Required explicit parameter

```text
confirmFinancialSandboxExecution=true
```

### Required Azure DevOps secret variables

```text
CASHFREE_SANDBOX_CLIENT_ID
CASHFREE_SANDBOX_CLIENT_SECRET
```

### Required backlog approval

The pipeline calculates the count of executable refund rows and requires an exact match with:

```text
expectedExecutableRefundCount
```

This prevents accidentally enabling the provider worker against unknown rows.

### Secrets created on Container App

```text
cashfree-sandbox-client-id
cashfree-sandbox-client-secret
```

### Runtime changes

```text
PAYMENT_PROVIDER_ENVIRONMENT=sandbox
PAYMENT_PROVIDER_API_VERSION=2025-01-01
PAYMENT_PROVIDER_CLIENT_ID=secretref:cashfree-sandbox-client-id
PAYMENT_PROVIDER_CLIENT_KEY=secretref:cashfree-sandbox-client-secret
CRAVES_CASHFREE_SANDBOX_REFUND_SIMULATION_STATUS=PENDING|SUCCESS|FAILED
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=true
CRAVES_REFUND_RECONCILIATION_ENABLED=false
```

No credential value is printed.

---

## 17. Stage 4 — Cashfree reconciliation

### Preconditions

- Stage 3 provider execution is active.
- `PAYMENT_PROVIDER_ENVIRONMENT=sandbox`.
- Client ID and client key are configured through secret references.
- Notification dispatch is still disabled.

### Change

```text
CRAVES_REFUND_RECONCILIATION_ENABLED=true
```

### Result

Refund rows in provider `PENDING` or `ONHOLD` states may be claimed and checked through Cashfree Get Refund. The normalized status event is then written to the Integration transactional outbox and published to Order Service.

---

## 18. Stage 5 — Customer and chef notification delivery

### Preconditions

- Timeout worker active.
- Sandbox execution active.
- Reconciliation active.
- Notification dispatcher still disabled.
- Order notification backlog equals `expectedNotificationBacklogCount`.
- Order notification internal key exists.
- Notification Service internal key exists.
- Both values match without being printed.
- Notification Service ingress FQDN exists.
- Notification Service passed the common healthy-revision preflight.

### Changes

```text
CRAVES_NOTIFICATION_INTERNAL_BASE_URL=https://{notification-fqdn}
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=true
```

### Result

The existing Order dispatcher sends due notification-outbox rows to Notification Service with the internal service key. Delivery remains retryable and idempotent.

---

## 19. Activation script implementation

### File

```text
scripts/refund/activate-refund-stage.sh
```

Key controls:

- `set -euo pipefail`;
- no shell tracing of secrets;
- explicit required-variable checks;
- Container App plain-env and secret-ref resolution;
- exact boolean checks;
- normalized event allow-list checks;
- PostgreSQL queries with `ON_ERROR_STOP`;
- exact backlog-count comparisons;
- Service Bus filter and DLQ checks;
- revision health checks after each mutation;
- hard-coded sandbox environment in financial stage;
- no production activation stage;
- no delivery or Borzo enablement.

---

## 20. Rollout status pipeline

### Files

```text
azure-pipelines-refund-rollout-status.yml
scripts/refund/refund-rollout-status.sh
```

The report displays non-secret operational information:

- inferred current stage;
- Order allow-list;
- timeout worker state;
- refund-status consumer state;
- notification dispatcher state;
- Integration refund consumer state;
- refund-status publisher state;
- provider execution state;
- reconciliation state;
- payment environment;
- API version;
- sandbox simulation status;
- delivery and Borzo switches;
- Service Bus refund DLQ counts;
- Order event outbox backlog;
- Order notification outbox backlog;
- Integration refund workflow backlog;
- Integration refund-status outbox backlog.

The report fails on unsafe findings such as provider execution outside sandbox, non-empty refund DLQs, or changed delivery/Borzo switches.

---

## 21. Rollback pipeline

### Files

```text
azure-pipelines-refund-stage-rollback.yml
scripts/refund/rollback-refund-stage.sh
```

Supported targets:

```text
safe_publisher_only
stage1_request_publication
stage2_timeout_worker
stage3_cashfree_sandbox_execution
stage4_reconciliation
```

Rollback order:

1. Disable outward notification dispatch.
2. Reduce provider execution and reconciliation to the selected target.
3. Reduce timeout and event generation to the selected target.
4. Keep consumers and status publisher active so already durable state can remain observable.
5. Wait for healthy revisions.

Rollback does not purge any database or broker record.

---

## 22. Safe baseline rollback target

The safest target is:

```text
safe_publisher_only
```

Result:

```text
Order event types: CHEF_ACCEPTED_ORDER
Chef timeout worker: false
Cashfree execution: false
Reconciliation: false
Notification dispatcher: false
Integration refund consumer: true
Integration refund-status publisher: true
Order refund-status consumer: true
```

This matches the state validated immediately before the combined module was built.

---

## 23. Manual steps required — Azure DevOps pipelines

Register these YAML files once:

```text
azure-pipelines-refund-end-to-end-ci.yml
azure-pipelines-refund-combined-deploy.yml
azure-pipelines-refund-stage-activation.yml
azure-pipelines-refund-rollout-status.yml
azure-pipelines-refund-stage-rollback.yml
```

Use the existing service connection variable:

```text
AZURE_SERVICE_CONNECTION
```

Do not register or run the activation pipeline before CI, merge and combined deployment succeed.

---

## 24. Manual steps required — Cashfree sandbox

Only the merchant account owner can retrieve sandbox credentials.

Create secret Azure DevOps variables with exact names:

```text
CASHFREE_SANDBOX_CLIENT_ID
CASHFREE_SANDBOX_CLIENT_SECRET
```

Requirements:

- mark both values secret;
- do not include them in ordinary YAML parameters;
- do not paste them into chat;
- do not commit them to GitHub;
- do not print them in Cloud Shell output;
- use sandbox credentials only for Stage 3 and Stage 4 validation.

No production credentials are required for this module.

---

## 25. Manual steps required — Azure RBAC

The activation preflight only checks roles.

When it reports a missing role, an authorized Azure administrator must grant:

### Order identity

```text
Azure Service Bus Data Sender
Scope: craves-domain-events topic or namespace
```

### Integration identity

```text
Azure Service Bus Data Receiver
Scope: refund-request subscription, topic or namespace

Azure Service Bus Data Sender
Scope: craves-domain-events topic or namespace
```

No role should be broadened unnecessarily.

---

## 26. Manual steps required — Notification internal key

Stage 5 requires the same secret value to be configured on both services:

Order environment variable:

```text
CRAVES_NOTIFICATION_INTERNAL_KEY
```

Notification environment variable:

```text
CRAVES_INTERNAL_SERVICE_KEY
```

The pipeline compares the resolved values without printing them. If either value is absent or values differ, Stage 5 fails.

---

## 27. Required CI sequence

Feature branch:

```text
feature/refund-end-to-end-rollout
```

Run:

```text
azure-pipelines-refund-end-to-end-ci.yml
```

Required result:

```text
Order mvn clean verify: successful
Integration mvn clean verify: successful
Notification mvn clean verify: successful
Event schemas: valid
Bash scripts: syntax valid
Safe defaults: verified
```

Do not merge on partial success.

---

## 28. Required merge and deployment sequence

After CI succeeds:

1. Merge the single feature PR into `main`.
2. Run `azure-pipelines-refund-combined-deploy.yml` from `main`.
3. Confirm all three revisions are healthy.
4. Confirm the pipeline reports every new stage disabled.
5. Run `azure-pipelines-refund-rollout-status.yml`.
6. Confirm current stage is `safe_publisher_only`.
7. Begin Stage 1 only after reviewing the reported backlogs and DLQs.

---

## 29. Stage validation sequence

After each stage:

1. Run the rollout-status pipeline.
2. Verify both refund DLQs are zero.
3. Verify no unexpected FAILED, DEAD or DEAD_LETTER rows.
4. Run one isolated synthetic test for that stage.
5. Verify idempotency by repeating the event/command where appropriate.
6. Remove only synthetic test rows after messages have completed.
7. Record the result in the final operations handover.
8. Advance only after explicit approval.

---

## 30. Stage 1 validation objective

Prove:

```text
Synthetic REFUND_REQUESTED Order outbox row
        ↓
Order publisher marks PUBLISHED
        ↓
Integration inbox marks PROCESSED
        ↓
Exactly one Integration refund intent exists
```

Cashfree execution must remain disabled. The refund row should remain in a pre-provider state such as `REQUESTED`.

---

## 31. Stage 2 validation objective

Use an isolated paid chef-specific order with an expired acceptance timestamp.

Prove:

```text
CHEF_ACCEPTANCE_PENDING
        ↓
CHEF_REJECTED
reason = CHEF_ACCEPTANCE_TIMEOUT
        ↓
refund_requested_amount = stored grand_total
        ↓
REFUND_REQUESTED published
        ↓
Integration refund intent created
```

Cashfree remains disabled.

---

## 32. Stage 3 validation objective

Use one explicitly approved synthetic executable refund row.

Prove:

- the worker claims only the expected row;
- Cashfree sandbox receives one idempotent create-refund call;
- the provider result is stored;
- the status event is inserted transactionally;
- Order receives and applies the status;
- no duplicate provider refund occurs after retry;
- no production endpoint is used.

---

## 33. Stage 4 validation objective

Use a sandbox refund deliberately left `PENDING`.

Prove:

- reconciliation claims the row at or after `next_attempt_at`;
- Get Refund uses the same Cashfree order and refund reference;
- provider response updates the existing row;
- the normalized event is published;
- Order advances only when the incoming event is newer;
- a `REFUNDED` order is never downgraded.

---

## 34. Stage 5 validation objective

Use synthetic customer and chef notification rows.

Prove:

- dispatcher authenticates with the internal key;
- Notification Service accepts each request once;
- Order outbox changes to `SENT`;
- duplicate request keys are idempotent;
- the customer receives correct pending/completed/failed copy;
- no secret or internal pickup address appears in notification payloads.

---

## 35. Database tables involved

### Order database

```text
order_schema.customer_order
order_schema.order_status_history
order_schema.domain_event_outbox
order_schema.refund_status_inbox
order_schema.notification_outbox
```

### Integration database

```text
payment_schema.payment_order
payment_schema.refund
payment_schema.refund_request_inbox
payment_schema.refund_status_outbox
```

The combined module adds no new table or migration because required storage was introduced in previous validated modules.

---

## 36. Event contracts involved

```text
contracts/events/refund-requested-v1.schema.json
contracts/events/refund-status-changed-v1.schema.json
```

### `REFUND_REQUESTED`

Carries chef-specific order, checkout, customer, amount, currency, reason and request time.

### `REFUND_STATUS_CHANGED`

Carries refund identifiers, provider reference, amount, currency, reason, normalized status, provider status, optional Cashfree refund ID and update time.

Event payloads remain version `1.0`.

---

## 37. Idempotency controls

### Order request publication

Stable outbox event key per chef-specific order/rejection decision.

### Integration request consumer

Inbox keyed by event ID plus unique refund constraints for request event, chef-specific order and idempotency key.

### Cashfree provider request

Stable refund reference plus deterministic UUID `x-idempotency-key`.

### Integration status publication

Unique status event key and immutable event ID.

### Order status consumer

Inbox primary key on refund-status event ID and stable refund-ID validation.

### Customer notification

Notification event key derived from refund-status event ID.

---

## 38. Retry and dead-letter behavior

- Order event outbox uses bounded attempts and local `DEAD` state.
- Integration refund-request Service Bus consumer uses bounded delivery and broker DLQ.
- Integration provider worker uses retry scheduling and local `DEAD_LETTER` state.
- Integration status outbox uses bounded attempts and local `DEAD_LETTER` state.
- Order refund-status consumer uses bounded delivery and broker DLQ.
- Order notification dispatcher uses bounded attempts and `FAILED` state with retry delay.

No stage silently discards failures.

---

## 39. Observability and support checks

The rollout-status pipeline is the immediate operational view. Before production launch, dashboards and alerts should include:

- oldest pending Order refund-request outbox age;
- Integration refund-request inbox failures;
- refund rows by status and next-attempt age;
- status-outbox failures and dead-letter rows;
- Service Bus active, scheduled and DLQ counts;
- Order refund-status inbox failures;
- customer notification pending/failed age;
- provider latency and HTTP error rate;
- count of refunds requiring manual support.

---

## 40. Security controls

- Cashfree credentials are Azure DevOps secrets and Container App secret references.
- Scripts disable `set -x`.
- Status reports never resolve or print payment credentials.
- Internal notification keys are compared without printing.
- Managed identities are used for Service Bus.
- Role checks do not grant permissions.
- Production payment activation is absent.
- Customer notification payloads contain refund identifiers but no provider secret.
- Existing privacy controls continue to hide chef pickup contact/address details from public Order JSON.

---

## 41. Financial safety controls

Existing Integration logic locks the payment row and verifies:

```text
payment status = PAID
refund currency = payment currency
refund amount > 0
refund amount <= captured amount
sum of reserved refunds + new amount <= captured amount
```

The staged activation adds:

- explicit sandbox confirmation;
- exact executable backlog count;
- exact API environment check;
- secret-reference check before reconciliation;
- no production stage;
- rollback that disables provider execution without deleting evidence.

---

## 42. Scale considerations

The current low-concurrency configuration uses bounded polling batches and modest worker concurrency. PostgreSQL `FOR UPDATE SKIP LOCKED` supports multiple replicas without two workers owning the same row.

Before large-scale launch, load tests must measure:

- outbox claim duration;
- database lock contention;
- Service Bus throughput;
- provider rate limits;
- notification throughput;
- retry-storm behavior during provider outages;
- backlog recovery after a long outage;
- Container App scale-out and scale-in behavior.

The current settings should not be assumed suitable for one million concurrent users without measured evidence.

---

## 43. Known operational risk — historical backlogs

Enabling a publisher or worker can process old eligible rows immediately. The activation pipeline therefore requires exact expected counts.

An operator must not blindly replace an unexpected count with the reported value. First inspect the rows, establish whether they represent legitimate customers, synthetic tests or failed historical experiments, and obtain approval for any real financial execution.

---

## 44. Known operational risk — provider timeout ambiguity

A timeout after Cashfree receives a create-refund request does not prove failure. The same idempotency key and refund reference are reused, and conflict/unprocessable responses trigger Get Refund reconciliation.

Operators must not manually create a second refund simply because the first HTTP call timed out.

---

## 45. Known operational risk — customer messaging order

At-least-once and asynchronous delivery means messages can be delayed. The Order transition policy prevents stale provider events from overwriting newer state. Notification records are produced only for accepted state changes, reducing contradictory messages.

The mobile/web UI should still use the current Order state as the source of truth rather than assuming the latest push notification is current.

---

## 46. Known operational risk — Notification Service readiness

Stage 5 requires the Notification Container App to be healthy, but channel-specific downstream providers may still be disabled. This rollout currently creates `IN_APP` records only for refund status. Email, push and SMS remain governed by Notification Service’s own channel flags and separate credentials.

---

## 47. What this module does not enable

The feature branch and combined deployment do not enable:

- production Cashfree credentials;
- production Cashfree refund execution;
- automatic chef payouts;
- delivery command creation;
- Borzo execution;
- delivery-provider refunds or compensation;
- tax adjustments;
- refund deductions;
- automatic chef reassignment;
- customer credits or wallet balances;
- admin manual-refund endpoints;
- permanent purging of refund audit records.

---

## 48. Repository file inventory

### Application code

```text
services/order-service/src/main/java/in/craves/order/refund/RefundStatusCustomerNotificationService.java
services/order-service/src/main/java/in/craves/order/refund/RefundStatusUpdateService.java
services/integration-service/src/main/java/in/craves/integration/config/PaymentProviderProperties.java
services/integration-service/src/main/java/in/craves/integration/refund/CashfreeRefundClient.java
```

### Tests

```text
services/order-service/src/test/java/in/craves/order/refund/RefundStatusCustomerNotificationServiceTest.java
services/integration-service/src/test/java/in/craves/integration/config/PaymentProviderPropertiesTest.java
```

### Pipelines

```text
azure-pipelines-refund-end-to-end-ci.yml
azure-pipelines-refund-combined-deploy.yml
azure-pipelines-refund-stage-activation.yml
azure-pipelines-refund-rollout-status.yml
azure-pipelines-refund-stage-rollback.yml
```

### Scripts

```text
scripts/refund/verify-refund-rollout-dependencies.sh
scripts/refund/activate-refund-stage.sh
scripts/refund/refund-rollout-status.sh
scripts/refund/rollback-refund-stage.sh
```

### Documentation

```text
services/order-service/modules/refund-end-to-end-rollout/README.md
docs/handover/2026-07-17-refund-end-to-end-rollout.md
```

---

## 49. Immediate next action

Run the combined build-only pipeline from the feature branch:

```text
Pipeline YAML: /azure-pipelines-refund-end-to-end-ci.yml
Branch: feature/refund-end-to-end-rollout
```

Do not run the combined deployment or any activation stage until this CI pipeline succeeds and the pull request is merged.

---

## 50. Pending items after successful staged validation

After all five stages are validated in the sandbox environment, the following remain separate launch work:

1. Produce the final consolidated operational handover in Word and PDF with validation evidence and exact revision IDs.
2. Add Application Insights dashboards and alerts for refund lag, failure and DLQ conditions.
3. Add an admin/support workflow for genuine `REFUND_FAILED` cases.
4. Define production credential rotation and Key Vault ownership.
5. Perform Cashfree production-readiness review and merchant-account checks.
6. Perform controlled production canary with a very small approved order set.
7. Add customer support wording and SLA approved by Product/Legal.
8. Load-test outbox, consumer, provider and notification throughput.
9. Test provider outage, network timeout and duplicate-webhook scenarios.
10. Create the production operations runbook and incident escalation matrix.

Production activation must remain a new explicit approval. It is not implied by sandbox success.

---

## Final handover statement

The remaining refund workflow is now engineered as one coherent release package. It can be built and deployed together, but it cannot silently activate financial or customer-facing behavior. Every stage is guarded by service health, Service Bus topology, managed-identity RBAC, current runtime flags, database backlog counts, DLQ counts, credential handling and revision-health verification.

This design preserves the speed requested by the project owner while retaining the financial and operational controls required for a marketplace handling customer payments and partial multi-chef refunds.
