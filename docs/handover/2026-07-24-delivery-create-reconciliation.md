# Craves Delivery Create Reconciliation Handover

**Date:** 2026-07-24  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Branch:** `feature/delivery-create-reconciliation`  
**Service:** `services/integration-service`  
**Status:** Implementation complete on branch; Maven/CI and Azure controlled validation pending  
**Production provider execution:** Disabled  

## 1. Purpose

This change closes the unsafe ambiguity that exists when Craves sends a delivery-create request to a provider but does not receive the response.

A connection or read timeout does not prove that the provider rejected the request. The provider may have created the delivery and only the response may have been lost. Immediately retrying create, or falling back to another provider, could therefore create two active delivery bookings for one `chef_sub_order_id`.

The implementation is deliberately fail-closed:

1. A normal provider rejection may still use the existing bounded provider fallback.
2. A lost create response is classified as an uncertain create.
3. The command moves to `RECONCILIATION_PENDING`.
4. The Service Bus command is completed so it cannot call create again.
5. A separate worker performs read-only provider reconciliation.
6. No second create and no fallback are allowed while the outcome is uncertain.
7. The existing delivery-job/outbox transaction completes only after the exact provider order is found.
8. Unresolved records retry with bounded exponential backoff and eventually move to `DEAD_LETTER` for manual investigation.

## 2. Safety state

The new functionality is disabled by default.

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

The normal Integration Service deployment pipeline explicitly writes and verifies these values. Merely merging or deploying this branch cannot create a Borzo order.

## 3. Previously completed gates

The following Azure tests were completed before this change:

- Delivery command persistence verified.
- PostgreSQL `Instant` to `timestamptz` binding defect fixed and deployed.
- Duplicate `CHEF_ACCEPTED_ORDER` idempotency verified using two event IDs for one `chef_sub_order_id`.
- Exactly one command row and one scheduled Service Bus message were produced.
- Synthetic scheduled messages and database rows were removed.
- Topic and queue active/dead-letter/scheduled counts returned to zero.
- Borzo remained disabled during all tests.

Safe runtime at the end of the previous test:

```text
Container App revision: ca-craves-integration-service-pr--0000044
Delivery processing: false
Delivery intelligence: true
Borzo: false
```

## 4. Provider API basis

Craves writes a deterministic reference on the delivery point:

```text
CRV-<first 28 hexadecimal characters of chef_sub_order_id>
```

The reference is at most 32 characters and is sent as the provider point's `client_order_id`.

The Borzo orders endpoint is read-only and returns recent orders in descending order. Reconciliation pages through recent orders using `offset` and `count`, inspects each point, and requires an exact `client_order_id` match.

The implementation does not infer a match from phone number, address, amount, order time, or contact name. Those fields are not unique enough to safely identify a provider booking.

## 5. State-machine change

### Existing command states

```text
SCHEDULED
PROCESSING
COMPLETED
FAILED
DEAD_LETTER
```

### New state

```text
RECONCILIATION_PENDING
```

### New transition

```text
PROCESSING
  -> provider create response lost
  -> RECONCILIATION_PENDING
  -> read-only provider lookup
      -> exact order found -> COMPLETED
      -> unresolved -> RECONCILIATION_PENDING
      -> retry budget exhausted -> DEAD_LETTER
```

There is intentionally no transition from `RECONCILIATION_PENDING` back to a create attempt.

## 6. Runtime flow

### 6.1 Normal successful create

```text
Delivery command received
  -> command row claimed
  -> provider quotes collected
  -> intelligent assignment loaded/created
  -> selected provider create succeeds
  -> delivery job inserted
  -> DELIVERY_STATUS_CHANGED outbox row inserted
  -> command marked COMPLETED
  -> Service Bus message completed
```

### 6.2 Normal provider rejection

```text
Selected provider create returns a definite HTTP/application rejection
  -> failure audit recorded
  -> next ranked provider may be attempted
  -> bounded by CRAVES_DELIVERY_MAX_PROVIDER_ATTEMPTS
```

### 6.3 Uncertain provider create

```text
Selected provider create request is sent
  -> connection/read response is lost
  -> ProviderCreateUncertainException
  -> fallback loop stops immediately
  -> command marked RECONCILIATION_PENDING
  -> provider ID, client reference, and attempted time persisted
  -> Service Bus message completed
```

### 6.4 Reconciliation

```text
Scheduled reconciliation worker
  -> claims due rows with FOR UPDATE SKIP LOCKED
  -> checks whether local delivery job already exists
  -> loads persisted assignment and provider candidate
  -> calls provider reconcileCreate(clientReference, attemptedAt)
  -> pages recent provider orders
  -> exact client_order_id found
  -> existing completion transaction creates delivery job/outbox
  -> command marked COMPLETED
```

### 6.5 Unresolved reconciliation

```text
No exact match, provider unavailable, page limit reached, or invalid response
  -> no create call
  -> no fallback
  -> next_reconciliation_at calculated with exponential backoff
  -> reconciliation attempt count incremented
  -> eventually DEAD_LETTER after configured maximum
```

## 7. Database changes

Migration:

```text
services/integration-service/src/main/resources/db/migration/
  V101__delivery_create_reconciliation.sql
```

New columns in `delivery_schema.delivery_command`:

```text
reconciliation_provider_id
reconciliation_client_reference
reconciliation_started_at
reconciliation_attempt_count
reconciliation_processing_started_at
next_reconciliation_at
```

The migration also:

- Adds `RECONCILIATION_PENDING` to the command-status constraint.
- Enforces non-negative reconciliation attempts.
- Requires provider ID, client reference, start time, and next-attempt time for pending rows.
- Adds a partial index for due reconciliation work.

No new Azure resource or new database is created.

## 8. Concurrency behaviour

The reconciliation claim query uses:

```text
FOR UPDATE SKIP LOCKED
```

This means multiple Integration Service replicas may run the worker without reconciling the same command at the same time.

A processing timestamp acts as a lease. If a replica stops after claiming a row, another replica may reclaim it after `reconciliation-stale-minutes`.

The final delivery job remains protected by the existing unique `chef_sub_order_id` constraint and the completion transaction.

## 9. Configuration

### Delivery reconciliation controls

```text
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_BATCH_SIZE=20
CRAVES_DELIVERY_RECONCILIATION_INTERVAL_MS=15000
CRAVES_DELIVERY_MAX_RECONCILIATION_ATTEMPTS=20
CRAVES_DELIVERY_RECONCILIATION_RETRY_BASE_SECONDS=30
CRAVES_DELIVERY_RECONCILIATION_STALE_MINUTES=10
```

### Borzo bounded-search controls

```text
BORZO_RECONCILIATION_PAGE_SIZE=50
BORZO_RECONCILIATION_MAX_PAGES=5
BORZO_RECONCILIATION_LOOKBACK_SECONDS=120
```

### Existing provider controls

```text
BORZO_API_ENABLED=false
BORZO_API_BASE_URL=<sandbox or production URL>
BORZO_API_AUTH_TOKEN=<Key Vault-backed secret>
BORZO_CONNECT_TIMEOUT_SECONDS=5
BORZO_READ_TIMEOUT_SECONDS=20
```

Never paste the Borzo auth token into chat, source code, pipeline YAML, or logs.

## 10. Files changed

### Provider-neutral contract

```text
services/integration-service/src/main/java/in/craves/integration/delivery/provider/
  DeliveryProviderAdapter.java
```

Adds:

- `reconcileCreate`
- `CreateReconciliationStatus`
- `CreateReconciliationResult`
- `ProviderCreateUncertainException`

### Borzo provider adapter

```text
services/integration-service/src/main/java/in/craves/integration/delivery/borzo/
  BorzoApiClient.java
```

Adds:

- uncertain-create classification for lost responses
- bounded recent-order paging
- exact point-level `client_order_id` matching
- creation-time window checks
- fail-closed inconclusive results

### Durable command state

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliveryCommandRepository.java
```

Adds:

- persistence of uncertain-create identity
- safe multi-replica claim
- retry scheduling
- reconciliation attempt tracking
- terminal dead-letter transition

### Routing safety boundary

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliveryProviderRouter.java
```

Changes:

- definite create failures may continue existing fallback
- uncertain create stops fallback immediately
- reconciliation uses the persisted assignment and exact provider candidate
- reconciliation never calls create

### Service Bus worker integration

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliveryCommandWorker.java
```

Changes:

- moves uncertain create to `RECONCILIATION_PENDING`
- completes duplicate/redelivered messages already pending reconciliation
- avoids command redelivery becoming another create attempt

### Reconciliation worker

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliveryCreateReconciliationWorker.java
```

Responsibilities:

- claims due pending rows
- performs read-only lookup
- calls existing completion transaction only on an exact match
- retries unresolved records with bounded exponential backoff

### Configuration

```text
services/integration-service/src/main/java/in/craves/integration/config/
  BorzoProperties.java

services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliveryCommandProperties.java

services/integration-service/src/main/resources/
  application.yml
```

### Deployment guard

```text
azure-pipelines-integration-service.yml
```

The deployment step explicitly sets and verifies:

```text
delivery=false
reconciliation=false
intelligence=true
borzo=false
```

### Tests

```text
services/integration-service/src/test/java/in/craves/integration/delivery/borzo/
  BorzoApiClientTest.java

services/integration-service/src/test/java/in/craves/integration/delivery/command/
  DeliveryCommandSchedulerTest.java
  DeliveryCommandWorkerTest.java
  DeliveryCreateReconciliationWorkerTest.java
  DeliveryProviderRouterTest.java
```

## 11. Test coverage added

The branch adds tests proving:

1. A Borzo create call whose response is lost becomes `ProviderCreateUncertainException`.
2. The Craves client reference is preserved in the uncertain exception.
3. The router does not call the backup provider after an uncertain create.
4. The command worker persists `RECONCILIATION_PENDING` and does not call completion.
5. Borzo reconciliation finds the exact point-level `client_order_id`.
6. Router reconciliation returns the recovered provider delivery without calling create.
7. The reconciliation worker calls the existing completion transaction for a recovered delivery.
8. Existing normal provider fallback remains functional for definite failures.

## 12. Required CI before merge

Run the Integration Service Maven build on the branch or PR:

```bash
cd services/integration-service
mvn -B clean package
```

Required result:

```text
BUILD SUCCESS
Tests run: all passed
```

Do not merge if any compilation, unit-test, Flyway-validation, or Spring-context test fails.

## 13. Safe deployment sequence

### Stage 1 — Deploy code and migration only

Use the normal Integration Service deployment pipeline from the merged `main` commit.

The pipeline must finish with:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

This stage applies migration V101 but runs no delivery processor, no reconciliation worker, and no Borzo API request.

### Stage 2 — Validate schema

Verify the new columns and constraint exist. Do not change feature flags.

Suggested read-only query:

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'delivery_schema'
  AND table_name = 'delivery_command'
  AND column_name IN (
      'reconciliation_provider_id',
      'reconciliation_client_reference',
      'reconciliation_started_at',
      'reconciliation_attempt_count',
      'reconciliation_processing_started_at',
      'next_reconciliation_at'
  )
ORDER BY column_name;
```

### Stage 3 — Controlled mocked/isolated reconciliation test

Do not create a real provider order for the first validation.

The preferred test is an isolated provider stub that simulates:

1. create response timeout after recording the order
2. exact client-reference lookup returning that order
3. one local delivery job
4. one delivery status outbox event
5. command state `COMPLETED`
6. zero second create calls
7. zero backup provider calls

### Stage 4 — Controlled Borzo sandbox test

This stage requires explicit approval because it may create a real sandbox provider booking.

Keep the test to one synthetic chef sub-order and one deterministic client reference. Capture:

- provider order ID
- command state transitions
- reconciliation attempts
- delivery job row
- outbox row
- provider create-call count
- provider list/read-call count

Clean up or cancel the sandbox provider order after validation.

### Stage 5 — Reconciliation-only activation

Only after a valid pending test row exists and sandbox behaviour is confirmed:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=true
BORZO_API_ENABLED=true
```

This mode performs read-only reconciliation for already-pending work. It does not consume new delivery commands because delivery command processing remains off.

### Stage 6 — Full delivery activation

Full activation requires separate approval after webhook/status processing is complete:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=true
CRAVES_DELIVERY_RECONCILIATION_ENABLED=true
BORZO_API_ENABLED=true
```

## 14. Rollback

### Immediate runtime rollback

Set:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
BORZO_API_ENABLED=false
```

Verify the new Container App revision is healthy and ready.

### Application image rollback

Repoint the Container App to the last known safe Integration Service image while keeping all three flags false.

### Database rollback

Do not manually delete Flyway history or remove V101 columns during an incident. The added columns and status are backward-compatible while the features remain disabled.

A schema rollback must be a separate reviewed forward migration after all pending reconciliation records are resolved.

## 15. Monitoring queries

### Pending reconciliation commands

```sql
SELECT
    id,
    chef_sub_order_id,
    order_id,
    reconciliation_provider_id,
    reconciliation_client_reference,
    reconciliation_started_at,
    reconciliation_attempt_count,
    next_reconciliation_at,
    reconciliation_processing_started_at,
    last_error,
    updated_at
FROM delivery_schema.delivery_command
WHERE status = 'RECONCILIATION_PENDING'
ORDER BY next_reconciliation_at, created_at;
```

### Reconciliation dead letters

```sql
SELECT
    id,
    chef_sub_order_id,
    order_id,
    reconciliation_provider_id,
    reconciliation_client_reference,
    reconciliation_attempt_count,
    last_error,
    updated_at
FROM delivery_schema.delivery_command
WHERE status = 'DEAD_LETTER'
  AND reconciliation_started_at IS NOT NULL
ORDER BY updated_at DESC;
```

### Possible duplicate provider delivery protection

```sql
SELECT provider_id, provider_delivery_id, COUNT(*)
FROM delivery_schema.delivery_job
WHERE provider_delivery_id IS NOT NULL
GROUP BY provider_id, provider_delivery_id
HAVING COUNT(*) > 1;
```

Expected result: zero rows.

### One delivery job per chef sub-order

```sql
SELECT chef_sub_order_id, COUNT(*)
FROM delivery_schema.delivery_job
GROUP BY chef_sub_order_id
HAVING COUNT(*) > 1;
```

Expected result: zero rows.

## 16. Alerts recommended before activation

Add alerts for:

- any `RECONCILIATION_PENDING` row older than 10 minutes
- any reconciliation command reaching 5 attempts
- any reconciliation-related `DEAD_LETTER`
- sudden increase in Borzo `/orders` errors or latency
- command pending count growing continuously
- completion transaction failures
- delivery outbox dead letters

These alerts are operational follow-up; they are not provisioned by this branch.

## 17. Manual steps required

### Azure Portal / Container Apps

- None for code review or CI.
- Deployment is a billing-neutral update to the existing Container App and ACR image.
- Feature activation must be performed only after the controlled validation stages.

### Secrets and credentials

- Do not paste provider credentials into chat.
- Continue using the existing Key Vault-backed `BORZO_API_AUTH_TOKEN` reference.
- No new secret name is required by this branch.

### Borzo dashboard

- No action for code review or fail-closed deployment.
- A controlled sandbox order/cancellation is required only during the approved sandbox validation stage.

### Service Bus

- No new topic, subscription, or queue is required.

### Database

- Flyway applies V101 during normal service startup.
- Do not run the migration manually unless the standard deployment process fails and a reviewed recovery procedure explicitly requires it.

## 18. Known limitations and remaining work

This branch closes ambiguous create timeout handling only.

The following delivery work remains pending:

1. Process accepted Borzo webhook inbox rows into `delivery_event`.
2. Resolve webhook events to the correct `delivery_job`.
3. Update normalized delivery-job status idempotently.
4. Publish `DELIVERY_STATUS_CHANGED` through the existing outbox.
5. Handle out-of-order and duplicate provider callbacks.
6. Add scheduled tracking reconciliation for missed callbacks.
7. Validate customer and chef notifications downstream.
8. Add dashboards and alerts described above.

Until that work is implemented and tested:

```text
BORZO_API_ENABLED=false
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
```

must remain the default deployment state.

## 19. Review checklist

- [ ] Maven build succeeds on Java 21.
- [ ] All existing Integration Service tests pass.
- [ ] New uncertain-create tests pass.
- [ ] V101 validates against existing Flyway history.
- [ ] No code path retries create from `RECONCILIATION_PENDING`.
- [ ] No code path falls back after `ProviderCreateUncertainException`.
- [ ] Reconciliation uses exact `client_order_id` only.
- [ ] Reconciliation paging is bounded.
- [ ] Worker claim is multi-replica safe.
- [ ] Reconciliation is disabled by default.
- [ ] Borzo is disabled by default.
- [ ] Deployment pipeline verifies all fail-closed flags.
- [ ] No secrets are committed.
- [ ] No live provider call occurs during CI.

## 20. Next action

Open the draft pull request and run the Integration Service CI/Maven build. Resolve every CI finding before marking the PR ready for review or merging it.
