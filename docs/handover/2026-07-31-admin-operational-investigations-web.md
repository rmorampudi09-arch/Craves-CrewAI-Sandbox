# Craves admin operational investigations web handover

**Date:** 31 July 2026  
**Repository:** `rmorampudi09-arch/Craves-Build-platform`  
**Parent:** `feature/backend-redis-abuse-revocation` / PR #100  
**Branch:** `feature/admin-operational-investigations-web`  
**Runtime action performed:** None

## 1. Purpose
Adds a secure Next.js administrator workspace for privacy-reduced operational investigation evidence.

## 2. Route
The operator page is `/admin/operations`.

## 3. Browser API
The same-origin browser route is `POST /api/admin/operations/investigate`.

## 4. Backend ownership
Order Service owns order evidence; Integration Service owns payment, refund and delivery evidence.

## 5. Order contract
The module calls `GET /api/v1/admin/operations/orders/{orderId}`.

## 6. Payment contract
The module calls `GET /api/v1/admin/operations/payments/{paymentOrderId}`.

## 7. Refund contract
The module calls `GET /api/v1/admin/operations/refunds/{refundId}`.

## 8. Delivery contract
The module calls `GET /api/v1/admin/operations/delivery-commands/{commandId}`.

## 9. Exact identifier rule
Every lookup requires one exact UUID. No broad person, phone, email or transaction search was invented.

## 10. Mandatory reason
The operational reason must contain 10–500 characters.

## 11. Audit forwarding
The Next.js BFF forwards the reason as `X-Admin-Reason`.

## 12. Correlation
The owning backend returns `X-Correlation-ID`; the BFF validates and returns it.

## 13. Audit ownership
Only the owning Spring service creates authoritative audit evidence.

## 14. Authentication
The BFF uses the existing HTTP-only Craves access-session cookie.

## 15. Authorization
The browser does not infer ADMIN authority. Every backend controller requires the exact ADMIN role.

## 16. Same-origin protection
The investigation POST is rejected unless its Origin matches the current Next.js origin.

## 17. Request validation
Resource type, UUID and reason are validated before any upstream request.

## 18. Allowed resources
The only accepted resources are order, payment, refund and delivery-command.

## 19. No raw passthrough
The upstream JSON body is never returned directly to the browser.

## 20. Response allow-list
Each resource has a dedicated parser that creates a bounded operator summary and timeline.

## 21. Order privacy
Order output excludes full customer contact data, pickup details and unapproved backend fields.

## 22. Payment privacy
Payment output excludes credentials, signatures and raw webhook/provider payloads.

## 23. Refund privacy
Refund output includes bounded state evidence and sanitized error text only.

## 24. Delivery privacy
Delivery output includes provider-neutral command/job evidence and never exposes the command payload.

## 25. Bounded arrays
Upstream history arrays are capped at 100 entries in the browser contract.

## 26. Bounded text
Displayed upstream text is normalized and length bounded.

## 27. Date handling
Timeline timestamps are validated before display and rendered in Asia/Kolkata.

## 28. Currency handling
Amounts use backend-supplied amount and currency; the UI performs no fee arithmetic.

## 29. Read-only boundary
The BFF invokes backend GET operations only.

## 30. No payment mutation
The module cannot create, verify, capture, refund or retry a payment.

## 31. No refund mutation
The module cannot approve, execute, retry, cancel or reconcile a refund.

## 32. No delivery mutation
The module cannot quote, book, cancel, track, reconcile or retry a provider request.

## 33. No order mutation
The module cannot change order, chef or delivery status.

## 34. No account mutation
The module cannot suspend, reactivate or alter identity roles.

## 35. No provider execution
Cashfree, Borzo and notification-provider execution flags remain unchanged.

## 36. Browser storage
No localStorage or sessionStorage path was added.

## 37. Logging
No token, request body, provider response or investigation evidence is logged by this module.

## 38. Cache control
Successful and error BFF responses use no-store behavior.

## 39. UI states
The screen covers empty, invalid UUID, invalid reason, unauthorized, forbidden, not found, malformed upstream and unavailable states.

## 40. Admin shell
The existing administrator shell now links to Operational investigations.

## 41. Styling
The screen uses the locked Craves navy, cream, gold and purple design language.

## 42. Tests
Contract tests cover valid requests, audit-reason bounds, upstream identifier rejection, privacy reduction and delivery payload exclusion.

## 43. CI
`azure-pipelines-admin-operational-investigations-ci.yml` runs Node 24, typecheck, tests and Next.js build.

## 44. Static gates
CI verifies same-origin handling, reason/correlation headers, no-store behavior, no insecure browser storage and absence of browser state-mutation methods.

## 45. Local test
Run `npm install --ignore-scripts && npm run typecheck && npm run test && npm run build` in `apps/customer-web-next`.

## 46. New files
The module adds the contract, tests, BFF route, page, component, README, CI YAML and this handover.

## 47. Modified file
`apps/customer-web-next/src/components/admin-shell.tsx` gains one navigation card.

## 48. APIM dependency
The four backend operations are not yet exposed by the existing APIM stack.

## 49. Next child module
A separate guarded APIM PR must add only the named investigation operations after backend deployment.

## 50. APIM safety
The child rollout must refuse multiple path owners, inherited backend routing conflicts and subscription-key relaxation.

## 51. Deployment dependency
The parent backend PR stack through #100 must pass CI and merge before this web branch.

## 52. Deployment policy
Deploy the web only from merged main and only after the APIM status verification passes.

## 53. Azure changes
No Azure resource, Container App, APIM API, secret, DNS record or certificate was changed.

## 54. Database changes
No migration was run and no production record was read or written.

## 55. Pipeline execution
No Azure DevOps or GitHub Actions pipeline was executed while creating the module.

## 56. Manual steps later
Run the module CI with the established `Craves-Dev-Service-Connection` only where the pipeline requires Azure access; this build-only CI does not need it.

## 57. Security review
Review administrator least privilege, reason quality, audit retention and operational access before rollout.

## 58. Operational training
Administrators must use a real support/incident reference in the reason and must not use the workspace for curiosity browsing.

## 59. Rollback
Before deployment, rollback is branch/PR closure. After deployment, restore the previously recorded immutable customer-web image.

## 60. Completion state
Source implementation is complete on the feature branch. CI, parent merges, APIM exposure, deployment and authenticated smoke remain pending.
