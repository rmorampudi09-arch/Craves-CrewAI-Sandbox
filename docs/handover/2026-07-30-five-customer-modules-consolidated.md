# Craves Five Customer Modules Consolidated Handover

## 1. Purpose

This document records the five customer-facing modules completed after the delivery downstream/APIM/tracking foundation.

## 2. Work date

30 July 2026.

## 3. Runtime change status

No Azure pipeline, deployment, APIM write, Firebase console action, native bootstrap or provider action was run during development.

## 4. Delivery safety status

Delivery creation, reconciliation, webhook processing, tracking reconciliation, status publication and Borzo remain disabled until the controlled rollout.

## 5. Stacked development model

Every module is a separate branch and draft PR. No direct write was made to `main`.

## 6. Pre-existing parent PR

PR #25: Order delivery-status downstream consumer.

## 7. Pre-existing APIM PR

PR #26: customer delivery-status APIM operation.

## 8. Pre-existing Next.js tracking PR

PR #27: Next.js customer delivery tracking foundation.

## 9. Module 1 branch

`feature/customer-web-next-auth-session`.

## 10. Module 1 PR

PR #28: secure Next.js phone OTP session.

## 11. Module 1 scope

Firebase Web phone OTP, server-side Craves exchange, HTTP-only cookie, identity BFF and logout.

## 12. Module 1 security

No Craves token is returned to browser JavaScript or stored in browser storage.

## 13. Module 1 cookie

`craves_access_token`, HTTP-only, SameSite=Lax, root path and Secure in production.

## 14. Module 1 cross-site control

Session creation and logout require a same-origin request.

## 15. Module 1 Firebase setup

Firebase Web public application configuration is injected during the Docker build.

## 16. Module 1 runtime setup

`CRAVES_API_BASE_URL` remains a Container App runtime environment variable.

## 17. Module 1 CI

`azure-pipelines-customer-web-next-auth-ci.yml`.

## 18. Module 1 deployment

The existing guarded Next.js deployment pipeline now requires Firebase web values.

## 19. Module 1 manual work

Enable/confirm Phone Auth, add final authorized domains and configure test phone numbers.

## 20. Module 1 deferred work

Password authentication and refresh-token rotation require approved Java Auth Service contracts.

## 21. Module 2 branch

`feature/customer-web-next-orders`.

## 22. Module 2 PR

PR #29: customer order history and details.

## 23. Module 2 routes

`/orders` and `/orders/{orderId}`.

## 24. Module 2 BFF

Authenticated no-store BFF routes forward only the HTTP-only cookie token.

## 25. Module 2 privacy

Customer identity ID, kitchen pickup address and chef-private contact data are removed.

## 26. Module 2 amounts

Order amounts use the backend-provided INR/currency contract; no pricing logic is recalculated in the client.

## 27. Module 2 APIM operations

`GET /` and `GET /{orderId}` on the existing `api/v1/orders` API.

## 28. Module 2 APIM guard

Exactly one Order API path owner is required.

## 29. Module 2 backend-policy guard

Inherited `backend-id` policies fail the rollout because they cannot safely be overridden with an operation base URL.

## 30. Module 2 CI

`azure-pipelines-customer-web-next-orders-ci.yml`.

## 31. Module 2 APIM pipeline

`azure-pipelines-order-customer-read-apim.yml`.

## 32. Module 2 manual test

Verify owned list/detail, unowned order privacy and logout behavior.

## 33. Module 3 branch

`feature/customer-web-next-notifications`.

## 34. Module 3 PR

PR #30: customer notification inbox.

## 35. Module 3 route

`/notifications`.

## 36. Module 3 backend reads

`GET /api/v1/notifications/in-app?limit=50`.

## 37. Module 3 backend write

`PATCH /api/v1/notifications/in-app/{noticeId}/read`.

## 38. Module 3 same-origin control

Mark-read requests require a same-origin PATCH.

## 39. Module 3 privacy

Raw payloads, internal event keys, provider transaction IDs and dispatch metadata are excluded.

## 40. Module 3 UI

Unread count, chronological notices, refresh and mark-read behavior.

## 41. Module 3 CI

`azure-pipelines-customer-web-next-notifications-ci.yml`.

## 42. Module 3 Azure requirement

No new APIM route or Notification Service deployment is required for this module.

## 43. Module 4 branch

`feature/customer-mobile-auth-foundation`.

## 44. Module 4 PR

PR #31: React Native customer auth foundation.

## 45. Module 4 stack

React Native 0.86, React 19, TypeScript, React Navigation and React Native Firebase.

## 46. Module 4 identity

Application name `CravesCustomer`; proposed Android package `in.craves.customer`.

## 47. Module 4 native OTP

Native Firebase Phone Authentication performs Android/iOS app verification and OTP confirmation.

## 48. Module 4 exchange

Firebase ID token is exchanged through APIM with the existing Craves Auth endpoint.

## 49. Module 4 secure storage

`react-native-keychain` stores the session with device-only unlocked-device protection.

## 50. Module 4 forbidden storage

No AsyncStorage, plaintext preferences, clipboard or logs are used for access tokens.

## 51. Module 4 refresh limitation

Any refresh token in the backend exchange response is discarded.

## 52. Module 4 local expiry

The session expires locally up to thirty seconds before backend expiry.

## 53. Module 4 sign-out

Secure storage and Firebase authentication state are cleared.

## 54. Module 4 native shells

Android/iOS folders are generated later by the no-overwrite bootstrap.

## 55. Module 4 bootstrap

`scripts/mobile/bootstrap-customer-mobile-native.sh`.

## 56. Module 4 bootstrap confirmation

`CONFIRM_NATIVE_BOOTSTRAP=true` is mandatory.

## 57. Module 4 CI

`azure-pipelines-customer-mobile-auth-ci.yml`.

## 58. Module 4 Firebase files

`google-services.json` and `GoogleService-Info.plist` are manual and gitignored.

## 59. Module 4 signing

Android keystore, Apple certificates and provisioning profiles remain outside Git.

## 60. Module 4 native-build gate

Android/iOS compilation begins only after native shells and test Firebase configuration are reviewed.

## 61. Module 5 branch

`feature/customer-mobile-delivery-tracking`.

## 62. Module 5 scope

Native provider-neutral delivery status, progress and chronological history.

## 63. Module 5 API

`GET /api/v1/orders/{orderId}/delivery-status`.

## 64. Module 5 ownership

Order Service remains authoritative for customer ownership.

## 65. Module 5 response parser

Unknown statuses and malformed identifiers/timestamps fail closed.

## 66. Module 5 privacy

Provider delivery IDs, raw callback bodies and internal worker metadata are removed.

## 67. Module 5 link safety

Only HTTPS tracking URLs can be opened.

## 68. Module 5 polling

Thirty-second polling while the application is active.

## 69. Module 5 background behavior

Polling stops while the application is inactive or in the background.

## 70. Module 5 terminal behavior

Polling stops for delivered, cancelled, returned and failed.

## 71. Module 5 refresh

Pull-to-refresh remains available.

## 72. Module 5 session expiry

HTTP 401 clears the mobile session.

## 73. Module 5 lookup

The temporary UI accepts a chef-specific order UUID.

## 74. Module 5 future integration

A native order-list module should replace manual UUID entry.

## 75. Module 5 CI

`azure-pipelines-customer-mobile-delivery-tracking-ci.yml`.

## 76. Module 5 deployment

No store/deployment pipeline is included before signing and native-shell decisions are completed.

## 77. Code-only completion

All five modules are pushed as draft, stacked branches.

## 78. No business logic invented

No pricing, commission, delivery radius, GST, FSSAI, cancellation, compensation or refund-entitlement rule was created.

## 79. No provider activation

No Borzo network operation or callback registration was introduced.

## 80. No new Azure resource

No Container App, APIM instance, database, Service Bus entity, Key Vault or paid SKU was provisioned.

## 81. Pipeline phase 1

Run PR #25 downstream CI on its exact branch head.

## 82. Pipeline phase 2

Merge PR #25 and deploy Order Service with the consumer disabled.

## 83. Pipeline phase 3

Verify Flyway V9 and fail-closed flags.

## 84. Pipeline phase 4

Enable and validate the Order delivery-status consumer.

## 85. Pipeline phase 5

Enable Integration status publisher only after consumer/DLQ validation.

## 86. Pipeline phase 6

Run PR #26 APIM CI, merge and configure delivery-status APIM.

## 87. Pipeline phase 7

Run PR #27 Next.js tracking CI and create the reviewed package lock amendment.

## 88. Pipeline phase 8

Run PR #28 auth CI using placeholders only for build validation.

## 89. Pipeline phase 9

Run PR #29 orders CI and order-read APIM rollout.

## 90. Pipeline phase 10

Run PR #30 notification CI.

## 91. Pipeline phase 11

Run PR #31 mobile auth CI.

## 92. Pipeline phase 12

Run the final mobile delivery-tracking CI.

## 93. Web manual phase

Configure Firebase Web values and authorized domain after all web CI gates succeed.

## 94. Web deployment phase

Record the current legacy web image and run the guarded Next.js replacement once authentication is validated.

## 95. Mobile manual phase

Generate native shells, register Firebase Android/iOS apps and add local config files.

## 96. Mobile build phase

Run Android debug and iOS simulator/device builds before signing work.

## 97. Mobile store phase

Configure Google Play and Apple Developer signing only after functional acceptance.

## 98. Acceptance data

Use Firebase test identities and controlled synthetic/non-production orders; do not use real customer data initially.

## 99. Rollback principle

Rollback switches/images/operations only; never delete durable order, delivery, notification, refund or audit records.

## 100. Current handoff

The code side for these five modules is complete. Remaining work is CI validation, controlled merge/deployment, Firebase/native configuration, APIM execution and final acceptance testing.
