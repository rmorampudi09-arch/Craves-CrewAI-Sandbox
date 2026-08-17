# Customer Mobile Subscription Plans — Handover

Date: 2026-07-30

## Scope
Adds active meal-plan discovery to the secure React Native customer app.

## Branch
`feature/customer-mobile-subscription-plans`.

## Dependency
PR #58 backend privacy hardening, PR #59 web plan contract, and the existing mobile auth foundation.

## Screen
`SubscriptionPlans`.

## API
`GET /api/v1/subscriptions/plans`.

## Contract
Plan ID, plan code, name, description, weekly/monthly period, amount and currency.

## Privacy
Chef identity and all customer identity fields are excluded by the parser and CI.

## Pricing boundary
The app formats only the backend amount and currency.

## Product boundaries
Renewal, unused meals, refunds, credits, payouts and holiday rules remain undefined.

## Refresh
Pull-to-refresh reloads active plans.

## Error handling
Timeout, network and malformed-response errors are controlled.

## Storage
No plan or subscription data is persisted in AsyncStorage or device secure storage.

## Placeholder
`SubscriptionEnrollmentPendingScreen` prevents accidental enrollment and keeps this branch independently runnable.

## Navigation
Customer Home → Meal subscriptions → Choose plan.

## Main files

```text
src/subscriptions/contracts.ts
src/subscriptions/api.ts
src/screens/SubscriptionPlansScreen.tsx
src/screens/SubscriptionEnrollmentPendingScreen.tsx
src/navigation/RootNavigator.tsx
```

## Tests
Contract tests verify privacy and supported billing periods.

## CI
`azure-pipelines-customer-mobile-subscription-plans-ci.yml`.

## Native work
No Android/iOS shell, signing, Firebase file or store action was performed.

## Azure
No gateway or Container App change was performed.

## Rollback
Restore the previous mobile source/image branch; no backend data cleanup is required.

## Acceptance
Plans load, backend amounts render, malformed/private payloads fail closed, and selecting a plan opens the non-mutating placeholder.
