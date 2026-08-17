# Craves Refund E2E Validator Evidence-Lookup Fix

Date: 2026-07-19

## Issue

The Cashfree sandbox refund flow reached the expected final state, but the Azure DevOps validator timed out while reporting the Integration refund-status outbox as missing.

Observed successful durable evidence:

- Order status: `REFUND_PENDING`
- Integration refund status: `PENDING`
- Cashfree provider status: `PENDING`
- `REFUND_REQUESTED` outbox: `1 / PUBLISHED`
- `REFUND_STATUS_CHANGED` outbox: `1 / PUBLISHED`
- Order refund-status inbox: `1 / PROCESSED`
- Customer notification: `1 / SENT`
- Both refund Service Bus DLQs: `0`

## Root cause

The validator queried `payment_schema.refund_status_outbox.aggregate_id` with the Integration refund UUID.

The Integration implementation stores the event subject (the chef sub-order UUID) in `aggregate_id`. The refund UUID remains inside `payload.data.refundId`.

## Correction

The guarded runner patches the validator workspace copy so the outbox evidence query uses:

- `aggregate_id = SYNTHETIC_CHEF_SUB_ORDER_ID`
- `payload.data.refundId = integration_refund_id`
- `payload.data.status = EXPECTED_FINAL_ORDER_STATUS`

The source validator is never edited during a pipeline run. The runner refuses execution unless it finds exactly one known defective query block, preventing silent or broad replacements after future refactors.

## Files

- `scripts/refund/run-refund-e2e-sandbox-validation.sh`
- `azure-pipelines-refund-e2e-sandbox-validation.yml`

## Safety

This change does not alter refund business logic, Cashfree calls, database records, Service Bus messages, acceptance expiry behavior, or production configuration. It only corrects pipeline evidence correlation.
