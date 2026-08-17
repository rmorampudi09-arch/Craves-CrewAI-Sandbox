# Admin Subscription Plan Management — Handover

Date: 2026-07-30

## Scope
Adds ADMIN plan creation, listing and DRAFT/ACTIVE/INACTIVE status control.

## Branch
`feature/admin-subscription-plans`.

## Dependencies
Subscription ownership/audit hardening, admin shell and approved chef review data.

## Route
`/admin/subscription-plans`.

## APIs

```text
GET|POST /api/v1/admin/subscription-plans
PATCH /api/v1/admin/subscription-plans/{planId}/status
GET /api/v1/backoffice/chef-reviews?status=APPROVED
```

## Approved chef selector
A dedicated ADMIN-only BFF maps approved applications to identity ID, application ID, display name and email. Pending/rejected applicants are rejected by the parser.

## Create payload
Plan code, optional chef identity, name, description, WEEKLY/MONTHLY period, non-negative amount and three-letter currency.

## Pricing boundary
All values are explicit operator inputs. The UI contains no formula, recommendation, discount, commission, tax or delivery-fee logic.

## Status boundary
Only DRAFT, ACTIVE and INACTIVE are exposed.

## Backend authority
Subscription Service validates roles, owner, values and writes `subscription_plan_audit`.

## Origin protection
Plan creation and status changes require same-origin requests.

## Privacy
Approved chef identity IDs are available only to the ADMIN page and are never stored in browser storage.

## Main files

```text
src/lib/admin-subscription-plan-contract.ts
src/app/api/admin/subscription-plans/**
src/components/admin-subscription-plan-manager.tsx
src/app/admin/subscription-plans/page.tsx
```

## Tests
Plan states/amounts and approved-chef filtering are contract-tested.

## CI
`azure-pipelines-admin-subscription-plans-ci.yml`.

## APIM
Operations are configured in the consolidated APIM module later in this stack.

## Manual later
Run CI, deploy Subscription Service hardening, configure APIM, create a synthetic draft, change status, and inspect the audit row.

## Rollback
Restore the previous web image and service image. Retain plan/audit records.

## Acceptance
Non-admin is denied; only approved chefs appear; plans are created as backend DRAFT; allowed status changes audit successfully; no pricing calculations exist in frontend code.
