# Craves Customer Web Nearby Discovery Handover

Date: 2026-07-30
Status: Code complete; pipeline and deployment pending.

## 1. Purpose

Add nearby kitchen and dish discovery to the locked Next.js customer web.

## 2. Architecture

Browser calls same-origin Next.js BFF routes; BFF calls APIM Catalog discovery routes.

## 3. Backend ownership

Catalog Service remains the source of truth for kitchens, menu items, availability and prices.

## 4. Pricing boundary

The frontend displays backend prices and never calculates or modifies them.

## 5. Location boundary

Coordinates are supplied only by explicit customer action.

## 6. Storage boundary

Coordinates are not written to localStorage, sessionStorage, cookies or databases.

## 7. Public route

The discovery API is intentionally public and carries no customer token.

## 8. Kitchen response

Only public summary fields are exposed.

## 9. Kitchen privacy

Identity IDs, phone numbers, email addresses and exact coordinates are removed.

## 10. Menu response

Only customer-visible menu metadata is exposed.

## 11. Image safety

Only HTTPS primary image URLs survive validation.

## 12. Response validation

Unknown or malformed upstream payloads return HTTP 502.

## 13. Timeout

Catalog calls have a ten-second abort timeout.

## 14. Caching

BFF and browser requests use no-store.

## 15. Latitude validation

Latitude is bounded to -90 through 90.

## 16. Longitude validation

Longitude is bounded to -180 through 180.

## 17. Radius validation

Radius is bounded technically to one through 100000 metres; this is not a delivery-serviceability promise.

## 18. Pagination

Page and page-size values are bounded before forwarding.

## 19. Browser permission

Geolocation uses the browser permission prompt.

## 20. Manual fallback

Customers may enter coordinates manually when location permission is unavailable.

## 21. User interface

The page supports nearby dishes and nearby home kitchens.

## 22. Currency

Prices use the backend currency and Indian locale formatting.

## 23. Distance

Distance is presentation-only and comes from Catalog Service.

## 24. No ETA

The module does not invent ETA or preparation promises.

## 25. No serviceability

Discovery does not mean delivery serviceability.

## 26. No cart mutation

This module does not add items to cart.

## 27. No reviews

Ratings and reviews are not invented or displayed because they are not in this contract.

## 28. No provider calls

No delivery provider is called.

## 29. No Azure write

Development of this module changes no Azure resource.

## 30. Existing APIM dependency

The existing api/v1/discovery APIM API must be verified later.

## 31. CI file

azure-pipelines-customer-web-next-discovery-ci.yml

## 32. CI typecheck

The full TypeScript application is typechecked.

## 33. CI tests

All customer-web tests, including discovery contract tests, run.

## 34. CI build

The Next.js production build is required.

## 35. CI privacy scan

The UI is scanned for private catalog fields.

## 36. Package lock

A reviewed package-lock remains required before production and npm ci migration.

## 37. Branch

feature/customer-web-discovery

## 38. Stack base

feature/customer-mobile-delivery-tracking

## 39. Deployment

No deployment pipeline is run during code preparation.

## 40. Rollback

No runtime rollback is needed until this branch is deployed.

## 41. Manual APIM check

Confirm discover-nearby-kitchens and discover-nearby-menu-items operations.

## 42. Manual smoke

Test with a known Hyderabad coordinate and bounded radius after deployment.

## 43. Accessibility

Inputs have labels and status updates use a live status region.

## 44. Image alt policy

Decorative menu images use empty alternative text; item names remain visible.

## 45. Failure state

The page provides clear location, timeout and upstream failure messages.

## 46. Empty state

Zero nearby results are shown as a normal empty state.

## 47. Future enhancement

Kitchen detail and cuisine filters require approved contracts before implementation.

## 48. Scalability

Server-side validation and page-size bounds protect the web layer; Catalog Service owns query scaling.

## 49. Security review

No credentials, access tokens or private catalog payloads are logged or rendered.

## 50. Acceptance

Typecheck, tests, build, APIM smoke and privacy review must pass before merge/deployment.
