# Craves Customer Web Payment Handover

Date: 2026-07-30
Status: Code complete; CI, deployment, APIM, Cashfree configuration and sandbox testing pending.

## 1. Purpose
Provide secure customer-owned Cashfree checkout.
## 2. Backend owner
Integration Service owns payment records and Cashfree credentials.
## 3. Order owner
Order Service verifies customer checkout ownership.
## 4. Create authentication
Bearer token is mandatory.
## 5. Read authentication
Bearer token is now mandatory.
## 6. Verify authentication
Bearer token is now mandatory.
## 7. Ownership flow
Payment Service loads the checkout using the customer token.
## 8. Customer match
Payment customer identity must match checkout customer identity.
## 9. Missing ownership
The service returns not found rather than revealing another payment.
## 10. Internal loader
Webhook processing uses a private database loader.
## 11. Public loader
Customer reads use the owned checkout path.
## 12. Provider verification
Cashfree status is fetched only after ownership succeeds.
## 13. Create amount
Amount comes from Order Service grand total.
## 14. Currency
Currency comes from Order Service checkout.
## 15. Customer data
Phone, email and display name come from Auth Service identity.
## 16. Secrets
Cashfree client ID and secret stay server-side.
## 17. Browser SDK
Cashfree v3 is loaded directly from the official SDK host.
## 18. SDK mode
Only sandbox or production is accepted.
## 19. Default rollout
Sandbox is required for initial testing.
## 20. Payment session
Session ID remains in component memory only.
## 21. Browser storage
No payment session or access token is persisted.
## 22. Logging
Payment sessions, tokens and secrets are not logged.
## 23. Hosted checkout
Cashfree modal collects payment details.
## 24. PCI boundary
Craves does not collect card or bank credentials.
## 25. Verification
Browser always calls the backend verification endpoint.
## 26. Webhook
Cashfree webhook remains a separate signed provider operation.
## 27. Webhook APIM
This module does not change webhook routing.
## 28. Status privacy
Provider IDs and provider status are removed from browser DTOs.
## 29. Customer status
Only Craves payment status is displayed.
## 30. Return URL
The same checkout payment page is used.
## 31. HTTPS
Production return origin must be HTTPS.
## 32. Domain whitelist
Final web domain must be approved in Cashfree.
## 33. BFF create
POST /api/payments/orders.
## 34. BFF read
GET /api/payments/orders/{paymentOrderId}.
## 35. BFF verify
POST /api/payments/orders/{paymentOrderId}/verify.
## 36. APIM path
api/v1/payments.
## 37. APIM operations
Create, owned read and owned verify only.
## 38. APIM policy
Bearer syntax and no-store are enforced.
## 39. APIM collision
Multiple path owners fail closed.
## 40. Subscription policy
Existing requirements are not relaxed.
## 41. CI Java
Integration Service tests run on Java 21.
## 42. CI web
Typecheck, tests and Next build run in sandbox mode.
## 43. CI secret scan
Credential literals and payment-session logging are blocked.
## 44. Deployment order
Deploy Integration ownership fix before APIM exposure.
## 45. Azure state
No Azure changes occurred during coding.
## 46. Cashfree state
No payment, API request or webhook registration occurred.
## 47. Test checkout
Use one owned non-production checkout.
## 48. Test payment
Use Cashfree sandbox only.
## 49. Failure cleanup
Do not delete payment audit records; mark tests in operational notes.
## 50. Acceptance
CI, owned-read denial, sandbox checkout, backend verify, webhook and order-paid propagation must pass.
