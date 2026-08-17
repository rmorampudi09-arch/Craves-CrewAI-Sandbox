# CRAVES Refund End-to-End Cashfree Sandbox Validation

**Date:** 18 July 2026  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Branch:** `feature/refund-e2e-sandbox-validation`  
**Classification:** Internal Confidential  
**Scope:** Order Service → Azure Service Bus → Integration Service → Cashfree sandbox → Order Service → Notification Service

## 1. Purpose

This package adds one guarded Azure DevOps pipeline that validates the already activated Stage 5 refund workflow using one explicitly approved synthetic Cashfree-sandbox order.

The validator does **not** create a checkout, fabricate a paid order, modify a payment record, enable production Cashfree, enable delivery commands, or purge financial evidence. The synthetic checkout and payment must first be completed through the normal Craves/Cashfree sandbox flow.

## 2. Files added

```text
azure-pipelines-refund-e2e-sandbox-validation.yml
scripts/refund/validate-refund-e2e-sandbox.sh
docs/handover/2026-07-18-refund-e2e-sandbox-validation.md
```

The existing combined refund CI pipeline already runs `bash -n` against every script under `scripts/refund`, so the new script is covered automatically by the existing shell-syntax gate.

## 3. Safety model

The pipeline is fail-closed.

Default parameters cannot execute a test:

```text
syntheticChefSubOrderId = 00000000-0000-0000-0000-000000000000
confirmSyntheticSandboxRefund = false
forceAcceptanceExpiry = false
```

The run stops unless:

- the operator supplies a valid non-placeholder chef sub-order UUID;
- `confirmSyntheticSandboxRefund=true` is explicitly selected;
- the refund rollout is currently at Stage 5;
- the payment environment is exactly `sandbox`;
- Cashfree credentials are configured through Container App secret references;
- delivery-command and Borzo switches remain disabled;
- both refund Service Bus DLQs are zero;
- the named order is `CHEF_ACCEPTANCE_PENDING`;
- the named order has no existing refund metadata;
- the related Integration payment is genuinely `PAID` and has a Cashfree order ID;
- the requested chef-specific refund fits within the captured and still-unreserved payment amount;
- no existing refund, refund request inbox, refund request outbox, refund-status inbox, or refund-status notification already exists for that chef sub-order.

## 4. Optional deadline acceleration

When `forceAcceptanceExpiry=true`, the script performs exactly one narrow mutation:

```text
order_schema.customer_order.chef_acceptance_expires_at = now() - 1 second
```

This update is allowed only for the supplied order while it is still `CHEF_ACCEPTANCE_PENDING` and contains no refund metadata.

The script does not alter payment state, captured amount, order amount, customer identity, refund amount, rejection status, refund status, or provider identifiers. The normal Order worker must perform the timeout decision and create the transactional refund request.

When `forceAcceptanceExpiry=false`, the order must already be naturally expired.

## 5. End-to-end assertions

The validator waits for and proves the following path:

```text
CHEF_ACCEPTANCE_PENDING
  → normal timeout worker
  → REFUND_REQUESTED Order transactional outbox = exactly 1 / PUBLISHED
  → Integration refund intent = exactly 1
  → Cashfree sandbox provider result stored
  → REFUND_STATUS_CHANGED Integration transactional outbox = exactly 1 / PUBLISHED
  → Order refund-status inbox = exactly 1 / PROCESSED
  → Order normalized status updated
  → customer refund notification outbox = exactly 1 / SENT
```

It also validates:

- deterministic refund reference `CRV{chefSubOrderUuidWithoutHyphens}`;
- Integration idempotency key exists;
- Order and Integration refund IDs match;
- Order and Integration refund references match;
- Order status event ID matches the published Integration outbox event ID;
- provider status matches the configured sandbox simulation;
- a Cashfree refund ID exists for non-failed provider results;
- the customer notification payload does not contain credentials, passwords, access keys, or private pickup address/contact fields;
- both Service Bus refund DLQs remain zero after the test;
- no local `DEAD` or `DEAD_LETTER` state is accepted.

## 6. Expected result mapping

The pipeline verifies that the selected expected result matches the currently configured Cashfree sandbox simulation:

```text
PENDING → REFUND_PENDING
SUCCESS → REFUNDED
FAILED  → REFUND_FAILED
```

The current rollout was activated with simulation status `PENDING`, so the normal first validation selection is:

```text
expectedFinalOrderStatus = REFUND_PENDING
```

## 7. Manual steps required

### 7.1 Merge and register the pipeline once

After the PR is merged, register this YAML once in Azure DevOps:

```text
/azure-pipelines-refund-e2e-sandbox-validation.yml
```

Use the existing non-secret pipeline variable:

```text
AZURE_SERVICE_CONNECTION = Craves-Dev-Service-Connection
```

Suggested pipeline name:

```text
Craves Refund E2E Sandbox Validation
```

No new Azure resource is created and no additional Azure DevOps secret is required.

### 7.2 Prepare one synthetic paid order

Use a dedicated test customer and test chef. Create the checkout through the normal Craves API/application flow and complete payment through Cashfree sandbox.

Do not update database payment rows manually.

The required input is the chef-specific order UUID, not the checkout UUID and not the Cashfree order ID.

The selected chef-specific order must still be:

```text
CHEF_ACCEPTANCE_PENDING
```

Do not accept or reject it through the chef application.

### 7.3 Run parameters

```text
branch = main
syntheticChefSubOrderId = <real paid sandbox chef sub-order UUID>
confirmSyntheticSandboxRefund = true
forceAcceptanceExpiry = true
expectedFinalOrderStatus = REFUND_PENDING
maxWaitSeconds = 900
pollIntervalSeconds = 10
```

Leave all Azure resource names at their defaults.

### 7.4 Expected final output

```text
CRAVES REFUND E2E SANDBOX VALIDATION PASSED
Order REFUND_REQUESTED outbox: exactly 1 / PUBLISHED
Integration refund: exactly 1 / idempotency key present
Integration REFUND_STATUS_CHANGED outbox: exactly 1 / PUBLISHED
Order refund-status inbox: exactly 1 / PROCESSED
Customer notification outbox: exactly 1 / SENT
Refund Service Bus DLQs: 0
```

## 8. Evidence retention and cleanup

The pipeline intentionally retains the synthetic order, payment, refund, inbox, outbox, provider metadata, status history, and notification records. These records are financial and operational audit evidence.

Do not delete them automatically. If the shared test environment later requires cleanup, use a separately reviewed cleanup procedure that identifies the exact synthetic checkout and preserves an exported audit record first.

## 9. Failure handling

If the validation fails before `forceAcceptanceExpiry`, no application record is modified.

If it fails after the deadline is accelerated, do not rerun blindly with another expected status. Run `Craves Refund Rollout Status`, inspect the named order/refund rows and both DLQs, and preserve all records for diagnosis.

The rollback pipeline may disable runtime stages when necessary, but rollback does not erase durable records.

## 10. Scale and production limits

This validates correctness for one isolated sandbox refund. It is not a performance test and does not prove suitability for one million concurrent users.

Before production Cashfree activation, Craves still requires:

- production merchant approval and credentials;
- production webhook validation and signature handling;
- a production activation design separate from the sandbox-only rollout;
- operational dashboards and alerts;
- refund-volume, retry-storm and provider-outage tests;
- support procedures for ambiguous provider timeouts and manual reconciliation;
- approved financial and customer-support policies.

No production Cashfree activation is added by this package.
