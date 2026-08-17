# Delivery Status Handover Amendment — Scoped Scheduling and Final-Lease Recovery

**Date:** 2026-07-24  
**Parent handover:** `2026-07-24-delivery-webhook-status-reconciliation.md`  
**Branch:** `feature/delivery-webhook-status-reconciliation`

## 1. Why this amendment exists

The final static review found two cross-cutting worker-infrastructure issues after the main handover was written:

1. Integration scheduling is intentionally scoped and must not be enabled globally on `IntegrationServiceApplication`.
2. A replica crash after taking the final allowed lease can otherwise leave work permanently in-flight.

Both issues are corrected and regression-tested on this branch.

## 2. Scoped scheduling decision

Current Integration Service design deliberately leaves the application class without `@EnableScheduling`.

Scheduling is activated through conditional configuration classes only when a related feature switch is true. This prevents scheduled infrastructure from starting during a fail-closed deployment where all relevant workers are disabled.

New path:

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliverySchedulingConfiguration.java
```

The configuration enables scheduling when any of these properties is true:

```text
craves.delivery-command.enabled
craves.delivery-command.reconciliation-enabled
craves.delivery-command.webhook-processing-enabled
craves.delivery-command.tracking-reconciliation-enabled
craves.delivery-command.status-publisher-enabled
```

Normal deployment writes every corresponding execution switch as false, so delivery scheduling remains inactive after deployment.

## 3. Scheduling regression test

Updated path:

```text
services/integration-service/src/test/java/in/craves/integration/
  IntegrationServiceSchedulingTest.java
```

The test proves:

- scheduling is not enabled globally;
- refund status scheduling remains scoped to its refund flag;
- delivery scheduling is scoped to delivery worker flags;
- every new delivery worker flag is present in the condition.

## 4. Final-attempt lease failure mode

Each worker increments an attempt counter when claiming work. Without a terminal sweep, this sequence can occur:

```text
claim final allowed attempt
  -> processing lease stored
  -> replica stops before success/failure update
  -> lease becomes stale
  -> attempt_count is already at maximum
  -> row is not eligible for another claim
  -> row remains permanently in-flight
```

This is unacceptable because support cannot distinguish active processing from permanently abandoned work.

## 5. Terminal lease recovery implementation

New path:

```text
services/integration-service/src/main/java/in/craves/integration/delivery/status/
  DeliveryLeaseRecoveryRepository.java
```

Before each worker claims new work, the repository converts stale final leases into explicit terminal states.

### Create reconciliation

```text
status=DEAD_LETTER
reconciliation_processing_started_at=NULL
next_reconciliation_at=NULL
```

### Webhook processing

```text
processing_status=DEAD_LETTER
processing_started_at=NULL
processed_at=now()
```

### Tracking reconciliation

```text
next_tracking_at=NULL
tracking_processing_started_at=NULL
tracking_dead_lettered_at=now()
```

Each path preserves or records a safe error message.

## 6. Workers updated

```text
services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliveryCreateReconciliationWorker.java

services/integration-service/src/main/java/in/craves/integration/delivery/status/
  DeliveryWebhookProcessor.java
  DeliveryTrackingReconciliationWorker.java
```

Each worker performs its terminal sweep before claiming a new batch.

## 7. Database amendment included in V102

V102 includes:

```text
tracking_dead_lettered_at TIMESTAMPTZ
ix_delivery_job_tracking_dead_letter
```

Tracking claims exclude jobs where `tracking_dead_lettered_at` is populated.

Historical jobs are still not automatically scheduled for tracking.

## 8. Tests added or updated

```text
services/integration-service/src/test/java/in/craves/integration/delivery/status/
  DeliveryLeaseRecoveryRepositoryTest.java
  DeliveryWebhookProcessorTest.java
  DeliveryTrackingReconciliationWorkerTest.java

services/integration-service/src/test/java/in/craves/integration/delivery/command/
  DeliveryCreateReconciliationWorkerTest.java
```

The tests verify that terminal sweeps execute before batch claims and that SQL writes explicit support-visible terminal state.

## 9. Operational monitoring additions

Create reconciliation:

```sql
SELECT id, chef_sub_order_id, reconciliation_attempt_count,
       reconciliation_processing_started_at, last_error
FROM delivery_schema.delivery_command
WHERE status = 'DEAD_LETTER';
```

Webhook processing:

```sql
SELECT id, provider_id, provider_event_id, attempt_count,
       processing_status, error_message, processed_at
FROM delivery_schema.delivery_webhook_inbox
WHERE processing_status = 'DEAD_LETTER';
```

Tracking reconciliation:

```sql
SELECT id, chef_sub_order_id, provider_id, provider_delivery_id,
       tracking_attempt_count, tracking_dead_lettered_at,
       last_tracking_error
FROM delivery_schema.delivery_job
WHERE tracking_dead_lettered_at IS NOT NULL;
```

## 10. Safety conclusion

The amendment introduces no Azure resource, provider call, secret or activation.

The deployment state remains:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=false
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
BORZO_API_ENABLED=false
```

Branch CI must pass before merge. No live provider validation is authorized by this amendment.
