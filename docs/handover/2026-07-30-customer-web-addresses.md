# Craves Customer Web Address Management Handover

Date: 2026-07-30
Status: Code complete; pipelines, APIM and runtime testing pending.

## 1. Purpose

Provide customer-owned saved delivery address management.

## 2. Architecture

Next.js BFF forwards authenticated requests to User/Chef Service through APIM.

## 3. Authentication

The HTTP-only Craves access-token cookie is the only browser session source.

## 4. Authorization

User/Chef Service remains responsible for customer identity and address ownership.

## 5. CSRF

POST, PUT and DELETE BFF routes reject non-same-origin requests.

## 6. Response privacy

identityId is removed before any address reaches browser JavaScript.

## 7. Address fields

Recipient, contact, address lines, landmark, area, city, state, postal code, coordinates and default flag are supported.

## 8. Labels

HOME, WORK and OTHER are accepted exactly as defined by the backend.

## 9. Phone validation

Phone numbers require 10 to 15 digits with an optional leading plus.

## 10. Coordinates

Latitude and longitude use backend-compatible global bounds.

## 11. Postal code

The client applies a technical length bound but does not invent region-specific serviceability.

## 12. Location permission

Current location requires an explicit browser permission prompt.

## 13. Location recommendation

User/Chef Service decides whether live coordinates match a saved address.

## 14. No geocoding

This module does not invent address geocoding or reverse-geocoding.

## 15. No serviceability

Saving an address does not promise delivery serviceability.

## 16. No browser storage

Address data is not stored in localStorage or sessionStorage.

## 17. Caching

All address BFF responses are no-store.

## 18. Timeout

Authenticated API calls use the shared ten-second timeout.

## 19. List route

GET /api/customer/addresses.

## 20. Create route

POST /api/customer/addresses.

## 21. Detail route

GET /api/customer/addresses/{addressId}.

## 22. Update route

PUT /api/customer/addresses/{addressId}.

## 23. Delete route

DELETE /api/customer/addresses/{addressId}.

## 24. Recommendation route

GET /api/customer/addresses/recommendation.

## 25. UI

The page provides list, create, edit, default and delete interactions.

## 26. Delete confirmation

The browser requires explicit confirmation before delete.

## 27. Empty state

No saved addresses is a valid state.

## 28. Error state

Authentication, validation, not-found and upstream errors are customer-safe.

## 29. APIM path

api/v1/customer.

## 30. APIM backend

User/Chef Service /api/v1/customer.

## 31. APIM ownership

Multiple path owners cause a hard failure.

## 32. APIM creation

A dedicated API is created only when no existing API owns the path.

## 33. Subscription keys

The script never relaxes an existing subscription-key requirement.

## 34. Inherited backend policy

backend-id inheritance causes a fail-closed stop.

## 35. APIM operations

List, create, get, update, delete and recommendation operations are configured.

## 36. APIM policy

Bearer syntax is required and no-store headers are enforced.

## 37. CI web

Typecheck, tests and Next.js build run.

## 38. CI APIM

Bash syntax and policy XML are validated.

## 39. CI privacy

UI source is scanned for identity IDs and browser storage.

## 40. Branch

feature/customer-web-addresses.

## 41. Stack base

feature/customer-web-discovery.

## 42. Deployment

No Azure or application deployment occurs during code preparation.

## 43. Billable warning

APIM writes can affect an Azure resource and remain manual.

## 44. Test data

Use one non-production customer and exact test address identifiers.

## 45. Cleanup

Delete only the exact created test address.

## 46. Rollback

Remove only the named APIM operations; do not delete a shared API.

## 47. Future maps

Map picker and address autocomplete require a separately approved provider.

## 48. Business boundary

No delivery radius, fee, SLA or compliance rule is implemented.

## 49. Security boundary

No secret, token or private profile field is logged.

## 50. Acceptance

CI, APIM verification, CRUD smoke, ownership test and exact cleanup must pass.
