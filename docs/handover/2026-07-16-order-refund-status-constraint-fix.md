# Craves Technical Handover — Order Refund Status Constraint Fix

**Date:** 16 July 2026  
**Service:** Order Service  
**Branch:** `fix/order-refund-status-constraint`

## Incident

The controlled `REFUND_STATUS_CHANGED` validation successfully proved invalid-event DLQ handling, but a valid `REFUND_PENDING` event was dead-lettered after five deliveries.

PostgreSQL rejected the Order Service update with:

```text
constraint: chk_customer_order_refund_request
attempted status: REFUND_PENDING
previous status: CHEF_REJECTED
```

The original Flyway V6 constraint required `status = 'CHEF_REJECTED'` whenever `refund_requested_at` was populated. That protected the initial refund request, but unintentionally prevented the same row from progressing into the provider-derived refund lifecycle.

## Root cause

The Java consumer was correct to transition the affected chef-specific order from `CHEF_REJECTED` to `REFUND_PENDING`. The database constraint was narrower than the approved lifecycle.

## Fix

A new immutable Flyway migration was added:

```text
services/order-service/src/main/resources/db/migration/V8__expand_refund_request_status_constraint.sql
```

V8 drops and recreates `chk_customer_order_refund_request` so that a populated refund request is valid while the order is in:

```text
CHEF_REJECTED
REFUND_PENDING
REFUNDED
REFUND_FAILED
```

The original safeguards remain mandatory:

```text
chef_rejection_code IS NOT NULL
refund_requested_amount IS NOT NULL
refund_requested_amount > 0
```

V6 and V7 were not edited because both migrations have already been applied.

## Regression test

Added:

```text
services/order-service/src/test/java/in/craves/order/refund/RefundRequestConstraintMigrationTest.java
```

The test verifies that V8 contains all four lifecycle statuses and retains the rejection and positive-amount protections.

## Runtime safety

This fix changes no runtime switches and performs no external payment action.

Keep:

```text
CRAVES_REFUND_STATUS_CONSUMER_ENABLED=true
CRAVES_CHEF_ACCEPTANCE_WORKER_ENABLED=false
CRAVES_DOMAIN_EVENT_ENABLED_TYPES=CHEF_ACCEPTED_ORDER
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=false
```

## Validation sequence

1. Run Order Service CI on `fix/order-refund-status-constraint`.
2. Merge after CI succeeds.
3. Run the normal Order Service deployment from `main` so Flyway V8 applies.
4. Confirm the Order revision is healthy and the refund-status consumer remains enabled.
5. Remove the preserved failed validation message from the refund-status DLQ.
6. Rerun the controlled Order refund-status consumer test.
7. Do not enable Integration status publication, Cashfree execution, reconciliation, `REFUND_REQUESTED` publication, or the chef timeout worker yet.
