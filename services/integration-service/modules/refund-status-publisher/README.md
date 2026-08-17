# Integration Service — Refund Status Publisher

## Purpose

This module activates the existing Integration Service transactional outbox publisher for `REFUND_STATUS_CHANGED` events.

The publisher reads durable rows from:

```text
payment_schema.refund_status_outbox
```

and publishes them to:

```text
Azure Service Bus namespace: sb-craves-prodlow-l3ing6
Topic: craves-domain-events
Application property: eventType = REFUND_STATUS_CHANGED
```

Order Service consumes the event through its already validated filtered subscription and updates only the affected chef-specific order.

## Scope

This module includes:

```text
services/integration-service/src/main/java/in/craves/integration/refund/RefundStatusPublisherSchedulingConfiguration.java
services/integration-service/src/test/java/in/craves/integration/IntegrationServiceSchedulingTest.java
azure-pipelines-integration-refund-status-publisher-enable.yml
services/integration-service/modules/refund-status-publisher/README.md
docs/handover/2026-07-16-integration-refund-status-publisher-enablement.md
```

No database migration is required because Flyway V100 already created the refund status outbox.

## Scheduling boundary

Scheduling is enabled only when this property is true:

```text
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true
```

The main Integration application is not globally annotated with `@EnableScheduling`. Instead, the conditional refund publisher configuration enables scheduling only for the activated revision. This avoids broad, accidental scheduler activation during normal deployments where the publisher is disabled.

Other scheduled workers remain protected by their own runtime checks. In particular, the refund execution worker immediately returns while both provider execution and reconciliation are false.

## Runtime behavior

The existing publisher:

1. Claims due outbox records with PostgreSQL row locking.
2. Uses a unique lock token for multi-replica safety.
3. Sends the stored JSON payload to the domain-events topic.
4. Sets the Service Bus message ID to the event ID.
5. Adds `eventType`, `eventVersion`, `source`, and `subject` application properties.
6. Marks the row `PUBLISHED` only after Service Bus send succeeds.
7. Retains failed rows for bounded retry.
8. Moves repeatedly failing rows to local `DEAD_LETTER` state.

Service Bus is at-least-once. A send may be repeated if the broker accepts the event but the database acknowledgement fails. The Order Service consumer is idempotent by event ID, so this is expected and safe.

## Required configuration

```text
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true
SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE=sb-craves-prodlow-l3ing6.servicebus.windows.net
SERVICE_BUS_TOPIC_NAME=craves-domain-events
```

The Integration Container App managed identity must have:

```text
Azure Service Bus Data Sender
```

at either the namespace or topic scope.

## Safety settings that must remain unchanged

```text
CRAVES_REFUND_CONSUMER_ENABLED=true
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_DELIVERY_COMMAND_ENABLED=false
BORZO_API_ENABLED=false
```

Order Service must remain:

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=true
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

Therefore this module does not:

- call Cashfree;
- reconcile a Cashfree refund;
- publish `REFUND_REQUESTED` from Order Service;
- run the chef acceptance timeout worker;
- create delivery commands;
- call Borzo.

## Controlled enablement pipeline

Register and run:

```text
/azure-pipelines-integration-refund-status-publisher-enable.yml
Branch: main
```

Required pipeline variable:

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

The pipeline fails closed unless all of the following are true:

- the Service Bus topic exists;
- the Order refund-status subscription exists;
- its approved SQL filter contains `eventType = 'REFUND_STATUS_CHANGED'`;
- its DLQ is empty;
- the Order refund-status consumer is enabled;
- the Order timeout/refund-request publisher remains disabled;
- the Integration refund consumer is enabled;
- Cashfree execution and reconciliation remain disabled;
- delivery execution and Borzo remain disabled;
- the Integration managed identity has Service Bus Data Sender.

## Local test

From the repository root:

```bash
cd services/integration-service
mvn -B clean verify
```

The CI pipeline is authoritative for compilation and automated tests.

## Rollback

Disable only the publisher:

```bash
az containerapp update \
  --resource-group rg-craves-prodlow-centralindia \
  --name ca-craves-integration-service-pr \
  --set-env-vars CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=false
```

Disabling the publisher does not delete outbox rows. Pending events remain durable for later controlled replay.

## Deferred work

The following remain separate approvals:

- Cashfree sandbox refund execution;
- Cashfree refund reconciliation;
- Order `REFUND_REQUESTED` publisher allow-list expansion;
- chef acceptance timeout worker activation;
- customer refund-status notifications;
- operations dashboard and alerting for outbox lag and dead-letter rows.
