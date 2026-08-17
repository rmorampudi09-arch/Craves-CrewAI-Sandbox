# Delivery Status and Tracking Reconciliation

## Purpose

This module completes the Integration Service side of delivery lifecycle updates after a provider delivery job already exists.

It provides four independently controlled capabilities:

1. durable asynchronous webhook processing;
2. normalized delivery-job status application;
3. read-only provider tracking reconciliation when callbacks are delayed;
4. transactional `DELIVERY_STATUS_CHANGED` outbox publication.

It does not create a provider delivery and does not enable Borzo by itself.

## Safety defaults

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
CRAVES_DELIVERY_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=false
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
CRAVES_DELIVERY_INTELLIGENCE_ENABLED=true
BORZO_API_ENABLED=false
```

The normal Integration Service deployment pipeline writes and verifies all these values.

## Runtime flow

```text
Signed provider callback
  -> existing public webhook endpoint
  -> signature verification
  -> delivery_webhook_inbox RECEIVED
  -> HTTP success returned quickly

Asynchronous webhook worker
  -> recover stale final leases
  -> claim due inbox rows with FOR UPDATE SKIP LOCKED
  -> provider-specific normalization without network I/O
  -> locate and lock delivery_job
  -> append delivery_event
  -> ignore stale/unknown/terminal-regression updates safely
  -> update delivery_job for accepted state changes
  -> enqueue DELIVERY_STATUS_CHANGED transactionally
  -> mark inbox PROCESSED

Tracking fallback worker
  -> recover stale final leases
  -> claim overdue non-terminal delivery jobs
  -> provider track() read call
  -> reuse the same status-application transaction
  -> retry with bounded backoff or mark tracking dead-letter

Status publisher
  -> claim delivery_outbox rows
  -> publish DELIVERY_STATUS_CHANGED
  -> mark PUBLISHED or retry/dead-letter locally
```

## Main code paths

```text
services/integration-service/src/main/java/in/craves/integration/delivery/borzo/
  BorzoWebhookService.java              existing signed ingestion
  BorzoWebhookInboxRepository.java      existing durable intake
  BorzoWebhookNormalizer.java           new provider payload normalization
  BorzoStatusMapper.java                existing provider-to-canonical mapping

services/integration-service/src/main/java/in/craves/integration/delivery/provider/
  DeliveryProviderAdapter.java          create/cancel/track provider contract
  DeliveryWebhookNormalizer.java        network-free callback contract

services/integration-service/src/main/java/in/craves/integration/delivery/status/
  DeliveryStatusRepository.java
  DeliveryStatusUpdateService.java
  DeliveryWebhookProcessor.java
  DeliveryTrackingReconciliationWorker.java
  DeliveryLeaseRecoveryRepository.java

services/integration-service/src/main/java/in/craves/integration/delivery/command/
  DeliveryJobRepository.java
  DeliveryOutboxRepository.java
  DeliveryOutboxPublisher.java
  DeliveryServiceBusConfiguration.java
  DeliveryServiceBusPublisher.java
  DeliveryCommandProperties.java
  DeliveryCreateReconciliationWorker.java
```

## Database migration

```text
services/integration-service/src/main/resources/db/migration/
  V102__delivery_webhook_status_reconciliation.sql
```

V102 extends:

- `delivery_schema.delivery_webhook_inbox` with retry, lease, result, job and normalized-status fields;
- `delivery_schema.delivery_job` with provider status, observation source/time, tracking schedule, retry, lease, dead-letter and error fields;
- `delivery_schema.delivery_event` with source, provider status, applied/ignored audit fields.

Historical non-terminal jobs are deliberately not auto-scheduled for provider polling.

## Event contract

```text
contracts/events/delivery-status-changed-v1.schema.json
```

The event contains only delivery identifiers, provider identity, canonical status, tracking URL and observation time. Raw provider callback data remains internal to the Integration database.

## Local test

```bash
cd services/integration-service
mvn -B clean verify
```

The tests cover:

- Borzo delivery and order callback normalization;
- rejection of incomplete callbacks;
- newer status application and outbox enqueue;
- stale callback audit without state regression;
- terminal-state protection;
- webhook retry behavior;
- read-only tracking worker behavior;
- final-lease crash recovery wiring;
- V102 safety fields and historical-polling guard;
- fail-closed configuration defaults.

## Environment variables

### Webhook processing

```text
CRAVES_DELIVERY_WEBHOOK_PROCESSING_ENABLED=false
CRAVES_DELIVERY_WEBHOOK_BATCH_SIZE=20
CRAVES_DELIVERY_WEBHOOK_PROCESSING_INTERVAL_MS=2000
CRAVES_DELIVERY_MAX_WEBHOOK_ATTEMPTS=10
CRAVES_DELIVERY_WEBHOOK_RETRY_BASE_SECONDS=5
CRAVES_DELIVERY_WEBHOOK_STALE_MINUTES=5
```

### Tracking reconciliation

```text
CRAVES_DELIVERY_TRACKING_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_TRACKING_BATCH_SIZE=20
CRAVES_DELIVERY_TRACKING_RECONCILIATION_INTERVAL_MS=15000
CRAVES_DELIVERY_TRACKING_POLL_SECONDS=60
CRAVES_DELIVERY_MAX_TRACKING_ATTEMPTS=20
CRAVES_DELIVERY_TRACKING_RETRY_BASE_SECONDS=30
CRAVES_DELIVERY_TRACKING_STALE_MINUTES=5
```

### Status publication

```text
CRAVES_DELIVERY_STATUS_PUBLISHER_ENABLED=false
SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=<existing namespace FQDN>
SERVICE_BUS_TOPIC_NAME=craves-domain-events
```

### Existing Borzo controls

```text
BORZO_API_ENABLED=false
BORZO_API_BASE_URL=<sandbox or production URL>
BORZO_API_AUTH_TOKEN=<Key Vault-backed secret>
BORZO_CALLBACK_TOKEN=<Key Vault-backed secret>
```

Never place provider credentials in source, pipeline YAML, command output or chat.

## Controlled rollout order

1. Run branch CI only.
2. Merge after CI succeeds.
3. Deploy from `main` with all delivery/provider switches false.
4. Verify Flyway V102 and the new columns/indexes.
5. Run an isolated synthetic webhook database test with:
   - delivery creation off;
   - Borzo off;
   - tracking off;
   - status publication off;
   - webhook processing temporarily on.
6. Clean synthetic rows and disable webhook processing.
7. Build and validate downstream Order/Notification consumers.
8. Validate publisher-only propagation.
9. Validate read-only Borzo sandbox tracking with explicit approval.
10. Enable full delivery only after all gates are closed.

## Manual steps required

- Azure DevOps: run Integration Service CI against the feature branch.
- Azure DevOps: after merge, run the normal Integration deployment from `main`.
- Azure Portal/CLI: verify Container App flags and revision health.
- Azure Key Vault: preserve existing Borzo auth and callback secret references; do not reveal values.
- Service Bus: create downstream filtered subscriptions only as part of the downstream consumer module, not this module.
- Borzo dashboard: configure the production/sandbox callback only after the public ingress and controlled callback test are approved.

## Current scope exclusions

This module does not yet add:

- Order Service consumption of `DELIVERY_STATUS_CHANGED`;
- Notification Service delivery-status notifications;
- customer/chef UI tracking updates;
- APIM/public ingress changes for the callback;
- Borzo live sandbox callback registration;
- provider create activation;
- pricing, delivery fees, commissions, radius or compliance rules.

See the detailed handover for monitoring, rollback and test evidence requirements.
