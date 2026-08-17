# Craves Next Ten Customer Modules — Consolidated Engineering Handover

Date: 2026-07-30
Repository: `rmorampudi09-arch/Craves-Build-platform`
Status: All code prepared on stacked feature branches. No pipeline, Azure write, APIM change, Firebase change, Cashfree transaction or native release build has been performed.

## 1. Document purpose
This handover records the exact ten-module code delivery, architecture boundaries, files, pipeline order, manual actions, risks, rollback rules and acceptance sequence.

## 2. Scope delivered
The scope covers customer discovery, addresses, cart, checkout and payment on Next.js, followed by customer orders, notifications, addresses, cart, checkout and payment on React Native.

## 3. Stack discipline
The code uses the locked Craves stack: Next.js/TypeScript/Tailwind, React Native/TypeScript, Spring Boot 3/Java 21, Firebase Authentication, Cashfree Payments and Azure API Management.

## 4. Branch discipline
Every module is isolated on a feature branch and opened as a stacked draft pull request. Nothing is committed directly to `main`.

## 5. Runtime discipline
No code path in this batch enables Borzo, delivery creation, webhook processing, tracking reconciliation or delivery-status publication.

## 6. Business-rule discipline
No pricing policy, commission, delivery radius, ETA, SLA, refund entitlement, compensation, GST, FSSAI or compliance rule is invented.

## 7. Module 1 branch
`feature/customer-web-discovery`.

## 8. Module 1 PR
Draft PR #34: Next.js nearby kitchen and dish discovery.

## 9. Module 1 customer route
`/discover`.

## 10. Module 1 backend contract
Existing Catalog Service APIM routes `/api/v1/discovery/kitchens` and `/api/v1/discovery/menu-items`.

## 11. Module 1 privacy
Kitchen identity IDs, phone numbers, email addresses, exact kitchen coordinates and blob metadata are removed. Menu image URLs must use HTTPS.

## 12. Module 1 location policy
Browser geolocation is permission-based and invoked only by customer action. Coordinates are not stored in browser storage or a Craves database by this module.

## 13. Module 1 CI
`azure-pipelines-customer-web-next-discovery-ci.yml`.

## 14. Module 1 documentation
`apps/customer-web-next/modules/discovery/README.md` and `docs/handover/2026-07-30-customer-web-discovery.md`.

## 15. Module 2 branch
`feature/customer-web-addresses`.

## 16. Module 2 PR
Draft PR #35: Next.js customer address management.

## 17. Module 2 customer route
`/addresses`.

## 18. Module 2 backend contract
Customer address CRUD and recommendation under `/api/v1/customer/addresses`.

## 19. Module 2 mutation protection
POST, PUT and DELETE BFF routes require a same-origin browser request and an HTTP-only Craves session cookie.

## 20. Module 2 APIM
`scripts/apim/configure-customer-addresses-apim.sh` and `azure-pipelines-customer-addresses-apim.yml` configure `api/v1/customer` only after explicit confirmation.

## 21. Module 2 APIM fail-closed rules
The script fails on multiple path owners, inherited `backend-id`, missing User/Chef Service readiness or an existing subscription-key requirement that would need relaxation.

## 22. Module 2 CI
`azure-pipelines-customer-web-next-addresses-ci.yml`.

## 23. Module 3 branch
`feature/customer-web-cart`.

## 24. Module 3 PR
Draft PR #36: Next.js customer cart.

## 25. Module 3 customer route
`/cart`.

## 26. Module 3 backend ownership
Order Service owns item snapshots, price, currency, line totals, availability and food subtotal.

## 27. Module 3 charge boundary
The cart does not calculate platform fee, tax or delivery fee.

## 28. Module 3 discovery integration
Nearby dish cards call the same-origin cart BFF with a validated menu-item UUID and quantity one.

## 29. Module 3 APIM
`scripts/apim/configure-customer-cart-apim.sh` and `azure-pipelines-customer-cart-apim.yml` configure `api/v1/cart` only after explicit confirmation.

## 30. Module 3 CI
`azure-pipelines-customer-web-next-cart-ci.yml`.

## 31. Module 4 branch
`feature/customer-web-checkout`.

## 32. Module 4 PR
Draft PR #37: Next.js customer checkout.

## 33. Module 4 customer routes
`/checkout` and `/checkout/{checkoutId}`.

## 34. Module 4 input boundary
The browser sends only a customer-owned saved-address UUID and an optional note bounded to 500 characters.

## 35. Module 4 charge boundary
Food subtotal, platform fee, tax, delivery fee and grand total are rendered only from the Order Service checkout response.

## 36. Module 4 address boundary
Order Service validates ownership and creates the immutable delivery-address snapshot.

## 37. Module 4 APIM
`scripts/apim/configure-customer-checkout-apim.sh` and `azure-pipelines-customer-checkout-apim.yml` configure `api/v1/checkout` after confirmation.

## 38. Module 4 CI
`azure-pipelines-customer-web-next-checkout-ci.yml`.

## 39. Module 5 branch
`feature/customer-web-payments`.

## 40. Module 5 PR
Draft PR #38: payment ownership hardening and Next.js Cashfree checkout.

## 41. Module 5 backend security correction
Integration Service payment create, read and verify now require the customer Bearer token.

## 42. Module 5 ownership check
Integration Service loads the checkout through Order Service using the same customer token and compares the checkout customer identity with the payment record.

## 43. Module 5 information-disclosure rule
A payment belonging to another customer is returned as not found rather than revealing its existence.

## 44. Module 5 provider-verification rule
Cashfree verification is called only after customer ownership succeeds.

## 45. Module 5 web route
`/checkout/{checkoutId}/payment`.

## 46. Module 5 web SDK
The browser loads Cashfree JavaScript SDK v3 directly from `https://sdk.cashfree.com/js/v3/cashfree.js`.

## 47. Module 5 web environment
`NEXT_PUBLIC_CASHFREE_MODE` accepts only `sandbox` or `production`; initial deployment must use `sandbox`.

## 48. Module 5 web payment boundary
Cashfree client ID/key remain server-side. The browser receives only the hosted-checkout payment session needed by Cashfree.

## 49. Module 5 APIM
`scripts/apim/configure-customer-payments-apim.sh` configures customer create/read/verify operations and deliberately leaves the Cashfree webhook operation unchanged.

## 50. Module 5 CI
`azure-pipelines-customer-payments-ci.yml` runs Java 21 Integration Service tests plus Next.js payment tests/build. `azure-pipelines-customer-payments-apim.yml` remains confirmation-gated.

## 51. Module 6 branch
`feature/customer-mobile-orders`.

## 52. Module 6 PR
Draft PR #39: React Native customer order history and details.

## 53. Module 6 API
Owned `GET /api/v1/orders` and `GET /api/v1/orders/{orderId}` calls use the Keychain/Keystore-backed access session.

## 54. Module 6 privacy
Customer identity IDs, kitchen pickup snapshots and provider payloads are not accepted by the mobile DTO.

## 55. Module 6 delivery integration
An owned order detail navigates directly to the existing provider-neutral delivery-tracking screen.

## 56. Module 6 CI
`azure-pipelines-customer-mobile-orders-ci.yml`.

## 57. Module 7 branch
`feature/customer-mobile-notifications`.

## 58. Module 7 PR
Draft PR #40: React Native customer notification inbox.

## 59. Module 7 API
Owned list and mark-read operations under `/api/v1/notifications/in-app`.

## 60. Module 7 privacy
Raw event/provider payloads, internal keys, retry metadata and outbox/inbox fields are excluded.

## 61. Module 7 navigation
Known ORDER targets open owned order details; known DELIVERY targets open owned delivery tracking.

## 62. Module 7 CI
`azure-pipelines-customer-mobile-notifications-ci.yml`.

## 63. Module 8 branch
`feature/customer-mobile-addresses`.

## 64. Module 8 PR
Draft PR #41: React Native customer address management.

## 65. Module 8 API
Owned list, create, get, update, delete and recommendation operations under `/api/v1/customer/addresses`.

## 66. Module 8 native-location decision
The app accepts exact coordinates but does not add an unreviewed geolocation dependency before native Android/iOS shells exist.

## 67. Module 8 pending native amendment
Android location permissions, iOS usage description, chosen location library and final consent copy must be reviewed together later.

## 68. Module 8 CI
`azure-pipelines-customer-mobile-addresses-ci.yml` blocks identity fields, insecure storage, logging and unreviewed geolocation APIs.

## 69. Module 9 branch
`feature/customer-mobile-cart`.

## 70. Module 9 PR
Draft PR #42: React Native customer cart.

## 71. Module 9 API
Owned cart read, add, update, remove, clear and validate operations under `/api/v1/cart`.

## 72. Module 9 pricing boundary
Mobile displays only Order Service unit price, line total, currency and food subtotal.

## 73. Module 9 future discovery handoff
The Cart navigation route accepts a validated optional menu-item UUID and quantity; no customer-facing developer UUID form is shown.

## 74. Module 9 stack safety
PR #42 includes a real checkout-preparation screen so the branch is independently runnable before PR #43 replaces it.

## 75. Module 9 CI
`azure-pipelines-customer-mobile-cart-ci.yml` blocks frontend fee arithmetic, identity/provider fields, insecure storage and logging.

## 76. Module 10 branch
`feature/customer-mobile-checkout-payment`.

## 77. Module 10 PR
The final draft PR targets `feature/customer-mobile-cart` and replaces the preparation screen with real checkout/payment.

## 78. Module 10 checkout screen
The customer selects a saved address, reviews backend cart subtotal, validates the cart and creates an Order Service checkout.

## 79. Module 10 payment screen
The customer sees the Order Service grand total and starts Cashfree hosted checkout.

## 80. Module 10 Cashfree dependencies
`react-native-cashfree-pg-sdk` is pinned to `2.4.0`; `cashfree-pg-api-contract` is pinned to `2.1.1`.

## 81. Module 10 Cashfree environment
The implementation uses `CFEnvironment.SANDBOX`; CI rejects `CFEnvironment.PRODUCTION`.

## 82. Module 10 SDK session
`CFSession` receives the backend payment session ID, Cashfree order ID and sandbox environment.

## 83. Module 10 callback ordering
`CFPaymentGatewayService.setCallback` is registered before `doWebPayment`.

## 84. Module 10 callback trust
`onVerify` is only a trigger. It does not directly mark the payment or order paid.

## 85. Module 10 callback correlation
The callback Cashfree order ID must match the current backend-issued Cashfree order ID.

## 86. Module 10 backend verification
The app calls the ownership-protected Integration Service verify endpoint and displays success only when Craves returns `PAID`.

## 87. Module 10 ephemeral data
Payment session ID and Cashfree order ID remain in component memory and are not stored with the Craves mobile session.

## 88. Module 10 CI
`azure-pipelines-customer-mobile-checkout-payment-ci.yml` pins SDK versions, verifies callback ordering, requires backend verification and blocks production mode/persistent payment data/sensitive logging.

## 89. Required merge order
Merge the entire existing stack in order: PR #25, #26, #27, #28, #29, #30, #31, #33, then #34, #35, #36, #37, #38, #39, #40, #41, #42 and the final mobile checkout/payment PR.

## 90. Initial CI order
Run each PR's build-only CI against its exact branch head before marking that PR ready. Do not use a later child branch as proof that an earlier parent branch passed independently.

## 91. Delivery rollout prerequisite
Before customer tracking deployment, deploy Order Service V9, verify the delivery consumer is false, validate its Service Bus subscription, then enable and test the consumer before enabling the Integration status publisher.

## 92. APIM rollout order
Configure delivery status, customer order reads, customer addresses, cart, checkout and customer payment operations one gateway module at a time. After each write, run its read-only verification/status pipeline and an authenticated smoke test.

## 93. Web deployment order
After all web CI and APIM gates pass, create reviewed lockfiles, replace `npm install` with `npm ci`, rerun CI, record the current legacy web image, then run the guarded Next.js replacement deployment.

## 94. Required web build variables
`NEXT_PUBLIC_FIREBASE_API_KEY`, `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`, `NEXT_PUBLIC_FIREBASE_PROJECT_ID`, `NEXT_PUBLIC_FIREBASE_APP_ID`, optional Firebase sender/storage values and `cashfreeMode=sandbox` must be supplied through Azure DevOps variables/parameters.

## 95. Cashfree server environment keys
Use the existing Integration Service keys without values in source control: `PAYMENT_PROVIDER_ENVIRONMENT`, `PAYMENT_PROVIDER_API_VERSION`, `PAYMENT_PROVIDER_CLIENT_ID`, `PAYMENT_PROVIDER_CLIENT_KEY`, `PAYMENT_PROVIDER_DEFAULT_RETURN_URL`, and `PAYMENT_PROVIDER_WEBHOOK_URL`.

## 96. Firebase manual actions
Confirm Phone provider, authorized web domains and test numbers; register Android/iOS apps; download `google-services.json` and `GoogleService-Info.plist` locally. These files must not be committed.

## 97. Native manual actions
Run the guarded native-shell bootstrap, review Gradle/Xcode projects, install dependencies, run CocoaPods, add Firebase files locally, configure Android keystore and Apple provisioning, then perform clean Android/iOS builds.

## 98. Cashfree manual actions
Complete merchant KYC, create sandbox credentials, configure application/domain/return settings, register the webhook URL, validate webhook signatures and perform only controlled sandbox payments before any production request.

## 99. Rollback and cleanup
Rollback APIM by removing only named operations, restore the exact recorded web image, disable any temporarily enabled consumer/publisher, and clean only exact synthetic address/cart/checkout fixtures. Payment audit and webhook records must remain durable.

## 100. Final acceptance
The batch is accepted only after every independent CI passes, lockfiles use `npm ci`, Java ownership tests pass, APIM operations verify, web auth/discovery/address/cart/checkout/payment smokes pass, mobile Android/iOS builds pass, Firebase OTP works, Cashfree sandbox callback plus backend verify works, order-paid propagation works, and all delivery/provider safety flags return to approved states.
