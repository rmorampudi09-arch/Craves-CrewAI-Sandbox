# Customer Mobile Subscription Management — Handover

Date: 2026-07-30

## Scope
Authenticated enrollment, owned subscription list, pause and cancel in React Native.

## Branch
`feature/customer-mobile-subscription-management`.

## Dependencies
Subscription backend hardening, mobile auth, mobile addresses and mobile plan discovery.

## Screens

```text
SubscriptionEnrollment
Subscriptions
```

## Enrollment
Loads the chosen active plan and customer-owned active saved addresses. The customer enters a non-past date in `YYYY-MM-DD` format and optional notes.

## APIs

```text
POST /api/v1/subscriptions
GET /api/v1/subscriptions
PATCH /api/v1/subscriptions/{id}/pause
PATCH /api/v1/subscriptions/{id}/cancel
```

## Authentication
All owned operations use the Keychain/Keystore-backed Craves access session.

## Session expiry
HTTP 401 clears the local secure session through the existing AuthProvider.

## Privacy
Customer identity and chef identity UUIDs are excluded by the mobile contracts and CI.

## Storage
Subscription and payment data are not stored in AsyncStorage or appended to the secure auth session.

## Lifecycle
Only pause and cancel are exposed. Backend remains authoritative for allowed transitions.

## Product boundaries
Renewal, resume, credits, refunds, payouts, holidays and cancellation cutoffs remain undefined.

## Native dependencies
No date picker, calendar, payment SDK or notification dependency is added.

## Navigation
Customer Home → My subscriptions or Browse meal plans.

## Main files

```text
src/screens/SubscriptionEnrollmentScreen.tsx
src/screens/SubscriptionsScreen.tsx
src/subscriptions/api.ts
src/subscriptions/contracts.ts
src/navigation/RootNavigator.tsx
```

## CI
`azure-pipelines-customer-mobile-subscription-management-ci.yml`.

## Azure/APIM
No runtime or gateway change was executed.

## Manual later
Run CI, configure subscription APIM routes, deploy Subscription Service, complete native builds, and validate with two customer identities for ownership isolation.

## Rollback
Restore the previous mobile source/release. Do not delete subscription or audit records.

## Acceptance
A customer can enroll with an owned saved address, list only owned subscriptions, pause/cancel allowed states, and is signed out on expired session.
