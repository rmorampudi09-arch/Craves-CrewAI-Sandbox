# Admin account intervention web

## Purpose

Provides the Craves administrator with a guarded UI for an exact Auth identity UUID.

## Routes

- `GET /api/admin/accounts/{identityId}`
- `POST /api/admin/accounts/{identityId}`
- `/admin/accounts`

The BFF maps to the existing Auth Service contracts:

- `GET /api/v1/admin/accounts/{identityId}/intervention-status`
- `POST /api/v1/admin/accounts/{identityId}/suspend`
- `POST /api/v1/admin/accounts/{identityId}/reactivate`

## Safety controls

- existing HTTP-only Craves session
- backend ADMIN authorization on every call
- exact UUID only
- status must be loaded before intervention
- 10–500 character audit reason
- exact typed `SUSPEND` or `REACTIVATE` confirmation
- same-origin mutation guard
- privacy-reduced parser; no Firebase UID or raw phone exposure
- no browser storage or sensitive logging
- no-store responses

## Activation dependency

The backend API and Firebase synchronization worker remain disabled until their existing controlled activation pipeline is reviewed and executed.

## Local verification

```bash
cd apps/customer-web-next
npm install --ignore-scripts
npm run typecheck
npm run test
npm run build
```

No Azure, Firebase or account state is changed by local build verification.
