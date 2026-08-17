# Craves Customer Web Next.js Delivery Tracking Handover

**Date:** 2026-07-29  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Branch:** `feature/customer-web-delivery-tracking-nextjs`  
**Parent branch:** `feature/delivery-status-apim-route`  
**Module:** `apps/customer-web-next`  
**Status:** Code complete; CI, parent merges and deployment pending  
**Confidentiality:** Internal Craves engineering and operations

## 1. Executive summary

This module creates the clean Next.js migration path for the Craves customer website and implements authenticated delivery tracking for one chef-specific order.

## 2. Architecture correction

The existing `apps/customer-web` package is a temporary Vite shell. The approved stack requires Next.js, TypeScript and Tailwind CSS, so the new module does not extend the legacy app.

## 3. New application path

```text
apps/customer-web-next
```

## 4. Parent dependencies

Merge order is PR #25 downstream consumer, PR #26 APIM route, then this customer-web PR.

## 5. Browser route

```text
/orders/{orderId}/tracking
```

## 6. BFF route

```text
/api/orders/{orderId}/delivery-status
```

## 7. Upstream route

```text
GET /api/v1/orders/{orderId}/delivery-status
```

## 8. End-to-end flow

```text
browser -> Next.js BFF -> secure cookie -> APIM -> Order Service -> delivery projection -> sanitised UI response
```

## 9. Authentication boundary

The browser does not read the access token. The Next.js server reads the future `craves_access_token` HTTP-only cookie.

## 10. Cookie requirements

`HttpOnly=true`, `Secure=true`, `SameSite=Lax`, path `/`, and a lifetime no longer than the Craves access token.

## 11. Token-storage prohibition

The module contains no access-token `localStorage` or `sessionStorage` use. CI scans for these patterns.

## 12. Authentication dependency

The phone-OTP/password Next.js authentication migration remains separate. `/sign-in` contains no manual token field.

## 13. Framework

Next.js `16.2.11`, React/React DOM `19.2.8`, TypeScript and Tailwind CSS `4.3.3`.

## 14. App Router

The module uses Next.js App Router dynamic pages and route handlers.

## 15. Standalone output

`output: standalone` creates a production-suitable Node server image.

## 16. Container runtime

Node 24 Alpine, port 3000, non-root `nextjs` user.

## 17. API configuration

```text
CRAVES_API_BASE_URL=https://api.craves.in/api/v1
```

## 18. UUID validation

Both page and BFF reject malformed order identifiers.

## 19. Upstream timeout

The BFF aborts APIM calls after ten seconds.

## 20. Error mapping

401, 403, 404, timeout and unexpected upstream failures become controlled customer responses.

## 21. Response allow-list

The BFF parses only `orderId`, `deliveryJobId`, `providerId`, normalized status, HTTPS tracking URL, timestamps and bounded history.

## 22. Privacy exclusions

Raw webhook payloads, provider transaction IDs, credentials, retry metadata and internal inbox/outbox IDs are discarded.

## 23. Tracking URL safety

Only HTTPS URLs are exposed. HTTP, malformed and JavaScript URLs are removed.

## 24. No-cache policy

BFF responses contain `Cache-Control: no-store, no-cache, must-revalidate` and `Pragma: no-cache`.

## 25. Search indexing

Tracking pages use `noindex, nofollow`.

## 26. Customer status presentation

Raw provider status is mapped to provider-neutral Craves copy.

## 27. Progress model

Nine stages cover preparation, partner search, assignment, pickup, transit, arrival and completion.

## 28. Attention states

Delayed, cancelled, returning, returned and failed states use warning-oriented presentation.

## 29. Terminal states

Delivered, cancelled, returned and failed stop automatic polling.

## 30. Automatic refresh

Active pages refresh every 30 seconds only while the browser tab is visible.

## 31. Manual refresh

Customers retain an explicit refresh button in all ready states.

## 32. Pre-delivery state

Owned orders without a delivery job show a waiting state with nullable delivery fields and empty history.

## 33. History boundary

At most 100 chronological delivery history entries are accepted.

## 34. Locale

Timestamps use `en-IN` and `Asia/Kolkata`.

## 35. Loading state

The page renders an accessible skeleton.

## 36. Error states

Authentication expiry, access denial, absence, timeout, offline and temporary upstream failure have separate messages.

## 37. Accessibility

Status areas use `aria-live`; invalid form input uses `role=alert`; external links use `noopener noreferrer`.

## 38. Brand design

Approved navy, cream, gold and purple palette; rounded cards; restrained shadows; responsive layout.

## 39. Tests

Five unit tests cover sanitisation, nullable state, invalid IDs/statuses, HTTPS links and terminal behavior.

## 40. Local evidence

All five pure delivery-domain tests passed locally. Full dependency installation and Next build are deferred to Azure CI because npm registry access timed out in this session.

## 41. Lockfile limitation

No `package-lock.json` is committed yet. The first successful dependency install must generate and review it before production replacement.

## 42. Required lockfile amendment

After CI resolves dependencies, commit the lockfile, switch Docker/CI from `npm install` to `npm ci`, and rerun CI.

## 43. Build-only CI

```text
azure-pipelines-customer-web-next-delivery-tracking-ci.yml
```

Runs typecheck, tests, Next build, Docker build and security scans.

## 44. Deployment pipeline

```text
azure-pipelines-customer-web-next-delivery-tracking.yml
```

Default confirmation is false.

## 45. Replacement risk

Deployment targets existing `ca-craves-web-prodlow`, replacing the legacy Vite image only after explicit approval.

## 46. Current-image guard

Only existing `craves/customer-web` or `craves/customer-web-next` ACR images are accepted as replacement targets.

## 47. Rollback evidence

The deployment prints the exact `ROLLBACK_IMAGE`; record it before the update.

## 48. Readiness verification

The pipeline requires a new latest revision, latest equals ready, `Running`, exact image match, FQDN and HTTP 200 home response.

## 49. Azure health-state decision

The pipeline does not rely on Container Apps `healthState` because earlier Azure validation returned `None` for running revisions.

## 50. Status pipeline

```text
azure-pipelines-customer-web-next-delivery-tracking-status.yml
```

Read-only image, revision, FQDN, API base and HTTP report.

## 51. Rollback pipeline

```text
azure-pipelines-customer-web-next-delivery-tracking-rollback.yml
```

Requires `confirmRollback=true` and an exact prior ACR image.

## 52. No resource provisioning

No Container App, ACR, APIM, database, Service Bus, Key Vault, DNS or certificate resource is created.

## 53. Billing

No new paid resource is introduced. Later deployment reuses existing resources.

## 54. Delivery safety

No delivery creation, reconciliation, webhook, tracking, status publisher or Borzo flag is changed.

## 55. Pipeline sequence

```text
1 PR #25 CI/merge/deploy
2 Order consumer validation
3 Integration publisher validation
4 PR #26 APIM CI/merge/rollout
5 customer-web CI
6 customer-web merge
7 record legacy image
8 guarded Next.js deployment
9 read-only status
```

## 56. Authentication rollout

Do not make the new site the final public customer experience until the secure-cookie authentication migration is complete.

## 57. Legacy app preservation

`apps/customer-web` and its old deployment pipeline remain unchanged for rollback continuity.

## 58. Deployment exclusivity

Do not run legacy and Next.js customer-web deployment pipelines together because both target the same Container App.

## 59. Scale note

Thirty-second visible-page polling is appropriate for 50–100 concurrent users. Million-user scale requires SSE/WebSocket delivery updates with polling fallback.

## 60. Product-policy boundary

The UI does not calculate ETA, delivery price, serviceability, compensation, cancellation consequence, refund consequence, GST or compliance rules.

## 61. Provider neutrality

Customer copy does not commit to Borzo or expose provider transaction details.

## 62. Manual Azure DevOps actions

Register the CI, deployment, status and rollback YAML files after merge.

## 63. Secrets

No new secret is required. Access tokens must never be stored in pipeline variables for customer runtime use.

## 64. DNS

No DNS change is required for module validation. Custom-domain work remains deferred.

## 65. Firebase

No Firebase Console change occurs in this module. The next auth module reuses the approved phone-auth flow.

## 66. Cashfree

No Cashfree configuration or payment execution is changed.

## 67. Provider configuration

No delivery-provider callback registration or credential is changed.

## 68. Local run

```bash
cd apps/customer-web-next
npm install
npm run verify
npm run dev
```

## 69. Production start

The standalone runtime starts `server.js` on `0.0.0.0:3000`.

## 70. Logging rule

Never log cookies, Authorization headers, upstream bodies or raw provider tracking payloads.

## 71. Security scan

CI checks likely credential literals, browser token storage and the legacy `VITE_API_BASE_URL` pattern.

## 72. Real-order testing

Use only a chef-specific order owned by the authenticated test customer.

## 73. Unauthenticated result

The BFF returns HTTP 401 and the UI directs the customer to `/sign-in`.

## 74. Wrong-owner result

A different customer receives access denial without ownership disclosure.

## 75. Before first event

The page shows “Waiting for delivery updates” and bounded polling continues.

## 76. Terminal event

Automatic polling stops while manual refresh remains available.

## 77. External tracking

The link opens in a new tab only after HTTPS validation.

## 78. Response mismatch

The BFF verifies upstream `orderId` equals the requested path ID.

## 79. Next recommended module

Next.js authentication/session migration that creates, refreshes and clears the HTTP-only cookie.

## 80. Following module

Customer order history/details migration with Track Delivery navigation.

## 81. Later web modules

Browsing, addresses, cart, checkout, Cashfree, notifications, wishlist and profile.

## 82. Mobile module

React Native delivery tracking must reuse the APIM contract with platform-secure token storage.

## 83. Completion statement

Next.js tracking UI, BFF, tests, Docker packaging, CI, guarded deployment, status, rollback, README and handover are complete on the feature branch.

## 84. Pending acceptance gates

Parent CI/merges, Next build CI, lockfile generation, Order/APIM runtime validation, authentication-cookie integration and explicit legacy-image replacement approval remain pending.
