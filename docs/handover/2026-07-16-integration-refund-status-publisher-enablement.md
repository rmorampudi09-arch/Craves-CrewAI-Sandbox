# Craves Technical Handover — Integration Refund Status Publisher Enablement

**Date:** 16 July 2026  
**Service:** Integration Service  
**Branch:** `feature/integration-refund-status-publisher-enable`  
**Status:** Build validation pending

## 1. Objective

Activate the already implemented `REFUND_STATUS_CHANGED` transactional outbox publisher only after the Order Service consumer has been deployed, enabled, and validated.

This closes the message path:

```text
Integration refund provider result
        ↓
Transactional refund_status_outbox row
        ↓
Integration Service scheduled publisher
        ↓
Azure Service Bus craves-domain-events
        ↓
Order filtered subscription
        ↓
Idempotent Order refund-status consumer
        ↓
Affected chef-specific order refund status
```

## 2. Approved architecture alignment

The implementation follows the approved Craves architecture baseline:

- critical state and event creation use a transactional outbox;
- Service Bus delivery is at least once;
- consumers must be idempotent;
- managed identity is preferred for Azure service authentication;
- failed publication remains durable and retryable;
- payment-provider timeouts must not be interpreted as success or failure without verification;
- refund events preserve correlation, causation, subject, provider reference, amount, reason, and normalized status.

The approved refund mapping remains:

```text
PENDING or ONHOLD   -> REFUND_PENDING
SUCCESS             -> REFUNDED
FAILED or CANCELLED -> REFUND_FAILED
```

## 3. Existing implementation reused

The previous Integration refund workflow already contains:

```text
RefundStatusEventFactory
RefundStatusOutboxPublisher
RefundRepository.claimStatusOutbox(...)
RefundRepository.markStatusPublished(...)
RefundRepository.markStatusPublishFailed(...)
payment_schema.refund_status_outbox
contracts/events/refund-status-changed-v1.schema.json
```

No new payment calculation, provider call, status mapping, database table, or event contract is introduced here.

## 4. Gap found during activation review

`RefundStatusOutboxPublisher` uses a Spring `@Scheduled` polling method. The application did not contain scheduling activation for this publisher.

Without scheduling activation, the publisher bean could be created while its polling method never runs.

The correction is deliberately scoped:

```text
services/integration-service/src/main/java/in/craves/integration/refund/RefundStatusPublisherSchedulingConfiguration.java
```

This configuration contains:

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "craves.refund",
    name = "status-publisher-enabled",
    havingValue = "true"
)
```

The main Integration application is not globally annotated with `@EnableScheduling`. Scheduling starts only in a revision where the refund status publisher flag is true.

A regression test verifies:

- scheduling is present on the conditional configuration;
- the condition is exactly `craves.refund.status-publisher-enabled=true`;
- the main application does not globally enable scheduling.

## 5. Files changed

Added:

```text
services/integration-service/src/main/java/in/craves/integration/refund/RefundStatusPublisherSchedulingConfiguration.java
services/integration-service/src/test/java/in/craves/integration/IntegrationServiceSchedulingTest.java
azure-pipelines-integration-refund-status-publisher-enable.yml
services/integration-service/modules/refund-status-publisher/README.md
docs/handover/2026-07-16-integration-refund-status-publisher-enablement.md
```

No existing Flyway migration is modified.

## 6. Controlled activation pipeline

Pipeline:

```text
/azure-pipelines-integration-refund-status-publisher-enable.yml
```

Default Azure targets:

```text
Resource group: rg-craves-prodlow-centralindia
Integration app: ca-craves-integration-service-pr
Order app: ca-craves-order-service-prodlow
Service Bus namespace: sb-craves-prodlow-l3ing6
Topic: craves-domain-events
Order subscription: order-service-refund-status-changed
Order rule: refund-status-changed-only
```

The pipeline does not create billable Azure resources and does not create role assignments.

## 7. Pipeline prerequisites

Before activation, the pipeline verifies:

1. The namespace and topic exist.
2. The Order refund-status subscription exists.
3. The subscription rule is a SQL filter for `REFUND_STATUS_CHANGED`.
4. The subscription DLQ is empty.
5. Order refund-status consumption is enabled.
6. Order chef timeout execution is disabled.
7. Order publishes only `CHEF_ACCEPTED_ORDER`.
8. Integration refund request consumption is enabled.
9. Cashfree execution is disabled.
10. Cashfree reconciliation is disabled.
11. Delivery command execution is disabled.
12. Borzo execution is disabled.
13. Integration managed identity has `Azure Service Bus Data Sender` at namespace or topic scope.
14. The Integration revision is healthy after activation.

If Sender RBAC is missing, the pipeline exits with code 2 and prints the Integration principal ID and required scopes. An authorized Azure user must grant the role once and rerun the pipeline.

## 8. Runtime settings after activation

Expected Integration settings:

```text
CRAVES_REFUND_CONSUMER_ENABLED=true
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true
CRAVES_DELIVERY_COMMAND_ENABLED=false
BORZO_API_ENABLED=false
```

Expected Order settings:

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=true
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
```

## 9. Deployment sequence

1. Run Integration Service CI on the feature branch.
2. Merge only after CI succeeds.
3. Run normal Integration Service deployment from `main` so the conditional scheduling configuration is deployed.
4. Confirm the new Integration revision is healthy and the publisher remains disabled.
5. Register and run the controlled publisher enablement pipeline from `main`.
6. Grant Sender RBAC once if the pipeline reports it missing.
7. Run one synthetic outbox-to-Order end-to-end test.
8. Confirm outbox row becomes `PUBLISHED`, Order inbox becomes `PROCESSED`, affected synthetic order changes to `REFUND_PENDING`, DLQ remains empty, and synthetic rows are removed.

## 10. Validation design

The controlled end-to-end validation will insert:

- one isolated synthetic `CHEF_REJECTED` Order row with immutable refund request metadata;
- one isolated Integration refund row representing a provider-derived `PENDING` result;
- one matching `REFUND_STATUS_CHANGED` outbox row.

The real scheduled publisher must claim and publish that outbox row. The real Order consumer must process it.

The validation must prove:

```text
Integration outbox status = PUBLISHED
Order inbox status = PROCESSED
Order status = REFUND_PENDING
Order refund/provider fields match the event
Order status history written once
Order refund-status DLQ = 0
```

It must then remove all synthetic database rows and any temporary diagnostic Service Bus access.

## 11. Safety boundaries

This module does not authorize or enable:

- Cashfree create-refund API calls;
- Cashfree get-refund reconciliation;
- Order `REFUND_REQUESTED` publication;
- chef acceptance timeout processing;
- customer-facing refund notifications;
- delivery creation;
- Borzo provider calls.

No pricing, commission, refund deduction, tax, GST, FSSAI, or delivery-radius rule is changed.

## 12. Manual steps required

### Azure DevOps

Register the new YAML pipeline once and configure:

```text
AZURE_SERVICE_CONNECTION=Craves-Dev-Service-Connection
```

### Azure RBAC

Only if the pipeline reports Sender role missing, grant:

```text
Role: Azure Service Bus Data Sender
Assignee: Integration Container App system-assigned managed identity
Scope: sb-craves-prodlow-l3ing6 namespace or craves-domain-events topic
```

### Secrets

No secret value should be pasted into chat or committed to GitHub.

## 13. Rollback

Disable only status publication:

```bash
az containerapp update \
  --resource-group rg-craves-prodlow-centralindia \
  --name ca-craves-integration-service-pr \
  --set-env-vars CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=false
```

Pending or failed outbox rows remain durable. Do not delete them during rollback.

## 14. Risks and controls

### Duplicate delivery

A broker send can succeed while the database acknowledgement fails. The same event may then be sent again. This is expected at-least-once behavior. The Order inbox primary key on event ID prevents duplicate state transitions.

### Outbox backlog

If Service Bus or managed identity authentication fails, outbox rows remain retryable. Operations must monitor pending age, failed attempts, and local dead-letter rows before provider execution is enabled.

### Scheduling scope

The scheduling infrastructure is conditionally imported only when refund status publication is enabled. Once active, Spring can invoke other `@Scheduled` methods, but refund provider execution still immediately returns while provider execution and reconciliation flags are false. Delivery execution remains protected by its disabled runtime flag.

### Scale

The current polling batch size and five-second interval are suitable for the present low-concurrency environment. Before high-volume production, measure outbox lag, database lock duration, Service Bus throughput, and Container App replica behavior rather than increasing concurrency blindly.

## 15. Pending next approvals

After publisher validation, the next end-to-end steps remain separately controlled:

1. Expand the Order domain-event allow-list to include `REFUND_REQUESTED`.
2. Enable the Order chef acceptance timeout worker.
3. Validate Order-to-Integration refund intent creation with Cashfree still disabled.
4. Configure Cashfree sandbox credentials through Key Vault.
5. Enable sandbox provider execution.
6. Enable reconciliation only after create-refund behavior and provider status handling are verified.
7. Add customer refund-status notifications and operations dashboards.
