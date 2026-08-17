# Customer Web Subscription Plans — Engineering Handover

Date: 2026-07-30

## Scope
Adds public active-plan discovery to the Next.js customer web.

## Branch
`feature/customer-web-subscription-plans`.

## Dependency
Requires PR #58 Subscription Service ownership/privacy hardening.

## Customer route
`/subscriptions/plans`.

## Browser BFF
`GET /api/subscriptions/plans`.

## Upstream
`GET /api/v1/subscriptions/plans`.

## Main files

```text
apps/customer-web-next/src/lib/subscription-contract.ts
apps/customer-web-next/src/app/api/subscriptions/plans/route.ts
apps/customer-web-next/src/components/subscription-plan-browser.tsx
apps/customer-web-next/src/app/subscriptions/plans/page.tsx
```

## Response fields
Plan ID, plan code, name, description, weekly/monthly period, amount and currency.

## Excluded fields
Chef identity, internal status timestamps and backend ownership data.

## Pricing boundary
The page formats only the backend amount and currency. It does not calculate a subscription price.

## Product boundaries
Renewal, unused meals, holidays, refunds, payout and cancellation cutoffs remain undefined.

## Availability
Only ACTIVE plans are returned by the hardened backend.

## Authentication
Browsing is public. Enrollment remains authenticated in the next module.

## Storage
No localStorage, sessionStorage or cookies are written.

## Caching
BFF and upstream requests use no-store.

## Timeout
Upstream calls are bounded to ten seconds.

## HTTPS
`CRAVES_API_BASE_URL` must use HTTPS.

## UI states
Loading, empty, active plan cards and upstream error.

## Navigation
Plan cards link to `/subscriptions/new?planId=<uuid>` for the next module.

## Tests
Contract tests reject unsupported periods and strip identity fields.

## CI
`azure-pipelines-customer-web-subscription-plans-ci.yml`.

## APIM
No gateway write is executed by this branch. Subscription APIM configuration is delivered later in the batch.

## Azure
No resource is created or updated.

## Secrets
No secret or credential is introduced.

## Rollback
Remove the route/page and restore the previous customer-web image.

## Manual later
Run CI, merge in dependency order, configure APIM, deploy web and run browser smoke tests.

## Acceptance
Active plans render with backend currency; empty plans show controlled copy; malformed/private payloads fail closed.
