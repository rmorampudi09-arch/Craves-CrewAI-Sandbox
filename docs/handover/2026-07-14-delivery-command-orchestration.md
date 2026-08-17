# Delivery Command Orchestration Handover

**Date:** 14 July 2026  
**Service:** Craves Integration Service  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Branch:** `main`  
**Architecture references:** CRV-ARCH-HLD-001 v1.0 and CRV-INT-DELIVERY-001 v0.1

## Objective

Replace manual delivery-vendor dashboard work with an automatic, provider-neutral workflow that begins only
after a chef accepts a chef-specific sub-order. The Integration Service schedules dispatch near food readiness,
quotes active adapters, creates one provider delivery, persists the result, and publishes the canonical delivery
status through a transactional outbox.

## Decisions made

- Keep delivery orchestration inside the existing Spring Boot Integration Service; no new microservice.
- Use Azure Service Bus Standard because the approved topology requires both a topic/subscription and a queue.
- Use `craves-domain-events` for incoming `CHEF_ACCEPTED_ORDER` and outgoing `DELIVERY_STATUS_CHANGED`.
- Use `delivery-command` for delayed quote/create work.
- Use Service Bus native scheduled enqueue rather than a polling scheduler.
- Authenticate from Container Apps with system-assigned managed identity and data-plane RBAC.
- Keep a connection-string option only as a temporary fallback; do not use it in the preferred deployment.
- Keep orchestration disabled by default so the V4 deployment is safe before Azure resources exist.
- Use PeekLock and explicit complete/abandon/dead-letter settlement.
- Use PostgreSQL as the business-outcome authority; Service Bus provides at-least-once delivery.
- Use a dedicated Java 21 virtual-thread executor for bounded provider fan-out; do not use `parallelStream()`.
- Rank normalized pickup ETA first, delivery fee second, provider ID third for deterministic ties.
- Persist routing evidence and publish through the existing delivery outbox.

## Implemented source tree

```text
services/integration-service/
├── docs/delivery-orchestration/README.md
├── pom.xml
├── src/main/java/in/craves/integration/
│   ├── delivery/command/
│   │   ├── DeliveryCommandProperties.java
│   │   ├── DeliveryCommandModels.java
│   │   ├── DeliveryCommandRepository.java
│   │   ├── DeliveryProviderCatalogRepository.java
│   │   ├── DeliveryJobRepository.java
│   │   ├── DeliveryOutboxRepository.java
│   │   ├── DeliveryProviderRouter.java
│   │   ├── DeliveryServiceBusConfiguration.java
│   │   ├── DeliveryServiceBusPublisher.java
│   │   ├── DeliveryCommandScheduler.java
│   │   ├── DeliveryCommandWorker.java
│   │   ├── DeliveryCommandCompletionService.java
│   │   ├── DeliveryServiceBusProcessors.java
│   │   └── DeliveryOutboxPublisher.java
│   └── web/
│       └── DeliveryOrchestrationInternalController.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/V4__delivery_command_orchestration.sql
└── src/test/java/in/craves/integration/delivery/command/
    ├── DeliveryCommandSchedulerTest.java
    ├── DeliveryProviderRouterTest.java
    └── DeliveryCommandWorkerTest.java
```

## Runtime behavior

### Chef acceptance intake

The topic subscription receives only messages whose application property is:

```text
event_type = CHEF_ACCEPTED_ORDER
```

The message body is the approved event envelope. The scheduler validates event identity, version, occurrence,
correlation, source, subject, order ID, chef sub-order ID, ready time and delivery request.

### Delayed scheduling

The command is stored first with a unique `chef_sub_order_id`. The dispatch time is:

```text
dispatchAt = readyAt - configuredLeadTime
```

The default lead is ten minutes. When that timestamp has already passed, scheduling uses a minimum of five
seconds in the future to avoid immediate/past-time ambiguity.

The Service Bus message ID is deterministic:

```text
delivery-command:<chefSubOrderId>
```

Queue duplicate detection is therefore required during Azure provisioning. The scheduled sequence number is
stored in PostgreSQL for audit and cancellation support.

### Delivery worker

The queue worker atomically claims the command. A stale `PROCESSING` lease can be recovered after ten minutes.
Before any provider call, it checks whether a delivery job already exists for the chef sub-order. This makes a
redelivered message return the existing business outcome instead of creating a second delivery.

The router loads only providers marked active in `delivery_schema.delivery_provider` and intersects them with
adapters actually deployed in the application. Quotes execute concurrently with a four-second bounded timeout.
Candidates are deterministic and fallback is bounded by configuration.

### Atomic completion

One database transaction:

1. inserts the final `delivery_job` and complete routing audit snapshot;
2. inserts `DELIVERY_STATUS_CHANGED` into `delivery_outbox`;
3. marks the `delivery_command` completed.

The outbox worker later publishes the event to the topic. An outbox processing lease recovers a replica crash.
The Service Bus topic must also enable duplicate detection because a crash can happen after send but before the
outbox row is marked published.

## Flyway V4

The migration adds:

```text
delivery_command.source_event_id
delivery_command.scheduled_sequence_number
delivery_command.service_bus_message_id
delivery_command.processing_started_at
delivery_outbox.processing_started_at
```

It also adds event uniqueness, command-lease, outbox-due and outbox-lease indexes. No existing table or status
constraint is replaced.

## Environment variables

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=
SERVICE_BUS_CONNECTION_STRING=
SERVICE_BUS_TOPIC_NAME=craves-domain-events
SERVICE_BUS_CHEF_ACCEPTED_SUBSCRIPTION=integration-service-chef-accepted
SERVICE_BUS_DELIVERY_COMMAND_QUEUE=delivery-command
CRAVES_DELIVERY_COMMAND_LEAD_TIME_MINUTES=10
CRAVES_DELIVERY_QUOTE_TIMEOUT_SECONDS=4
CRAVES_DELIVERY_MAX_PROVIDER_ATTEMPTS=3
CRAVES_DELIVERY_MAX_DELIVERY_ATTEMPTS=5
CRAVES_DELIVERY_MAX_CONCURRENT_MESSAGES=4
CRAVES_DELIVERY_PREFETCH_COUNT=8
CRAVES_DELIVERY_MAX_AUTO_LOCK_RENEW_MINUTES=5
CRAVES_DELIVERY_OUTBOX_BATCH_SIZE=20
CRAVES_DELIVERY_OUTBOX_PUBLISH_INTERVAL_MS=5000
```

Do not add `SERVICE_BUS_CONNECTION_STRING` when managed identity is configured.

## Security controls

- No Service Bus key is committed to GitHub.
- Managed identity is the preferred runtime credential.
- RBAC uses Azure Service Bus Data Sender and Azure Service Bus Data Receiver, not management-owner rights.
- The internal controlled-test endpoint requires `X-Craves-Internal-Secret`.
- The internal endpoint must remain outside public APIM products.
- Provider credentials remain separate Container App secret references until Key Vault migration.
- Provider and customer PII must not be logged from message bodies.

## Tests added

- Scheduler computes the lead time and records the scheduled sequence number.
- Router quotes each adapter once, ranks by ETA, and falls back without requoting.
- Worker treats a redelivered command with an existing job as completed and never calls a provider.
- Existing Spring bean-name uniqueness and Borzo tests remain part of the Maven pipeline.

## Deployment state at handover

The code is committed to `main`, but it has not yet been deployed in this handover stage. The first deployment
must keep:

```text
CRAVES_DELIVERY_COMMAND_ENABLED=false
BORZO_API_ENABLED=false
```

That deployment should only compile, run tests, apply V4, and prove the existing application remains healthy.
It must not connect to Service Bus or create a Borzo order.

## Manual steps required

### Azure Portal / Cloud Shell — billable

- Create one Standard Service Bus namespace in `rg-craves-prodlow-centralindia`.
- Create `craves-domain-events` with duplicate detection.
- Create `integration-service-chef-accepted` and replace the default rule with the CHEF_ACCEPTED_ORDER filter.
- Create `delivery-command` with duplicate detection, PeekLock-compatible settings and maximum delivery count 5.
- Assign a system identity to `ca-craves-integration-service-pr`.
- Grant Azure Service Bus Data Sender and Azure Service Bus Data Receiver on the namespace.

### Container App configuration

- Add `SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE` as a non-secret environment value.
- Keep `SERVICE_BUS_CONNECTION_STRING` absent.
- Keep `CRAVES_DELIVERY_COMMAND_ENABLED=false` until infrastructure and health verification finish.
- Later enable only during a controlled sandbox test.

### Order Service

- Implement the transactional outbox publisher for `CHEF_ACCEPTED_ORDER`.
- Include the exact event envelope and `event_type` application property.
- Do not call the Integration Service synchronously to create a delivery at payment success.

## Verification sequence

1. Run a new Azure DevOps Integration Service pipeline from `main`.
2. Confirm Maven tests pass.
3. Confirm V4 is applied and Spring starts with Service Bus disabled.
4. Provision Service Bus and RBAC.
5. Bind namespace/entity environment variables, still disabled.
6. Confirm the new Container App revision is healthy.
7. Enable Borzo sandbox and mark Borzo active only for the test window.
8. Enable delivery commands.
9. Submit one internal chef-accepted event with a future ready time.
10. Confirm one scheduled message and one command row.
11. After dispatch, confirm one Borzo sandbox order, one delivery job and one published outbox event.
12. Repeat the same event and confirm no second business outcome.
13. Cancel the sandbox order and return Borzo and orchestration to disabled.

## Open risks and pending modules

1. **Ambiguous provider timeout:** Borzo does not guarantee `client_order_id` as provider idempotency. Before
   production automatic retry, add provider reconciliation for timeouts where create may have succeeded remotely.
2. **Normalized ETA contract:** ETA is currently read from known normalized metadata keys. Add an explicit ETA
   field to the provider-neutral contract before real multi-provider routing.
3. **Webhook processing:** signed webhook inbox ingestion is complete, but asynchronous inbox processing into
   `delivery_event`, `delivery_job` and outbox remains pending.
4. **Order/Admin support state:** exhausted delivery commands dead-letter correctly, but Order/Admin support-required
   propagation needs the downstream consumer.
5. **Provider coverage:** only Borzo exists. Shadowfax and Porter require vendor-approved schemas and credentials.
6. **Network hardening:** private ingress/APIM policy and Service Bus private networking are later hardening gates.
7. **Secret hardening:** vendor secrets must migrate from direct Container App secrets to Key Vault references.
8. **Production readiness:** business registration, KYC, written SLA/commercial terms and a two-provider Hyderabad
   pilot remain mandatory.
