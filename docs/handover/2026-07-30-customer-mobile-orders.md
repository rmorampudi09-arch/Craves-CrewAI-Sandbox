# Craves Mobile Customer Orders Handover

Date: 2026-07-30
Status: Code complete; CI, native shells and device testing pending.

## 1. Purpose
Show customer-owned orders on mobile.
## 2. Platform
React Native 0.86 and TypeScript.
## 3. Session
Keychain/Keystore-backed Craves access session.
## 4. Authentication
Bearer token on every order request.
## 5. Authorization
Order Service verifies CUSTOMER role and ownership.
## 6. List route
GET /api/v1/orders.
## 7. Detail route
GET /api/v1/orders/{orderId}.
## 8. UUID validation
Order IDs are validated before request.
## 9. Timeout
Mobile network timeout uses shared configuration.
## 10. Session expiry
HTTP 401 clears the local session.
## 11. Not found
Unowned and missing orders use a safe message.
## 12. Response validation
Every order field is allow-listed.
## 13. Status validation
Only known Order Service statuses are accepted.
## 14. Currency
Backend currency is preserved.
## 15. Amounts
Backend totals are displayed without recalculation.
## 16. Items
Item name, quantity and backend line total are shown.
## 17. Address
Only customer delivery snapshot is shown.
## 18. Identity privacy
customerIdentityId is removed.
## 19. Pickup privacy
Kitchen pickup snapshot is removed.
## 20. Provider privacy
No provider payload is rendered.
## 21. Orders screen
Pull-to-refresh order history.
## 22. Order details
Items, totals, address and status.
## 23. Delivery handoff
Details navigate to existing tracking screen.
## 24. Date locale
Timestamps use en-IN presentation.
## 25. Money locale
Amounts use en-IN with backend currency.
## 26. Empty state
No orders is supported.
## 27. Loading state
Activity indicator and accessible status copy.
## 28. Network state
Connection failures are customer-safe.
## 29. Home navigation
My Orders is the primary signed-in action.
## 30. Manual ID tracking
Existing tracking lookup remains available.
## 31. CI typecheck
Full mobile TypeScript is checked.
## 32. CI tests
All mobile domain tests run.
## 33. CI privacy
Private order fields are scanned.
## 34. CI auth
Authorization header presence is verified.
## 35. Branch
feature/customer-mobile-orders.
## 36. Stack base
feature/customer-web-payments.
## 37. APIM dependency
Order list/detail APIM from PR #29 must be configured first.
## 38. Delivery dependency
Delivery APIM and Order consumer stack must be working.
## 39. Native shell
Android/iOS projects remain a later reviewed amendment.
## 40. Firebase files
No Firebase native configuration is committed.
## 41. Signing
No keystore or provisioning profile is committed.
## 42. Azure state
No Azure change occurred during coding.
## 43. Provider state
No delivery or payment provider was called.
## 44. Test account
Use one customer with multiple chef-specific orders.
## 45. Ownership test
A different customer must not load the order.
## 46. Session test
Expired access token must return to sign-in.
## 47. Tracking test
Order detail must open matching delivery status.
## 48. Device test
Android and iOS layouts require device review.
## 49. Rollback
Remove the two screens/navigation entries only.
## 50. Acceptance
CI, ownership, privacy, session expiry and tracking navigation must pass.
