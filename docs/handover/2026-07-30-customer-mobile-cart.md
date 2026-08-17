# Craves Mobile Customer Cart Handover

Date: 2026-07-30
Status: Code complete; CI, APIM dependency and device testing pending.

## 1. Purpose
Manage the customer cart on mobile.
## 2. Platform
React Native and TypeScript.
## 3. Session
Keychain/Keystore-backed Craves access token.
## 4. Authentication
Bearer token on every cart request.
## 5. Authorization
Order Service resolves customer ownership.
## 6. Cart read
GET /api/v1/cart.
## 7. Cart clear
DELETE /api/v1/cart.
## 8. Add item
POST /api/v1/cart/items.
## 9. Update item
PUT /api/v1/cart/items/{cartItemId}.
## 10. Remove item
DELETE /api/v1/cart/items/{cartItemId}.
## 11. Validate cart
POST /api/v1/cart/validate.
## 12. Identifier
Cart and menu item IDs must be UUIDs.
## 13. Quantity
One through one hundred.
## 14. Price
Order Service owns unit price.
## 15. Line total
Order Service owns line total.
## 16. Subtotal
Order Service owns food subtotal.
## 17. Currency
Cart and totals currency must match.
## 18. Platform fee
Not calculated by mobile.
## 19. Tax
Not calculated by mobile.
## 20. Delivery fee
Not calculated by mobile.
## 21. Availability
Order Service validation is explicit.
## 22. Session expiry
HTTP 401 signs the customer out.
## 23. Not found
Missing item receives a safe message.
## 24. Timeout
Shared mobile timeout applies.
## 25. Cart screen
Lists items and backend totals.
## 26. Quantity controls
Plus and minus respect bounds.
## 27. Remove
Individual item removal is supported.
## 28. Clear
Clear cart requires native confirmation.
## 29. Pull refresh
Cart can be refreshed.
## 30. Checkout handoff
Non-empty cart navigates to MobileCheckout.
## 31. Stack-safe placeholder
PR #42 includes a real preparation screen.
## 32. Final replacement
PR #43 replaces preparation with checkout/payment.
## 33. Discovery handoff
Typed optional menuItemId and quantity are supported.
## 34. No UUID form
Customers do not see a developer identifier entry field.
## 35. Identity privacy
customerIdentityId is removed.
## 36. Provider privacy
Provider payload is removed.
## 37. Storage
Cart data is not stored in AsyncStorage.
## 38. Logging
Tokens and cart payloads are not logged.
## 39. CI typecheck
Full mobile TypeScript is checked.
## 40. CI tests
Cart parser tests run.
## 41. CI pricing scan
Frontend fee arithmetic is blocked.
## 42. CI privacy scan
Identity, provider and insecure storage are blocked.
## 43. Branch
feature/customer-mobile-cart.
## 44. Stack base
feature/customer-mobile-addresses.
## 45. APIM dependency
Customer cart APIM from PR #36 must exist.
## 46. Azure state
No Azure action occurred during coding.
## 47. Test fixture
Use one active non-production menu item.
## 48. CRUD test
Add, update, validate and remove exact item.
## 49. Cleanup
Confirm cart returns to prior state.
## 50. Acceptance
CI, ownership, totals, validation and exact cleanup must pass.
