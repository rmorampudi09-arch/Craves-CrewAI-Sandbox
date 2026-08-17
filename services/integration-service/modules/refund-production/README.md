# Refund production readiness

This module adds the final code-side safety layer for production Cashfree refunds. It reuses the existing durable `REFUND_REQUESTED` consumer, refund ledger, Cashfree adapter, reconciliation and `REFUND_STATUS_CHANGED` outbox.

## Separate production approvals

```text
CRAVES_REFUND_PRODUCTION_RECONCILIATION_APPROVED=false
CRAVES_REFUND_PRODUCTION_PROVIDER_EXECUTION_APPROVED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
```

In production, runtime worker flags cannot execute without their matching approval. Startup fails if a production flag is enabled without approval.

## Internal readiness API

```text
GET /internal/v1/refund-production-readiness
X-Craves-Internal-Secret: <shared internal secret>
```

It reports executable, reconcilable, processing and dead-letter refund counts; status-outbox backlog; request-inbox failures; downstream state and blocker codes. It does not return customer, payment, provider or refund identifiers.

## Activation order

`azure-pipelines-refund-production-activation.yml` supports:

1. `downstream`: consumer and status publisher only.
2. `reconciliation`: production read-only provider reconciliation.
3. `provider_execution`: production refund creation only after reconciliation is active and exact backlog counts are approved.

Cashfree production payment configuration and one validated paid transaction must exist before this pipeline is run.

## Rollback

`azure-pipelines-refund-production-rollback.yml` disables production refund execution and reconciliation approvals/flags. It intentionally leaves refund rows, inbox/outbox evidence, payment records and Order history untouched.

## Manual work later

- complete the controlled low-value production payment first;
- choose an exact paid chef-specific order that is eligible under approved business policy;
- run downstream and reconciliation with zero dead letters;
- run one production refund with exact expected counts;
- reconcile Cashfree dashboard, refund ledger, Order state and notifications;
- document finance ownership and manual escalation.

This module does not define refund eligibility, deductions, compensation, commission impact or customer SLA.
