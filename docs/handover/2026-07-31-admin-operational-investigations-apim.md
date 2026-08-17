# Craves admin operational investigations APIM handover

**Date:** 31 July 2026  
**Parent PR:** #101  
**Branch:** `feature/admin-operational-investigations-apim`  
**Azure change performed:** None  
**Pipeline run performed:** None

## 1. Purpose
Adds a controlled APIM route for the existing administrator investigation controllers.

## 2. API identifier
The approved API ID is `craves-admin-operational-investigations-v1`.

## 3. URL suffix
The approved URL suffix is `api/v1/admin/operations`.

## 4. Shared gateway boundary
One APIM API serves both backend owners while preserving operation-level routing.

## 5. Order owner
`GET /orders/{resourceId}` routes to Order Service.

## 6. Payment owner
`GET /payments/{resourceId}` routes to Integration Service.

## 7. Refund owner
`GET /refunds/{resourceId}` routes to Integration Service.

## 8. Delivery owner
`GET /delivery-commands/{resourceId}` routes to Integration Service.

## 9. Order app
The default Order Container App is `ca-craves-order-service-prodlow`.

## 10. Integration app
The default Integration Container App is `ca-craves-integration-service-pr`.

## 11. Resource group
The default resource group is `rg-craves-prodlow-centralindia`.

## 12. APIM instance
The default APIM name is `apim-craves-prodlow-l3ing6`.

## 13. HTTPS only
The API is created with HTTPS as its only protocol.

## 14. Subscription keys
The API is created without an APIM subscription-key requirement.

## 15. No relaxation
If an existing approved API requires a subscription key, rollout fails rather than relaxing it.

## 16. Exact owner
If the path is owned by an API with a different ID, rollout fails.

## 17. Duplicate owner
If more than one APIM API owns the path, rollout fails.

## 18. Backend health
Both Container Apps must have latest equal to latest-ready and Running status.

## 19. Health endpoint
Both `/actuator/health` endpoints must return success before any APIM write.

## 20. Global policy check
A global inherited `set-backend-service backend-id` blocks rollout.

## 21. API policy check
An API-level inherited `set-backend-service backend-id` blocks rollout.

## 22. Routing method
Each operation uses an explicit operation-level backend base URL.

## 23. Authentication precheck
The APIM policy requires an Authorization header beginning with Bearer.

## 24. Backend authorization
The owning Spring controller still validates the Craves JWT and exact ADMIN role.

## 25. Reason header
APIM forwards the existing `X-Admin-Reason` header unchanged to the backend.

## 26. Correlation header
The backend-generated `X-Correlation-ID` remains available to the Next.js BFF.

## 27. Cache policy
Responses receive `Cache-Control: no-store, no-cache, must-revalidate`.

## 28. Pragma policy
Responses receive `Pragma: no-cache`.

## 29. MIME hardening
Responses receive `X-Content-Type-Options: nosniff`.

## 30. Frame hardening
Responses receive `X-Frame-Options: DENY`.

## 31. Provider payloads
APIM does not inspect, log, transform or emit provider payloads.

## 32. Tokens
APIM does not persist or return access tokens.

## 33. Sensitive tracing
No body trace or credential trace policy is introduced.

## 34. Methods
All four operations are GET and read-only.

## 35. No mutation
No POST, PUT, PATCH or DELETE business operation is added.

## 36. Confirmation gate
Rollout requires `CONFIRM_APIM_WRITE=true`.

## 37. Rollout verification
Every operation and policy is read back after creation.

## 38. Backend verification
Read-back must contain the exact expected backend URL.

## 39. Header verification
Read-back must contain Bearer, no-store and nosniff controls.

## 40. Unsafe backend verification
Read-back must not contain `backend-id`.

## 41. Gateway smoke
Rollout sends one unauthenticated request using a synthetic UUID.

## 42. Smoke expectation
The unauthenticated request must return HTTP 401.

## 43. No data read
Because the smoke request has no token and is rejected at APIM, no backend record is read.

## 44. Status script
The status script is read-only and checks apps, paths, operations, policies and four 401 guards.

## 45. Rollback scope
Rollback removes only the four approved operation IDs.

## 46. API deletion
The API is deleted only when empty and `DELETE_EMPTY_API=true` is explicitly supplied.

## 47. Unexpected operations
If non-module operations remain, rollback preserves the API.

## 48. CI
CI validates Bash syntax, policy XML and required fail-closed controls.

## 49. Rollout pipeline
`azure-pipelines-admin-operational-investigations-apim.yml` performs the guarded write.

## 50. Status pipeline
`azure-pipelines-admin-operational-investigations-apim-status.yml` performs read-only verification.

## 51. Rollback pipeline
`azure-pipelines-admin-operational-investigations-apim-rollback.yml` performs the bounded rollback.

## 52. Service connection
All Azure tasks use `$(AZURE_SERVICE_CONNECTION)` with established value `Craves-Dev-Service-Connection`.

## 53. Secret handling
No service-principal value or Azure credential is committed.

## 54. Deployment order
Backend PRs through #100 must merge and deploy before this gateway rollout.

## 55. Web dependency
PR #101 must pass CI and merge before the customer web deployment.

## 56. Smoke after rollout
An authorized administrator must run one exact-UUID lookup with a real support/incident reason.

## 57. Evidence
Retain pipeline run IDs, APIM operation table, correlation ID and backend audit-row evidence.

## 58. Current state
No APIM write, pipeline, deployment, database access or authenticated API request was performed while creating this branch.

## 59. Manual action
Later set `confirmApimWrite=true` only after reviewing the target subscription, resource group, APIM name and app revisions.

## 60. Completion state
Source, CI, rollout, status, rollback and documentation are complete; execution remains deliberately pending.
