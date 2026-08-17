# Craves Mobile Customer Checkout and Payment Handover

Date: 2026-07-30
Status: Code complete; CI, native build, APIM, Cashfree configuration and sandbox testing pending.

## 1. Purpose
Create backend checkout and complete hosted Cashfree payment on mobile.
## 2. Platform
React Native 0.86 and TypeScript.
## 3. Session
Keychain/Keystore-backed Craves access token.
## 4. Cart dependency
Order Service customer cart must be available.
## 5. Address dependency
User/Chef Service saved addresses must be available.
## 6. Checkout dependency
Order Service checkout APIM must be available.
## 7. Payment dependency
Ownership-hardened Integration Service must be deployed.
## 8. Create checkout
POST /api/v1/checkout.
## 9. Read checkout
GET /api/v1/checkout/{checkoutId}.
## 10. Create payment
POST /api/v1/payments/orders.
## 11. Verify payment
POST /api/v1/payments/orders/{paymentOrderId}/verify.
## 12. Cart validation
Mobile validates the cart before checkout creation.
## 13. Checkout input
Saved address UUID and optional note only.
## 14. Note bound
Maximum five hundred characters.
## 15. Food subtotal
Returned by Order Service.
## 16. Platform fee
Returned by Order Service.
## 17. Tax
Returned by Order Service.
## 18. Delivery fee
Returned by Order Service.
## 19. Grand total
Returned by Order Service.
## 20. No mobile arithmetic
Business charges are not calculated by mobile.
## 21. Cashfree SDK
react-native-cashfree-pg-sdk 2.4.0.
## 22. API contract package
cashfree-pg-api-contract 2.1.1.
## 23. Environment
CFEnvironment.SANDBOX only.
## 24. Production gate
CI rejects CFEnvironment.PRODUCTION.
## 25. Session creation
Integration Service creates the provider payment session.
## 26. SDK session
CFSession uses payment session ID and Cashfree order ID.
## 27. Callback registration
Callback is registered before doWebPayment.
## 28. Payment method
Cashfree hosted web checkout is used.
## 29. SDK verify callback
onVerify is treated only as a verification trigger.
## 30. Order reference check
Callback order ID must equal the current backend-issued order ID.
## 31. Backend verify
Craves verify endpoint decides payment status.
## 32. SDK error
onError never marks payment successful.
## 33. Card data
Craves does not collect card data.
## 34. UPI credential
Craves does not collect UPI PIN.
## 35. Banking credential
Craves does not collect banking credentials.
## 36. Client secrets
Cashfree credentials remain server-side.
## 37. Payment session storage
Session ID remains in component memory.
## 38. Provider order storage
Cashfree order ID remains in component memory.
## 39. Logging
Payment sessions, tokens and secrets are not logged.
## 40. Session expiry
HTTP 401 signs the customer out.
## 41. Checkout screen
Saved address and backend cart preview.
## 42. Payment screen
Backend amount and hosted checkout action.
## 43. Success navigation
Verified payment can open customer orders.
## 44. Native dependency install
Android/iOS shells and lockfile are pending.
## 45. iOS pods
CocoaPods installation is a manual native step.
## 46. Android build
Clean Gradle build is required after native shell generation.
## 47. Cashfree merchant setup
KYC, sandbox credentials, app/domain and webhook setup are manual.
## 48. Signing
Android keystore and Apple provisioning are not committed.
## 49. Sandbox acceptance
Owned checkout, SDK callback, backend verification and order-paid propagation must pass.
## 50. Production acceptance
Only a separately reviewed production-mode amendment may follow sandbox acceptance.
