# Craves Delivery Webhook, Status and Tracking Reconciliation Handover

**Document date:** 2026-07-24  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Feature branch:** `feature/delivery-webhook-status-reconciliation`  
**Service:** `services/integration-service`  
**Runtime environment:** Azure production-low-cost environment  
**Document status:** Engineering implementation complete on branch; branch CI, merge, deployment and controlled Azure validation pending  
**Confidentiality:** Internal Craves engineering and operations use

---

## 1. Executive summary

This module completes the Integration Service side of delivery lifecycle processing after a provider booking already exists. It turns signed provider callbacks and provider tracking reads into durable, idempotent, auditable Craves delivery state changes.

The implementation is deliberately fail-closed. Deploying it does not create a delivery, call Borzo, process a webhook, poll provider tracking or publish a delivery-status event unless the corresponding independent switch is explicitly enabled.

## 2. Business risk closed

Before this module, the Borzo callback endpoint verified signatures and stored raw payloads, but nothing asynchronously applied those callbacks to `delivery_job`. A provider delivery could therefore progress while Craves continued showing an old status.

The second risk was callback delay or loss. There was no controlled read-only `track()` fallback to repair a stale delivery job.

The third risk was a permanently in-flight worker lease after a replica crash on the final attempt. That could leave a webhook, tracking job or create-reconciliation command stuck indefinitely without becoming visible as terminal support work.

## 3. Approved architectural basis

The implementation follows the approved delivery integration blueprint:

```text
signed webhook
  -> durable inbox
  -> quick provider acknowledgement
  -> asynchronous normalization
  -> append-only provider event
  -> canonical delivery job update
  -> DELIVERY_STATUS_CHANGED outbox
  -> inbox completion
```

The blueprint also requires provider `track()` as a fallback when callbacks are delayed.

## 4. Previously completed delivery gates

The following gates were already completed before this branch:

1. `CHEF_ACCEPTED_ORDER` reached the Integration subscription.
2. Scheduled delivery command persistence was validated.
3. PostgreSQL `Instant` to `TIMESTAMPTZ` binding was corrected.
4. Duplicate event idempotency was validated using two event IDs for one chef sub-order.
5. Exactly one command row and one scheduled Service Bus message were produced.
6. The synthetic scheduled message and database row were removed.
7. Topic and queue active/dead-letter/scheduled counts returned to zero.
8. Fail-closed create reconciliation was implemented, merged and deployed.
9. Flyway V101 was verified successfully in Azure.
10. There were zero current `RECONCILIATION_PENDING` rows.

## 5. Current deployed baseline before this module

```text
Integration image: cravesprodlowacr82121.azurecr.io/craves/integration-service:137
Container App revision: ca-craves-integration-service-pr--0000046
Revision health: Healthy
Running replicas: 1
Delivery commands: false
Create reconciliation: false
Delivery intelligence: true
Borzo API: false
Topic active/DLQ: 0/0
Delivery queue active/DLQ/scheduled: 0/0/0
```

## 6. Scope of this module

This branch adds:

- asynchronous webhook inbox processing;
- provider-specific callback normalization;
- exact provider-order-to-delivery-job matching;
- append-only delivery event audit;
- stale callback protection;
- terminal status protection;
- delivery job status/timestamp/tracking updates;
- read-only tracking reconciliation;
- transactional `DELIVERY_STATUS_CHANGED` outbox generation;
- independently controlled status outbox publication;
- bounded retries and terminal dead-letter state;
- final-lease crash recovery for webhook, tracking and create reconciliation workers;
- V102 migration;
- versioned JSON Schema;
- CI tests and deployment guards.

## 7. Explicit exclusions

This branch does not add or enable:

- provider delivery creation;
- Borzo sandbox or production execution;
- Order Service status consumer;
- Notification Service status consumer;
- customer or chef UI tracking;
- APIM route changes;
- public callback DNS changes;
- callback registration in the Borzo portal;
- delivery pricing, commissions or serviceability rules;
- GST, FSSAI or other compliance logic.

## 8. Safety defaults

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=false
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

## 9. Independent activation boundaries

The module separates five concerns:

1. command intake and provider create;
2. ambiguous-create reconciliation;
3. webhook processing;
4. tracking reconciliation;
5. delivery status publication.

Each concern has a separate environment switch. This permits isolated validation without activating the full delivery flow.

## 10. Existing webhook ingress reused

The following existing paths remain authoritative:

```text
services/integration-service/src/main/java/in/craves/integration/web/
  BorzoWebhookController.java

services/integration-service/src/main/java/in/craves/integration/delivery/borzo/
  BorzoWebhookService.java
  BorzoWebhookInboxRepository.java
  BorzoWebhookSignatureVerifier.java
```

The endpoint continues to:

1. receive the raw request body;
2. verify `X-DV-Signature` using the Key Vault-backed callback secret;
3. parse the payload;
4. derive a deterministic provider event ID;
5. insert `delivery_webhook_inbox` with `RECEIVED`;
6. return success quickly.

No provider network call occurs in the HTTP request thread.

## 11. New provider callback normalization contract

```text
services/integration-service/src/main/java/in/craves/integration/delivery/provider/
  DeliveryWebhookNormalizer.java
```

The contract is intentionally separate from network operations:

```java
String providerId();
ProviderStatusUpdate normalize(JsonNode payload);
```

Normalization must be deterministic and must not perform network I/O.

## 12. Borzo callback normalizer

```text
services/integration-service/src/main/java/in/craves/integration/delivery/borzo/
  BorzoWebhookNormalizer.java
```

It supports both delivery-level and order-level callback payloads. It extracts:

- provider order ID;
- provider delivery ID when present;
- raw provider status;
- canonical Craves status;
- tracking URL;
- provider observation timestamp;
- internal provider metadata snapshot.

## 13. Canonical delivery statuses

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
UNKNOWN
```

`UNKNOWN` is stored as an ignored audit event and never overwrites the delivery job.

## 14. Provider identity matching

Webhook processing locates a job only through:

```text
provider_id + provider_order_id
```

For Borzo, `delivery_job.provider_delivery_id` stores the Borzo `order_id` returned by create/reconciliation. The module does not infer a job from phone, address, customer name, amount or approximate time.

## 15. Durable webhook claim

```text
services/integration-service/src/main/java/in/craves/integration/delivery/status/
  DeliveryStatusRepository.java
```

Due inbox rows are claimed with:

```text
FOR UPDATE SKIP LOCKED
```

This allows multiple Container App replicas to process separate rows safely.

## 16. Webhook inbox lifecycle

```text
RECEIVED
  -> PROCESSING
      -> PROCESSED
      -> DUPLICATE
      -> FAILED
          -> PROCESSING
          -> DEAD_LETTER
```

`REJECTED` remains available for signature or contract rejection workflows.

## 17. Webhook retry policy

Defaults:

```text
batch size: 20
worker interval: 2 seconds
maximum attempts: 10
retry base: 5 seconds
stale lease: 5 minutes
maximum retry delay: 300 seconds
```

Retry delay uses bounded exponential backoff.

## 18. Unmatched callbacks

A valid signed callback may arrive before the corresponding local job transaction becomes visible. Therefore an unmatched provider order is retryable rather than immediately discarded.

After the configured attempt limit, the inbox row becomes `DEAD_LETTER` and retains the safe error message for support review.

## 19. Append-only delivery event audit

Every unique normalized callback is inserted into:

```text
delivery_schema.delivery_event
```

New audit fields record:

- source: `WEBHOOK` or `TRACK`;
- provider status;
- canonical status;
- applied boolean;
- ignored reason;
- provider payload;
- provider observation time.

## 20. Duplicate event behavior

The existing uniqueness rule remains:

```text
UNIQUE(provider_id, provider_event_id)
```

If the same provider event is seen again, no second delivery event, job update or outbox event is created.

## 21. Out-of-order protection

A callback is ignored when:

```text
incoming observedAt <= delivery_job.last_status_observed_at
```

The ignored callback is still stored in the append-only event table with:

```text
applied=false
ignored_reason=STALE_OR_EQUAL_OBSERVED_AT
```

## 22. Terminal status protection

After the job reaches one of these terminal states:

```text
DELIVERED
CANCELLED
RETURNED
FAILED
```

a later callback cannot move it to a different state, even if the provider timestamp is newer.

The event is retained with:

```text
ignored_reason=TERMINAL_STATUS_PROTECTED
```

## 23. No-state-change behavior

If canonical status, provider status and tracking URL are unchanged, the callback is recorded for audit but does not generate another status outbox event.

```text
ignored_reason=NO_STATE_CHANGE
```

## 24. Unknown provider status behavior

Unmapped provider statuses do not fail the entire inbox row. They are recorded as ignored events:

```text
normalized_status=UNKNOWN
applied=false
ignored_reason=UNKNOWN_PROVIDER_STATUS
```

This gives operations evidence for mapping updates without corrupting current job state.

## 25. Transactional status application

```text
services/integration-service/src/main/java/in/craves/integration/delivery/status/
  DeliveryStatusUpdateService.java
```

For an accepted update, one PostgreSQL transaction performs:

1. delivery job row lock;
2. delivery event insert;
3. delivery job status update;
4. pickup/delivery timestamp update when applicable;
5. next tracking schedule update;
6. `DELIVERY_STATUS_CHANGED` outbox insert;
7. webhook inbox completion.

If any step fails, all steps roll back.

## 26. Delivery job observation fields

V102 adds:

```text
provider_status
last_status_observed_at
last_status_source
```

Allowed observation sources:

```text
CREATE
WEBHOOK
TRACK
RECONCILIATION
```

## 27. Pickup and delivery timestamps

The first accepted status in:

```text
PICKED_UP
IN_TRANSIT
AT_DROPOFF
DELIVERED
```

sets `picked_up_at` when it is still null.

The first accepted `DELIVERED` status sets `delivered_at` when it is still null.

Provider observation time, not local worker time, is used.

## 28. Tracking reconciliation purpose

Tracking reconciliation is a repair path for delayed or missing callbacks. It never creates or cancels a provider order.

```text
adapter.track(providerDeliveryId)
```

is the only provider operation performed by this worker.

## 29. Tracking eligibility

A job is eligible only when:

- it is non-terminal;
- `next_tracking_at` is due;
- it has not exhausted attempts;
- it is not already dead-lettered;
- no unexpired processing lease exists.

## 30. Historical job safety

V102 does not automatically set `next_tracking_at` on historical jobs.

Existing rows receive observation metadata only. A historical job enters tracking only through:

- a fresh accepted webhook;
- an explicit reviewed operations repair;
- another code path that deliberately schedules it.

This prevents a provider-read storm after future activation.

## 31. New job tracking schedule

`DeliveryJobRepository` initializes tracking metadata for jobs created after V102.

Terminal jobs receive no next tracking time. Non-terminal jobs become eligible for the read-only repair worker.

## 32. Tracking retry policy

Defaults:

```text
batch size: 20
worker interval: 15 seconds
poll interval after success/no change: 60 seconds
maximum attempts: 20
retry base: 30 seconds
stale lease: 5 minutes
maximum retry delay: 900 seconds
```

## 33. Tracking terminal support state

When tracking exhausts its retry budget:

```text
next_tracking_at=NULL
tracking_dead_lettered_at=<timestamp>
last_tracking_error=<safe error>
```

This is directly queryable by operations.

## 34. Final-lease crash recovery

```text
services/integration-service/src/main/java/in/craves/integration/delivery/status/
  DeliveryLeaseRecoveryRepository.java
```

Before each worker claims a batch, it converts stale final-attempt leases to terminal support states for:

- create reconciliation;
- webhook processing;
- tracking reconciliation.

This closes the permanent `PROCESSING`/leased-row stall condition after a replica crash.

## 35. Create-reconciliation correction included

The previously deployed create-reconciliation worker now runs the final-lease sweep before claiming work.

No create-reconciliation business behavior changes. It still performs read-only deterministic provider lookup and never issues another create from reconciliation.

## 36. Delivery status event contract

```text
contracts/events/delivery-status-changed-v1.schema.json
```

Envelope:

```text
eventType: DELIVERY_STATUS_CHANGED
eventVersion: 1.0
source: integration-service
subject: delivery-job/<uuid>
```

Data:

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

## 37. Privacy boundary

The public status event excludes:

- customer address;
- chef pickup address;
- phone numbers;
- courier phone;
- provider auth data;
- raw provider callback body.

Raw callback and tracking payloads remain in the Integration database for internal audit only.

## 38. Transactional outbox

Accepted status changes enqueue to:

```text
delivery_schema.delivery_outbox
```

The outbox remains durable while the publisher is disabled. Publication can be validated separately from callback processing.

## 39. Independent status publisher

```text
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
```

When true, the existing outbox publisher and Service Bus sender are created even if command intake remains false.

The publisher sends both application properties during compatibility transition:

```text
event_type=DELIVERY_STATUS_CHANGED
eventType=DELIVERY_STATUS_CHANGED
```

## 40. Service Bus destination

Existing destination:

```text
Namespace: sb-craves-prodlow-l3ing6
Topic: craves-domain-events
```

No new Azure entity is created by this branch.

Downstream filtered subscriptions are deferred to the Order and Notification consumer module.

## 41. Database migration

```text
services/integration-service/src/main/resources/db/migration/
  V102__delivery_webhook_status_reconciliation.sql
```

V102 is forward-only and backward-compatible while all switches remain false.

## 42. V102 webhook inbox columns

```text
attempt_count
next_attempt_at
processing_started_at
delivery_job_id
provider_order_id
provider_delivery_id
normalized_status
processing_result
```

## 43. V102 delivery job columns

```text
provider_status
last_status_observed_at
last_status_source
next_tracking_at
tracking_attempt_count
tracking_processing_started_at
tracking_dead_lettered_at
last_tracking_error
```

## 44. V102 delivery event columns

```text
source
provider_status
applied
ignored_reason
```

## 45. Indexes introduced

```text
ix_delivery_webhook_process_due
ix_delivery_job_tracking_due
ix_delivery_job_tracking_dead_letter
ix_delivery_event_applied_time
```

These support due-work scans and operational triage without table-wide polling.

## 46. Configuration variables

Webhook:

```text
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=false
CRAVES_DELIVERY_WEBHOOK_BATCH_SIZE=20
CRAVES_DELIVERY_WEBHOOK_PROCESSING_INTERVAL_MS=2000
CRAVES_DELIVERY_MAX_WEBHOOK_ATTEMPTS=10
CRAVES_DELIVERY_WEBHOOK_RETRY_BASE_SECONDS=5
CRAVES_DELIVERY_WEBHOOK_STALE_MINUTES=5
```

Tracking:

```text
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_TRACKING_BATCH_SIZE=20
CRAVES_DELIVERY_TRACKING_RECONCILIATION_INTERVAL_MS=15000
CRAVES_DELIVERY_TRACKING_POLL_SECONDS=60
CRAVES_DELIVERY_MAX_TRACKING_ATTEMPTS=20
CRAVES_DELIVERY_TRACKING_RETRY_BASE_SECONDS=30
CRAVES_DELIVERY_TRACKING_STALE_MINUTES=5
```

Publisher:

```text
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
```

## 47. Deployment pipeline guard

```text
azure-pipelines-integration-service.yml
```

Every normal deployment explicitly writes and verifies:

```text
delivery=false
create reconciliation=false
webhook processing=false
tracking reconciliation=false
status publisher=false
intelligence=true
Borzo=false
```

The deployment fails unless the new revision is healthy, ready, running the intended image and retaining all fail-closed values.

## 48. Tests added

```text
BorzoWebhookNormalizerTest
DeliveryStatusUpdateServiceTest
DeliveryWebhookProcessorTest
DeliveryTrackingReconciliationWorkerTest
DeliveryStatusMigrationTest
DeliveryStatusControlsTest
```

The existing create-reconciliation worker test is updated for final-lease recovery.

## 49. CI command

```bash
cd services/integration-service
mvn -B clean verify
```

The Azure DevOps Integration CI pipeline also validates every event JSON Schema.

## 50. Required branch CI result

```text
Compile and run Integration Service tests: Succeeded
Validate all domain-event schema JSON files: Succeeded
```

Do not merge if compilation, tests or schema validation fail.

## 51. Safe merge procedure

1. Open a draft pull request from the feature branch to `main`.
2. Run Integration CI against the exact branch head.
3. Confirm the PR head did not move after the successful run.
4. Mark ready.
5. Merge with the tested head SHA.
6. Do not enable any delivery switch.

## 52. Safe deployment procedure

1. Run `azure-pipelines-integration-service.yml` from merged `main`.
2. Confirm a new image tag and revision.
3. Confirm Flyway V102 succeeds.
4. Confirm all fail-closed values.
5. Confirm topic and delivery queue remain empty.
6. Do not register or call Borzo during this stage.

## 53. V102 verification SQL

```sql
SELECT version, description, success, installed_on
FROM payment_schema.flyway_schema_history
WHERE version = '102';
```

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'delivery_schema'
  AND table_name IN (
      'delivery_webhook_inbox',
      'delivery_job',
      'delivery_event'
  )
  AND column_name IN (
      'attempt_count',
      'next_attempt_at',
      'processing_started_at',
      'delivery_job_id',
      'provider_order_id',
      'provider_delivery_id',
      'normalized_status',
      'processing_result',
      'provider_status',
      'last_status_observed_at',
      'last_status_source',
      'next_tracking_at',
      'tracking_attempt_count',
      'tracking_processing_started_at',
      'tracking_dead_lettered_at',
      'last_tracking_error',
      'source',
      'applied',
      'ignored_reason'
  )
ORDER BY table_name, column_name;
```

## 54. Initial isolated webhook test

The first Azure test must not call Borzo.

Required state:

```text
delivery=false
create reconciliation=false
tracking=false
status publisher=false
Borzo=false
webhook processing=true temporarily
```

Test data must use one synthetic delivery job and one signed synthetic Borzo-style callback stored directly through the existing webhook service or controlled database fixture.

## 55. Isolated webhook acceptance criteria

For one valid newer callback:

```text
one inbox row -> PROCESSED
one delivery event -> applied=true
one delivery job status update
one pending delivery outbox row
zero Service Bus publication while publisher=false
zero Borzo network calls
```

## 56. Stale callback acceptance criteria

For one older callback:

```text
one inbox row -> PROCESSED
one delivery event -> applied=false
ignored_reason=STALE_OR_EQUAL_OBSERVED_AT
no delivery job regression
no new delivery outbox row
```

## 57. Terminal protection acceptance criteria

For a delivered synthetic job followed by a newer non-terminal callback:

```text
event retained
applied=false
ignored_reason=TERMINAL_STATUS_PROTECTED
job remains DELIVERED
no status outbox row
```

## 58. Duplicate acceptance criteria

Repeated identical provider event:

```text
no second delivery event
no second job update
no second outbox event
```

## 59. Publisher-only validation

After downstream consumers exist, enable only:

```text
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=true
```

Keep command intake, create reconciliation, webhook processing, tracking and Borzo false unless the specific test requires otherwise.

Validate one pending synthetic outbox row reaches the expected filtered subscription exactly once.

## 60. Tracking sandbox validation

This requires explicit approval because it performs provider API reads.

Required state:

```text
delivery=false
create reconciliation=false
webhook processing=false
status publisher=false initially
tracking=true
Borzo=true
```

The test must use an existing known sandbox provider order. It must not create a new order.

## 61. Tracking validation evidence

Capture:

- provider order ID;
- local delivery job ID;
- tracking attempt count;
- provider read response status;
- append-only event row;
- delivery job before/after status;
- outbox row count;
- zero provider create calls.

## 62. Cleanup requirements

Synthetic validation must remove only rows identified by the exact synthetic UUIDs and provider event IDs.

Never use broad date-based deletion. Preserve durable evidence for any real provider transaction.

After cleanup, return all feature switches to false and verify a healthy ready revision.

## 63. Rollback switches

Immediate rollback:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=false
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
BORZO_API_ENABLED=false
```

## 64. Image rollback

If application rollback is required, point the Container App to the prior known-safe image while retaining every switch above as false.

Do not delete Flyway V102 or manually edit Flyway history during an incident.

## 65. Database rollback policy

V102 is additive. Its columns and indexes are safe while workers remain disabled.

Any schema removal must be a separate reviewed forward migration after:

- all inbox rows are terminal;
- no tracking leases remain;
- no outbox status rows are pending;
- no deployed image depends on the columns.

## 66. Monitoring: webhook backlog

```sql
SELECT processing_status, COUNT(*)
FROM delivery_schema.delivery_webhook_inbox
GROUP BY processing_status
ORDER BY processing_status;
```

## 67. Monitoring: oldest due webhook

```sql
SELECT MIN(received_at) AS oldest_due,
       COUNT(*) AS due_count
FROM delivery_schema.delivery_webhook_inbox
WHERE processing_status IN ('RECEIVED', 'FAILED')
  AND next_attempt_at <= now();
```

## 68. Monitoring: webhook dead-letter

```sql
SELECT id,
       provider_id,
       provider_event_id,
       attempt_count,
       error_message,
       received_at,
       processed_at
FROM delivery_schema.delivery_webhook_inbox
WHERE processing_status = 'DEAD_LETTER'
ORDER BY received_at;
```

## 69. Monitoring: tracking due

```sql
SELECT provider_id,
       status,
       COUNT(*) AS due_count,
       MIN(next_tracking_at) AS oldest_due
FROM delivery_schema.delivery_job
WHERE next_tracking_at <= now()
  AND tracking_dead_lettered_at IS NULL
GROUP BY provider_id, status
ORDER BY provider_id, status;
```

## 70. Monitoring: tracking dead-letter

```sql
SELECT id,
       chef_sub_order_id,
       provider_id,
       provider_delivery_id,
       status,
       tracking_attempt_count,
       tracking_dead_lettered_at,
       last_tracking_error
FROM delivery_schema.delivery_job
WHERE tracking_dead_lettered_at IS NOT NULL
ORDER BY tracking_dead_lettered_at;
```

## 71. Monitoring: ignored events

```sql
SELECT ignored_reason,
       normalized_status,
       COUNT(*)
FROM delivery_schema.delivery_event
WHERE applied = false
GROUP BY ignored_reason, normalized_status
ORDER BY ignored_reason, normalized_status;
```

## 72. Monitoring: status outbox

```sql
SELECT status, COUNT(*)
FROM delivery_schema.delivery_outbox
WHERE event_type = 'DELIVERY_STATUS_CHANGED'
GROUP BY status
ORDER BY status;
```

## 73. Monitoring: stale final leases

Webhook final leases should be converted to `DEAD_LETTER`; tracking final leases should have `tracking_dead_lettered_at`; create reconciliation final leases should become command `DEAD_LETTER`.

Any final-attempt stale lease that remains active indicates the recovery sweep is not running or the worker switch is off.

## 74. Alert recommendations

Create alerts for:

- webhook `DEAD_LETTER` count greater than zero;
- tracking dead-letter count greater than zero;
- outbox `DEAD_LETTER` count greater than zero;
- oldest due webhook above the agreed threshold;
- oldest due tracking job above the agreed threshold;
- unknown provider status events;
- unhealthy Integration revision.

Thresholds are operational decisions and are not hard-coded in this module.

## 75. Security controls

- callback signature verification occurs before persistence;
- provider auth token remains Key Vault-backed;
- callback secret remains Key Vault-backed;
- secrets are never included in events or logs;
- unsafe exception text is truncated;
- public status events exclude PII;
- raw provider payload remains internal.

## 76. Azure manual intervention

No new paid Azure resource is created by this branch.

Manual actions after merge:

1. run normal Integration deployment;
2. verify revision and flags;
3. verify V102;
4. later create filtered downstream subscriptions only when consumer code exists;
5. later register callback URL in provider portal only after explicit approval.

## 77. Secrets manual intervention

Do not paste secret values into chat.

Existing expected secret-backed environment variables:

```text
BORZO_API_AUTH_TOKEN
BORZO_CALLBACK_TOKEN
SPRING_DATASOURCE_PASSWORD
```

Preserve current Key Vault references and managed identity access.

## 78. Service Bus RBAC

Status publication requires the Integration managed identity to retain `Azure Service Bus Data Sender` at namespace or topic scope.

Deployment does not create or change role assignments. A controlled activation pipeline must verify existing RBAC and fail if missing.

## 79. Downstream Order Service work pending

Order Service must add a filtered, idempotent consumer for:

```text
DELIVERY_STATUS_CHANGED v1.0
```

It must validate identifiers, prevent stale status regression, update order-visible delivery state and maintain a durable inbox.

## 80. Downstream Notification Service work pending

Notification Service must consume selected customer/chef-visible delivery states and generate idempotent notification records.

Notification policy, wording, channels and throttling require product approval and are not invented here.

## 81. Frontend/mobile work pending

Web and React Native clients must obtain delivery state from an authoritative backend API or real-time channel. They must not call provider tracking APIs directly or receive provider credentials.

## 82. APIM/public ingress work pending

The existing callback endpoint must be reviewed for public routing, TLS, rate limiting and provider reachability before provider registration.

No APIM route or public DNS change is part of this branch.

## 83. Provider callback registration pending

Borzo sandbox/production callback registration is a manual provider-console action. It must occur only after:

- ingress verification;
- signed callback test;
- webhook processing test;
- rollback procedure review.

## 84. Known operational limitation

Webhook and tracking workers currently use scheduled polling against PostgreSQL. This is appropriate for the current 50–100 concurrent-user resource plan. At much larger scale, worker batch sizes, replica count, database IOPS, queue-based fan-out and partition strategy must be load-tested before claiming one-million-concurrent-user readiness.

## 85. Scale safeguards already present

- `FOR UPDATE SKIP LOCKED` multi-replica claims;
- bounded batch sizes;
- bounded retry delays;
- explicit leases;
- stale lease recovery;
- indexed due-work queries;
- idempotent provider event identity;
- transactional outbox;
- terminal dead-letter states.

## 86. CFIN-style audit characteristics

The module preserves:

- immutable provider event history;
- explicit applied/ignored decision evidence;
- exact provider and local identifiers;
- provider observation timestamps;
- worker attempt and lease state;
- safe error text;
- transactional state/outbox linkage;
- no broad destructive cleanup.

## 87. Code review focus

Reviewers should verify:

1. no provider create call exists in webhook or tracking workers;
2. callback parsing performs no network I/O;
3. job matching uses exact provider identity;
4. stale events cannot overwrite newer state;
5. terminal states cannot regress;
6. event/job/outbox/inbox operations share a transaction;
7. final leases become terminal;
8. deployment switches remain false;
9. event schema matches the Java payload;
10. no PII enters the public event.

## 88. Test files

```text
services/integration-service/src/test/java/in/craves/integration/delivery/borzo/
  BorzoWebhookNormalizerTest.java

services/integration-service/src/test/java/in/craves/integration/delivery/status/
  DeliveryStatusUpdateServiceTest.java
  DeliveryWebhookProcessorTest.java
  DeliveryTrackingReconciliationWorkerTest.java
  DeliveryStatusMigrationTest.java

services/integration-service/src/test/java/in/craves/integration/delivery/command/
  DeliveryStatusControlsTest.java
  DeliveryCreateReconciliationWorkerTest.java
```

## 89. Complete changed-path summary

```text
azure-pipelines-integration-service.yml
contracts/events/delivery-status-changed-v1.schema.json
services/integration-service/src/main/java/in/craves/integration/delivery/borzo/BorzoWebhookNormalizer.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCommandProperties.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCreateReconciliationWorker.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryJobRepository.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryOutboxPublisher.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryServiceBusConfiguration.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryServiceBusPublisher.java
services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryProviderAdapter.java
services/integration-service/src/main/java/in/craves/integration/delivery/provider/DeliveryWebhookNormalizer.java
services/integration-service/src/main/java/in/craves/integration/delivery/status/DeliveryLeaseRecoveryRepository.java
services/integration-service/src/main/java/in/craves/integration/delivery/status/DeliveryStatusRepository.java
services/integration-service/src/main/java/in/craves/integration/delivery/status/DeliveryStatusUpdateService.java
services/integration-service/src/main/java/in/craves/integration/delivery/status/DeliveryTrackingReconciliationWorker.java
services/integration-service/src/main/java/in/craves/integration/delivery/status/DeliveryWebhookProcessor.java
services/integration-service/src/main/resources/application.yml
services/integration-service/src/main/resources/db/migration/V102__delivery_webhook_status_reconciliation.sql
services/integration-service/modules/delivery-status-reconciliation/README.md
docs/handover/2026-07-24-delivery-webhook-status-reconciliation.md
```

## 90. Final handover status

Completed in code on the feature branch:

```text
signed webhook durable ingestion: existing and reused
asynchronous webhook worker: implemented
provider normalization: implemented
append-only status audit: implemented
stale and terminal guards: implemented
tracking fallback: implemented
transactional status outbox: implemented
independent status publisher: implemented
final lease crash recovery: implemented
V102: implemented
event schema: implemented
tests: implemented
deployment fail-closed guard: implemented
```

Still pending:

```text
branch CI
PR merge
deployment from main
V102 Azure verification
isolated webhook database test
synthetic cleanup
Order Service consumer
Notification Service consumer
publisher propagation test
read-only Borzo sandbox tracking test
public callback ingress verification
provider callback registration
full delivery activation approval
```

No production delivery or Borzo capability should be enabled until the remaining gates are completed and documented.
