# Craves Customer Web Checkout Handover

Date: 2026-07-30
Status: Code complete; CI, APIM and runtime testing pending.

## 1. Purpose
Create a customer checkout from a validated cart.
## 2. Source of truth
Order Service owns checkout state.
## 3. Authentication
HTTP-only Craves access cookie.
## 4. Authorization
Order Service verifies checkout ownership.
## 5. CSRF
Checkout creation requires same origin.
## 6. Create route
POST /api/v1/checkout.
## 7. Read route
GET /api/v1/checkout/{checkoutId}.
## 8. Input
Saved deliveryAddressId and optional note only.
## 9. Note bound
Maximum five hundred characters.
## 10. Cart validation
The UI calls Order Service validation before checkout.
## 11. Address validation
User/Chef Service ownership is resolved by Order Service.
## 12. Address snapshot
Order Service creates the immutable checkout snapshot.
## 13. Food subtotal
Returned by Order Service.
## 14. Platform fee
Returned by Order Service.
## 15. Tax
Returned by Order Service.
## 16. Delivery fee
Returned by Order Service.
## 17. Grand total
Returned by Order Service.
## 18. No browser calculation
Frontend arithmetic for backend charges is blocked by CI.
## 19. Currency
Backend currency is preserved.
## 20. Chef orders
Checkout response contains chef-specific orders.
## 21. Pickup privacy
Kitchen pickup snapshots are excluded.
## 22. Customer identity privacy
Customer identity IDs are excluded.
## 23. Response validation
Unknown status, bad UUID or malformed total causes 502.
## 24. Checkout status
PAYMENT_PENDING, PAID and CANCELLED are accepted.
## 25. Timeout
Checkout creation uses fifteen seconds.
## 26. Caching
Responses are no-store.
## 27. Checkout page
Customer selects address and optional note.
## 28. Totals page
Backend totals are shown before payment.
## 29. Details page
Checkout can be reloaded by customer-owned ID.
## 30. Payment handoff
The page links to the separate payment module.
## 31. Empty cart
Checkout is disabled.
## 32. Missing address
Checkout is disabled.
## 33. APIM path
api/v1/checkout.
## 34. APIM backend
Order Service /api/v1/checkout.
## 35. APIM creation
Dedicated API only when no path owner exists.
## 36. APIM collision
Multiple path owners fail closed.
## 37. Subscription policy
Existing requirements are not relaxed.
## 38. Backend inheritance
Inherited backend-id fails closed.
## 39. APIM authentication
Bearer syntax guard is applied.
## 40. APIM caching
No-store is enforced.
## 41. CI
Typecheck, tests and production build are required.
## 42. APIM CI
Bash and policy XML are validated.
## 43. Branch
feature/customer-web-checkout.
## 44. Stack base
feature/customer-web-cart.
## 45. Azure state
No Azure changes occurred during coding.
## 46. Payment state
No payment order or Cashfree session was created.
## 47. Test fixture
Use one non-production customer cart and address.
## 48. Duplicate caution
Do not repeatedly create paid checkouts during smoke tests.
## 49. Rollback
Remove named APIM operations only.
## 50. Acceptance
CI, APIM verification, cart validation, checkout creation and total matching must pass.
