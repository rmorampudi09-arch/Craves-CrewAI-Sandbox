# Craves Refund End-to-End Rollout

## Purpose

This module packages the remaining chef-timeout and customer-refund work into one build and deployment unit while keeping runtime activation incremental and reversible.

The implementation follows the approved Craves architecture baseline:

- Order state changes and critical events use transactional outboxes.
- Azure Service Bus delivery is at least once.
- Consumers and provider calls are idempotent.
- A multi-chef checkout refunds only the rejected chef-specific order amount.
- Cashfree credentials remain outside source control.
- Production payment execution is not enabled by any pipeline in this module.

## What is included

### Customer refund-status notifications

`RefundStatusUpdateService` now writes an in-app notification outbox row in the same database transaction when an Order status actually changes to:

```text
REFUND_PENDING
REFUNDED
REFUND_FAILED
```

The notification event key contains the immutable refund-status event ID, so duplicate Service Bus delivery cannot create duplicate notifications.

Notification delivery remains separately controlled by:

```text
CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED
```

### Cashfree sandbox controls

The existing Cashfree refund adapter remains provider-neutral to the rest of the workflow. This module adds an optional sandbox-only simulation setting:

```text
CRAVES_CASHFREE_SANDBOX_REFUND_SIMULATION_STATUS
```

Allowed values:

```text
PENDING
SUCCESS
FAILED
```

The setting is used only when:

```text
PAYMENT_PROVIDER_ENVIRONMENT=sandbox
```

Production continues to use the normal Craves refund note. The adapter also accepts either an object or a single-element array response while requiring a valid `refund_status`.

## Build together

Run:

```text
/azure-pipelines-refund-end-to-end-ci.yml
```

This performs Java 21 `mvn clean verify` for:

```text
Order Service
Integration Service
Notification Service
```

It also validates every event schema and confirms all execution switches remain disabled by default.

## Deploy together

After merge, run:

```text
/azure-pipelines-refund-combined-deploy.yml
```

It builds and deploys all three services with one immutable image tag. Deployment explicitly returns runtime to:

```text
Order
  CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
  CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
  CRAVES_NOTIFICATION_OUTBOX_DISPATCHER_ENABLED=false
  CRAVES_REFUND_STATUS_CONSUMER_ENABLED=true

Integration
  CRAVES_REFUND_CONSUMER_ENABLED=true
  CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=true
  CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
  CRAVES_REFUND_RECONCILIATION_ENABLED=false
  CRAVES_DELIVERY_COMMAND_ENABLED=false
  BORZO_API_ENABLED=false
```

No refund request is published and no Cashfree API is called merely because deployment succeeds.

## Activate safely in stages

Register one pipeline:

```text
/azure-pipelines-refund-stage-activation.yml
```

Run it repeatedly, selecting exactly one next stage.

### Stage 1 — Refund request publication

```text
targetStage=stage1_request_publication
```

Enables:

```text
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER,REFUND_REQUESTED
```

Keeps timeout, Cashfree, reconciliation and notification delivery disabled.

Before activation, the pipeline compares the current Order refund-request outbox count with:

```text
expectedRefundRequestOutboxCount
```

### Stage 2 — Chef timeout worker

```text
targetStage=stage2_timeout_worker
```

Enables:

```text
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=true
```

Refund intents can now be created, but provider execution remains disabled.

### Stage 3 — Cashfree sandbox execution

```text
targetStage=stage3_cashfree_sandbox_execution
confirmFinancialSandboxExecution=true
```

Required Azure DevOps secret variables:

```text
CASHFREE_SANDBOX_CLIENT_ID
CASHFREE_SANDBOX_CLIENT_SECRET
```

The pipeline verifies the executable refund count equals:

```text
expectedExecutableRefundCount
```

It then stores the values as Container App secrets and enables only sandbox execution. Reconciliation remains disabled.

### Stage 4 — Reconciliation

```text
targetStage=stage4_reconciliation
```

Requires provider execution to be enabled in the sandbox environment and credential environment variables to be secret references.

### Stage 5 — Notification dispatch

```text
targetStage=stage5_customer_notifications
```

The pipeline verifies:

- Order and Notification Service internal keys exist and match;
- the Notification Container App has an ingress FQDN;
- the Order notification backlog exactly matches `expectedNotificationBacklogCount`.

It then enables the existing Order notification outbox dispatcher.

## Status report

Run:

```text
/azure-pipelines-refund-rollout-status.yml
```

The report displays only non-secret state:

- inferred current stage;
- Order and Integration runtime flags;
- payment environment and API version;
- Service Bus DLQ counts;
- Order event and notification outbox backlogs;
- Integration refund and refund-status outbox backlogs.

The report fails when payment execution is outside sandbox, refund DLQs are non-empty, or unrelated delivery/Borzo execution changed.

## Rollback

Run:

```text
/azure-pipelines-refund-stage-rollback.yml
```

Choose a safer target stage. The rollback stops notification delivery first, provider execution second, and timeout/event generation last. It never deletes outbox, inbox, payment or refund records.

The safest target is:

```text
safe_publisher_only
```

## Manual steps required

### Azure DevOps

Register these YAML pipelines once:

```text
azure-pipelines-refund-end-to-end-ci.yml
azure-pipelines-refund-combined-deploy.yml
azure-pipelines-refund-stage-activation.yml
azure-pipelines-refund-rollout-status.yml
azure-pipelines-refund-stage-rollback.yml
```

All use the existing variable:

```text
AZURE_SERVICE_CONNECTION
```

### Cashfree

Create or retrieve sandbox credentials in the Cashfree merchant dashboard. Store them only as secret Azure DevOps variables using the exact names documented above. Do not paste them into chat, source control, logs or ordinary pipeline parameters.

### Notification Service

The existing internal service key must be configured on both Order and Notification Container Apps. Stage 5 compares the values without printing them.

## Local tests

```bash
cd services/order-service
mvn -B clean verify

cd ../integration-service
mvn -B clean verify

cd ../notification-service
mvn -B clean verify
```

The combined Azure DevOps CI pipeline is the authoritative build gate.
