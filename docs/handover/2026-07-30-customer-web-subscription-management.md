# Customer Web Subscription Management — Handover

Date: 2026-07-30

## Scope
Authenticated subscription enrollment, list, detail, pause and cancel.

## Dependency
PR #58 backend hardening and PR #59 plan discovery.

## Routes

```text
/subscriptions
/subscriptions/new
/subscriptions/{subscriptionId}
```

## Upstream APIs

```text
POST /api/v1/subscriptions
GET /api/v1/subscriptions
GET /api/v1/subscriptions/{subscriptionId}
PATCH /api/v1/subscriptions/{subscriptionId}/pause
PATCH /api/v1/subscriptions/{subscriptionId}/cancel
```

## Security
All mutations require the HTTP-only session and same-origin validation. Customer identity and chef identity UUIDs are rejected by the DTO layer. No browser storage is used.

## Enrollment payload
Plan ID, non-past start date, customer-owned saved address ID and optional notes only.

## Lifecycle
Only pause and cancel are exposed because those are the current customer backend transitions.

## Product boundaries
No renewal, resume, unused-meal credit, refund, payout, holiday or cancellation-cutoff rule is introduced.

## Main files

```text
src/app/api/subscriptions/**
src/components/subscription-manager.tsx
src/components/subscription-enrollment-form.tsx
src/components/subscription-details.tsx
src/app/subscriptions/**
```

## CI
`azure-pipelines-customer-web-subscription-management-ci.yml`.

## APIM
Gateway routes are delivered in the consolidated subscription/backoffice APIM module later in this batch.

## Manual later
Run CI, deploy Subscription Service, configure APIM, create a customer address, enroll in an active sandbox plan, then verify pause/cancel and ownership denial.

## Rollback
Restore the previous web image. Do not delete subscription or audit rows.
