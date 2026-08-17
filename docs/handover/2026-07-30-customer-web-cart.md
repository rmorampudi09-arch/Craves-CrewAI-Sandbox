# Craves Customer Web Cart Handover

Date: 2026-07-30
Status: Code complete; CI, APIM and runtime testing pending.

## 1. Purpose
Provide customer cart management in Next.js.
## 2. Source of truth
Order Service owns the cart.
## 3. Authentication
HTTP-only Craves access cookie.
## 4. Authorization
Order Service resolves the customer identity.
## 5. CSRF
All mutations require same-origin requests.
## 6. Cart route
GET /api/v1/cart.
## 7. Add route
POST /api/v1/cart/items.
## 8. Update route
PUT /api/v1/cart/items/{cartItemId}.
## 9. Remove route
DELETE /api/v1/cart/items/{cartItemId}.
## 10. Clear route
DELETE /api/v1/cart.
## 11. Validation route
POST /api/v1/cart/validate.
## 12. Quantity bounds
One through one hundred.
## 13. Identifier bounds
Cart and menu identifiers must be UUIDs.
## 14. Pricing
Unit prices come only from Order Service.
## 15. Subtotal
Food subtotal comes only from Order Service.
## 16. Fees
Platform, tax and delivery fees are not calculated in cart UI.
## 17. Currency
Backend cart and total currencies must match.
## 18. Availability
Order Service validates item availability.
## 19. Menu snapshots
The frontend does not override item or kitchen names.
## 20. Response privacy
customerIdentityId is removed.
## 21. Provider privacy
Provider data is not exposed.
## 22. Caching
Cart responses are no-store.
## 23. Timeout
Shared authenticated fetch uses ten seconds.
## 24. Discovery integration
Nearby menu cards can add quantity one.
## 25. Cart page
/cart displays items and backend subtotal.
## 26. Quantity UI
Customers can change quantity within bounds.
## 27. Removal UI
Customers can remove individual items.
## 28. Clear UI
Clear cart requires browser confirmation.
## 29. Validation UI
Availability validation is explicit.
## 30. Checkout link
Checkout is enabled only for non-empty carts.
## 31. Empty state
Empty cart is a normal state.
## 32. Error state
Authentication and backend errors are customer-safe.
## 33. APIM path
api/v1/cart.
## 34. APIM backend
Order Service /api/v1/cart.
## 35. APIM creation
Dedicated API only when no path owner exists.
## 36. APIM collision
Multiple path owners stop deployment.
## 37. Subscription policy
Existing subscription requirement is never relaxed.
## 38. Backend inheritance
Inherited backend-id stops deployment.
## 39. APIM authentication
Bearer syntax guard is applied.
## 40. APIM caching
No-store headers are applied.
## 41. CI
Typecheck, tests and Next build are mandatory.
## 42. CI pricing scan
Frontend fee calculations are blocked.
## 43. Branch
feature/customer-web-cart.
## 44. Stack base
feature/customer-web-addresses.
## 45. Azure state
No Azure action occurred during coding.
## 46. Provider state
No delivery or payment provider was called.
## 47. Test fixture
Use one active menu item belonging to a test kitchen.
## 48. Cleanup
Remove the exact cart item and confirm empty cart.
## 49. Rollback
Remove named APIM operations only; preserve shared API.
## 50. Acceptance
CI, APIM verification, add/update/validate/remove and cleanup must pass.
