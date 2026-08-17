# Delivery Provider Outage Resilience Handover — 16 August 2026

## Incident that exposed the defect

Sandbox order `#B6626658` produced a delivery command but reached `DEAD_LETTER` before a delivery job was created.

Observed database state before recovery:

```text
status        = DEAD_LETTER
attempt_count = 5
last_error    = No active delivery providers are configured
```

The five attempts were consumed almost immediately because the Service Bus processor abandoned the same message after a transient routing failure. Service Bus could redeliver it immediately, so the configured delivery attempt budget was exhausted before the provider configuration recovered.

Two later sandbox orders, `#7F20B6B1` and `#78678021`, completed intelligent assignment and Borzo booking after Borzo was active. This isolated the failure to provider availability/retry handling rather than order creation, chef acceptance, intelligent scoring, or Borzo booking.

## Production risk

The previous behavior was not acceptable for live customer traffic. A temporary provider configuration outage or a period in which all active provider quote calls failed could permanently dead-letter a delivery command in seconds. The commercial customer order remained durable, but fulfillment stopped until manual database and Service Bus intervention.

## Implemented design

Temporary provider infrastructure failures now use a separate durable state:

```text
WAITING_FOR_PROVIDER
```

This state does not consume the normal `max-delivery-attempts` budget while the provider layer is unavailable.

The Service Bus delivery-command processor no longer uses immediate `abandon()` for worker-classified transient delivery failures. Instead it schedules a new copy of the original command body for a future enqueue time and completes the current message after the scheduled retry is accepted by Service Bus.

The retry message receives a transport-level retry message ID while the business payload preserves the original:

- `commandId`
- `orderId`
- `chefSubOrderId`
- `idempotencyKey`

This retains existing command and provider idempotency guarantees.

## Provider outage classification

`DeliveryProviderRouter` now distinguishes temporary provider infrastructure failures from ordinary routing failures.

The following conditions are classified as temporary provider unavailability:

1. no active provider exists in the provider catalog;
2. active providers exist, but none returns a quote response because adapters are absent or provider quote calls fail.

A provider response that explicitly returns no available quote remains an ordinary routing failure. This prevents an unsupported/unserviceable route from waiting indefinitely merely because a provider responded `available=false`.

## Retry behavior

Default delayed retry timing:

```text
attempt 1:  30 seconds
attempt 2:  60 seconds
attempt 3: 120 seconds
attempt 4: 240 seconds
attempt 5: 480 seconds
attempt 6+: 600 seconds maximum
```

Provider-wait attempts are stored separately in `provider_wait_attempt_count` and do not increment the normal command `attempt_count` when a `WAITING_FOR_PROVIDER` command is reclaimed.

Normal processing failures still retain the existing finite `max-delivery-attempts` protection, but their retries are now scheduled with backoff rather than intentionally rapid redelivery when they are surfaced as `DeliveryCommandTransientException`.

Runtime tuning variables:

```text
CRAVES_DELIVERY_RETRY_BASE_SECONDS=30
CRAVES_DELIVERY_RETRY_MAX_SECONDS=600
CRAVES_DELIVERY_CLAIM_CONTENTION_SECONDS=10
```

## Database migration

Migration:

```text
services/integration-service/src/main/resources/db/migration/V109__delivery_provider_wait_resilience.sql
```

Adds:

```text
provider_wait_attempt_count
provider_wait_started_at
next_provider_retry_at
```

and permits the new command status:

```text
WAITING_FOR_PROVIDER
```

An indexed provider-wait due path is also added for operational inspection.

## Changed code paths

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryProviderRouter.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCommandRepository.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCommandWorker.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryCommandRetryProperties.java
services/integration-service/src/main/java/in/craves/integration/delivery/command/DeliveryServiceBusProcessors.java
services/integration-service/src/main/resources/application.yml
services/integration-service/src/main/resources/db/migration/V109__delivery_provider_wait_resilience.sql
```

Tests updated/added:

```text
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryCommandWorkerTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryProviderRouterTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryCreateReconciliationWorkerTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryCommandSchedulerTest.java
services/integration-service/src/test/java/in/craves/integration/delivery/command/DeliveryCommandRetryPropertiesTest.java
```

## Deployment gate

Deploy only through:

```text
azure-pipelines-integration-service.yml
```

The pipeline runs:

```text
mvn -B -ntp clean verify
```

before building and pushing the Integration Service image. Do not replay the sandbox regression order if Maven verification or deployment fails.

## Post-deployment schema verification

Run against the Integration PostgreSQL database:

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'delivery_schema'
  AND table_name = 'delivery_command'
  AND column_name IN (
      'provider_wait_attempt_count',
      'provider_wait_started_at',
      'next_provider_retry_at'
  )
ORDER BY column_name;
```

Expected: three rows.

## Controlled outage regression test

Before replaying the historical failed order, validate the state machine with a controlled sandbox order where the provider catalog is temporarily unavailable or the provider is disabled.

Expected behavior after the fix:

```text
PROCESSING
  -> WAITING_FOR_PROVIDER
  -> scheduled Service Bus retry
  -> WAITING_FOR_PROVIDER if provider still unavailable
  -> PROCESSING after provider becomes available
  -> intelligent assignment
  -> delivery job
  -> COMPLETED
```

The command must not become `DEAD_LETTER` merely because the provider layer remains unavailable across repeated provider-wait cycles.

Operational inspection query:

```sql
SELECT
    chef_sub_order_id,
    status,
    attempt_count,
    provider_wait_attempt_count,
    provider_wait_started_at,
    next_provider_retry_at,
    last_error,
    updated_at
FROM delivery_schema.delivery_command
WHERE status = 'WAITING_FOR_PROVIDER'
ORDER BY provider_wait_started_at;
```

## Historical `#B6626658` regression replay

The order was manually reset from the historical `DEAD_LETTER` state to `FAILED` with `attempt_count=0` before this code change was deployed. No delivery job and no delivery assignment existed at the time of that reset.

After the new Integration Service revision is healthy, re-enqueue the existing stored command payload rather than creating a new customer order. The payload must retain the original command identity and use a new Service Bus transport message ID.

Expected final state while Borzo sandbox is active:

```text
command.status                 = COMPLETED
command.attempt_count          = 1
delivery_assignment.status     = ASSIGNED
delivery_assignment.strategy   = STOCHASTIC
delivery_job.provider_id       = borzo
delivery_job.status            = SEARCHING or later
```

## Remaining production hardening

This change prevents the specific rapid-dead-letter provider outage defect. The following should still be completed before broad production rollout:

1. Azure Monitor alert for `WAITING_FOR_PROVIDER` commands older than the agreed operations threshold.
2. Azure Monitor alert for delivery commands entering `DEAD_LETTER`.
3. Customer-facing projection for a pre-booking provider-delay state so the UI does not misleadingly remain only at `READY_FOR_PICKUP`.
4. Operational admin/replay endpoint or runbook automation so support staff never need direct SQL mutation for ordinary recovery.
5. Multi-provider sandbox activation after additional Spring provider adapters are implemented, allowing the intelligent engine to choose among more than one executable provider.

No pricing, commission, delivery-radius, or provider commercial-selection rule was changed by this resilience work.
