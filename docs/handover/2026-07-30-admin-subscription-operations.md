# Admin Subscription Status Operations — Handover

Date: 2026-07-30

## Scope
Adds exact-ID subscription lookup and controlled ADMIN status intervention.

## Branch
`feature/admin-subscription-operations`.

## Dependency
Subscription ownership/privacy hardening, admin shell and admin plan management.

## Route
`/admin/subscriptions`.

## Lookup API
`GET /api/v1/subscriptions/{subscriptionId}`. The hardened service allows ADMIN while returning the privacy-reduced customer DTO.

## Status API
`PATCH /api/v1/admin/subscriptions/{subscriptionId}/status/{status}`.

## Search boundary
No admin list/search backend exists. The UI requires an exact UUID and does not invent a new data-access contract.

## Allowed statuses
PENDING_PAYMENT, ACTIVE, PAUSED, PAYMENT_FAILED, EXPIRED and CANCELLED.

## Reason
The admin BFF requires a non-empty operational reason up to 1000 characters for every change.

## Confirmation
The browser displays old/new status and requires explicit confirmation.

## Backend authority
Subscription Service verifies ADMIN role, validates the status and writes subscription status history.

## Privacy
Customer identity and chef identity UUIDs are removed by the existing customer subscription parser.

## Origin protection
Status changes require same-origin requests.

## Unsupported operations
Refunds, credits, payouts, renewals, meal generation and delivery scheduling are not exposed.

## Main files

```text
src/lib/admin-subscription-operation-contract.ts
src/app/api/admin/subscriptions/**
src/components/admin-subscription-operator.tsx
src/app/admin/subscriptions/page.tsx
```

## Tests
Contract tests require both supported status and reason.

## CI
`azure-pipelines-admin-subscription-operations-ci.yml`.

## APIM
Operations are configured in the consolidated APIM module.

## Manual later
Run CI, deploy Subscription Service, configure APIM, create a synthetic subscription, verify lookup and history-recording status change.

## Rollback
Restore the previous web image. Do not delete subscription history.

## Acceptance
Non-admin is denied; invalid IDs fail before upstream; every mutation has a reason; unsupported finance/product actions are absent.
