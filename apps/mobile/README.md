# Craves Mobile (React Native bootstrap)

This directory contains the **minimal React Native bootstrap shell** for Craves mobile.

## Scope
- React Native + TypeScript only
- Bootstrap shell only; no production feature implementation
- Safe for CI/docs alignment during build-train convergence

## Current status
- `src/App.tsx` provides a lightweight architecture status screen
- Firebase integration is documented as a placeholder and must be wired through environment/config at implementation time
- API integration is represented by a typed config module and a health-check style client helper only
- No signing certificates, secrets, or store metadata are committed here

## Scripts
- `npm run start`
- `npm run android`
- `npm run ios`
- `npm run test`
- `npm run typecheck`

## Firebase integration
This bootstrap does **not** commit Firebase credentials. When product scope is approved:
- add platform-specific Firebase configuration outside source control or through approved secure handling
- map runtime values via `src/config/env.ts`
- keep admin/service credentials out of the app

## API integration
The shell reads endpoint settings from `src/config/env.ts` and uses `src/services/api/client.ts` for typed connectivity checks.

Expected variables:
- `CRAVES_API_BASE_URL`
- `CRAVES_FIREBASE_PROJECT_ID` (optional placeholder)
- `CRAVES_ENV_NAME`

## Testing
- Jest smoke test for app shell rendering
- Config tests for API/env mapping

## Store actions
App Store submission: **PENDING MANUAL ACTION**
Google Play submission: **PENDING MANUAL ACTION**
